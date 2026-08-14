# AP-17 · My Account

## 1. Page information

| Field | Value |
| --- | --- |
| **Page ID** | AP-17 |
| **Route** | `/account` |
| **Roles** | All authenticated roles (in practice `ADMIN`, `STAFF`, `TECHNICIAN` — customers cannot sign in to the panel, [README §3](../README.md)) |
| **Entry points** | Top-bar avatar menu → My account · Avatar menu → Change password (opens the page with the dialog open) · Redirect from [AP-01](AP-01-login.md) when `mustChangePassword` is true |
| **Primary endpoints** | `GET /me` · `PATCH /me` · `POST /me/change-password` · `POST /auth/logout` |
| **Archetype** | Form page ([Design-System §4](../Design-System.md)) |
| **Related** | [AP-01 Login](AP-01-login.md) · [AP-02 App Shell](AP-02-app-shell.md) · [API.md §7](../../API.md) · [features/authentication.md](../../features/authentication.md) · [Database.md §3.1](../../Database.md) |

> **Contract note:** endpoints below are verified against the deployed backend controllers, which differ from [API.md](../../API.md) in places. See [API-Contract-Drift.md](../API-Contract-Drift.md).

## 2. Purpose

The user's own profile: who the panel thinks you are, the two things about yourself you can change, and the way out. It is intentionally small — staff accounts are created and managed by an administrator on [AP-12](AP-12-staff.md), so nothing here edits anyone else, changes a role, or touches account status. What it does have to do well is be honest about its limits: a username cannot be changed, a role cannot be self-assigned, and a password change has consequences for other sessions that the user deserves to be told about before they commit.

## 3. Layout anatomy

```
┌──────────────────────────────────────────────────────────────────────┐
│ My Account                                                           │  page header
├──────────────────────────────────────────────────────────────────────┤
│   ( PR )   Priya Raman                                               │  identity card
│            @praman     [ Admin ]     Active                          │
├──────────────────────────────────────────────────────────────────────┤
│ Profile                                                              │  section 1
│   Username        praman                                             │
│                   Usernames can't be changed. Ask an administrator   │
│                   if you need a different one.                       │
│   Mobile          0712345678                       [ Change ]        │
│   Role            Admin                                              │
│                   Roles are assigned by an administrator.            │
├──────────────────────────────────────────────────────────────────────┤
│ Security                                                             │  section 2
│   Password        Last changed —                   [ Change ]        │
├──────────────────────────────────────────────────────────────────────┤
│ Session                                                              │  section 3
│   Signed in on this browser.                       [ Sign out ]      │
└──────────────────────────────────────────────────────────────────────┘
```

Single column, max 640px, key–value rows with a fixed 160px label column ([Design-System §5.4](../Design-System.md)).

## 4. Element inventory

| # | Element | Type | Content / source | Behavior & rules |
| --- | --- | --- | --- | --- |
| 1 | Page title | `<h1>` | "My Account" | Static. |
| 2 | Avatar | Initials circle | `name`, else `username` | First letter of the display name, uppercased, on a primary tint. Decorative — `aria-hidden`, since the name is adjacent as text. |
| 3 | Display name | Text, `title-large` | `name` from `GET /me`, falling back to `username` | `name` is optional ([Database.md §3.1](../../Database.md)). Not editable here — see open question 1. |
| 4 | Username handle | Text, `text-secondary` | `username` | Rendered as `@praman`. |
| 5 | Role badge | Role badge | `role` | Shared mapping: `ADMIN` error red, `STAFF` primary, `TECHNICIAN` secondary ([Design-System §5.3](../Design-System.md)). Always the word, never color alone. |
| 6 | Account status | Status chip | `status` | **Active** in normal use. `DISABLED` cannot occur here — a disabled account is rejected at the next request ([API.md §2](../../API.md)) — so the chip renders but is effectively always Active. |
| 7 | Username row | Key–value + helper text | `username` | **Read-only text, not a disabled input.** Helper text: "Usernames can't be changed. Ask an administrator if you need a different one." A greyed-out field with no explanation reads as a bug; a sentence costs nothing. |
| 8 | Mobile row | Key–value + **Change** button | `mobile` | The one self-editable field ([API.md §7](../../API.md)). §5.1. |
| 9 | Role row | Key–value + helper text | `role` | Read-only, with "Roles are assigned by an administrator" so it is clear the omission is deliberate. |
| 10 | Password row | Key–value + **Change** button | — | The value line reads "Last changed —"; `GET /me` exposes no password-changed timestamp, so the em-dash is honest rather than invented. See open question 2. §5.2. |
| 11 | Sign out | Secondary button, error-colored text | — | §5.3. |
| 12 | Must-change-password banner | Warning banner | `mustChangePassword` from `GET /me` | Shown at the top of the page when true. §5.4. |
| 13 | Loading / error | Panels | — | Per [Design-System §6](../Design-System.md); see §6. |

## 5. Interactions & dialogs

### 5.1 Change mobile dialog

| Field | Type | Rules |
| --- | --- | --- |
| **Mobile number** * | Text input, `tel` | Required, prefilled with the current value, max 20 chars ([Database.md §3.1](../../Database.md)). Must differ from the current value or Save stays disabled. |

- Actions: **Cancel** · **Save** (busy while in flight).
- Endpoint: `PATCH /me` with the new mobile → the updated profile ([API.md §7](../../API.md)).
- Success: dialog closes, the Mobile row updates from the response, toast "Mobile number updated".
- **`409 CONFLICT`** — the number belongs to another account — binds to the field as "That mobile number is already in use." The dialog stays open with the input preserved. This is an exception to [README §6.2](../README.md)'s "conflicts get a dialog or toast": inside a one-field form, a field-level message is the clearer placement.
- **`422 VALIDATION_ERROR`** binds `fieldErrors[].field` back to the input.
- The mobile number is a **login identifier** as well as a contact ([API.md §6](../../API.md)), so the dialog says so before saving: "You sign in with this number, so use the new one next time." Changing it does not end the session.

### 5.2 Change password dialog

| Field | Type | Rules |
| --- | --- | --- |
| **Current password** * | Password input with show/hide | Required. Never prefilled. |
| **New password** * | Password input with show/hide | Required, minimum 8 characters ([API.md §7](../../API.md)). Must differ from the current password. |
| **Confirm new password** * | Password input with show/hide | Required, must match the new password. Compared on blur and on submit. |

- Actions: **Cancel** · **Change password** (busy while in flight).
- Endpoint: `POST /me/change-password` with `{ currentPassword, newPassword, confirmPassword }` → `204` ([API.md §7](../../API.md)).
- Client rules mirror the server's purely to save a round trip; the server remains the authority ([Design-System §9](../Design-System.md)). Fields are never prefilled, echoed back, or logged.
- **Wrong current password** binds to the Current password field as "That's not your current password." — never a page-level state. Note that the server returns this as a `403` rather than the `401` documented in API.md (open question 3); because [README §6.2](../README.md) maps `403` to a page-level forbidden state, this endpoint is explicitly exempted: a `403` from `POST /me/change-password` is a field error, not a forbidden page. Getting this wrong would throw a user out of a dialog and onto an "access denied" screen for a typo.
- **Session behavior, stated up front.** The dialog carries a note before the buttons: "You'll stay signed in on this browser. If you're signed in anywhere else, those sessions stay active too — sign out there if you don't want that." That is what actually happens: the endpoint returns `204` with no new token, JWTs are non-expiring and stateless with no revocation list (ADR-003, [API.md §2](../../API.md)), and [features/authentication.md §8](../../features/authentication.md) lists session termination triggers as logout, disable, and delete — password change is not among them. The success toast repeats the outcome: "Password changed. You're still signed in on this browser." The one place the docs waver is [features/authentication.md §7](../../features/authentication.md), which says existing sessions *may* be invalidated after a reset — see open question 4.
- Success also clears `mustChangePassword` server-side, so the §5.4 banner disappears on the next profile read.

### 5.3 Sign out

**Sign out** confirms first — "Sign out of RepairDesk? You'll need your username or mobile and password to get back in." — because an accidental click on a page the user opened to change a phone number is annoying, and re-authentication is a real cost.

On confirm: `POST /auth/logout` → `204`, then the local token and any cached identity are cleared and the browser is redirected to `/login`. **The local clear happens regardless of the server's response** — [features/authentication.md §5](../../features/authentication.md) requires logout to succeed from the user's perspective even when the server call cannot be completed. A failed call is logged to telemetry, not surfaced. Note that the server-side call is an audit no-op: the token remains cryptographically valid ([API.md §6](../../API.md)), which is precisely why the client must clear it.

### 5.4 Must-change-password

`GET /me` returns `mustChangePassword` ([API.md §7](../../API.md)), which is set for the seeded default administrator account ([Database.md §9](../../Database.md)). When it is true:

- A warning banner sits at the top of the page: "Change your password. This account is still using its initial password — set your own before continuing."
- The Change password dialog opens automatically on arrival, with **Cancel** still available (this page is where the user was sent; trapping them in a modal adds nothing that the banner does not).
- The banner stays until a successful change clears the flag.

Whether the user is *forced* here at sign-in, and whether the rest of the panel is blocked until the change is made, is owned by [AP-01](AP-01-login.md) — that decision belongs with the sign-in flow, and this page implements whichever landing behavior AP-01 specifies. This page's contribution is that the flag has a visible, actionable home.

## 6. Page states

| State | Presentation |
| --- | --- |
| **Loading** | Skeletons shaped like the identity card and the three sections. |
| **Loaded** | The page above. |
| **Empty** | Not applicable — an authenticated user always has a profile. |
| **Error** | Standard error panel: mapped message, **Retry** that refetches `GET /me`, and the `correlationId` ([README §6.2](../README.md)). This replaces the mobile app's bare error string, which had no retry at all — see §9. |
| **Action failed** | Dialog stays open, field-bound message where the error maps to a field, error toast otherwise. Input is never discarded. |
| **Saving** | The dialog's confirming button shows a spinner and disables; its fields go read-only; no double submit ([Design-System §5.5](../Design-System.md)). |
| **Must change password** | The §5.4 banner, plus the dialog opened on arrival. |
| **Forbidden** | Not expected — `/me` is scoped to the caller and has no role gate. A `403` renders the standard forbidden panel and is treated as a bug. |
| **Session invalid** | A `401` from any call here is handled by the global interceptor: the session is cleared and the user is sent to `/login?returnUrl=/account` with "Your session is no longer valid" ([README §6.1](../README.md)). |

## 7. Permissions & visibility

- Every authenticated user sees this page, and every endpoint on it acts on the caller's own record ([API.md §7](../../API.md)). There is no role gating and no way to reach another user's profile from here.
- The page never shows an administrative control regardless of role: an admin who wants to change someone else's mobile, reset their password, or change a role does it on [AP-12 Staff](AP-12-staff.md). An admin editing their **own** account uses this page like everyone else.
- Role and account status are display-only. Self-elevation is not possible in the API and is not hinted at in the UI.
- A `CUSTOMER` cannot reach this page because they cannot sign in to the panel ([README §3](../README.md)).

## 8. Acceptance criteria

**AC-AP17-01** — Given I open `/account`,
When the page loads,
Then my display name, username, mobile number, and role badge are shown from `GET /me`, with the role rendered as a word rather than color alone.

**AC-AP17-02** — Given the page is loading,
When I wait for the response,
Then I see skeletons shaped like the profile sections rather than a bare centered spinner.

**AC-AP17-03** — Given the username row,
When it renders,
Then it is read-only text accompanied by a sentence explaining that usernames cannot be changed and who to ask — not a disabled input with no explanation.

**AC-AP17-04** — Given I open **Change** on the mobile row,
When I submit a mobile number already used by another account,
Then the server's `409` is shown as a field-level message on the mobile field, the dialog stays open, and my input is preserved.

**AC-AP17-05** — Given I change my mobile number successfully,
When the dialog closes,
Then the Mobile row shows the new value from the server's response, a toast confirms the change, and I remain signed in.

**AC-AP17-06** — Given the change-password dialog,
When I enter a new password shorter than 8 characters, or a confirmation that does not match,
Then submission is blocked with a field-level message and no request is sent.

**AC-AP17-07** — Given the change-password dialog,
When I submit with an incorrect current password,
Then the error is shown on the Current password field and the dialog stays open — I am **not** taken to a forbidden or error page.

**AC-AP17-08** — Given I change my password successfully,
When the dialog closes,
Then I remain signed in on this browser, no re-authentication is requested, and the confirmation tells me both that I am still signed in here and that sessions on other devices remain active.

**AC-AP17-09** — Given the change-password dialog is open,
When I inspect the fields,
Then all three are masked with a show/hide toggle, none is prefilled, and none of the values appears in a toast, the URL, or the console.

**AC-AP17-10** — Given my account has `mustChangePassword` set,
When I open `/account`,
Then a warning banner explains that the initial password is still in use, the change-password dialog opens automatically, and the banner clears only after a successful change.

**AC-AP17-11** — Given I choose **Sign out**,
When I confirm,
Then `POST /auth/logout` is attempted, the local token and cached identity are cleared even if that call fails, and I land on `/login` with no authenticated page reachable via browser Back.

**AC-AP17-12** — Given `GET /me` returns a 500,
When the error renders,
Then I see a human-readable message, a **Retry** that refetches the profile, and the `correlationId` in small text — never a raw exception string and never an error with no way forward.

**AC-AP17-13** — Given I am a `TECHNICIAN` or `STAFF` user,
When I open `/account`,
Then I see the same page with my own details and my own role badge, and no control that would change my role or account status.

**AC-AP17-14** — Given I operate the page with a keyboard,
When I open a dialog and close it,
Then focus moves into the dialog, is trapped while it is open, `Esc` cancels without saving, and focus returns to the button that opened it.

## 9. Deviations from the mobile app

| Mobile behavior | Panel behavior | Why |
| --- | --- | --- |
| Load failure renders `e.toString()` centered on the screen with **no retry** | Standard error panel with a mapped message, **Retry**, and `correlationId` ([README §6.2](../README.md)) | A raw exception with no way forward is a dead end, and leaking exception text to users is a hard rule after [Bugs.md #6](../../Bugs.md). |
| Username is shown as a plain list row with no note about immutability | Explicit "Usernames can't be changed" helper text | Silence about a constraint is indistinguishable from a missing feature. |
| Role is shown as a badge only, with no explanation of who sets it | Role row with "Roles are assigned by an administrator" | Same reason. |
| Change-mobile dialog has no validation at all and no mention that the number is a login identifier | Required-field validation, no-op guard, and a note that the number is used to sign in | Users change a contact number without realizing they have changed a credential. |
| Change-password errors surface as `Error: <exception>` in a snackbar, including a wrong current password | Field-bound messages, with `403` from this endpoint explicitly treated as a field error rather than a page-level forbidden state | The most common failure here is a mistyped current password; it belongs on the field. |
| No statement about what a password change does to the session | The dialog and the success toast both state the outcome | With non-expiring tokens the answer is surprising — nothing is signed out anywhere — and users assume the opposite. |
| **Sign Out** fires immediately on tap | Confirmation dialog first | One stray click otherwise costs a full re-authentication. |
| `mustChangePassword` is never surfaced anywhere in the UI | Banner plus auto-opened dialog | The flag exists precisely so the seeded admin's initial credentials get replaced; a flag nobody sees achieves nothing. |
| Account details, actions, and sign-out share one undifferentiated card stack | Grouped Profile / Security / Session sections | Sign-out sitting directly beneath "Change password" in the same list invites mis-clicks. |

## 10. Open questions

1. **Editing your own display name.** `GET /me` returns `name`, but `PATCH /me` is documented as updating **only** the mobile number ([API.md §7](../../API.md)), while `PATCH /users/{id}` lets an admin change anyone's `name`. So a user cannot fix a typo in their own name without asking an admin. Either extend `PATCH /me` to accept `name` (preferred — it is self-owned, non-security-bearing data) or add a line to element #3 explaining who to ask. The page currently shows the name as read-only with no explanation, which is the same anti-pattern element #7 exists to avoid.
2. **Password-changed timestamp.** Element #10 shows "Last changed —" because no field carries it. `users.updated_at` exists ([Database.md §3.1](../../Database.md)) but is bumped by any profile change, so it cannot be labelled as a password date. Either expose a `passwordChangedAt` on `GET /me` or drop the value line and leave the row as a labelled action.
3. **Wrong-current-password status code.** [API.md §7](../../API.md) documents `401` for an incorrect current password, but the implementation raises `403 FORBIDDEN` (verified against `backend/.../feature/auth/service/AuthService.java`). This matters more than a wrong number in a table: the panel's global handling sends `401` to the login redirect ([README §6.1](../README.md)) and `403` to a page-level forbidden state ([README §6.2](../README.md)), so **either** code, handled generically, throws the user out of the dialog over a typo. §5.2 exempts this endpoint from both, but the contract and the implementation need to agree. **Blocking for the change-password error handling.**
4. **Does a password change invalidate other sessions?** §5.2 tells the user it does not, based on [features/authentication.md §8](../../features/authentication.md)'s session-termination triggers, the stateless non-expiring token (ADR-003), and the endpoint returning `204` with no new token. But [features/authentication.md §7](../../features/authentication.md) says existing sessions "may be invalidated, requiring re-login with the new password" — for the reset flow, though the wording is loose enough to read as a general intent. Confirm the product answer. If sessions *should* be invalidated, the API needs token versioning or a revocation list, which is a backend change outside this panel's scope; if they should not, correct §7's wording. Until it is settled, the copy in §5.2 states current behavior rather than intent.
5. **The seeded admin may never see the must-change-password flow.** [Database.md §9](../../Database.md) specifies the default admin is seeded with `must_change_password = true`, but the initializer inserts `false` (verified against `backend/.../bootstrap/AdminAccountInitializer.java`), so §5.4's banner would never appear for the account it was designed for. Confirm which is intended, and coordinate the sign-in-time behavior with [AP-01](AP-01-login.md).
6. **Sign-out is a no-op server-side.** `POST /auth/logout` audits but does not invalidate the token ([API.md §6](../../API.md)), so a token captured before sign-out stays valid forever. That is a session-management decision, not a UI one, but it interacts directly with the token-storage question already open in [AP-01](AP-01-login.md) — the two should be settled together.
7. **Deleting or deactivating your own account.** Not offered, and no self-service endpoint exists ([API.md §7](../../API.md)). Confirm this is intended for internal staff accounts rather than an omission; the last-admin guard ([API.md §8](../../API.md)) suggests it is.

---

*Spec status: draft. Element inventory verified against `frontend/lib/features/account/presentation/account_screen.dart`, [API.md §7](../../API.md), [features/authentication.md](../../features/authentication.md), and [Database.md §3.1/§9](../../Database.md).*
