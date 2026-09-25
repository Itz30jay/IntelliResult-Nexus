# Phase 1 — Foundation: Architecture Decisions

Every decision below was made because the literal spec left room for a
technical choice, or because verified current information (2026-08-16)
diverged from what the spec's example names assumed. Each entry names the
alternatives considered and why they were not chosen.

---

## 1. Jakarta EE 11 namespace (not `javax.*`)

**Decision:** Tomcat 11.0.x, Jakarta Servlet 6.1, JSP 4.0, `jakarta.*` imports
throughout — never `javax.servlet.*` or `javax.persistence.*`.

**Why:** The spec's own package example (`com.srs`) and folder layout don't
specify `javax` vs `jakarta`, but the *library* choices elsewhere in the spec
force the answer: Hibernate ORM's actively-maintained series (6.0+) dropped
`javax.persistence` entirely in favor of `jakarta.persistence`. Choosing a
current Hibernate version therefore already commits this project to the
Jakarta namespace — a `javax`-based servlet layer talking to a
`jakarta.persistence`-based ORM layer isn't a real option.

**Alternatives considered:**
- *Tomcat 9 / `javax.*`* — would require Hibernate 5.6 or older (the last
  release with a `javax`-compatible build), which is a limited-support,
  aging series. Rejected: starts the project on a deprecated foundation.
- *Tomcat 10.1 (Jakarta EE 10, Servlet 6.0)* — a legitimate, still-maintained
  option. Rejected in favor of 11.0 only because Tomcat's own documentation
  states 11.0 is "the recommended target for new deployments... since it
  gives the longest runway," and both require the same Java 17 floor this
  project already has, so there's no cost to taking the newer one.

**Consequence:** Every servlet/filter/listener in every future phase uses
`jakarta.servlet.*`. Every entity in Phase 3 uses `jakarta.persistence.*`.

---

## 2. Hibernate ORM 7.4.5.Final (not 6.x)

**Decision:** `org.hibernate.orm:hibernate-core:7.4.5.Final`.

**Why:** Verified live against hibernate.org on 2026-08-16: the 7.4 series is
the current **latest stable**; 6.6 is now *limited-support* (maintenance
only); 8.0 exists only as a Beta. A training-data-only answer would very
plausibly have named 6.x, since that was current as of most models' cutoffs —
this is exactly the kind of fact that goes stale and needs a live check
rather than being recalled.

**Consequence:** Jakarta Persistence 3.2 (7.0's migration guide: *"7.0
migrates to Jakarta Persistence 3.2"*), Java 17 baseline (matches this
project's floor exactly), `hibernate.connection.provider_class=hikaricp` and
`hibernate.current_session_context_class=managed` short-name properties
confirmed against Hibernate's own current documentation.

---

## 3. OpenPDF instead of iText 5

**Decision:** `com.github.librepdf:openpdf:3.0.5` for marksheet PDF generation (Phase 12).

**Why:** The spec itself allows this — *"iText 5 (or fully compatible PDF
library)."* iText moved to AGPL-only licensing starting at version 5.0, which
is a copyleft license with network-use provisions that complicate exactly the
two things this project is meant for (a college deploying it, a student
publishing it on GitHub as a portfolio piece) unless a commercial license is
purchased. OpenPDF is a fork of iText 4/5 maintained under LGPL/MPL
specifically because of that licensing change, and is API-compatible at the
concept level (same `Document`/`PdfWriter` model). It is not a byte-for-byte
drop-in: OpenPDF 3.0+ renamed its base package from `com.lowagie.text` to
`org.openpdf.text`, which `PDFUtil.java` (Phase 12) will use directly — noted
here so nobody mid-project assumes iText 5 tutorials' import statements apply
unchanged.

**Alternatives considered:** Apache PDFBox — also permissively licensed, but
a lower-level API (no direct `Table`/`Paragraph` object model), which would
mean more hand-rolled layout code for a marksheet with a fairly complex fixed
layout (logo, tables, signature area, QR, watermark). OpenPDF's model matches
what the spec's marksheet description implies more closely.

---

## 4. HikariCP for connection pooling (not asked for explicitly, added because required)

**Decision:** `com.zaxxer:HikariCP:7.1.0`, wired via `hibernate-hikaricp`.

**Why:** The spec's Hibernate Best Practices section (Sec. 38) and
Performance section (Sec. 49) require production-grade behavior, and
Hibernate's own connection pool is explicitly not intended for production
use. HikariCP is the de facto standard for this. This is the clearest example
of "add what was required but not explicitly named" in this phase.

---

## 5. Package name `com.intelliresult.nexus` (not `com.srs`)

**Decision:** All Java code lives under `com.intelliresult.nexus`.

**Why:** The spec's folder-structure example uses `com.srs` (Student Result
System), which reads as a leftover generic placeholder — the product is
named IntelliResult Nexus everywhere else in the spec, including the title.
Flagging this explicitly (per the spec's own Sec. 74: *"do not silently
rename classes"*) rather than quietly using either name.

---

## 6. Session-per-request via `ManagedSessionContext`, transactions demarcated in the Service layer

**Decision:** `HibernateSessionFilter` opens and binds exactly one Hibernate
`Session` per HTTP request using `ManagedSessionContext.bind()`/`unbind()`,
and closes it in a `finally` block. It does **not** begin or commit a
transaction.

**Why:** Sec. 66 requires that some operations — marks update + history
insert, bulk import — commit or roll back as one atomic unit, while a single
page can also legitimately need zero write transactions (read-only report) or
exactly one. Only the Service layer knows where those boundaries actually
are. Coupling "one session" to "one transaction" at the filter level (which
`current_session_context_class=thread`'s auto-open behavior invites) would
make expressing "these three DAO calls commit together" awkward. `managed`
was chosen specifically because it's Hibernate's own strategy for code that
explicitly owns bind/unbind — which is exactly what this filter does.

---

## 7. Sentry: SDK wired now, DSN not hardcoded

**Decision:** `Sentry.init()` runs unconditionally at startup
(`ApplicationStartupListener`), initialized with whatever `sentry.dsn`
resolves to — which is empty unless `INTELLIRESULT_SENTRY_DSN` is set.

**Why:** Sentry's Java SDK is documented to safely no-op when given no DSN,
so leaving the integration code active in every environment and toggling it
purely by whether a DSN is configured is simpler and less error-prone than
writing custom conditional-skip logic to reproduce behavior the SDK already
provides.

**Note on scope:** This sandbox could not verify whether a real Sentry
organization/project is already connected to this workspace, so no live DSN
is wired in. The moment a Sentry project exists for this app, setting
`INTELLIRESULT_SENTRY_DSN` is the only step needed — no code changes.

---

## 8. Vercel and GitHub Integration — status, not yet exercised

**Vercel:** The spec's MCP rules ask Vercel to be used "for any static assets,
verification pages, or frontend-heavy parts." A Java Servlet/JSP WAR does not
deploy to Vercel (it has no JVM/servlet-container runtime target) — this is
flagged now rather than silently ignored. The one place Vercel could
genuinely fit is the public QR-verification page (Phase 12: a page that only
needs to read minimal, non-sensitive result-status data and could reasonably
be a decoupled static/serverless frontend calling a small verification API).
That decision is deferred to Phase 12, where there's an actual page to
evaluate it against, rather than committed to speculatively now.

**GitHub Integration:** No GitHub connector is active in this chat, so
commits described in the spec's phase-by-phase workflow can't be pushed
automatically yet. A local Git repository with a clean Phase 1 commit is
included so the project is push-ready the moment a remote is added — see the
delivery message for how to connect one.

**Supabase:** Deferred to Phase 14 (Notifications) per the spec's own
instruction, where there will be an actual real-time-notification
requirement to evaluate a dual-store architecture against, instead of
adding a second datastore speculatively now.
