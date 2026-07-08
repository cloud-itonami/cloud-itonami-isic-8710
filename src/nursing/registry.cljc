(ns nursing.registry
  "Pure-function medication-administration + incident-response-
  finalization record construction -- an append-only residential-
  nursing-care book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a medication-administration
  or incident-response reference number -- every nursing-care
  operator/jurisdiction assigns its own reference format. This
  namespace does NOT invent one; it builds a jurisdiction-scoped
  sequence number and validates the record's required fields, the
  same honest, non-fabricating discipline `nursing.facts` uses.

  `medication-contraindicated?` reuses `clinic.registry/treatment-
  contraindicated?`'s set-membership/conflict shape verbatim for the
  residential-nursing-care domain -- the FOURTH instance of this
  fleet's set-membership/conflict check family (`clinic.registry/
  treatment-contraindicated?` established the first, `veterinary.
  registry/treatment-contraindicated?` reused it literally for the
  veterinary domain as the second, `entertainment.governor/release-
  channel-restricted-violations` reused the SHAPE under a different
  name as the third) -- the THIRD instance to reuse the literal
  'contraindicated' concept (clinic, veterinary, and now nursing all
  share the same real-world failure mode: a proposed medication/
  treatment appearing on the patient/resident's own recorded
  contraindication list).

  `medication-dosage-exceeds-maximum?` is the EIGHTH instance of this
  fleet's MAXIMUM-ceiling check family (`facility.registry/occupancy-
  exceeds-capacity?`/`school.registry/class-size-exceeds-maximum?`/
  `card.registry/settlement-amount-exceeds-authorized?`/`recovery.
  registry/contamination-percentage-exceeds-maximum?`/`care.registry/
  caregiver-workload-exceeds-maximum?`/`navigator.registry/
  eligibility-window-elapsed-exceeds-validity?`/`advertising.registry/
  media-spend-exceeds-authorized?` established the first seven),
  applying the SAME lo-bound-absent/hi-bound-only comparison to a
  resident's own recorded proposed medication dosage against their own
  recorded maximum-authorized dosage -- a direct, natural mapping onto
  real medication-safety practice (administering a dose above a
  resident's own recorded maximum is exactly the failure mode a
  nursing-care operator must not let an advisor wave through).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real nursing-care system. It builds the RECORD a
  nursing-care operator would keep, not the act of administering the
  medication or finalizing the incident response itself (that is
  `nursing.operation`'s `:actuation/administer-medication`/`:actuation/
  finalize-incident-response`, always human-gated -- see README
  `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  nursing-care operator's own act, not this actor's. See README
  `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn medication-contraindicated?
  "Does `resident`'s own proposed `:proposed-medication` appear in its
  own recorded `:medication-contraindications` set? A pure ground-
  truth check against the resident's own permanent fields -- no
  upstream comparison needed. The THIRD literal reuse of `clinic.
  registry/treatment-contraindicated?`'s set-membership/conflict
  concept (see ns docstring)."
  [{:keys [proposed-medication medication-contraindications]}]
  (contains? (set medication-contraindications) proposed-medication))

(defn medication-dosage-exceeds-maximum?
  "Does `resident`'s own recorded `:medication-dosage-mg` exceed its
  own recorded `:medication-max-authorized-dosage-mg`? A pure ground-
  truth check against the resident's own permanent fields -- no
  upstream comparison needed. The EIGHTH instance of this fleet's
  MAXIMUM-ceiling check family (see ns docstring)."
  [{:keys [medication-dosage-mg medication-max-authorized-dosage-mg]}]
  (and (number? medication-dosage-mg) (number? medication-max-authorized-dosage-mg)
       (> medication-dosage-mg medication-max-authorized-dosage-mg)))

(defn register-medication-administration
  "Validate + construct the MEDICATION-ADMINISTRATION registration
  DRAFT -- the nursing-care operator's own act of administering a real
  medication dose. Pure function -- does not touch any real nursing-
  care system; it builds the RECORD an operator would keep. `nursing.
  governor` independently re-verifies the resident's own contraindication
  list and dosage ceiling, and blocks a double-administration for the
  same resident, before this is ever allowed to commit."
  [resident-id jurisdiction sequence]
  (when-not (and resident-id (not= resident-id ""))
    (throw (ex-info "medication-administration: resident_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "medication-administration: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "medication-administration: sequence must be >= 0" {})))
  (let [administration-number (str (str/upper-case jurisdiction) "-MED-" (zero-pad sequence 6))
        record {"record_id" administration-number
                "kind" "medication-administration-draft"
                "resident_id" resident-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "administration_number" administration-number
     "certificate" (unsigned-certificate "MedicationAdministration" administration-number administration-number)}))

(defn register-incident-response-finalization
  "Validate + construct the INCIDENT-RESPONSE-FINALIZATION
  registration DRAFT -- the nursing-care operator's own act of
  finalizing a real incident response. Pure function -- does not touch
  any real nursing-care system; it builds the RECORD an operator would
  keep. `nursing.governor` independently re-verifies the resident's own
  evidence checklist and blocks a double-finalization for the same
  resident, before this is ever allowed to commit."
  [resident-id jurisdiction sequence]
  (when-not (and resident-id (not= resident-id ""))
    (throw (ex-info "incident-response-finalization: resident_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "incident-response-finalization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "incident-response-finalization: sequence must be >= 0" {})))
  (let [incident-number (str (str/upper-case jurisdiction) "-INC-" (zero-pad sequence 6))
        record {"record_id" incident-number
                "kind" "incident-response-finalization-draft"
                "resident_id" resident-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "incident_number" incident-number
     "certificate" (unsigned-certificate "IncidentResponseFinalization" incident-number incident-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
