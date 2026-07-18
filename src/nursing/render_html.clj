(ns nursing.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout): this repo previously had NO demo page and no
  generator at all. This namespace drives the REAL actor stack
  (`nursing.operation` -> `nursing.governor` -> `nursing.store`)
  through a scenario adapted from this repo's own `nursing.sim` demo
  driver (`clojure -M:dev:run`, confirmed by actually running it before
  this file was written -- unlike `cloud-itonami-isic-851`'s
  `schoolops.sim`, this repo's own sim driver uses ids that DO match
  `nursing.store/demo-data`'s seeded residents exactly (resident-1..5),
  and every disposition it produces (auto-commit / escalate+approve /
  HARD hold, and the exact `:rule` on each hold) matches
  `nursing.governor`'s own documented checks precisely, so it was safe
  to reuse rather than author from scratch), trimmed to a
  representative subset (one clean phase-3 auto-commit, the full
  care-plan-verification/credential-screening/medication-administration/
  incident-response-finalization lifecycle for one resident -- the
  latter two ALWAYS escalate, never auto, at any phase -- and three
  distinct HARD-hold reasons that never reach a human) and rendered
  deterministically -- no invented numbers, no timestamps in the page
  content, byte-identical across reruns against the same seed (verified
  by diffing two consecutive runs before shipping).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [nursing.store :as store]
            [nursing.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness (unchanged across every repo
;; in this cluster -- do not rewrite, only copy) -----------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :licensed-nurse :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, using ONLY real resident ids from
  `nursing.store/demo-data`:

  resident-1 (JPN, clean; proposed acetaminophen 500mg, max
  authorized 1000mg, no contraindications, credential current) walks
  the full clean lifecycle: a `:resident/intake` directory-
  normalization patch is a phase-3, no-capital-risk auto-commit
  (governor clean, `:resident/intake` is the ONLY op in phase 3's
  `:auto` set); `:careplan/verify` (JPN has a real spec-basis in
  `nursing.facts`) and `:credential/screen` (clean) each ALWAYS
  escalate (neither op is ever auto-eligible, at any phase) and are
  approved by a human licensed nurse; `:actuation/administer-
  medication` and `:actuation/finalize-incident-response` -- the two
  REAL-WORLD actuation events this actor performs (a real medication
  dose administered / a real incident response finalized) -- ALSO
  ALWAYS escalate (the governor's own `high-stakes` gate AND the phase
  table agree, independently, that actuation is never auto, at any
  phase) and are each approved, producing one draft medication-
  administration record (`JPN-MED-000000`) and one draft incident-
  response-finalization record (`JPN-INC-000000`).

  Then three DISTINCT HARD-hold reasons, none of which ever reach a
  human (a human approver cannot override a HARD violation):
    - resident-2 (jurisdiction ATL, not in `nursing.facts/catalog`):
      `:careplan/verify` HARD-holds on `:no-spec-basis` -- the advisor
      may not invent a jurisdiction's residential-nursing-care
      requirements.
    - resident-3 (JPN, proposed medication warfarin, which IS on its
      own recorded `:medication-contraindications` set): assessed first
      (clean escalate+approve, so evidence is on file and this HARD
      hold below is isolated to the medication check alone), then
      `:actuation/administer-medication` HARD-holds on
      `:medication-contraindicated` -- the governor independently
      recomputes the resident's own contraindication set, never trusts
      the advisor's proposal.
    - resident-4 (JPN, `:credential-not-current? true` in the seed
      data): `:credential/screen` HARD-holds on `:credential-not-
      current` -- a not-current nursing-staff credential blocks
      progress, un-overridably, even though the screening op itself is
      the one that (re)discovers it.

  Returns the resulting store -- every field `render` below reads is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]

    ;; resident-1: clean directory-normalization patch -- phase-3
    ;; auto-commit, no capital risk yet.
    (exec! actor "r1-intake" {:op :resident/intake :subject "resident-1"
                               :patch {:id "resident-1" :resident-name "Sato Kenji"}})

    ;; resident-1: per-jurisdiction care-plan evidence checklist (JPN
    ;; has a real spec-basis) -- ALWAYS escalates, approved by a human.
    (exec! actor "r1-verify" {:op :careplan/verify :subject "resident-1"})
    (approve! actor "r1-verify")

    ;; resident-1: nursing-staff credential-currency screening, clean --
    ;; ALWAYS escalates, approved by a human.
    (exec! actor "r1-screen" {:op :credential/screen :subject "resident-1"})
    (approve! actor "r1-screen")

    ;; resident-1: REAL medication administration (actuation/administer-
    ;; medication, a real dose given) -- ALWAYS escalates regardless of
    ;; phase or confidence, approved by a human licensed nurse.
    (exec! actor "r1-administer" {:op :actuation/administer-medication :subject "resident-1"})
    (approve! actor "r1-administer")

    ;; resident-1: REAL incident-response finalization (actuation/
    ;; finalize-incident-response) -- ALWAYS escalates, approved by a
    ;; human.
    (exec! actor "r1-finalize" {:op :actuation/finalize-incident-response :subject "resident-1"})
    (approve! actor "r1-finalize")

    ;; resident-2 (ATL): no official spec-basis in nursing.facts -> HARD
    ;; hold on :no-spec-basis, never reaches a human.
    (exec! actor "r2-verify" {:op :careplan/verify :subject "resident-2" :no-spec? true})

    ;; resident-3: verify JPN first (clean escalate+approve) so evidence
    ;; is on file and the medication-contraindicated hold below is
    ;; isolated.
    (exec! actor "r3-verify" {:op :careplan/verify :subject "resident-3"})
    (approve! actor "r3-verify")

    ;; resident-3: proposed medication warfarin is on the resident's own
    ;; recorded contraindication list -> HARD hold on
    ;; :medication-contraindicated, never reaches a human.
    (exec! actor "r3-administer" {:op :actuation/administer-medication :subject "resident-3"})

    ;; resident-4: seeded with a not-current nursing-staff credential ->
    ;; HARD hold on :credential-not-current, never reaches a human.
    (exec! actor "r4-screen" {:op :credential/screen :subject "resident-4"})

    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- resident-row [ledger {:keys [id resident-name jurisdiction proposed-medication
                                     medication-contraindications medication-dosage-mg
                                     medication-max-authorized-dosage-mg credential-not-current?
                                     medication-administered? incident-response-finalized?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s / %s</td><td>%s</td><td>%s / %s</td><td>%s</td></tr>"
          (esc id) (esc resident-name) (esc jurisdiction) (esc (name (or proposed-medication :n-a)))
          (esc (if (seq medication-contraindications)
                 (str/join ", " (map name medication-contraindications))
                 "none"))
          (esc medication-dosage-mg) (esc medication-max-authorized-dosage-mg)
          (if credential-not-current? "<span class=\"critical\">not current</span>" "<span class=\"ok\">current</span>")
          (if medication-administered? "administered" "not administered")
          (if incident-response-finalized? "finalized" "not finalized")
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map #(if (keyword? %) (name %) %)) (str/join ", "))
                    (some-> disposition name) ""))))

(defn- record-row [prefix {:strs [record_id resident_id jurisdiction kind immutable]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc prefix) (esc record_id) (esc resident_id) (esc jurisdiction)
          (if immutable "<span class=\"ok\">immutable draft</span>" (esc kind))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract
  ;; (`nursing.governor`/`nursing.phase`) -- documentation of fixed
  ;; behavior, not runtime telemetry, so it is legitimately hand-
  ;; described rather than derived from a live run.
  ["        <tr><td><code>:resident/intake</code></td><td><span class=\"ok\">phase-3 auto-commit when clean, no capital risk yet -- the ONLY auto-eligible op in this domain</span></td></tr>"
   "        <tr><td><code>:careplan/verify</code></td><td><span class=\"warn\">ALWAYS human approval &middot; spec-basis independently checked against <code>nursing.facts</code>, never fabricated</span></td></tr>"
   "        <tr><td><code>:credential/screen</code></td><td><span class=\"warn\">ALWAYS human approval when clean &middot; a not-current nursing-staff credential is a HARD, un-overridable hold instead</span></td></tr>"
   "        <tr><td><code>:actuation/administer-medication</code></td><td><span class=\"warn\">ALWAYS human approval &middot; real medication dose given &middot; contraindication + dosage-ceiling independently recomputed, never auto at any phase</span></td></tr>"
   "        <tr><td><code>:actuation/finalize-incident-response</code></td><td><span class=\"warn\">ALWAYS human approval &middot; real incident response finalized &middot; evidence checklist independently recomputed, never auto at any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        residents (store/all-residents db)
        resident-rows (str/join "\n" (map (partial resident-row ledger) residents))
        ledger-rows (str/join "\n" (map ledger-row ledger))
        administration-rows (str/join "\n" (map (partial record-row "administration") (store/administration-history db)))
        finalization-rows (str/join "\n" (map (partial record-row "finalization") (store/finalization-history db)))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-8710 &middot; residential nursing care facilities</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Residential nursing care facilities (ISIC 8710) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · medication administration/incident-response finalization always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Residents</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>nursing.store</code> via <code>nursing.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Resident</th><th>Name</th><th>Jurisdiction</th><th>Proposed medication</th><th>Contraindications</th><th>Dosage / Max (mg)</th><th>Credential</th><th>Administration / Finalization</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     resident-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Draft medication-administration / incident-response-finalization records</h2>\n"
     "    <p class=\"muted\">Unsigned drafts only — the licensed nursing-care operator's own act of signing is outside this actor's authority (see README <code>Actuation</code>).</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Kind</th><th>Record id</th><th>Resident</th><th>Jurisdiction</th><th>Status</th></tr></thead>\n"
     "      <tbody>\n"
     administration-rows (when (seq administration-rows) "\n")
     finalization-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Nursing Care Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden by a human approver. Jurisdiction spec-basis, evidence completeness, medication contraindications, dosage ceilings and nursing-staff credential currency are independently recomputed, never trusted from the advisor's proposal; a real medication administration or incident-response finalization is always a human licensed nurse's call, at every rollout phase.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/administration-history db)) "administration drafts,"
             (count (store/finalization-history db)) "finalization drafts )")))
