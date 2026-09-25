# Phase 4 — Security: Architecture Decisions

## 1. Filters moved from `@WebFilter` to explicit `web.xml` registration

A correction to Phase 1's stated preference for annotations over XML.
`HibernateSessionFilter` → `AuthenticationFilter` → `AuthorizationFilter` →
`CsrfFilter` must run in exactly that order (each depends on something the
previous one set up: a bound Session, then a resolved user, then that user's
role), but the Servlet specification does not guarantee any particular
execution order for annotation-scanned filters - only `<filter-mapping>`
declaration order in `web.xml` is an actual guarantee. This wasn't visible
in Phase 1 with a single filter and nothing depending on it; it became a
real correctness issue the moment a second, order-dependent filter existed.
Servlets and listeners stay as annotations - nothing about their execution
depends on relative order the way this filter chain does.

## 2. Generic vs. specific login failure messages

`AuthenticationService.login()` returns the identical "Invalid email or
password" message whether the email doesn't exist or the password is wrong
- telling an attacker "that email isn't registered" is itself a user-
enumeration leak. A disabled account gets its own distinct message ("This
account has been disabled") because that's genuinely more helpful to a
legitimate user and doesn't weaken the enumeration defense - reaching that
branch already required guessing a real, correctly-paired email and
password.

## 3. Session fixation protection: `changeSessionId()`, not invalidate+recreate

`LoginServlet` calls `request.changeSessionId()` on successful
authentication rather than `session.invalidate()` followed by a fresh
`getSession(true)`. The latter is the more commonly-seen pattern in older
tutorials but silently drops every attribute the pre-login session held -
concretely, the CSRF token `CsrfFilter` may have already stamped into that
session earlier in the very same request/response cycle would be lost,
breaking the form that's about to render. `changeSessionId()` (Servlet 3.1+,
unchanged through the Jakarta rename) gives the session a new ID in place,
which is what actually closes the fixation hole (an attacker who fixed a
victim's pre-login session ID can't reuse it post-login) without discarding
session state gained earlier in the same request.

## 4. Re-verifying the session's user against the database on every request

`AuthenticationFilter` doesn't just trust `session.getAttribute(SESSION_USER_ID) != null`
- it re-loads the User via `UserDAO` and checks `isActive()` on every
non-public request. This is Sec. 3's "Invalid session handling" taken
literally: without it, disabling a compromised or offboarded account would
leave their existing sessions valid until they happened to expire on their
own. The trade-off against Sec. 49's performance concerns is real - this is
a DB round-trip per request - but the round-trip is a single indexed
primary-key lookup, and the alternative (a stale-but-technically-valid
session for a disabled account) is exactly the security gap Sec. 3 calls
out by name. If this specific query is ever measured as an actual
bottleneck, adding a short-lived cache in front of it is a targeted,
evidence-based fix; it isn't one worth making speculatively now.

## 5. CSRF: standard synchronizer token pattern, GET/HEAD/OPTIONS never validated

One random token per session, embedded as a hidden field in every
state-changing form, checked against the session's copy for every request
whose method isn't GET/HEAD/OPTIONS. Restricting validation to mutating
methods isn't a shortcut - requiring a token on GET would break plain links
and any browser prefetching for zero security benefit, since GET requests
aren't supposed to mutate state in the first place.

## 6. Open-redirect protection on `returnUrl`

`AuthenticationFilter` appends the originally-requested path as
`?returnUrl=...` when it redirects an unauthenticated request to `/login`,
so a successful login can send the person back where they were headed
instead of always landing on a generic dashboard. `LoginServlet` only
honors that value if it's an unambiguous same-site relative path
(`isSafeRelativeReturnUrl()` rejects anything starting with `//` or
containing `://`) - otherwise it falls back to the role's dashboard. Without
that check, a crafted link like `/login?returnUrl=https://evil.example`
could redirect a freshly-authenticated session off-site - a well-known
phishing vector the spec doesn't name explicitly but that the same
XSS-adjacent security discipline (Sec. 4) clearly calls for.

## 7. "Unauthorized access page" is the redirect to `/login`, not a separate page

Sec. 3 lists "Unauthorized access page" and "Access denied page" as two
items. This project treats them as two different *situations*, not
necessarily two different *pages*: an unauthenticated request gets
redirected straight to `/login` (the login form itself is the "you need to
sign in" communication - this is how most real systems handle it, and an
extra interstitial page that just says "please log in, click here" adds a
click without adding information). An authenticated-but-wrong-role request
gets `common/access-denied.jsp`, which genuinely needs to be its own page
(redirecting to `/login` would be actively wrong - the person doesn't need
to re-authenticate, they need to be told which role this section requires).

## 8. `NavigationUtil` - a small factor-out caught during self-review

The role → dashboard-path mapping was first written directly inside
`LoginServlet`. Reviewing `access-denied.jsp`'s "go to my dashboard" link
found the same mapping was needed a second place; rather than duplicate the
switch statement, it's now `NavigationUtil.dashboardPathFor()`, used by
both. Noted here the same way Phase 3's `AbstractSoftDeletableDAO` fix was -
this project tries to surface these self-corrections rather than only
presenting the after-the-fact clean version.

## 9. Login page design

The first permanent (non-placeholder) UI in the project, so it got real
design attention rather than being deferred to Phase 16's polish pass -
see `assets/css/tokens.css`'s header comment for the palette/type reasoning
and the chat delivery for the full brainstorm. Established here as a small,
reusable token set (`tokens.css`) that Phase 5+ dashboards extend, not a
one-off page-specific stylesheet.

## 10. A bug I introduced and then caught: `${sessionScope.isProduction}`

The first draft of `login.jsp` gated the demo-credentials hint behind
`${not sessionScope.isProduction}` - referencing a session attribute that
was never actually set anywhere. In EL, a missing attribute evaluates as
null/false, so `not sessionScope.isProduction` would always have evaluated
to `true`, meaning the "gate" would have silently never gated anything,
including in a hypothetical production deployment. Fixed by having
`LoginServlet` set `isProduction` as a request attribute from
`AppConfig.isProduction()` on every code path that forwards to the page
(including both error-handling branches, which needed the same fix
separately). Flagged here rather than silently corrected, for the same
reason as #8.

---

## Verification

- **javac, uncapped (`-Xmaxerrs 10000`) this time**: earlier phases' default
  100-error cap was silently truncating output before reaching Phase 4's
  files at all - re-run properly here. 690 total errors across all 96
  project files, and every one traces to the same root cause (no dependency
  JARs reachable from this sandbox): `package jakarta/org/io does not exist`,
  the `cannot find symbol` cascade that follows from it, `method does not
  override...` (javac can't confirm an `@Override` against an unresolvable
  superclass like `HttpServlet`), and `static import only from classes and
  interfaces` (the same unresolvability applied to `import static
  org.junit...`). Confirmed by inspecting representative examples of every
  category, including all 14 `method does not override` instances (every
  one is a `doGet`/`doPost`/`doFilter`/`init` `@Override` on a class whose
  supertype comes from the unavailable `jakarta.servlet` package).
- **`web.xml` filter-class cross-check**: all four `<filter-class>` entries
  match their real package + class name on disk exactly.
- **Manual trace of the full filter chain + CSRF flow** for both the public
  `/login` path and a protected path, confirming: `CsrfFilter` still runs
  (and both issues and validates tokens) on public paths like `/login`'s POST
  - login forms are a real CSRF target ("login CSRF") - and that
  `AuthorizationFilter`'s role restriction correctly does *not* apply to
  `/change-password`, which needs authentication but no specific role.
