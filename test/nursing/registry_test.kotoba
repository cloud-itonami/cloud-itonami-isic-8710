(ns nursing.registry-test
  (:require [clojure.test :refer [deftest is]]
            [nursing.registry :as r]))

;; ----------------------------- medication-contraindicated? -----------------------------

(deftest not-contraindicated-when-not-on-list
  (is (not (r/medication-contraindicated? {:proposed-medication :acetaminophen :medication-contraindications #{}})))
  (is (not (r/medication-contraindicated? {:proposed-medication :acetaminophen :medication-contraindications #{:warfarin}}))))

(deftest contraindicated-when-on-list
  (is (r/medication-contraindicated? {:proposed-medication :warfarin :medication-contraindications #{:warfarin}})))

;; ----------------------------- medication-dosage-exceeds-maximum? -----------------------------

(deftest not-exceeded-when-within-max-dosage
  (is (not (r/medication-dosage-exceeds-maximum? {:medication-dosage-mg 500 :medication-max-authorized-dosage-mg 1000})))
  (is (not (r/medication-dosage-exceeds-maximum? {:medication-dosage-mg 1000 :medication-max-authorized-dosage-mg 1000}))))

(deftest exceeded-when-over-max-dosage
  (is (r/medication-dosage-exceeds-maximum? {:medication-dosage-mg 1500 :medication-max-authorized-dosage-mg 1000}))
  (is (r/medication-dosage-exceeds-maximum? {:medication-dosage-mg 1001 :medication-max-authorized-dosage-mg 1000})))

(deftest exceeded-is-false-on-missing-fields
  (is (not (r/medication-dosage-exceeds-maximum? {})))
  (is (not (r/medication-dosage-exceeds-maximum? {:medication-dosage-mg 1500}))))

;; ----------------------------- register-medication-administration -----------------------------

(deftest administration-is-a-draft-not-a-real-administration
  (let [result (r/register-medication-administration "resident-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest administration-assigns-administration-number
  (let [result (r/register-medication-administration "resident-1" "JPN" 7)]
    (is (= (get result "administration_number") "JPN-MED-000007"))
    (is (= (get-in result ["record" "resident_id"]) "resident-1"))
    (is (= (get-in result ["record" "kind"]) "medication-administration-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest administration-validation-rules
  (is (thrown? Exception (r/register-medication-administration "" "JPN" 0)))
  (is (thrown? Exception (r/register-medication-administration "resident-1" "" 0)))
  (is (thrown? Exception (r/register-medication-administration "resident-1" "JPN" -1))))

;; ----------------------------- register-incident-response-finalization -----------------------------

(deftest finalization-is-a-draft-not-a-real-finalization
  (let [result (r/register-incident-response-finalization "resident-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest finalization-assigns-incident-number
  (let [result (r/register-incident-response-finalization "resident-1" "JPN" 3)]
    (is (= (get result "incident_number") "JPN-INC-000003"))
    (is (= (get-in result ["record" "resident_id"]) "resident-1"))
    (is (= (get-in result ["record" "kind"]) "incident-response-finalization-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest finalization-validation-rules
  (is (thrown? Exception (r/register-incident-response-finalization "" "JPN" 0)))
  (is (thrown? Exception (r/register-incident-response-finalization "resident-1" "" 0)))
  (is (thrown? Exception (r/register-incident-response-finalization "resident-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-medication-administration "resident-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-medication-administration "resident-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-MED-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-MED-000001" (get-in hist2 [1 "record_id"])))))
