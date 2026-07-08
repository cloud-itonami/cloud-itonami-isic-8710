(ns nursing.nursingadvisor
  "NursingOps-LLM client -- the *contained intelligence node* for the
  nursing actor (README: \"Nursing Care Advisor\").

  It normalizes resident intake, drafts a per-jurisdiction residential-
  nursing-care evidence checklist, screens residents for a not-current
  nursing-staff credential, drafts the medication-administration
  action, and drafts the incident-response-finalization action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real medication administration/incident-
  response finalization. Every output is censored downstream by
  `nursing.governor` before anything touches the SSoT, and `:actuation/
  administer-medication`/`:actuation/finalize-incident-response`
  proposals NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/administer-medication | :actuation/finalize-incident-response | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [nursing.facts :as facts]
            [nursing.registry :as registry]
            [nursing.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the resident, jurisdiction or medication order. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "入所者記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :resident/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-careplan
  "Per-jurisdiction residential-nursing-care evidence checklist draft.
  `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `nursing.facts` -- the Nursing Care Governor must reject this
  (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [r (store/resident db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction r))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "nursing.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :careplan/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :careplan/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-credential
  "Nursing-staff credential-currency screening draft.
  `:credential-not-current?` on the resident record injects the
  failure mode: the Nursing Care Governor must HOLD, un-overridably,
  on any not-current credential."
  [db {:keys [subject]}]
  (let [r (store/resident db subject)]
    (cond
      (nil? r)
      {:summary "対象入所者記録が見つかりません" :rationale "no resident record"
       :cites [] :effect :credential-screen/set :value {:resident-id subject :credential-not-current? nil}
       :stake nil :confidence 0.0}

      (true? (:credential-not-current? r))
      {:summary    (str (:resident-name r) ": 担当看護職員の資格が最新でないことを検出")
       :rationale  "スクリーニングが資格の失効を検出。人手確認とホールドが必須。"
       :cites      [:credential-check]
       :effect     :credential-screen/set
       :value      {:resident-id subject :credential-not-current? true}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:resident-name r) ": 担当看護職員の資格は最新")
       :rationale  "資格スクリーニング完了。"
       :cites      [:credential-check]
       :effect     :credential-screen/set
       :value      {:resident-id subject :credential-not-current? false}
       :stake      nil
       :confidence 0.9})))

(defn- propose-medication-administration
  "Draft the actual MEDICATION-ADMINISTRATION action -- administering a
  real medication dose to a resident. ALWAYS `:stake :actuation/
  administer-medication` -- this is a REAL-WORLD clinical act, never a
  draft the actor may auto-run. See README `Actuation`: no phase ever
  adds this op to a phase's `:auto` set (`nursing.phase`); the governor
  also always escalates on `:actuation/administer-medication`. Two
  independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [r (store/resident db subject)]
    {:summary    (str subject " 向け与薬提案"
                      (when r (str " (resident=" (:resident-name r) ")")))
     :rationale  (if r
                   (str "proposed-medication=" (:proposed-medication r)
                        " medication-dosage-mg=" (:medication-dosage-mg r)
                        " medication-max-authorized-dosage-mg=" (:medication-max-authorized-dosage-mg r))
                   "入所者記録が見つかりません")
     :cites      (if r [subject] [])
     :effect     :resident/mark-administered
     :value      {:resident-id subject}
     :stake      :actuation/administer-medication
     :confidence (if (and r (not (registry/medication-contraindicated? r))
                          (not (registry/medication-dosage-exceeds-maximum? r))) 0.9 0.3)}))

(defn- propose-incident-response-finalization
  "Draft the actual INCIDENT-RESPONSE-FINALIZATION action -- finalizing
  a real incident response for a resident. ALWAYS `:stake :actuation/
  finalize-incident-response` -- this is a REAL-WORLD incident-response
  act, never a draft the actor may auto-run. See README `Actuation`: no
  phase ever adds this op to a phase's `:auto` set (`nursing.phase`);
  the governor also always escalates on `:actuation/finalize-incident-
  response`. Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [r (store/resident db subject)]
    {:summary    (str subject " 向けインシデント対応完了提案"
                      (when r (str " (resident=" (:resident-name r) ")")))
     :rationale  (if r
                   (str "credential-not-current?=" (:credential-not-current? r))
                   "入所者記録が見つかりません")
     :cites      (if r [subject] [])
     :effect     :resident/mark-finalized
     :value      {:resident-id subject}
     :stake      :actuation/finalize-incident-response
     :confidence (if (and r (not (:credential-not-current? r))) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :resident/intake                          (normalize-intake db request)
    :careplan/verify                          (verify-careplan db request)
    :credential/screen                        (screen-credential db request)
    :actuation/administer-medication          (propose-medication-administration db request)
    :actuation/finalize-incident-response     (propose-incident-response-finalization db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは特別養護老人ホーム運営事業の与薬・インシデント対応完了"
       "エージェントの助言者です。与えられた事実のみに基づき、提案を1つだけEDNマップで"
       "返します。説明や前置きは一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:resident/upsert|:careplan/set|:credential-screen/set|"
       ":resident/mark-administered|:resident/mark-finalized) "
       ":stake(:actuation/administer-medication か :actuation/finalize-incident-response か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :careplan/verify                          {:resident (store/resident st subject)}
    :credential/screen                        {:resident (store/resident st subject)}
    :actuation/administer-medication          {:resident (store/resident st subject)}
    :actuation/finalize-incident-response     {:resident (store/resident st subject)}
    {:resident (store/resident st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Nursing Care Governor
  escalates/holds -- an LLM hiccup can never auto-administer medication
  or auto-finalize an incident response."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :nursingadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
