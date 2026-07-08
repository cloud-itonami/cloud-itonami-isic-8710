(ns nursing.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [nursing.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sato Kenji" (:resident-name (store/resident s "resident-1"))))
      (is (= "JPN" (:jurisdiction (store/resident s "resident-1"))))
      (is (= :acetaminophen (:proposed-medication (store/resident s "resident-1"))))
      (is (= 500 (:medication-dosage-mg (store/resident s "resident-1"))))
      (is (= 1000 (:medication-max-authorized-dosage-mg (store/resident s "resident-1"))))
      (is (false? (:credential-not-current? (store/resident s "resident-1"))))
      (is (= #{:warfarin} (:medication-contraindications (store/resident s "resident-3"))))
      (is (true? (:credential-not-current? (store/resident s "resident-4"))))
      (is (= 1500 (:medication-dosage-mg (store/resident s "resident-5"))))
      (is (false? (:medication-administered? (store/resident s "resident-1"))))
      (is (false? (:incident-response-finalized? (store/resident s "resident-1"))))
      (is (= ["resident-1" "resident-2" "resident-3" "resident-4" "resident-5"]
             (mapv :id (store/all-residents s))))
      (is (nil? (store/credential-screen-of s "resident-1")))
      (is (nil? (store/careplan-of s "resident-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/administration-history s)))
      (is (= [] (store/finalization-history s)))
      (is (zero? (store/next-administration-sequence s "JPN")))
      (is (zero? (store/next-finalization-sequence s "JPN")))
      (is (false? (store/resident-already-administered? s "resident-1")))
      (is (false? (store/resident-already-finalized? s "resident-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :resident/upsert
                                 :value {:id "resident-1" :resident-name "Sato Kenji"}})
        (is (= "Sato Kenji" (:resident-name (store/resident s "resident-1"))))
        (is (= 1000 (:medication-max-authorized-dosage-mg (store/resident s "resident-1"))) "unrelated field preserved"))
      (testing "careplan / credential-screen payloads commit and read back"
        (store/commit-record! s {:effect :careplan/set :path ["resident-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/careplan-of s "resident-1")))
        (store/commit-record! s {:effect :credential-screen/set :path ["resident-1"]
                                 :payload {:resident-id "resident-1" :credential-not-current? false}})
        (is (= {:resident-id "resident-1" :credential-not-current? false} (store/credential-screen-of s "resident-1"))))
      (testing "medication administration drafts a record and advances the sequence"
        (store/commit-record! s {:effect :resident/mark-administered :path ["resident-1"]})
        (is (= "JPN-MED-000000" (get (first (store/administration-history s)) "record_id")))
        (is (= "medication-administration-draft" (get (first (store/administration-history s)) "kind")))
        (is (true? (:medication-administered? (store/resident s "resident-1"))))
        (is (= 1 (count (store/administration-history s))))
        (is (= 1 (store/next-administration-sequence s "JPN")))
        (is (true? (store/resident-already-administered? s "resident-1")))
        (is (false? (store/resident-already-administered? s "resident-2"))))
      (testing "incident-response finalization drafts a record and advances the sequence"
        (store/commit-record! s {:effect :resident/mark-finalized :path ["resident-1"]})
        (is (= "JPN-INC-000000" (get (first (store/finalization-history s)) "record_id")))
        (is (= "incident-response-finalization-draft" (get (first (store/finalization-history s)) "kind")))
        (is (true? (:incident-response-finalized? (store/resident s "resident-1"))))
        (is (= 1 (count (store/finalization-history s))))
        (is (= 1 (store/next-finalization-sequence s "JPN")))
        (is (true? (store/resident-already-finalized? s "resident-1")))
        (is (false? (store/resident-already-finalized? s "resident-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/resident s "nope")))
    (is (= [] (store/all-residents s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/administration-history s)))
    (is (= [] (store/finalization-history s)))
    (is (zero? (store/next-administration-sequence s "JPN")))
    (is (zero? (store/next-finalization-sequence s "JPN")))
    (store/with-residents s {"x" {:id "x" :resident-name "n"
                                  :proposed-medication :acetaminophen :medication-contraindications #{}
                                  :medication-dosage-mg 500 :medication-max-authorized-dosage-mg 1000
                                  :credential-not-current? false
                                  :medication-administered? false :incident-response-finalized? false
                                  :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:resident-name (store/resident s "x"))))))
