(ns nursing.facts
  "Per-jurisdiction residential-nursing-care regulatory catalog -- the
  G2-style spec-basis table the Nursing Care Governor checks every
  `:careplan/verify` proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's residential-nursing-
  care framework, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official long-term-
  care/nursing-home regulatory authority (see `:provenance`); they are
  a STARTING catalog, not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to
  `catalog`, cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the resident-
  consent/care-plan/medication-administration-record-verification/
  incident-report evidence set this blueprint's own Offer names;
  `:legal-basis` / `:owner-authority` / `:provenance` are the G2
  citation the governor requires before any `:actuation/administer-
  medication`/`:actuation/finalize-incident-response` proposal can
  commit."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省 (Ministry of Health, Labour and Welfare)"
          :legal-basis "指定介護老人福祉施設の人員、設備及び運営に関する基準 (厚生省令第39号) / 介護保険法"
          :national-spec "指定介護老人福祉施設の人員・設備・運営基準および事故発生時の対応義務"
          :provenance "https://www.mhlw.go.jp/stf/seisakunitsuite/bunya/hukushi_kaigo/kaigo_koureisha/index.html"
          :required-evidence ["入所者同意記録 (resident-consent-record)"
                              "ケアプラン記録 (care-plan-record)"
                              "与薬記録確認記録 (medication-administration-record-verification)"
                              "事故報告記録 (incident-report-record)"]}
   "USA" {:name "United States"
          :owner-authority "Centers for Medicare & Medicaid Services (CMS)"
          :legal-basis "Nursing Home Reform Act (OBRA '87) / 42 CFR Part 483 Subpart B"
          :national-spec "Requirements for Long Term Care Facilities: resident rights, care planning, medication administration and incident reporting"
          :provenance "https://www.ecfr.gov/current/title-42/chapter-IV/subchapter-G/part-483/subpart-B"
          :required-evidence ["Resident consent record"
                              "Care-plan record"
                              "Medication-administration-record verification"
                              "Incident-report record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Care Quality Commission (CQC)"
          :legal-basis "Health and Social Care Act 2008 (Regulated Activities) Regulations 2014"
          :national-spec "Regulated nursing-care-home provider requirements: care planning, safe management of medicines, and notification of significant events"
          :provenance "https://www.cqc.org.uk/guidance-providers/regulations-enforcement/regulation-12-safe-care-treatment"
          :required-evidence ["Resident consent record"
                              "Care-plan record"
                              "Medication-administration-record verification"
                              "Incident-report record"]}
   "DEU" {:name "Germany"
          :owner-authority "Bundesministerium für Gesundheit (BMG)"
          :legal-basis "Elftes Buch Sozialgesetzbuch (SGB XI, Pflegeversicherung) / Wohn- und Betreuungsvertragsgesetz (WBVG)"
          :national-spec "Zulassungs- und Qualitätsanforderungen an vollstationäre Pflegeeinrichtungen inkl. Medikamentengabe und Vorkommnismeldung"
          :provenance "https://www.bundesgesundheitsministerium.de/themen/pflege.html"
          :required-evidence ["Einwilligungsprotokoll (resident-consent-record)"
                              "Pflegeplanprotokoll (care-plan-record)"
                              "Medikationsnachweisprotokoll (medication-administration-record-verification)"
                              "Vorkommnismeldeprotokoll (incident-report-record)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to administer
  medication or finalize an incident response on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-8710 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `nursing.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
