(ns nursing.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:actuation/administer-medication`/`:actuation/finalize-
  incident-response` must NEVER be a member of any phase's `:auto`
  set."
  (:require [clojure.test :refer [deftest is testing]]
            [nursing.phase :as phase]))

(deftest administer-medication-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real medication administration"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/administer-medication))
          (str "phase " n " must not auto-commit :actuation/administer-medication")))))

(deftest finalize-incident-response-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real incident-response finalization"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :actuation/finalize-incident-response))
          (str "phase " n " must not auto-commit :actuation/finalize-incident-response")))))

(deftest credential-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :credential/screen))
          (str "phase " n " must not auto-commit :credential/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":resident/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:resident/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :resident/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/administer-medication} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :actuation/finalize-incident-response} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :resident/intake} :commit)))))
