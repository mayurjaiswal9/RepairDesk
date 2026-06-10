# Authentication - Feature Specification

**Product:** RepairDesk (built for ACE Telecom)
**Feature Area:** Authentication, Registration & Access Control
**Version:** 1.1
**Status:** Draft

---

## 1. Purpose

Define the authentication, self-registration, and access-control behavior for RepairDesk so that any user can create an account, securely sign in, recover access through a password reset, maintain a persistent session, and access only the screens, actions, and data permitted by their assigned role.

This specification covers how users register, authenticate, reset their password, how sessions are maintained and terminated, how tokens govern access, and how the system enforces role-based permissions. It establishes the functional rules that any implementation must satisfy.

Goals:

* Allow any user of the app to self-register with a simple form.
* Securely authenticate registered users.
* Allow users to reset a forgotten password.
* Maintain a persistent session that remains valid until the user logs out or the account is removed.
* Restrict access to screens and actions based on user role.
* Provide a single, default Administrator account that is seeded automatically and can be handed over to the app holder.

Out of scope:

* Multi-factor authentication, social/SSO login, and email/SMS-based verification codes unless added through approved requirements updates.

---

## 2. User Roles

Authentication applies to all users of the application. The following roles are recognized:

| Role | Description | How Assigned |
| --- | --- | --- |
| **Customer** | Default role for any self-registered user. Submits and tracks repair requests. | Assigned automatically on self-registration. |
| **Technician** | Views and updates assigned jobs. | Assigned by an Administrator. |
| **Staff Member** | Reviews requests, manages jobs, assigns technicians, manages schedules. | Assigned by an Administrator. |
| **Administrator** | Performs all staff functions plus user management, product management, settings, and reporting. | Seeded by default; may also be assigned by an existing Administrator. |

Notes:

* Registration is generic for all users. Every newly self-registered account starts with the **Customer** role.
* Only an Administrator can elevate a user to **Technician**, **Staff Member**, or **Administrator**, via Staff Management.
* Every user is assigned exactly one role at a time.
* A user account may be **active** or **disabled**. Disabled accounts cannot authenticate.
* Account removal (deletion) by an Administrator permanently revokes access and ends any active session for that account.

---

## 3. Registration Flow

Any user can create an account from the Login screen via a **Register** action.

### Steps

1. From the Login screen, the user selects **Register**.
2. The user is presented with the registration form and enters:
   * Username
   * Mobile number
   * Password
3. The user submits the registration form.
4. The system validates the input (see Validation Rules).
5. The system verifies that the username and mobile number are not already in use.
6. On success:
   * The system creates an account with the **Customer** role and an active status.
   * **V1 decision:** the user is **signed in automatically** on successful registration and routed to their default landing screen (no separate post-registration login step).
7. On failure:
   * The system rejects the registration and displays the relevant error (see Error Scenarios).
   * The user remains on the registration form and may correct and retry.

### Behavior

* Registration is available to anyone without prior authentication.
* New accounts always begin as **Customer**; role elevation is performed only by an Administrator afterward.
* The user's password must be stored securely and must never be returned in any response.

---

## 4. Login Flow

The login flow authenticates a user and establishes a session.

### Preconditions

* The user has an existing, active account.
* The user is not already authenticated (otherwise see Auto-Login Behavior).

### Steps

1. The user opens the application and is presented with the Login screen.
2. The user enters credentials:
   * Username or mobile number
   * Password
3. The user submits the login form.
4. The system validates the input format (see Validation Rules).
5. The system verifies the credentials against stored account records.
6. On success:
   * The system issues an authentication token and establishes a persistent session.
   * The session is associated with the user's identity and role.
   * The user is routed to the default landing screen appropriate for their role.
7. On failure:
   * The system rejects the attempt and displays an error (see Error Scenarios).
   * The user remains on the Login screen and may retry.

### Postconditions

* A valid, persistent authenticated session exists.
* The user's role is known and available for access-control decisions.

---

## 5. Logout Flow

The logout flow terminates an authenticated session.

### Steps

1. The user selects the logout action from within the authenticated application.
2. The system invalidates the active session and clears the stored token from the device.
3. The system clears any cached user identity and role information held for the session.
4. The user is returned to the Login screen.

### Behavior

* Logout is available to all authenticated roles.
* After logout, any attempt to access authenticated screens must redirect to the Login screen.
* Logout must succeed from the user's perspective even if a server-side invalidation call cannot be completed (local token must still be cleared).

### Postconditions

* No valid session remains on the device.
* The user must re-authenticate to access protected screens.

---

## 6. Password Reset Flow

Any user can reset a forgotten password from a dedicated **Reset Password** page reachable from the Login screen.

### Steps

1. From the Login screen, the user selects **Reset Password / Forgot Password**.
2. The user identifies their account by entering their username or mobile number.
3. The system locates the matching active account.
4. The user provides a new password and confirms it.
5. The system validates the new password (see Validation Rules).
6. On success:
   * The account password is updated and stored securely.
   * Any existing sessions for the account may be invalidated, requiring re-login with the new password.
   * The user is returned to the Login screen with a success message.
7. On failure:
   * The system displays the relevant error (see Error Scenarios) and the user may retry.

### Behavior

* The reset page is available without prior authentication.
* The system must not reveal whether an account exists for a given identifier in a way that enables account enumeration; responses are worded generically.
* A disabled or removed account cannot complete a password reset.

---

## 7. Default Administrator Account

The system is seeded with a single default Administrator account so the application is usable on first deployment.

### Rules

* On initial setup, the system automatically creates one **Administrator** account with default credentials (a default username and password).
* These default credentials are intended to be handed over to the app holder (ACE Telecom) for first sign-in.
* The default Administrator can create and manage users, assign roles, and elevate other accounts to Administrator.
* The default credentials should be changeable; the app holder is expected to update the password after first login.
* The seeding process must not create duplicate default Administrator accounts on subsequent restarts.

---

## 8. Session Management

A session represents an authenticated user's active access period and is designed to be persistent.

### Rules

* A session begins on successful login (or auto-registration sign-in) and remains valid indefinitely.
* A session ends only when one of the following occurs:
   * The user explicitly logs out.
   * An Administrator deletes (removes) the account.
   * An Administrator disables the account.
* Sessions do not expire due to time or inactivity.
* Each session is bound to a single user identity and role.
* The session must persist across app restarts (see Auto-Login Behavior).
* When a session ends, the user must be returned to the Login screen and prompted to authenticate again.
* Sensitive session information must never be exposed in API responses or application logs.

### Session Termination Triggers

* Explicit logout by the user.
* Administrative deletion of the user account.
* Administrative disabling of the user account.
* Detection of an invalid or tampered token.

---

## 9. JWT Strategy

Authentication is token-based using JWT, consistent with the PRD and Project Constitution.

### Rules

* Successful authentication produces a JWT that represents the user's authenticated identity.
* The token must carry, at minimum, the user identity and the assigned role required for access-control decisions.
* The token is **non-expiring**: it remains valid until the user logs out or the account is deleted or disabled. No time-based expiry or token renewal is required.
* The token must be presented with each request to protected resources.
* Token validity must be checked against the account's current status on protected requests, so that a deleted or disabled account is rejected even if its token was previously issued.
* The system must reject requests with missing, malformed, or invalid tokens, and requests whose associated account no longer exists or is disabled.
* The token must be stored securely on the device and cleared on logout or session termination.
* Token contents must never include sensitive data (such as the password) beyond what is required for authentication and authorization.

---

## 10. Auto-Login Behavior

Auto-login allows a returning user to resume a persistent session without re-entering credentials.

### Rules

* On application launch, the system checks for a stored token.
* If a valid token exists and its account is active:
   * The user is automatically authenticated and routed to their default landing screen.
   * The Login screen is bypassed.
* If no token exists, or the token is invalid, or the associated account has been deleted or disabled:
   * The user is directed to the Login screen.
* Because sessions are non-expiring, a stored token continues to enable auto-login until the user logs out or the account is removed or disabled.
* Auto-login must never grant access beyond the user's current role and account status.

---

## 11. Role-Based Access Rules

Access to screens, actions, and data is governed by the authenticated user's role.

### General Rules

* Every protected screen and action requires a valid authenticated session.
* Authorization is evaluated on the basis of the user's assigned role.
* A user must never be able to access screens or perform actions outside their role's permissions, regardless of navigation method.
* Unauthorized access attempts must be denied and must not reveal restricted data.

### Role Permissions

| Capability | Customer | Technician | Staff Member | Administrator |
| --- | --- | --- | --- | --- |
| Register an account | Yes | Yes | Yes | Yes |
| Submit repair requests | Yes | No | Yes | Yes |
| Track own repair status | Yes | No | Yes | Yes |
| View assigned jobs | No | Yes | Yes | Yes |
| Update repair progress / add notes | No | Yes | Yes | Yes |
| Review repair requests | No | No | Yes | Yes |
| Approve / reject requests | No | No | Yes | Yes |
| Manage jobs | No | No | Yes | Yes |
| Assign / reassign technicians | No | No | Yes | Yes |
| Manage schedules | No | No | Yes | Yes |
| Manage users and assign roles | No | No | No | Yes |
| Manage products | No | No | No | Yes |
| Configure settings | No | No | No | Yes |
| Access reporting / administration | No | No | No | Yes |

Notes:

* Self-registered users receive the **Customer** role and its permissions only.
* Administrators inherit all Staff Member capabilities.
* Disabled or deleted accounts have no access regardless of role.
* The default landing screen after authentication is role-appropriate, with content and actions filtered by role.

---

## 12. Login Screen Branding

* The Login screen must display **ACE Telecom** as the company name.
* The company name must be presented prominently on the Login screen as the primary brand identifier for the app holder.
* This branding requirement applies to the Flutter login UI.

---

## 13. Validation Rules

### Registration

* Username is required, must not be empty, and must be unique.
* Mobile number is required, must not be empty, must follow a valid mobile-number format, and must be unique.
* Password is required and must not be empty and should meet a minimum length/strength requirement.
* Input must be trimmed of leading and trailing whitespace before validation.

### Login

* Username or mobile number is required and must not be empty.
* Password is required and must not be empty.
* Input must be trimmed of leading and trailing whitespace before validation.
* The system must not disclose which individual field caused an authentication failure (credential errors are reported generically; see Error Scenarios).

### Password Reset

* The account identifier (username or mobile number) is required and must not be empty.
* The new password is required and must meet the same strength requirement as registration.
* The new password and its confirmation must match.

General:

* Validation failures must be surfaced to the user before the corresponding action is attempted.

---

## 14. Error Scenarios

| Scenario | Expected Behavior |
| --- | --- |
| Empty required field on any form | Block submission; show field-level validation message. |
| Invalid mobile-number format | Block submission; show format validation message. |
| Username already in use (registration) | Reject registration; prompt the user to choose another username. |
| Mobile number already in use (registration) | Reject registration; prompt the user to use a different number. |
| Password does not meet strength requirement | Block submission; show password requirement message. |
| New password and confirmation do not match (reset) | Block submission; show mismatch message. |
| Incorrect username/mobile or password (login) | Reject login; show a generic "invalid credentials" message without revealing which field was wrong. |
| Disabled account (login or reset) | Reject the action; show a message indicating the account is not active. |
| Deleted account with a stored token | Treat the session as invalid; redirect to Login. |
| Reset for a non-existent account | Respond generically without confirming whether the account exists (prevent enumeration). |
| Network or server unavailable | Show a retryable error message; do not change any state. |
| Missing, malformed, or tampered token on a protected request | Deny the request; treat the session as invalid and require re-authentication. |
| Attempt to access a screen or action outside the user's role | Deny access; do not expose restricted data. |
| Logout while offline | Clear local token and return to Login regardless of server reachability. |

All error responses must follow a consistent structure and must never expose sensitive information.

---

## 15. Acceptance Criteria

The authentication feature is considered complete when the following are satisfied:

1. Any user can self-register with username, mobile number, and password, and the new account is created with the **Customer** role.
2. Registration enforces unique username and unique mobile number, and rejects duplicates with a clear message.
3. An active user can log in with valid credentials and is routed to the role-appropriate landing screen.
4. A user with invalid credentials cannot log in and receives a generic, non-revealing error.
5. A user can reset a forgotten password from the Reset Password page and subsequently log in with the new password.
6. The password reset flow does not allow account enumeration and does not work for disabled or deleted accounts.
7. A single default Administrator account is seeded automatically on first setup and can sign in with the default credentials.
8. The default Administrator can assign Technician, Staff Member, and Administrator roles to other users.
9. A successful login establishes a session represented by a non-expiring JWT carrying the user's identity and role.
10. A session remains valid across app restarts and never expires due to time or inactivity.
11. A session ends only on user logout, or when an Administrator disables or deletes the account; thereafter the user is returned to the Login screen.
12. Auto-login resumes a persistent session without re-entering credentials while the account remains active.
13. Protected screens and actions are inaccessible without a valid session, and role-based access rules are enforced for Customer, Technician, Staff Member, and Administrator.
14. The Login screen displays **ACE Telecom** as the company name.
15. Sensitive authentication information (including passwords) is never exposed in API responses or logs.
16. All authentication error scenarios behave as defined in Section 14 with a consistent error structure.
