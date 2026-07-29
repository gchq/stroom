# Internal IdP — behaviour stories

**Status:** documentation of decided behaviour, written 2026-07-29 against the working tree.
**Audience:** anyone needing to know what the internal identity provider *does*, without reading code.
Each story is one flow. Config properties are named where they change the outcome; defaults in brackets.

The three account states, because everything below leans on them:

| State | Who sets it | Who clears it | Meaning |
|---|---|---|---|
| **Disabled** | an administrator | an administrator | "You may not use this system." The strongest state; nothing overrides it. |
| **Locked** | the system, on repeated wrong passwords | time, a password reset, or an admin | Brute-force protection, nothing more. Admins cannot lock — only unlock. |
| **Inactive** | the system, on disuse | a successful sign-in (if configured) or an admin | "Nobody is using this." Admins cannot deactivate — only reactivate. |

These are independent flags, not one status. The account list shows them as three columns.

---

## 1. Signing in with a password

- Correct password, healthy account → signed in. Failure count and any lapsed lock are cleared.
- Wrong password → "invalid credentials", failure count +1. Nothing distinguishes a wrong password
  from an account that does not exist.
- Wrong password for the **N**th time (`failedLoginLockThreshold` [3]) → the account locks.
- First ever sign-in (`forcePasswordChangeOnFirstLogin` [true]), or an admin ticked *force password
  change*, or the password is older than `mandatoryPasswordChangeDuration` [90d]
  → signed in, but must set a new password before doing anything else.
- Disabled account → "account disabled, contact your administrator", whatever the password. The
  password is **not checked**, so nothing can be learned by guessing against it, and failures are not
  counted.
- Locked account → whatever the password, the same treatment: not checked, not counted. The message
  gives the cheapest true remedy: "try again in about N minutes" where the lock lapses on its own,
  a pointer at *Forgot password?* where self-service unlock is on, and "contact your administrator"
  only where neither is true.
- Inactive account, correct password → see §4.

The order of answers is fixed: disabled → locked → invalid credentials → inactive. The audit event
records the true reason; the caller gets only the generic message.

## 2. Getting locked out, and back in

Lock cause: N wrong passwords. Lock effect: sign-in refused, credentials unchecked, counting stopped.

Ways back in, in the order a user would try them:

- **Wait.** The lock lapses after `failedLoginLockDuration` [30m], and the refusal message says
  roughly how long remains. Nothing needs to happen at lapse — the next sign-in with the correct
  password simply succeeds and clears the lock.
  - The duration is read at sign-in time against when the lock was applied, so changing the config
    changes locks already in force, both ways.
  - Duration **0** → locks never lapse; one of the two routes below is then the only way back.
- **Self-service unlock** (`allowLockedAccountPasswordReset` [false]): request a password reset
  email (§3). Completing the reset unlocks the account and sets the new password in one step, then
  ends every existing session; the user signs in fresh. With this off, a locked account cannot
  reset — every deployment would otherwise have self-service unlock whether it wanted it or not.
- **Ask an admin**, who uses the *Unlock* action (§6).

While locked, further guesses do nothing: not counted, not extending the lock, not answered.

## 3. Password reset by email

- Any user → *forgot password* → reset email sent if the account qualifies. The response is the
  same whether or not the account exists or qualifies; refusal reasons go to the audit log only.
- Qualifying: enabled; not locked (unless `allowLockedAccountPasswordReset` is on); and not
  inactive (unless `reactivateInactiveAccountsOnLogin` is on, in which case the reset works and the
  sign-in that follows reactivates — the reset itself never does).
- A non-qualifying account gets a **courtesy email** instead of a link: *"A password reset was
  requested for your account, but it cannot currently be completed. If this continues, contact your
  administrator."* One fixed sentence for every refused state — it goes to the account's own
  mailbox so it reveals nothing to the requester, and for a locked account "currently" is honest:
  once the lock lapses, the reset works. No link is ever issued that cannot be completed, so nobody
  sets a new password only to be refused at sign-in. The exact refusal reason goes to the audit log.
- The link lasts `emailResetTokenExpiration` [10m] and works once; a second use of the same link
  fails even if the first is still in flight. Repeat requests are rate limited
  (`passwordResetRequestCooldown` [1m]).
- Completing a reset ends every session the user had, everywhere. They sign in with the new password.
- Requires `allowPasswordResets` [true] and working SMTP config.
- Silence — no email of any kind — happens only where there is nobody to speak to: the address
  matches no account, the account holds no address, the feature is off, or an email already went
  out inside the cooldown window.

## 4. Inactive accounts

- An account never signed into for `neverUsedAccountDeactivationThreshold` [30d], or not signed
  into for `unusedAccountDeactivationThreshold` [90d], becomes inactive. Accounts flagged
  *never expires* are exempt.
- Inactive user tries to sign in with a valid credential (correct password **or** valid
  certificate — the same rule for both):
  - `reactivateInactiveAccountsOnLogin` [false] off → "deactivated due to inactivity"; an admin
    must reactivate. A password reset is refused at the first step for the same reason (§3).
  - on → the account reactivates and the sign-in proceeds, in one step. The reactivation is audited
    as an account-state change, same as if an admin had done it.
- Disabled accounts are never auto-reactivated: reactivation happens only after the credential is
  accepted, and a disabled account never gets that far.

## 5. Certificate sign-in

- Off by default (`allowCertificateAuthentication` [false]). When on, the user id is extracted
  from the certificate CN via `certificateCnPattern` / `certificateCnCaptureGroupIndex`.
- A valid certificate is a credential like a correct password: same state checks, same order —
  disabled refused, locked refused, inactive refused-or-reactivated per §4.
- A lapsed lock is cleared by a certificate sign-in just as by a password one.

## 6. What an admin can do to an account

Actions, not checkboxes — each is explicit and applies regardless of what else is happening
to the account (no lost updates against a user busy failing logins):

- **Disable / Enable** — the admin's lever over signing in. Note this is the *account*: it blocks
  authentication but does **not** end sessions already running or revoke tokens already issued.
  Disabling the corresponding **Stroom user** is what does that (§7).
- **Unlock** — clears a failed-login lock. There is no *Lock*: to bar someone, disable them;
  locking is the system's brute-force response, not an admin control.
- **Reactivate** — clears inactivity. There is no *Deactivate* for the same reason.
- **Set password**, optionally with *force change at next sign-in* — both survive being set in the
  same save; the force-change flag is not lost by also setting the password.
- Account create/edit: user id, names, email, comments, *never expires*.

## 7. Ending access and sessions

Three actions, easily confused. Only the last two stop somebody, and the first is deliberately
named so it cannot be mistaken for containment.

| Action | Where | Ends sessions + tokens | Stops them signing in |
|---|---|---|---|
| *End this user's sessions and revoke their tokens* | Security > User Access | yes | **no** |
| Disable the **Stroom user** | Security > Users | yes | yes |
| Disable the **account** | Security > Manage Accounts | **no** | yes |

- **User, self-service:** *User > Sign Out Other Sessions* — ends every session but the current one.
  A password reset (§3) ends all of them.
- **Admin, in-flight access:** Security > User Access (MANAGE_USERS) shows every user with their
  session and token counts, plus the sessions held across all nodes. Its button ends the sessions
  and revokes the tokens cluster-wide. The tokens are dead permanently; the user is not shut out and
  can sign straight back in. Named for exactly that, since "revoke access" read as containment.
- **Admin, full stop:** disable the Stroom user — same screen, *Open this user* button. This ends
  sessions, revokes tokens *and* refuses them at authentication, interactive or token.
- **External IdP caveat:** when Stroom is not the identity provider, ending sessions forces
  re-authentication but the provider will typically sign the user straight back in without asking
  for a password, and tokens it minted cannot be revoked here. Containment is *disable* — at the
  provider, in Stroom, or both.

## 8. Passwords and policy

- Minimum length [8] and strength [3], applied on the server on **every** route that sets a
  password: sign-in change, reset link, admin set. The previous password cannot be reused.
- Passwords expire after `mandatoryPasswordChangeDuration` [90d]: the next sign-in succeeds but
  requires a change before anything else.

## 9. Signing keys (ADMINISTRATOR only)

- Keys rotate themselves every `jwkRotationInterval` [30d]; old keys stay trusted until tokens
  signed with them have expired, so rotation is invisible to users.
- The Signing Keys screen lists each key's status (Active / Retired / Expired / Revoked, with
  "trusted until <date>" where a wind-down is running) and issue date — and nothing else about the
  key.
- **Revoke** withdraws one key; **Revoke all** withdraws everything and mints a replacement.
  Revoking the active key signs out everyone; humans are signed back in on their next
  authentication, but nodes and stroom-proxy hold their own tokens and may be unable to talk to
  each other for up to ~10 minutes, recovering unaided. This is an emergency lever for a suspected
  key compromise, not an operational tool. Revocation cannot be undone.

## 10. Token lifetimes (background, for the curious)

- Access tokens last `accessTokenExpiration` [60m]; refresh tokens `refreshTokenExpiration` [30d]
  and are single-use, rotated on each refresh; a replayed refresh token revokes its whole family.
- Reset-email tokens [10m], API keys default [365d].

---

## Config quick reference

| Property (`stroom.security.identity.…`) | Default | Governs |
|---|---|---|
| `failedLoginLockThreshold` | 3 | wrong passwords before locking (§1, §2) |
| `failedLoginLockDuration` | 30m | how long a lock lasts; 0 = forever; retroactive (§2) |
| `allowLockedAccountPasswordReset` | false | self-service unlock via reset email (§2, §3) |
| `passwordResetRequestCooldown` | 1m | min gap between reset emails (§3) |
| `reactivateInactiveAccountsOnLogin` | false | auto-reactivation on valid credential (§4) |
| `allowCertificateAuthentication` | false | certificate sign-in at all (§5) |
| `passwordPolicy.allowPasswordResets` | true | the reset-email feature (§3) |
| `passwordPolicy.neverUsedAccountDeactivationThreshold` | 30d | new-and-unused → inactive (§4) |
| `passwordPolicy.unusedAccountDeactivationThreshold` | 90d | used-then-abandoned → inactive (§4) |
| `passwordPolicy.mandatoryPasswordChangeDuration` | 90d | password age forcing a change (§1, §8) |
| `passwordPolicy.forcePasswordChangeOnFirstLogin` | true | first sign-in forces a change (§1) |
| `passwordPolicy.minimumPasswordLength` / `…Strength` | 8 / 3 | policy on every set-password route (§8) |
| `token.accessTokenExpiration` / `…refreshTokenExpiration` | 60m / 30d | token lifetimes (§10) |
| `token.emailResetTokenExpiration` | 10m | reset-link lifetime (§3) |
| `token.jwkRotationInterval` | 30d | signing-key rotation (§9) |
