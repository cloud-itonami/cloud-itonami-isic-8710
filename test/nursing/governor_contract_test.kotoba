(ns nursing.governor-contract-test
  "The governor contract as executable tests -- the nursing analog of
  `cloud-itonami-isic-6512`'s `casualty.governor-contract-test`. The
  single invariant under test:

    NursingOps-LLM never administers medication or finalizes an
    incident response the Nursing Care Governor would reject,
    `:actuation/administer-medication`/`:actuation/finalize-incident-
    response` NEVER auto-commit at any phase, `:resident/intake` (no
    direct capital risk) MAY auto-commit when clean, and every
    decision (commit OR hold) leaves exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [nursing.store :as store]
            [nursing.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :licensed-nurse :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- verify!
  "Walks `subject` through verify -> approve, leaving a careplan on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-verify") {:op :careplan/verify :subject subject} operator)
  (approve! actor (str tid-prefix "-verify")))

(defn- screen!
  "Walks `subject` through credential-currency screening -> approve,
  leaving a screening on file. Only safe to call for a resident whose
  credential is already current -- a not-current credential HARD-holds
  the screen itself (see
  `credential-not-current-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :credential/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :resident/intake :subject "resident-1"
                   :patch {:id "resident-1" :resident-name "Sato Kenji"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sato Kenji" (:resident-name (store/resident db "resident-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest careplan-verify-always-needs-approval
  (testing "verify is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :careplan/verify :subject "resident-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/careplan-of db "resident-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a careplan/verify proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :careplan/verify :subject "resident-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/careplan-of db "resident-1")) "no careplan written"))))

(deftest administer-medication-without-careplan-is-held
  (testing "actuation/administer-medication before any careplan verification -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :actuation/administer-medication :subject "resident-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest medication-contraindicated-is-held
  (testing "a resident whose proposed medication appears on its own contraindication list -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t5pre" "resident-3")
          res (exec-op actor "t5" {:op :actuation/administer-medication :subject "resident-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:medication-contraindicated} (-> (store/ledger db) last :basis)))
      (is (empty? (store/administration-history db))))))

(deftest medication-dosage-exceeds-maximum-is-held
  (testing "a resident whose own proposed dosage exceeds their own recorded maximum -> HOLD"
    (let [[db actor] (fresh)
          _ (verify! actor "t6pre" "resident-5")
          res (exec-op actor "t6" {:op :actuation/administer-medication :subject "resident-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:medication-dosage-exceeds-maximum} (-> (store/ledger db) last :basis)))
      (is (empty? (store/administration-history db))))))

(deftest credential-not-current-is-held-and-unoverridable
  (testing "a not-current nursing-staff credential on a resident -> HOLD, and never reaches request-approval -- exercised via :credential/screen DIRECTLY, not via the actuation op against an unscreened resident (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's, behavioral's, secondary's, card's, water's, telecom's, aerospace's, recovery's, consulting's, union's, congregation's, fab's, energy's, care's, navigator's, learning's, banking's, advertising's, polling's, research's and design's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t7" {:op :credential/screen :subject "resident-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:credential-not-current} (-> (store/ledger db) first :basis)))
      (is (nil? (store/credential-screen-of db "resident-4")) "no clearance written"))))

(deftest administer-medication-always-escalates-then-human-decides
  (testing "a clean, fully-assessed resident still ALWAYS interrupts for human approval -- actuation/administer-medication is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t8pre" "resident-1")
          r1 (exec-op actor "t8" {:op :actuation/administer-medication :subject "resident-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, administration record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:medication-administered? (store/resident db "resident-1"))))
          (is (= 1 (count (store/administration-history db))) "one draft administration record"))))))

(deftest finalize-incident-response-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, credential-current resident still ALWAYS interrupts for human approval -- actuation/finalize-incident-response is never auto"
    (let [[db actor] (fresh)
          _ (verify! actor "t9pre" "resident-1")
          _ (screen! actor "t9pre2" "resident-1")
          r1 (exec-op actor "t9" {:op :actuation/finalize-incident-response :subject "resident-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, finalization record drafted"
        (let [r2 (approve! actor "t9")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:incident-response-finalized? (store/resident db "resident-1"))))
          (is (= 1 (count (store/finalization-history db))) "one draft finalization record"))))))

(deftest administer-medication-double-administration-is-held
  (testing "administering medication to the same resident twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t10pre" "resident-1")
          _ (exec-op actor "t10a" {:op :actuation/administer-medication :subject "resident-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :actuation/administer-medication :subject "resident-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-administered} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/administration-history db))) "still only the one earlier administration"))))

(deftest finalize-incident-response-double-finalization-is-held
  (testing "finalizing the same resident's incident response twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (verify! actor "t11pre" "resident-1")
          _ (screen! actor "t11pre2" "resident-1")
          _ (exec-op actor "t11a" {:op :actuation/finalize-incident-response :subject "resident-1"} operator)
          _ (approve! actor "t11a")
          res (exec-op actor "t11" {:op :actuation/finalize-incident-response :subject "resident-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-finalized} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/finalization-history db))) "still only the one earlier finalization"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :resident/intake :subject "resident-1"
                          :patch {:id "resident-1" :resident-name "Sato Kenji"}} operator)
      (exec-op actor "b" {:op :careplan/verify :subject "resident-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
