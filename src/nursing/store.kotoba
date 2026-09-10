(ns nursing.store
  "SSoT for the nursing actor, behind a `Store` protocol so the backend
  is a swap, not a rewrite -- the same seam every prior `cloud-itonami-
  isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/nursing/store_contract_test.clj), which is the whole point: the
  actor, the Nursing Care Governor and the audit ledger never know
  which SSoT they run on.

  Like every prior dual-actuation sibling, this actor has TWO
  actuation events (administering medication, finalizing an incident
  response) acting on the SAME entity (a `resident`), each with its OWN
  history collection, sequence counter and dedicated double-actuation-
  guard boolean (`:medication-administered?`/`:incident-response-
  finalized?`, never a `:status` value) -- the same discipline every
  prior sibling governor's guards establish, informed by `cloud-
  itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320).

  NOTE on naming: the protocol's per-entity accessor is `resident`
  directly -- not a Clojure special form, so no `-of` suffix workaround
  was needed (matching `cloud-itonami-isic-7410`'s own `project`
  accessor precedent).

  The ledger stays append-only on every backend: 'which resident was
  screened for a not-current credential, which medication dose was
  administered, which incident response was finalized, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a family trusting a nursing-care
  operator needs, and the evidence an operator needs if an
  administration or finalization decision is later disputed."
  (:require [nursing.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (resident [s id])
  (all-residents [s])
  (credential-screen-of [s resident-id] "committed credential-currency screening verdict for a resident, or nil")
  (careplan-of [s resident-id] "committed care-plan evidence assessment, or nil")
  (ledger [s])
  (administration-history [s] "the append-only medication-administration history (nursing.registry drafts)")
  (finalization-history [s] "the append-only incident-response-finalization history (nursing.registry drafts)")
  (next-administration-sequence [s jurisdiction] "next administration-number sequence for a jurisdiction")
  (next-finalization-sequence [s jurisdiction] "next incident-number sequence for a jurisdiction")
  (resident-already-administered? [s resident-id] "has this resident's medication already been administered?")
  (resident-already-finalized? [s resident-id] "has this resident's incident response already been finalized?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-residents [s residents] "replace/seed the resident directory (map id->resident)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained resident set covering both actuation
  lifecycles (administering medication, finalizing an incident
  response) plus each distinctive HARD-check failure mode so the actor
  + tests run offline."
  []
  {:residents
   {"resident-1" {:id "resident-1" :resident-name "Sato Kenji"
                 :proposed-medication :acetaminophen :medication-contraindications #{}
                 :medication-dosage-mg 500 :medication-max-authorized-dosage-mg 1000
                 :credential-not-current? false
                 :medication-administered? false :incident-response-finalized? false
                 :jurisdiction "JPN" :status :intake}
    "resident-2" {:id "resident-2" :resident-name "Atlantis Doe"
                 :proposed-medication :acetaminophen :medication-contraindications #{}
                 :medication-dosage-mg 500 :medication-max-authorized-dosage-mg 1000
                 :credential-not-current? false
                 :medication-administered? false :incident-response-finalized? false
                 :jurisdiction "ATL" :status :intake}
    "resident-3" {:id "resident-3" :resident-name "鈴木花子"
                 :proposed-medication :warfarin :medication-contraindications #{:warfarin}
                 :medication-dosage-mg 5 :medication-max-authorized-dosage-mg 10
                 :credential-not-current? false
                 :medication-administered? false :incident-response-finalized? false
                 :jurisdiction "JPN" :status :intake}
    "resident-4" {:id "resident-4" :resident-name "田中一郎"
                 :proposed-medication :acetaminophen :medication-contraindications #{}
                 :medication-dosage-mg 500 :medication-max-authorized-dosage-mg 1000
                 :credential-not-current? true
                 :medication-administered? false :incident-response-finalized? false
                 :jurisdiction "JPN" :status :intake}
    "resident-5" {:id "resident-5" :resident-name "高橋美咲"
                 :proposed-medication :acetaminophen :medication-contraindications #{}
                 :medication-dosage-mg 1500 :medication-max-authorized-dosage-mg 1000
                 :credential-not-current? false
                 :medication-administered? false :incident-response-finalized? false
                 :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- administer-medication!
  "Backend-agnostic `:resident/mark-administered` -- looks up the
  resident via the protocol and drafts the medication-administration
  record, and returns {:result .. :resident-patch ..} for the caller
  to persist."
  [s resident-id]
  (let [r (resident s resident-id)
        seq-n (next-administration-sequence s (:jurisdiction r))
        result (registry/register-medication-administration resident-id (:jurisdiction r) seq-n)]
    {:result result
     :resident-patch {:medication-administered? true
                      :administration-number (get result "administration_number")}}))

(defn- finalize-incident-response!
  "Backend-agnostic `:resident/mark-finalized` -- looks up the resident
  via the protocol and drafts the incident-response-finalization
  record, and returns {:result .. :resident-patch ..} for the caller to
  persist."
  [s resident-id]
  (let [r (resident s resident-id)
        seq-n (next-finalization-sequence s (:jurisdiction r))
        result (registry/register-incident-response-finalization resident-id (:jurisdiction r) seq-n)]
    {:result result
     :resident-patch {:incident-response-finalized? true
                      :incident-number (get result "incident_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (resident [_ id] (get-in @a [:residents id]))
  (all-residents [_] (sort-by :id (vals (:residents @a))))
  (credential-screen-of [_ id] (get-in @a [:credential-screens id]))
  (careplan-of [_ resident-id] (get-in @a [:careplans resident-id]))
  (ledger [_] (:ledger @a))
  (administration-history [_] (:administrations @a))
  (finalization-history [_] (:finalizations @a))
  (next-administration-sequence [_ jurisdiction] (get-in @a [:administration-sequences jurisdiction] 0))
  (next-finalization-sequence [_ jurisdiction] (get-in @a [:finalization-sequences jurisdiction] 0))
  (resident-already-administered? [_ resident-id] (boolean (get-in @a [:residents resident-id :medication-administered?])))
  (resident-already-finalized? [_ resident-id] (boolean (get-in @a [:residents resident-id :incident-response-finalized?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :resident/upsert
      (swap! a update-in [:residents (:id value)] merge value)

      :careplan/set
      (swap! a assoc-in [:careplans (first path)] payload)

      :credential-screen/set
      (swap! a assoc-in [:credential-screens (first path)] payload)

      :resident/mark-administered
      (let [resident-id (first path)
            {:keys [result resident-patch]} (administer-medication! s resident-id)
            jurisdiction (:jurisdiction (resident s resident-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:administration-sequences jurisdiction] (fnil inc 0))
                       (update-in [:residents resident-id] merge resident-patch)
                       (update :administrations registry/append result))))
        result)

      :resident/mark-finalized
      (let [resident-id (first path)
            {:keys [result resident-patch]} (finalize-incident-response! s resident-id)
            jurisdiction (:jurisdiction (resident s resident-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:finalization-sequences jurisdiction] (fnil inc 0))
                       (update-in [:residents resident-id] merge resident-patch)
                       (update :finalizations registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-residents [s residents] (when (seq residents) (swap! a assoc :residents residents)) s))

(defn seed-db
  "A MemStore seeded with the demo resident set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :careplans {} :credential-screens {} :ledger [] :administration-sequences {}
                           :administrations [] :finalization-sequences {} :finalizations []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Compound values (careplan/credential-screen payloads, ledger facts,
  administration/finalization records) are stored as EDN strings so
  `langchain.db` doesn't expand them into sub-entities -- the same
  convention every sibling actor's store uses."
  {:resident/id                            {:db/unique :db.unique/identity}
   :careplan/resident-id                   {:db/unique :db.unique/identity}
   :credential-screen/resident-id          {:db/unique :db.unique/identity}
   :ledger/seq                             {:db/unique :db.unique/identity}
   :administration/seq                     {:db/unique :db.unique/identity}
   :finalization/seq                       {:db/unique :db.unique/identity}
   :administration-sequence/jurisdiction   {:db/unique :db.unique/identity}
   :finalization-sequence/jurisdiction     {:db/unique :db.unique/identity}})

;; EDN-blob codec (`enc`/`dec*`) comes from `kotoba-lang/langchain-store`
;; (ADR-2607141600) instead of being hand-rolled here.

(defn- resident->tx [{:keys [id resident-name proposed-medication medication-contraindications
                             medication-dosage-mg medication-max-authorized-dosage-mg
                             credential-not-current?
                             medication-administered? incident-response-finalized?
                             jurisdiction status administration-number incident-number]}]
  (cond-> {:resident/id id}
    resident-name                                (assoc :resident/resident-name resident-name)
    proposed-medication                           (assoc :resident/proposed-medication (ls/enc proposed-medication))
    medication-contraindications                  (assoc :resident/medication-contraindications (ls/enc medication-contraindications))
    medication-dosage-mg                          (assoc :resident/medication-dosage-mg medication-dosage-mg)
    medication-max-authorized-dosage-mg           (assoc :resident/medication-max-authorized-dosage-mg medication-max-authorized-dosage-mg)
    (some? credential-not-current?)               (assoc :resident/credential-not-current? credential-not-current?)
    (some? medication-administered?)              (assoc :resident/medication-administered? medication-administered?)
    (some? incident-response-finalized?)          (assoc :resident/incident-response-finalized? incident-response-finalized?)
    jurisdiction                                   (assoc :resident/jurisdiction jurisdiction)
    status                                         (assoc :resident/status status)
    administration-number                          (assoc :resident/administration-number administration-number)
    incident-number                                (assoc :resident/incident-number incident-number)))

(def ^:private resident-pull
  [:resident/id :resident/resident-name :resident/proposed-medication :resident/medication-contraindications
   :resident/medication-dosage-mg :resident/medication-max-authorized-dosage-mg
   :resident/credential-not-current? :resident/medication-administered? :resident/incident-response-finalized?
   :resident/jurisdiction :resident/status :resident/administration-number :resident/incident-number])

(defn- pull->resident [m]
  (when (:resident/id m)
    {:id (:resident/id m) :resident-name (:resident/resident-name m)
     :proposed-medication (ls/dec* (:resident/proposed-medication m))
     :medication-contraindications (ls/dec* (:resident/medication-contraindications m))
     :medication-dosage-mg (:resident/medication-dosage-mg m)
     :medication-max-authorized-dosage-mg (:resident/medication-max-authorized-dosage-mg m)
     :credential-not-current? (boolean (:resident/credential-not-current? m))
     :medication-administered? (boolean (:resident/medication-administered? m))
     :incident-response-finalized? (boolean (:resident/incident-response-finalized? m))
     :jurisdiction (:resident/jurisdiction m) :status (:resident/status m)
     :administration-number (:resident/administration-number m) :incident-number (:resident/incident-number m)}))

(defrecord DatomicStore [conn]
  Store
  (resident [_ id]
    (pull->resident (d/pull (d/db conn) resident-pull [:resident/id id])))
  (all-residents [_]
    (->> (d/q '[:find [?id ...] :where [?e :resident/id ?id]] (d/db conn))
         (map #(pull->resident (d/pull (d/db conn) resident-pull [:resident/id %])))
         (sort-by :id)))
  (credential-screen-of [_ id]
    (ls/dec* (d/q '[:find ?p . :in $ ?rid
                :where [?k :credential-screen/resident-id ?rid] [?k :credential-screen/payload ?p]]
              (d/db conn) id)))
  (careplan-of [_ resident-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?rid
                :where [?a :careplan/resident-id ?rid] [?a :careplan/payload ?p]]
              (d/db conn) resident-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (administration-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :administration/seq ?s] [?e :administration/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (finalization-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :finalization/seq ?s] [?e :finalization/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (next-administration-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :administration-sequence/jurisdiction ?j] [?e :administration-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-finalization-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :finalization-sequence/jurisdiction ?j] [?e :finalization-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (resident-already-administered? [s resident-id]
    (boolean (:medication-administered? (resident s resident-id))))
  (resident-already-finalized? [s resident-id]
    (boolean (:incident-response-finalized? (resident s resident-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :resident/upsert
      (d/transact! conn [(resident->tx value)])

      :careplan/set
      (d/transact! conn [{:careplan/resident-id (first path) :careplan/payload (ls/enc payload)}])

      :credential-screen/set
      (d/transact! conn [{:credential-screen/resident-id (first path) :credential-screen/payload (ls/enc payload)}])

      :resident/mark-administered
      (let [resident-id (first path)
            {:keys [result resident-patch]} (administer-medication! s resident-id)
            jurisdiction (:jurisdiction (resident s resident-id))
            next-n (inc (next-administration-sequence s jurisdiction))]
        (d/transact! conn
                     [(resident->tx (assoc resident-patch :id resident-id))
                      {:administration-sequence/jurisdiction jurisdiction :administration-sequence/next next-n}
                      {:administration/seq (count (administration-history s)) :administration/record (ls/enc (get result "record"))}])
        result)

      :resident/mark-finalized
      (let [resident-id (first path)
            {:keys [result resident-patch]} (finalize-incident-response! s resident-id)
            jurisdiction (:jurisdiction (resident s resident-id))
            next-n (inc (next-finalization-sequence s jurisdiction))]
        (d/transact! conn
                     [(resident->tx (assoc resident-patch :id resident-id))
                      {:finalization-sequence/jurisdiction jurisdiction :finalization-sequence/next next-n}
                      {:finalization/seq (count (finalization-history s)) :finalization/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (ls/enc fact)}])
    fact)
  (with-residents [s residents]
    (when (seq residents) (d/transact! conn (mapv resident->tx (vals residents)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:residents ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [residents]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-residents s residents))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo resident set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
