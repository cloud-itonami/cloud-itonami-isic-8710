(ns nursing.governor
  "Nursing Care Governor -- the independent compliance layer that earns
  the NursingOps-LLM the right to commit. The LLM has no notion of
  jurisdictional residential-nursing-care law, whether a proposed
  medication actually appears on a resident's own recorded
  contraindication list, whether a resident's own proposed dosage
  actually stays within their own recorded maximum-authorized dosage,
  whether the assigned nursing staff's own credential actually stays
  current, or when an act stops being a draft and becomes a real-world
  medication administration or incident-response finalization, so this
  MUST be a separate system able to *reject* a proposal and fall back
  to HOLD -- the residential-nursing-care analog of `cloud-itonami-
  isic-6512`'s CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete evidence, a
  contraindicated medication, a dosage above a resident's own
  recorded maximum, a not-current nursing-staff credential, or a
  double administration/finalization). The confidence/actuation gate
  is SOFT: it asks a human to look (low confidence / actuation), and
  the human may approve -- but see `nursing.phase`: for `:stake
  :actuation/administer-medication`/`:actuation/finalize-incident-
  response` (a real medication administration or a real incident-
  response finalization) NO phase ever allows auto-commit either. Two
  independent layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the care-plan proposal cite
                                       an OFFICIAL source (`nursing.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/administer-
                                       medication`/`:actuation/
                                       finalize-incident-response`, has
                                       the resident actually been
                                       assessed with a full resident-
                                       consent-record/care-plan-record/
                                       medication-administration-
                                       record-verification/incident-
                                       report-record evidence checklist
                                       on file?
    3. Medication contraindicated  -- for `:actuation/administer-
                                       medication`, INDEPENDENTLY
                                       recompute whether the resident's
                                       own proposed medication appears
                                       in its own recorded
                                       contraindication set (`nursing.
                                       registry/medication-
                                       contraindicated?`) -- needs no
                                       proposal inspection at all. The
                                       THIRD literal reuse of `clinic.
                                       governor/contraindicated-
                                       violations`'s set-membership/
                                       conflict shape (`veterinary.
                                       governor/contraindicated-
                                       violations` established the
                                       second).
    4. Medication dosage exceeds
       maximum                        -- for `:actuation/administer-
                                       medication`, INDEPENDENTLY
                                       recompute whether the resident's
                                       own recorded proposed dosage
                                       exceeds their own recorded
                                       maximum-authorized dosage
                                       (`nursing.registry/medication-
                                       dosage-exceeds-maximum?`) --
                                       needs no proposal inspection at
                                       all. The EIGHTH instance of this
                                       fleet's MAXIMUM-ceiling check
                                       family (`facility.registry/
                                       occupancy-exceeds-capacity?`/
                                       `school.registry/class-size-
                                       exceeds-maximum?`/`card.
                                       registry/settlement-amount-
                                       exceeds-authorized?`/`recovery.
                                       registry/contamination-
                                       percentage-exceeds-maximum?`/
                                       `care.registry/caregiver-
                                       workload-exceeds-maximum?`/
                                       `navigator.registry/
                                       eligibility-window-elapsed-
                                       exceeds-validity?`/
                                       `advertising.registry/media-
                                       spend-exceeds-authorized?`
                                       established the first seven).
    5. Credential not current      -- reported by THIS proposal itself
                                       (a `:credential/screen` that
                                       just found a lapsed nursing-
                                       staff credential), or already on
                                       file for the resident
                                       (`:credential/screen`/
                                       `:actuation/administer-
                                       medication`/`:actuation/
                                       finalize-incident-response`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `casualty.
                                       governor/sanctions-violations`/
                                       ...(forty-one prior siblings,
                                       most recently `design.governor/
                                       ip-licensing-conflict-
                                       unresolved-violations`)...
                                       established -- the FORTY-SECOND
                                       distinct application of this
                                       exact discipline overall, and a
                                       LITERAL reuse of `clinic.
                                       governor/credential-not-current-
                                       violations`'s own concept
                                       (already reused by `hospital`/
                                       `eldercare`/`veterinary`/several
                                       other licensed-professional
                                       siblings) -- not claimed as new,
                                       and directly grounded in this
                                       blueprint's own operator-guide
                                       text 'licensed-professional
                                       sign-off required before any
                                       determination'.
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       administer-medication`/
                                       `:actuation/finalize-incident-
                                       response` (REAL clinical/
                                       incident-response acts) ->
                                       escalate.

  Two more guards, double-administration/double-finalization
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-administered-violations`/`already-finalized-violations`
  refuse to administer medication/finalize an incident response for
  the SAME resident twice, off dedicated `:medication-administered?`/
  `:incident-response-finalized?` facts (never a `:status` value) --
  the SAME 'check a dedicated boolean, not status' discipline every
  prior sibling governor's guards establish, informed by `cloud-
  itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [nursing.facts :as facts]
            [nursing.registry :as registry]
            [nursing.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Administering a real medication dose and finalizing a real incident
  response are the two real-world actuation events this actor
  performs -- a two-member set, matching every prior dual-actuation
  sibling's shape. Both are POSITIVE actuations (administering/
  finalizing a record), matching this fleet's majority actuation shape
  (3600/6190 remain the only negative-actuation exceptions)."
  #{:actuation/administer-medication :actuation/finalize-incident-response})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:careplan/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  residential-nursing-care requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:careplan/verify :actuation/administer-medication :actuation/finalize-incident-response} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は施設運営基準として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/administer-medication`/`:actuation/finalize-
  incident-response`, the jurisdiction's required resident-consent-
  record/care-plan-record/medication-administration-record-
  verification/incident-report-record evidence must actually be
  satisfied -- do not trust the advisor's self-reported confidence
  alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/administer-medication :actuation/finalize-incident-response} op)
    (let [r (store/resident st subject)
          careplan (store/careplan-of st subject)]
      (when-not (and careplan
                     (facts/required-evidence-satisfied?
                      (:jurisdiction r) (:checklist careplan)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(入所者同意記録/ケアプラン記録/与薬記録確認記録/事故報告記録等)が充足していない状態での提案"}]))))

(defn- medication-contraindicated-violations
  "For `:actuation/administer-medication`, INDEPENDENTLY recompute
  whether the resident's own proposed medication appears in its own
  recorded contraindication set via `nursing.registry/medication-
  contraindicated?` -- needs no proposal inspection at all, since its
  inputs are permanent ground-truth fields already on the resident."
  [{:keys [op subject]} st]
  (when (= op :actuation/administer-medication)
    (let [r (store/resident st subject)]
      (when (registry/medication-contraindicated? r)
        [{:rule :medication-contraindicated
          :detail (str subject " の提案薬剤(" (:proposed-medication r)
                      ")が入所者自身の禁忌リスト" (:medication-contraindications r) "に含まれている")}]))))

(defn- medication-dosage-exceeds-maximum-violations
  "For `:actuation/administer-medication`, INDEPENDENTLY recompute
  whether the resident's own recorded proposed dosage exceeds their
  own recorded maximum-authorized dosage via `nursing.registry/
  medication-dosage-exceeds-maximum?` -- needs no proposal inspection
  at all, since its inputs are permanent ground-truth fields already
  on the resident."
  [{:keys [op subject]} st]
  (when (= op :actuation/administer-medication)
    (let [r (store/resident st subject)]
      (when (registry/medication-dosage-exceeds-maximum? r)
        [{:rule :medication-dosage-exceeds-maximum
          :detail (str subject " の提案投与量(" (:medication-dosage-mg r)
                      "mg)が上限(" (:medication-max-authorized-dosage-mg r) "mg)を超過")}]))))

(defn- credential-not-current-violations
  "A not-current nursing-staff credential -- reported by THIS proposal
  (e.g. a `:credential/screen` that itself just found a lapsed
  credential), or already on file in the store for the resident
  (`:credential/screen`/`:actuation/administer-medication`/
  `:actuation/finalize-incident-response`) -- is a HARD, un-overridable
  hold. Evaluated UNCONDITIONALLY (not scoped to a specific op) so the
  screening op itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (true? (get-in proposal [:value :credential-not-current?]))
        resident-id (when (contains? #{:credential/screen :actuation/administer-medication :actuation/finalize-incident-response} op) subject)
        hit-on-file? (and resident-id (true? (:credential-not-current? (store/resident st resident-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :credential-not-current
        :detail "看護職員の資格が最新でない状態での提案は進められない"}])))

(defn- already-administered-violations
  "For `:actuation/administer-medication`, refuses to administer
  medication to the SAME resident twice, off a dedicated `:medication-
  administered?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/administer-medication)
    (when (store/resident-already-administered? st subject)
      [{:rule :already-administered
        :detail (str subject " は既に与薬済み")}])))

(defn- already-finalized-violations
  "For `:actuation/finalize-incident-response`, refuses to finalize an
  incident response for the SAME resident twice, off a dedicated
  `:incident-response-finalized?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/finalize-incident-response)
    (when (store/resident-already-finalized? st subject)
      [{:rule :already-finalized
        :detail (str subject " は既にインシデント対応完了済み")}])))

(defn check
  "Censors a NursingOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (medication-contraindicated-violations request st)
                           (medication-dosage-exceeds-maximum-violations request st)
                           (credential-not-current-violations request proposal st)
                           (already-administered-violations request st)
                           (already-finalized-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
