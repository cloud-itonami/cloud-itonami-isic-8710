(ns nursing.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean resident through
  intake -> care-plan verification -> credential-currency screening ->
  medication-administration proposal (always escalates) -> human
  approval -> commit, then through incident-response-finalization
  proposal (always escalates) -> human approval -> commit, then shows
  five HARD holds (a jurisdiction with no spec-basis, a proposed
  medication on the resident's own contraindication list, a proposed
  dosage above the resident's own recorded maximum, a not-current
  nursing-staff credential screened directly via `:credential/screen`
  [never via an actuation op against an unscreened resident -- see this
  actor's own governor ns docstring / the lesson `parksafety`'s
  ADR-2607071922 Decision 5, `eldercare`'s, `museum`'s,
  `conservation`'s, `salon`'s, `entertainment`'s, `casework`'s,
  `hospital`'s, `facility`'s, `school`'s, `association`'s, `leasing`'s,
  `behavioral`'s, `secondary`'s, `card`'s, `water`'s, `telecom`'s,
  `aerospace`'s, `recovery`'s, `consulting`'s, `union`'s,
  `congregation`'s, `fab`'s, `energy`'s, `care`'s, `navigator`'s,
  `learning`'s, `banking`'s, `advertising`'s, `polling`'s, `research`'s
  and `design`'s ADR-0001s already recorded], and a double medication-
  administration/incident-response-finalization of an already-
  processed resident) that never reach a human at all, and prints the
  audit ledger + the draft medication-administration and incident-
  response-finalization records."
  (:require [langgraph.graph :as g]
            [nursing.store :as store]
            [nursing.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :licensed-nurse :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== resident/intake resident-1 (JPN, clean; medication within max dosage, no contraindication) ==")
    (println (exec! actor "t1" {:op :resident/intake :subject "resident-1"
                                :patch {:id "resident-1" :resident-name "Sato Kenji"}} operator))

    (println "== careplan/verify resident-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :careplan/verify :subject "resident-1"} operator))
    (println (approve! actor "t2"))

    (println "== credential/screen resident-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :credential/screen :subject "resident-1"} operator))
    (println (approve! actor "t3"))

    (println "== actuation/administer-medication resident-1 (always escalates -- actuation/administer-medication) ==")
    (let [r (exec! actor "t4" {:op :actuation/administer-medication :subject "resident-1"} operator)]
      (println r)
      (println "-- human licensed nurse approves --")
      (println (approve! actor "t4")))

    (println "== actuation/finalize-incident-response resident-1 (always escalates -- actuation/finalize-incident-response) ==")
    (let [r (exec! actor "t5" {:op :actuation/finalize-incident-response :subject "resident-1"} operator)]
      (println r)
      (println "-- human licensed nurse approves --")
      (println (approve! actor "t5")))

    (println "== careplan/verify resident-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :careplan/verify :subject "resident-2" :no-spec? true} operator))

    (println "== careplan/verify resident-3 (escalates -- human approves; sets up the contraindication test) ==")
    (println (exec! actor "t7" {:op :careplan/verify :subject "resident-3"} operator))
    (println (approve! actor "t7"))

    (println "== actuation/administer-medication resident-3 (warfarin on own contraindication list -> HARD hold) ==")
    (println (exec! actor "t8" {:op :actuation/administer-medication :subject "resident-3"} operator))

    (println "== credential/screen resident-4 (not-current -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :credential/screen :subject "resident-4"} operator))

    (println "== careplan/verify resident-5 (escalates -- human approves; sets up the dosage-ceiling test) ==")
    (println (exec! actor "t10" {:op :careplan/verify :subject "resident-5"} operator))
    (println (approve! actor "t10"))

    (println "== actuation/administer-medication resident-5 (dosage 1500mg > max 1000mg -> HARD hold) ==")
    (println (exec! actor "t11" {:op :actuation/administer-medication :subject "resident-5"} operator))

    (println "== actuation/administer-medication resident-1 AGAIN (double-administration -> HARD hold) ==")
    (println (exec! actor "t12" {:op :actuation/administer-medication :subject "resident-1"} operator))

    (println "== actuation/finalize-incident-response resident-1 AGAIN (double-finalization -> HARD hold) ==")
    (println (exec! actor "t13" {:op :actuation/finalize-incident-response :subject "resident-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft medication-administration records ==")
    (doseq [r (store/administration-history db)] (println r))

    (println "== draft incident-response-finalization records ==")
    (doseq [r (store/finalization-history db)] (println r))))
