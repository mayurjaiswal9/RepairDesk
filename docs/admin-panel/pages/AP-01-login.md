# AP-01 · Sign In

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-01 |
| **Route** | `/login` (optional `?returnUrl=<encoded relative path>`) · `/change-password` for the forced first-login step (see §5.3) |
| **Roles** | Public. `ADMIN`, `STAFF`, `TECHNICIAN` may complete sign-in; `CUSTOMER` is authenticated by the API but refused entry to the panel (§5.2) |
| **Entry points** | Direct URL · redirect from any guarded route when no session exists · `401` interception from any page ([README §6.1](../README.md)) · **Sign out** in the app shell ([AP-02](AP-02-app-shell.md)) |
| **Primary endpoints** | `POST /auth/login` · `GET /me` · `POST /me/change-password` · `POST /auth/reset-password` · `POST /auth/logout` (best-effort, only to discard a refused customer session) |
| **Archetype** | Standalone centered card — the only authenticated-shell-free page besides the error routes |
| **Related** | [AP-02 App Shell](AP-02-app-shell.md) · [AP-03 Dashboard](AP-03-dashboard.md) · [AP-17 My Account](AP-17-account.md) · [features/authentication.md](../../features/authentication.md) · [API.md §6–7](../../API.md) |

## 2. Purpose

The single door into the panel. It authenticates an internal user, establishes the session that every other page depends on, and — just as importantly — turns away the people who don't belong here: customers, who can authenticate perfectly well against the same API but whose entire feature set lives in the mobile app. It also carries the two obligations that only exist at sign-in: displaying **ACE Telecom** as the app holder's brand ([features/authentication.md §12](../../features/authentication.md)), and forcing the seeded default administrator to replace the handover password before anything else becomes reachable. Everything else on the page is deliberately minimal — two fields, one button, one recovery link — because a login screen that offers choices is a login screen that fails.

## 3. Layout anatomy

```
┌────────────────────────────────────────────────────────────────────┐
│                                                                    │
│                     ┌──────────────────────────┐                   │
│                     │        ACE Telecom       │  brand block      │
│                     │   RepairDesk Admin Panel │                   │
│                     └──────────────────────────┘                   │
│                                                                    │
│        ┌──────────────────────────────────────────────┐            │
│        │  Sign in                                     │  <h1>      │
│        │                                              │            │
│        │  ⓘ Your session is no longer valid.          │  notice    │
│        │  ⚠ Username or password is incorrect.        │  error     │
│        │                                              │            │
│        │  Username *                                  │            │
│        │  [ ____________________________ ]            │            │
│        │                                              │            │
│        │  Password *                                  │            │
│        │  [ ______________________ ] [👁 show]        │            │
│        │                                              │            │
│        │  [           Sign in            ]            │  primary   │
│        │                                              │            │
│        │  Forgot your password?                       │  link      │
│        └──────────────────────────────────────────────┘            │
│                                                                    │
│     Customers: use the RepairDesk mobile app to submit and         │  footer
│     track repairs. No account is created here.                     │  note
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

The card is 400px wide, vertically centered, on the `--color-background` surface. Below 768px it becomes full-width with `--space-m` gutters; nothing else about the page changes responsively.

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Brand block | Logo + text | "ACE Telecom" in `display`, "RepairDesk Admin Panel" in `body-medium` / `text-secondary` | **ACE Telecom is the prominent name**, the product name is secondary — required by [features/authentication.md §12](../../features/authentication.md) and [PRD §6](../../PRD.md). Static text; the business name is **not** fetched, because `GET /settings` is admin-only and there is no unauthenticated settings read. |
| 2 | Page title | `<h1>` | "Sign in" | One per page; receives focus on load. |
| 3 | Session notice | Info banner, `aria-live="polite"` | Reason the user was sent here | Rendered only when arriving from an interception: "Your session is no longer valid. Please sign in again." (`401`) or "Sign out complete." Dismissed on first submit. Distinct in styling from the error banner (#4) — an expired session is not a failed attempt. |
| 4 | Error banner | Error banner, `role="alert"` | Mapped message from §6 | Form-level errors only (credentials, account state, network, server). Field-level problems bind to their control instead. Never renders a raw exception or JSON ([README §6.2](../README.md)). |
| 5 | Username field | Text input, required | User entry | Label "Username" — and **only** a username. [API.md §6](../../API.md) documents an `identifier` accepting username *or* mobile, but the shipped endpoint takes `username` and looks up by username alone, so a mobile number silently fails as bad credentials. The label must not promise otherwise (§10 Q2). Trimmed of surrounding whitespace before submit ([features/authentication.md §13](../../features/authentication.md)). `autocomplete="username"`, autofocused when no error banner is present. Client validation: non-empty, on blur and on submit. |
| 6 | Password field | Password input, required | User entry | Masked by default with a **Show/Hide** toggle that flips `type` and its own `aria-pressed`/label. `autocomplete="current-password"`. Never pre-filled, echoed in any message, or logged ([Design-System §9](../Design-System.md)). Client validation: non-empty only — minimum length is a *registration* rule and enforcing it here would leak password policy against existing accounts. `Enter` submits the form. |
| 7 | Sign in | Primary button, full width | — | Disabled and showing an inline spinner while the request is in flight; no double-submit. Label stays "Sign in" (no text swap) so the button doesn't resize. |
| 8 | Forgot password | Text link | — | Opens the reset panel (§5.4). Exposed in the panel — see §10 Q6 for the security decision it depends on. |
| 9 | Registration link | *Intentionally absent* | — | The mobile app shows "Don't have an account? Register"; the panel **must not**. `POST /auth/register` only ever creates `CUSTOMER` accounts, and customers cannot use this panel. Staff accounts are created by an administrator in [AP-12 Staff](AP-12-staff.md). |
| 10 | Remember me | *Intentionally absent* | — | JWTs are non-expiring, so a "remember me" checkbox would only choose between `sessionStorage` and `localStorage` — i.e. it would ask the user to make a security decision on the team's behalf. V1 keeps the single storage policy recommended in [README §6.1](../README.md) and states the consequence in the footer note (#12). Revisit if Q1/Q7 in §10 resolve differently. |
| 11 | Customer footer note | Text, `body-small` | "Customers: use the RepairDesk mobile app to submit and track repairs. No account is created here." | Always visible. Sets the expectation *before* a customer wastes an attempt, so the rejection in §5.2 reads as confirmation rather than a surprise. |
| 12 | Session-scope note | Text, `body-small` | "You'll be signed out when you close your browser." | Only shown if the storage policy in [README §6.1](../README.md) is adopted as recommended; it makes the absence of "remember me" explicable rather than mysterious. |

## 5. Interactions & dialogs

### 5.1 Sign in

| Step | Behavior |
| --- | --- |
| Submit | Client validates both fields; if either is empty the request is not sent and messages bind to the offending controls. |
| Request | `POST /auth/login` with `{ username, password }`. The button is busy for the whole sequence below — the user sees one continuous action, not three. |
| Identify | The `200` response carries `role` and `mustChangePassword` directly, which is all the gate below needs; the shell then loads `GET /me` for the profile fields it displays ([AP-02 §4](AP-02-app-shell.md)). The response shape is undocumented, so §10 Q2 asks for it to be written into the contract before the panel depends on it. |
| Gate | `CUSTOMER` → §5.2. `mustChangePassword = true` → §5.3. Otherwise the session is established and the user is routed per §5.5. |

### 5.2 Customer rejection

A `CUSTOMER` who authenticates successfully is **never** given a panel session. The token is discarded before it is written anywhere, `POST /auth/logout` is called best-effort so the refusal is audited, and the error banner reads:

> **This panel is for staff.** Customers should use the RepairDesk mobile app to submit and track repairs.

The user stays on `/login` with the username preserved and the password cleared. No route change occurs, so there is no moment in which an empty shell flickers into view.

### 5.3 Forced password change

The seeded default administrator (`admin`, handed over with a known password — [PRD §4](../../PRD.md), and the `must_change_password` column in [Database.md](../../Database.md)) must replace it before reaching any other page. This is **not** a one-off first-boot path: the flag is also set on every account an administrator creates through `POST /users` and on every administrator-initiated `POST /users/{id}/reset-password`. In other words, every new staff member's first sign-in passes through this step, which makes it a routine onboarding screen rather than an edge case, and worth the polish of a real form.

- The session is **provisional**: the token is held for API calls, but the route guard sends every navigation attempt to `/change-password` until the flag clears. Typing `/dashboard` does not bypass it.
- The step is a form page, not a dialog — it is the whole task, and a dismissible overlay would imply it is optional.
- Copy: "Choose a new password. Your account still uses the password it was set up with."
- Fields: **Current password** * (not prefilled; the user just typed it, but it is re-entered rather than reused from memory), **New password** * (≥8 characters, mirroring the server rule), **Confirm new password** * (must match — a client-side check only, since the server takes just two fields). All masked with show/hide.
- Endpoint: `POST /me/change-password` with `{ currentPassword, newPassword }` → `204`. `422` binds `fieldErrors[]` to the matching controls. A wrong current password returns `403` from this endpoint (not the `401` documented in [API.md §7](../../API.md)) and must be shown as a field error on **Current password**, never as the page-level forbidden panel — see §10 Q4.
- On success: toast "Password updated", the flag is cleared server-side, and routing continues per §5.5.
- The only other action available on this step is **Sign out**, which clears the provisional session and returns to `/login`.

### 5.4 Forgot password

`POST /auth/reset-password` is public and enumeration-safe ([API.md §6](../../API.md)), so the panel exposes it rather than forcing every locked-out technician through an administrator. It replaces the card's contents in place (route `/login` with an internal step, so browser Back returns to the sign-in form).

| Field | Rules |
| --- | --- |
| **Username** * | Required. Sent as `username`. Username only, for the same reason as element #5. |
| **New password** * | Required, ≥8 characters. |
| **Confirm new password** * | Required, must match. Client-side only — the server takes just the username and the new password. |

- Endpoint: `POST /auth/reset-password` with `{ username, newPassword }` → `204` (API.md §6 says `200`; the body is empty either way).
- Success copy is **generic and identical regardless of whether the account exists**: "If an active account matches what you entered, its password has been updated. Sign in with your new password." Nothing in the UI — not the message, not the timing, not a differing error — may distinguish a hit from a miss ([features/authentication.md §6](../../features/authentication.md)).
- `422` binds to the password fields (too short, mismatch). Disabled accounts are silently skipped server-side and produce the same generic success, so the panel must not imply the password definitely changed.
- A **Back to sign in** link returns to §5.1 with the username preserved.

### 5.5 Post-sign-in routing (`returnUrl`)

1. If `returnUrl` is present, it must be a **relative path within the panel**. Absolute URLs, protocol-relative values (`//evil.example`), and anything that fails the allow-list are discarded silently — an open redirect on a login page is a phishing primitive.
2. If the resolved target is a route the user's role cannot access, it is discarded in favor of their default landing rather than dropping them onto a forbidden panel.
3. Default landing: `ADMIN` and `STAFF` → `/dashboard`. `TECHNICIAN` → `/jobs` (see [AP-03 §7](AP-03-dashboard.md) and §10 Q9 in [AP-02](AP-02-app-shell.md)).
4. The login page is replaced in history, so browser Back from the first authenticated page does not return to `/login`.
5. An already-authenticated user who navigates to `/login` is redirected straight to their default landing; the form is never shown to someone with a live session.

## 6. Page states

| State | Presentation |
| --- | --- |
| **Idle** | The form as sketched in §3. |
| **Submitting** | Sign in button busy and disabled; both fields read-only; the toggle and Forgot-password link stay operable. |
| **Invalid credentials** (`401`) | Error banner: "Username or password is incorrect." Identical wording for an unknown username and a wrong password — no enumeration. Password cleared, username preserved, focus returns to the password field. |
| **Account disabled** (`403`) | "This account is not active. Contact your administrator." A **deleted** account returns the generic `401` above instead, since the server only looks up active users. See §10 Q5 — distinguishing a disabled account from an unknown one sits uneasily with the no-enumeration rule. |
| **Customer refused** | The §5.2 banner. Not an error the user can fix by retrying, so the copy points at the mobile app instead of inviting another attempt. |
| **Must change password** | The §5.3 form, replacing the sign-in card. |
| **Network failure** | "Can't reach RepairDesk. Check your connection and try again." with **Try again**; nothing is cleared and no state changed. |
| **Server error** (`500`) | Generic error banner plus the `correlationId` in `body-small`, per [README §6.2](../README.md). Never the raw message body. |
| **Session expired** | The §4 #3 notice above the form, carried in from the `401` interception. |
| **Rate limited** (`429`, if enabled) | "Too many sign-in attempts. Wait a moment and try again." — see §10 Q10. |
| **Loading / Empty / Filtered-empty / Forbidden** | Not applicable. The page has no data to load, no collection to be empty, and is public by definition. |

## 7. Permissions & visibility

- The route is **public** and must render without a token. It is the only page besides the error routes that renders outside the app shell — no sidebar, no top bar, no bell.
- Authentication succeeds for any active account; **panel entry** is what is role-gated, and it is gated client-side by necessity: the API has no "internal roles only" endpoint to lean on. This is the one place in the panel where a client-side check is the primary control rather than a UX affordance, so it is expressed as "refuse to establish a session" rather than "hide the navigation".
- The refusal is not a security boundary for data: every endpoint still enforces its own roles server-side, so a customer who bypassed the client check would see `403` on essentially every panel endpoint anyway. The check exists to give an honest explanation instead of a wall of forbidden panels.
- Disabled and deleted accounts are rejected by the API; the panel only relays the outcome.

## 8. Acceptance criteria

**AC-AP01-01** — Given I am an administrator with valid credentials,
When I submit the sign-in form,
Then a session is established and I land on `/dashboard` with the app shell rendered, and browser Back does not return me to `/login`.

**AC-AP01-02** — Given I open `/login`,
When the page renders,
Then **ACE Telecom** is displayed as the most prominent name on the page, above the product name.

**AC-AP01-03** — Given I was redirected to `/login?returnUrl=%2Frequests%3Fstatus%3DPENDING_REVIEW` after my session was rejected,
When I sign in successfully as an administrator,
Then I land on `/requests?status=PENDING_REVIEW` with that filter applied, and the session notice explaining why I was signed out is no longer shown.

**AC-AP01-04** — Given a `returnUrl` that points to an absolute or protocol-relative URL outside the panel,
When I sign in,
Then the value is discarded and I land on my role's default landing page — the browser is never navigated off-origin.

**AC-AP01-05** — Given a `returnUrl` naming a route my role cannot access,
When I sign in,
Then I land on my default landing page instead of a forbidden panel.

**AC-AP01-06** — Given I submit the form with an empty username, an empty password, or both,
When validation runs,
Then submission is blocked with field-level messages and **no** request is sent to `POST /auth/login`.

**AC-AP01-07** — Given I enter a username that does not exist, and separately a real username with the wrong password,
When each attempt fails,
Then both show the identical message "Username or password is incorrect", the password field is cleared, the username is preserved, and nothing distinguishes the two cases.

**AC-AP01-08** — Given I hold a `CUSTOMER` account with correct credentials,
When I sign in,
Then I remain on `/login` with the message "This panel is for staff. Customers should use the RepairDesk mobile app to submit and track repairs", no token is persisted anywhere in the browser, and the panel shell is never rendered — not even momentarily.

**AC-AP01-09** — Given my account has been disabled by an administrator,
When I sign in with otherwise-correct credentials,
Then I see "This account is not active. Contact your administrator." and no session is established.

**AC-AP01-10** — Given I sign in as the seeded default administrator whose `mustChangePassword` is `true`,
When authentication succeeds,
Then I am taken to the change-password step instead of the dashboard, and typing `/dashboard`, `/jobs`, or any other panel URL returns me to that step.

**AC-AP01-11** — Given I am on the forced change-password step,
When I submit a new password shorter than 8 characters or one that does not match its confirmation,
Then submission is blocked with field-level messages, and when I submit a valid new password the change succeeds, I proceed to my landing page, and signing in again with the new password does not prompt for a change.

**AC-AP01-12** — Given I open **Forgot your password?** and submit an identifier that matches no account,
When the response returns,
Then I see the same generic success message shown for a real account, with no difference in wording, and the panel never states whether the account exists.

**AC-AP01-13** — Given the network is unreachable,
When I submit the form,
Then I see a retryable "Can't reach RepairDesk" message, my typed username is preserved, and no session state changes.

**AC-AP01-14** — Given `POST /auth/login` returns a `500`,
When the error renders,
Then I see a human-readable message with the `correlationId` in small text, and no raw exception text, stack trace, or JSON is visible anywhere on the page.

**AC-AP01-15** — Given I navigate the page using only the keyboard,
When I tab through it,
Then focus starts at the `<h1>`, moves through username, password, show/hide toggle, Sign in, and Forgot password with a visible focus ring on each; `Enter` in either field submits; and when an error banner appears it is announced by a screen reader without moving my focus unexpectedly.

**AC-AP01-16** — Given I already have a valid session,
When I navigate to `/login` directly,
Then I am redirected to my role's default landing page and the sign-in form is not shown.

**AC-AP01-17** — Given I am on `/login`,
When I look for a way to create an account,
Then no registration link or button exists anywhere on the page, and the footer note directs customers to the mobile app.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| "Don't have an account? **Register**" link to `/register` | No registration entry point at all | Self-registration only creates `CUSTOMER` accounts, and customers cannot use the panel. Offering it would guarantee a dead end. |
| Any authenticated role is routed into the app, including customers (the dashboard branches to `CustomerHomeScreen`) | `CUSTOMER` is refused with an explanation and no session | The panel has no customer surface; dropping a customer into it would show them nothing but forbidden panels. |
| `mustChangePassword` is parsed from the login response and then **ignored** — the seeded admin, every newly created staff account, and every admin-reset account can keep their initial password indefinitely | Forced change-password step blocks all other routes | [PRD §4](../../PRD.md) requires the default credentials to be changed after first login; a password chosen by someone else and never rotated is an open door, and the server already tracks the flag. |
| No forgot-password entry point in the login UI, despite the endpoint existing | **Forgot your password?** exposed with enumeration-safe copy | A locked-out technician otherwise needs an administrator; the endpoint is public and already designed for this. |
| Login errors surface as `'Error: $e'` for anything that isn't an `AppException` — raw exception text in the UI | Mapped, human messages only, with `correlationId` for support | [README §6.2](../README.md) hard rule; the raw-exception failure mode is [Bugs.md](../../Bugs.md) #6 recurring. |
| Session token persisted for indefinite auto-login on app launch | Session scoped per [README §6.1](../README.md); no "remember me" | A non-expiring JWT in browser `localStorage` is permanently harvestable via any XSS. Different threat model from a mobile keystore. |
| Field label "Username" with an email-style keyboard | Label "Username", with the mismatch against the documented `identifier` raised for resolution (§10 Q2) | The behavior is the same because the endpoint only resolves usernames; what changes is that the panel refuses to imply otherwise until the contract and the implementation agree. |

## 10. Open questions

1. **Token storage.** [README §6.1](../README.md) recommends holding the JWT in memory with `sessionStorage` as the reload fallback, accepting re-login on browser restart. This must be ratified before build: it determines elements #10 and #12, the `401` interception behavior in [AP-02](AP-02-app-shell.md), and whether a "remember me" control ever exists. **Blocking** — every authenticated page depends on the answer.
2. **The documented login contract does not match the shipped one.** [API.md §6](../../API.md) documents `POST /auth/login` taking `{ identifier, password }` — identifier being "username or mobile" — and returning `{ token, user }`. The endpoint actually takes `{ username, password }`, resolves the account **by username only**, and returns a flat `{ token, userId, username, role, mustChangePassword }`. The panel is specified against the shipped behavior, which means the field is labelled "Username" and a staff member who tries their mobile number gets "Username or password is incorrect" with no explanation. Either document the shipped contract or add mobile lookup; do not leave the two in disagreement. **Blocking for element #5** — the field label depends on it.
3. **`mustChangePassword` is returned by login but documented nowhere.** It is on `GET /me` in the contract and on the login response in the implementation. The forced flow in §5.3 depends on it, so it needs to be in API.md before it can be treated as a stable field rather than an accident.
4. **Wrong-current-password returns `403`, not `401`.** [API.md §7](../../API.md) documents `401` for `POST /me/change-password` with a bad current password; the endpoint returns `403 FORBIDDEN`. Under the panel's standard error mapping ([README §6.2](../README.md)) a `403` means "render the page-level forbidden state", which would replace the change-password form with an access-denied panel — exactly the wrong outcome. Either the status changes to `401`/`422`, or this page keeps a documented local exception to the mapping. **Blocking for §5.3.**
5. **Disabled-account response and enumeration.** Login returns `403 "Account is disabled"` for a disabled account and `401` for everything else, matching [API.md §6](../../API.md) but not §2/§18, which describe `401` for disabled accounts. Beyond the inconsistency, a distinct "account is not active" response *is* an enumeration signal and contradicts the generic-credentials rule two lines above it. Decide: keep the specific message (better for the locked-out employee) or collapse it into the generic one (better against enumeration). The panel currently specifies the specific message. Note that a **deleted** account produces the generic `401`, since the lookup only finds active users.
6. **Public password reset is an account-takeover vector.** `POST /auth/reset-password` takes `{ username, newPassword }` and sets the password — no token, no code, no proof of ownership, and no rate limit. Anyone who knows the seeded administrator is called `admin` can take that account from the public internet, and the reset does **not** set `mustChangePassword`, so the real admin gets no signal that it happened. This is a backend design flaw rather than a panel one, but exposing element #8 puts a convenient front door on it. Recommendation: keep the link only if the endpoint gains a verification step or is restricted; otherwise remove element #8 and route recovery through an administrator via `POST /users/{id}/reset-password`. **Blocking for element #8** — security sign-off required before build.
7. **Session lifetime expectations.** If Q1 resolves to in-memory storage, staff re-authenticate on every browser restart, which is a visible change from the mobile app's indefinite auto-login. Confirm the operational team accepts it, or specify an explicit, deliberate opt-in.
8. **`confirmPassword` does not exist server-side.** [API.md §6–7](../../API.md) lists it on both change-password and reset-password; neither request object has it. The panel treats confirmation as a client-side check only, which is the right UX regardless — but the contract should stop describing a field the server ignores.
9. **Route for the forced change step.** `/change-password` is not in the [README §4](../README.md) route map. Either add it there or fold the step into `/login`; the spec assumes a dedicated route so refreshing mid-flow behaves predictably. Documentation change, not a behavior question.
10. **There is no login throttling.** [API.md §3](../../API.md) lists `RATE_LIMITED` (429) as conditional ("if enabled"); nothing in the backend implements it. So `POST /auth/login` and the public `POST /auth/reset-password` accept unlimited attempts, which makes Q6 materially worse — an unthrottled endpoint that sets passwords by username is brute-forceable for the username alone. The panel cannot fix this client-side; the `429` copy in §6 is specified so it is ready if throttling is added. Raise with the backend team alongside Q6.

---

*Spec status: draft. Verified against `frontend/lib/features/authentication/presentation/login_screen.dart`, `frontend/lib/features/authentication/data/auth_repository.dart`, `frontend/lib/features/authentication/models/auth_models.dart`, `frontend/lib/features/account/data/profile_repository.dart`, the shipped `AuthController`, `AuthService`, `UserService`, and the auth request/response records in `backend/`, [API.md](../../API.md) §2, §6–7, §18, [features/authentication.md](../../features/authentication.md) §4–14, and [PRD.md](../../PRD.md) §4, §6.*
