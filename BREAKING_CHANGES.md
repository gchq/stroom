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
  behaviour. A lock set manually by an administrator is never affected by this and never expires.

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

## [v7.3]
* StroomQL `vis as` keyword combination replaced with `show`.

## [v7.2]

* Quoted strings in dashboard table expressions can now be expressed with single and double quotes. As part of this change apostrophes in text are no longer escaped with `''` but instead require a leading `\` before them if they are in a single quoted string. In many cases it is preferable to use double quotes if the string in question has an apostrophe. Note that the use of `\` as an escape character also means that any existing `\` characters will need to be escaped with a preceding `\` so `\` must now become `\\`.  