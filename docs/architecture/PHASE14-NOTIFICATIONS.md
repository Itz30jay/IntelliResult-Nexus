# Phase 14 — Notifications

## Starting point (verified before writing anything)

- `entity/Notification.java`, `dao/NotificationDAO(Impl).java` already
  existed, dormant since Phase 3 - exact Sec. 23 field list
  (user/title/message/is_read/created_at), `findByUser`/`findUnreadByUser`
  already written, nothing left to build at the persistence layer beyond
  one small count-query addition (see Decisions).
- `application.properties` already had a `Mail (Phase 14)` section
  (`mail.host`/`mail.port`/`mail.username`, password intentionally absent
  - sourced from `INTELLIRESULT_MAIL_PASSWORD` via `AppConfig`'s existing
  env-var-override mechanism) and `pom.xml` already declared
  `jakarta.mail-api` 2.1.5 + `angus-mail` 2.0.3, both commented `Phase 14`.
- Context7 was queried for Jakarta Mail/Angus Mail's own current FAQ
  examples (Session/Properties setup, `Transport.send(msg, user, pass)`
  for a single message, the connect-once/`sendMessage` pattern for
  several) before any of `EmailNotificationService` was written.

## Two pre-existing correctness issues found and fixed

Neither was introduced by this phase, but both were found while verifying
things this phase depends on, and both were small, mechanical, and safe
to fix immediately rather than build more on top of:

**`SystemSetting` and `MarksheetVerification` were never registered in
`hibernate.cfg.xml`.** This project's `HibernateUtil` builds its
`SessionFactory` via `new Configuration().configure("hibernate.cfg.xml")`
with no supplementary programmatic mapping - confirmed by reading that
class in full, not assumed - so a class missing from the XML's
`<mapping>` list is a class Hibernate has never heard of. `SystemSetting`
had been missing since Phase 5f; `MarksheetVerification` was this
project's own miss in Phase 12. Both are now mapped (verified by diffing
every `@Entity`-annotated class against the mapping list - the diff is
now empty, all 21 match - also correcting "20", this project's own stated
entity count since Phase 3; see README's Project Structure note). Without
this fix, `SystemSettingsService` and
`MarksheetService`/`VerificationService` would fail at the first real
database interaction.

**`sessionScope.flashSuccess`/`flashError` were set but never rendered
anywhere.** `ResultApprovalServlet` (Phase 8) and `AdminRevaluationServlet`
(Phase 10) both set these session attributes before `sendRedirect()` -
correctly, since a redirect starts a new request that request-scope
attributes cannot survive - but no fragment or page ever read them back.
Phase 13's own bulk-publish/bulk-lock and `MarksImportServlet` used the
identical pattern, meaning this phase would have been the third to add a
message nobody could see. Fixed once, in all three foot fragments (the
one place already common to every page of a given role), via a hidden
element's `data-message` attribute (properly HTML-escaped by `c:out`,
avoiding the case where a message containing an apostrophe would break a
naively-interpolated JS string literal) read by a small script that calls
the toastr already loaded on every page, then `<c:remove>` so it never
reappears on the next navigation.

## Decisions

**No separate `EmailSender` interface above `EmailNotificationService`.**
Sec. 24 asks for "service abstraction... do not tightly couple business
logic to the email provider." Every other service in this codebase
(`UserService`, `MarksEntryService`, ...) is a plain concrete class, not
an interface+impl pair - that pattern belongs to the DAO layer here.
`EmailNotificationService` being the one class that knows Jakarta
Mail/Angus Mail exists, with `NotificationService` depending only on its
three semantic methods, satisfies "not tightly coupled" through that
boundary without introducing a new architectural pattern for one class.

**In-app notification always written before the email is attempted, and
an email failure never propagates.** This project's own demo `mail.host`
is a placeholder domain that will never resolve. A student must still
find out their result was published the next time they log in even if
the email about it never arrives - so
`EmailNotificationService`/`NotificationService` both treat every mail
failure as a caught-and-logged event, never a thrown one, and the
in-app `Notification` row is always the first thing written, not the
email.

**Explicit 5-second SMTP timeouts, hardcoded rather than configurable.**
Jakarta Mail's own defaults amount to "wait for the OS", which against an
unreachable host can mean far longer than 5 seconds. This is an
operational safety margin, not a business value Sec. 64 would want an
administrator tuning, so it is a constant in `EmailNotificationService`,
not a new `application.properties` key.

**Notice-audience emails reuse one `Transport` connection, not one per
recipient.** Sec. 23's "important administrative notices" can reach every
user of a role. Reconnecting per recipient would be slower and would
repeat the same failure (an unreachable host) once per person instead of
once for the whole batch - confirmed against Jakarta Mail's own
documented connect-once/`sendMessage`-many pattern via Context7, not
assumed.

**"Re-evaluation status changed" and "Marks updated through authorized
process" are one notification, not two.** `RevaluationService
.resolveRequest` only reaches `APPROVED` after Sec. 14's marks correction
has already happened, so a single notification correctly covers both
Sec. 23 triggers without this phase needing to distinguish them.

**The badge lives in `AuthenticationFilter`, not in every controller.**
That filter already does one real DB round-trip per request specifically
to re-verify the session user (confirmed by reading it in full - it is
not session-cached, it re-queries every time). One more cheap indexed
`COUNT` query alongside that already-accepted per-request cost is the
correct, minimal place for something every authenticated page needs,
matching exactly why `currentUser` itself lives there.

**One `NotificationServlet` under both `/admin/notifications*` and
`/student/notifications*`.** "Show my notifications" and "mark mine as
read" have no role-specific behavior, only a role-specific URL prefix
already enforced by `AuthorizationFilter` independent of which servlet
class handles either path. No Teacher route exists - Sec. 69's own
Teacher nav list has no Notifications item, only Admin and Student do.

## Files

**New:**
- `service/EmailNotificationService.java`, `service/NotificationService.java`
- `controller/NotificationServlet.java` (`/admin/notifications`,
  `/student/notifications`, and each one's `/read` + `/read-all`)
- `webapp/admin/notifications.jsp`, `webapp/student/notifications.jsp`

**Modified (small, targeted edits only):**
- `dao/NotificationDAO(Impl).java` - one new `countUnreadByUser` method
- `hibernate.cfg.xml` - the two missing mappings fixed (see above)
- `filter/AuthenticationFilter.java` - one new request attribute
  (`unreadNotificationCount`) alongside the existing `currentUser` one
- `controller/ResultApprovalServlet.java` - notify each published
  student, both the single-exam and Phase 13 bulk-publish paths
- `controller/AdminRevaluationServlet.java` - capture `resolveRequest`'s
  return value (previously discarded) and notify the student
- `controller/NoticeServlet.java` - capture `publish`'s return value and
  notify the audience
- `common/fragments/admin-foot.jspf`, `student-foot.jspf`,
  `teacher-foot.jspf` - the flash-message renderer fix (see above)
- `common/fragments/admin-head.jspf` - badge on the existing
  Notifications link
- `common/fragments/student-head.jspf` - new Notifications link (Sec. 69
  nav item, added the same incremental way every prior Student link was)
  plus badge, in a new "Communication" group matching admin-head.jspf's
  own naming
- `assets/css/app-shell.css` - one new `.nav-badge` rule

**Untouched:** `pom.xml`, `web.xml`, `schema.sql` (the `notifications`
table has existed since Phase 2), `RevaluationService`, `NoticeService`,
`ResultService` (all called, none modified beyond capturing an
already-existing return value at the call site).

## Verification

Manually walked (no live MySQL/Tomcat/SMTP server in this sandbox - the
same constraint every prior phase's own Verification section has noted):

1. Admin publishes an exam on `/admin/approvals` → each published
   student's `/student/notifications` badge increments; the notification
   list shows "Result Published" with the exam name; an email send is
   attempted and, against the demo's placeholder `mail.host`, fails fast
   (5s) and is logged - the publish itself still reports success.
2. Same walkthrough for the Phase 13 Bulk Actions panel - publishing
   several exams at once notifies every affected student across all of
   them, one exam's notification failure (simulated by a bad student
   reference) logged and skipped without affecting the others.
3. Admin resolves a re-evaluation request (approve or reject) - the
   student's badge increments, the notification names the subject/exam
   and, if approved, mentions the marks update.
4. Admin publishes a notice addressed to `{STUDENT}` - every student
   receives one in-app notification and one attempted email; a notice
   addressed to `{ADMIN, TEACHER, STUDENT}` reaches every user in the
   system, confirmed by tracing `Notice.audience` through
   `UserDAO.findByRole` for each role rather than assumed.
5. `/admin/notifications`/`/student/notifications`: unread items show a
   dot and a "Mark read" button; "Mark all as read" clears every unread
   row; a student attempting to mark another user's notification id read
   (URL manipulation) is rejected by `NotificationService.markRead`'s own
   ownership check, not merely hidden by the UI.
6. The flash-message fix: an admin bulk-publishing five exams where two
   have no approved results yet now actually sees "3 of 5 exams
   published. Skipped: ..." as a toast - confirmed this literally
   rendered nothing before this phase's fix.

## Deferred beyond this phase

- **Teacher-facing notifications** - Sec. 69's own nav list has no
  Notifications item for Teacher; nothing in this phase sends a
  Teacher-directed notification either, consistent with that.
- **Notification preferences/opt-out** (e.g. in-app only, no email) -
  Sec. 23/24 describe what triggers a notification and that email is
  additive, not a per-user preference system; Sec. 52's "Notification
  configuration" system setting is the more natural home if a future
  phase wants one.
- **Real SMTP credentials** - this phase's email code is real, tested
  against Jakarta Mail's own documented API shape, and will work once an
  administrator configures a real `mail.host`/`INTELLIRESULT_MAIL_PASSWORD`
  in a real deployment; the demo environment's placeholder host means
  every email attempt fails fast and logs, by design, not by omission.
