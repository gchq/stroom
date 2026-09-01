# Breaking Change Log

All breaking changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).


## [v7.13]

* Account email addresses must now be unique, so that the internal identity provider's 'Forgot password'
  flow can identify an account from the email address the user gives it. An account may still have no
  email address at all, in which case it cannot reset its password by email, and any number of accounts
  may have no email address.

  **If two or more accounts currently share an email address then the database migration will stop with
  an error listing the addresses concerned, and Stroom will not start.** Before upgrading, give each
  account its own email address, or clear the email address of all but one of them, e.g.

  ```sql
  SELECT email, COUNT(1), GROUP_CONCAT(user_id)
  FROM account
  WHERE email IS NOT NULL
  GROUP BY email
  HAVING COUNT(1) > 1;
  ```

  This only affects the internal identity provider. Creating or updating an account with an email address
  that another account already uses is now rejected.

* When Stroom is configured to use an **external identity provider** (Keycloak, AWS Cognito, Google, etc.),
  the OIDC client registered at that provider must now list Stroom's sign-in callback as an allowed redirect
  URI. Add the following (substituting your Stroom public URL) to the client's *Valid redirect URIs*:

  ```
  https://<stroom-host>/api/auth/flow/v1/signin-oidc
  ```

  This is the single `redirect_uri` Stroom sends on the authorization request and exchanges the code
  against. **A provider that does not have this exact value registered will reject the login with an
  "invalid redirect_uri" error.**

  If you also use provider-side logout, add the post-logout landing page to the client's *Valid post logout
  redirect URIs*:

  ```
  https://<stroom-host>/
  ```

  The internal identity provider needs no configuration for this — it matches the callback automatically.

* The internal identity provider's password policy no longer supports a character-class **complexity
  regex**. Password strength is now enforced **on the server** using zxcvbn (the same estimator the sign-in
  UI already shows), governed by `minimumPasswordStrength` (a score of 0–4, default 3) — previously the
  strength policy was advertised but only checked in the browser, so the API accepted weak passwords.

  **If your configuration sets `passwordComplexityRegex` under the internal IdP password policy, remove it**
  — the property no longer exists. Passwords are now judged by estimated strength rather than a regex; if you
  relied on the regex to enforce a policy, review `minimumPasswordStrength` instead.

* The internal identity provider's account lockout is now **time-limited by default**. An account locked
  by exceeding `failedLoginLockThreshold` failed logins is unlocked automatically after the new
  `stroom.security.identity.failedLoginLockDuration` (default **30 minutes**), rather than staying locked
  until an administrator unlocks it. This removes a denial of service in which a few failed logins could
  lock any named user — including `admin` — out permanently.

  **If you rely on lockouts being permanent** (cleared only by an administrator or by completing the
  'Forgot password' flow), set `failedLoginLockDuration` to zero, e.g. `"PT0S"`, to restore the previous
  behaviour.

  **The duration now governs locks that are already held, not just new ones.** It is applied when a lock is
  read rather than baked in when the lock is applied, so shortening it releases people who are already
  locked, and lengthening it extends them. In particular, setting it to zero makes **every lock currently in
  force permanent** until an administrator clears it, where previously it would only have affected locks
  applied afterwards.

* When Stroom is configured to use an **external identity provider**, the audience (`aud`) claim of an
  inbound token is now **validated by default**. Previously, if `allowedAudiences` was not configured no
  audience validation was performed at all, so a token minted for a *different* application at the same
  provider could be replayed against Stroom. Now, when `allowedAudiences` is empty, the token's audience is
  validated against Stroom's configured `clientId` instead.

  **If your provider issues tokens to Stroom whose `aud` claim is not Stroom's `clientId`** (for example an
  API/resource identifier), logins or API calls will now be rejected until you list the expected value(s)
  under `allowedAudiences` in the OpenID configuration, e.g.

  ```yaml
  allowedAudiences:
    - "api://my-stroom-resource"
  ```

  A token that carries no `aud` claim at all is still accepted unless `audienceClaimRequired` is set to
  `true`. To opt out of audience validation entirely (not recommended), set `validateAudience` to `false`.
  This only affects external identity providers; the internal identity provider already validated the
  audience.

* The audience (`aud`) claim is now **mandatory by default** for tokens from an **external identity
  provider** — `audienceClaimRequired` now defaults to `true` (previously `false`). An external access token
  that does not carry an `aud` claim is now rejected.

  **If your external identity provider issues access tokens without an `aud` claim** (some Cognito/Okta
  access-token shapes do this), set `audienceClaimRequired` to `false` under the OpenID configuration to
  restore the previous behaviour:

  ```yaml
  audienceClaimRequired: false
  ```

  Relatedly, when `identityProviderType` is `EXTERNAL_IDP` and `validateAudience` is `true` (the default),
  Stroom now **refuses to start** unless either `allowedAudiences` or `clientId` is configured, so that
  audience validation cannot be silently skipped for lack of anything to validate against. Configure one of
  them, or set `validateAudience: false` to deliberately disable audience validation.

* **Optional hardening — new `requiredAccessTokenType` OpenID setting (not a breaking change).** When Stroom
  uses an **external identity provider**, a JWT `id_token` can otherwise be replayed on the API in place of an
  access token — it is signed by the same keys and, under the default audience handling, carries the same
  `aud`. A new optional setting closes this: set `requiredAccessTokenType` under the OpenID configuration to
  the JOSE `typ` header value your provider stamps on its **access** tokens, and any token of a different type
  (such as an `id_token`) is then refused on the API even if its signature is otherwise valid:

  ```yaml
  appConfig:
    security:
      authentication:
        openId:
          requiredAccessTokenType: "at+jwt"
  ```

  The correct value depends on the provider:
  - **RFC 9068-conformant providers** (e.g. Okta), and Keycloak configured with an `at+jwt` access-token
    header type: `"at+jwt"`.
  - **Keycloak** with default settings: `"Bearer"`.
  - Providers that use the **same** `typ` for both access and id tokens (e.g. AWS Cognito, and some Azure AD
    configurations, use `"JWT"` for both) cannot be separated by this setting — leave it unset and rely on
    audience validation (`allowedAudiences`) instead, ensuring the access token's `aud` differs from the
    id_token's.

  **The default is unset, which preserves the existing behaviour** (any token type is accepted), so no action
  is required on upgrade. This setting applies only to an external identity provider; the internal identity
  provider already requires RFC 9068 (`at+jwt`) access tokens.

* The **`TEST_CREDENTIALS` identity provider type has been removed**, along with the publicly-known
  credentials it shipped with. It provided a fixed, committed key and service-user token so that stroom and
  stroom-proxy could authenticate to each other with no real identity provider — convenient for CI/demo, but
  a security risk because the credentials were baked into the product.

  **If your configuration sets `identityProviderType` to `TEST_CREDENTIALS`, Stroom/stroom-proxy will now
  fail validation on start.** Use `INTERNAL_IDP` (stroom) or `NO_IDP`/`EXTERNAL_IDP` (stroom-proxy) instead.

  For test/demo environments that still need a zero-setup shared credential to wire stroom and stroom-proxy
  together, an opt-in replacement is available that is independent of `identityProviderType`. It is **off**
  unless **both** of these are supplied as an environment variable or system property:

  ```
  STROOM_ALLOW_INSECURE_TEST_CREDENTIALS=true
  STROOM_INSECURE_TEST_CREDENTIAL=<a secret string of your choosing>
  ```

  When enabled, a request whose bearer token equals the secret is authenticated as the service (processing)
  user. Because these are runtime settings rather than config-file properties, they do not travel with a
  copied configuration. **Never set them in production.**

* **Locking an account is no longer something an administrator does.** The internal identity provider's
  three account states now have one owner each: an administrator decides whether an account is **enabled**,
  the sign-in process locks an account after repeated wrong passwords, and the account maintenance job marks
  an unused account **inactive**. An administrator can still *undo* the latter two — unlocking an account or
  making it active again — but can no longer apply them.

  **To prevent an account being used, disable it.** That is now the only administrative control over access,
  and it is what the Locked checkbox was often being used for. On the account edit screen, Locked and
  Inactive are no longer tick boxes: each shows what has happened and offers the one action an administrator
  can take.

  **On upgrade, two kinds of account are converted to disabled** and will appear in the accounts list as
  such:

  - an account that was locked with no expiry, which is how a lock applied by an administrator was stored;
  - an account flagged as a *processing account*, a setting that has been removed.

  Review the accounts list after upgrading and re-enable anything that was disabled in error. If you run
  with `failedLoginLockDuration` set to zero, note that failure locks are also stored without an expiry and
  so cannot be told apart from an administrator's — those accounts will be disabled too, and should be
  re-enabled deliberately.

  A user who is locked out is now told that their account is locked, rather than that their credentials were
  wrong, whether or not the password they typed was correct.

* The internal identity provider's account fields have been **renamed** in the REST API and in the accounts
  screen's quick filter, to name the lockout for the one thing it is for:

  | Was | Now |
  | --- | --- |
  | `loginFailures` | `failureCount` |
  | `failureLocked` | `failureLockedMs` (when the lock was applied, rather than a flag) |
  | `status:Locked`, `status:Enabled`, `status:Inactive`, `status:Disabled` | `locked:true`, `enabled:true`, `inactive:true` |

  **If you have saved quick filters, dashboards or scripts that use `status:` or `loginFailures`, update
  them.** The `status` filter term has been removed rather than renamed: the three states are independent,
  so the accounts list now has a column for each instead of collapsing them into one value.

* The internal identity provider's **authorization endpoint now validates the request**. `/oauth2/v1/auth`
  requires `response_type=code`, a `nonce`, and the `openid` scope, and rejects a request that omits any of
  them. Stroom's own sign-in sends all three, so no change is needed for a normal deployment.

  **If you have set `requestScopes` to an empty list**, Stroom omits the `scope` parameter entirely and
  sign-in through the internal identity provider will now fail. Restore the default, or ensure the list
  contains `openid`:

  ```yaml
  requestScopes:
    - "openid"
  ```

* The unused **`allowPasswordResets` property has been removed from the internal identity provider's `email`
  section**. It was documented as the switch that enabled password reset emails, but nothing read it —
  password resets were, and still are, governed only by `allowPasswordResets` under the **password policy**
  section, which defaults to enabled.

  **If your configuration sets `email.allowPasswordResets`, Stroom will now fail to start** with an unknown
  property error. Remove it. If you set it to `false` believing that password reset emails were switched
  off, they were not — set `allowPasswordResets` under the password policy section to `false` to actually
  disable them.

## [v7.3]
* StroomQL `vis as` keyword combination replaced with `show`.

## [v7.2]

* Quoted strings in dashboard table expressions can now be expressed with single and double quotes. As part of this change apostrophes in text are no longer escaped with `''` but instead require a leading `\` before them if they are in a single quoted string. In many cases it is preferable to use double quotes if the string in question has an apostrophe. Note that the use of `\` as an escape character also means that any existing `\` characters will need to be escaped with a preceding `\` so `\` must now become `\\`.  