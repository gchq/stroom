# Stroom Authentication Design

How Stroom authenticates browsers, machines and its own nodes — as built. This document
describes the current design and the intents behind it; it is not a change plan. For
deployment/how-to documentation see stroom-docs (`install-guide/setup/open-id/`), which this
document deliberately does not duplicate.

## 1. The three deployment models

Stroom authenticates users against an OIDC identity provider. There are three deployment models,
distinguished by *where accounts live* and *which component performs the sign-in* (the OIDC
Relying Party):

| Model | Accounts | Relying Party | Config |
|---|---|---|---|
| Internal IdP | Stroom | Stroom | `identityProviderType: INTERNAL_IDP` (default) |
| External IdP | 3rd party (Keycloak, Cognito, Google, Entra ID) | Stroom | `identityProviderType: EXTERNAL_IDP` |
| Edge proxy RP | 3rd party | An authenticating reverse proxy in front of Stroom (AWS ALB + Cognito, NGINX + oauth2-proxy) | `EXTERNAL_IDP` + `edgeAuthentication.enabled` |

**The governing invariant: for any given path, exactly one component is the Relying Party.**
Every historical defect in the edge deployment was a consequence of breaking this — Stroom
starting a second OIDC flow stacked on the one the proxy had already completed. The design
enforces the invariant in code, not just documentation (§6).

Whichever model is used, *authorisation* is always Stroom's: the provider establishes who the
user is; Stroom decides what they may do. A Stroom user record is auto-created (with no
permissions) the first time a verified identity is seen
(`StroomUserIdentityFactory#getOrCreateUserBySubjectId`).

Supported providers: Amazon Cognito, Google, Keycloak, Microsoft Entra ID (all via their OIDC
discovery documents), and the internal IdP. Google requires
`authenticationRequestExtraParams: {access_type: offline}` to issue refresh tokens; Entra ID
requires the `offline_access` scope. Other providers, and edge proxies that mint their own
tokens other than the AWS ALB, are out of scope (§13).

## 2. Component map

| Component | Role |
|---|---|
| `SecurityFilter` (stroom-security-impl) | Every request's authentication decision tree (§3) and CSRF gate (§4) |
| `AuthFlowResource` / `AuthFlowResourceImpl` | The API-driven sign-in flow: `/api/auth/flow/v1/status` and `/api/auth/flow/v1/signin-oidc` (§5, §6). In the impl module, not core-shared: nothing GWT-compiled calls it |
| `OpenIdManager` | Builds the authentication request URI; resolves request-token credentials for the filter |
| `AuthenticationStateCache` / `AuthenticationState` | Server-side state/nonce/PKCE for in-flight flows |
| `StroomUserIdentityFactory` (extends `AbstractUserIdentityFactory`) | Resolves a request to an identity + `CredentialSource`; code-flow token exchange; user auto-creation |
| `DelegatingJwtContextFactory` → `InternalJwtContextFactory` / `StandardJwtContextFactory` | Token verification, selected by `identityProviderType` (§7) |
| `ApiKeyService` | Stroom API keys (`CredentialSource.API_KEY`) |
| Cluster token verifier + `RUN_AS_USER_HEADER` | Inter-node identity (`CredentialSource.CLUSTER_TOKEN`) (§8) |
| `AppServlet` | Injects the bootstrap script that gates GWT loading on the status endpoint (§5, §6) |
| `SessionResourceImpl` | Logout, both models (§9) |
| `RefreshManager` / `UpdatableToken` | Server-side token refresh for session-held identities |

## 3. Request authentication: the `SecurityFilter` decision tree

For every request (in order):

1. **OPTIONS** → passed through (CORS preflight).
2. **Static resources** → passed through.
3. **`@Unauthenticated` servlets/resources** → run as the processing user. State-changing
   requests still get an Origin check (`isOriginValid`) so a cross-site page cannot drive login
   CSRF or trigger reset emails; requests with no Origin/Referer (server-to-server) pass.
4. **Session identity** — `UserIdentitySessionUtil` from the `STROOM_SESSION_ID` cookie. An
   expired (`HasExpiry`) session identity is discarded so the request re-authenticates from
   headers (this is how ALB token refresh reaches a pre-existing session, and how session expiry
   hands over to the code flow).
5. **Request-token identity** — `OpenIdManager#loginWithRequestCredential` →
   `StroomUserIdentityFactory#getApiCredential`, which resolves in strict order (§4's
   classification depends on this): insecure test credential → **API key** → **cluster token**
   → **verified JWT** (bearer, ALB-signed, or a token relayed by another node).
6. **Identity found** → CSRF gate (§4), then the request runs `asUser`.
7. **No identity** → 401. The UI treats a 401 as "restart the auth flow".

## 4. Credential sources and the CSRF model

`CredentialSource` (`SESSION`, `API_KEY`, `CLUSTER_TOKEN`, `REQUEST_TOKEN`, `TEST_CREDENTIAL`)
records *what kind of credential proved the identity*. It exists for one purpose: deciding
whether the credential was **ambient**.

A credential is ambient when the victim's browser — or an intermediary acting on the browser's
ambient cookies — attaches it without the initiating page's involvement:

- a **session cookie** (the classic case), and
- a **request token injected by an authenticating edge proxy**. The proxy derives it from *its*
  session cookie, which the browser attaches cross-site all by itself; by the time it reaches
  Stroom, an ambient credential has been relabelled as a deliberate-looking bearer token. This is
  why "bearer tokens are not automatically attached, so they are not CSRF-vulnerable" — the
  pre-edge assumption — stops being true behind an edge RP.

`SecurityFilter#isCsrfSafe` applies two mechanisms:

**Mechanism 1 (declared edge).** Ambient ⇔ `SESSION`, or (`REQUEST_TOKEN` ∧
`edgeAuthentication.enabled` ∧ *browser provenance*). Ambient credentials on unsafe methods get
two independent checks: `isOriginValid` (Origin, falling back to Referer, matched against the
request's own proxied host plus the configured public/UI URIs; an `Origin: null` literal defers
to the header check) **and** `isCsrfValid` (the `X-CSRF: 1` header, which cross-origin script
cannot set).

*Browser provenance* = any of `Origin`, `Referer`, `Sec-Fetch-Site` present. Browsers send
`Origin` on all cross-origin unsafe requests and fetch metadata on everything modern; machine
clients send none of them. This gate is load-bearing: **inter-node calls relay the user's JWT
as a plain bearer header** (`AbstractUserIdentityFactory#getAuthHeaders`, explicitly including
ALB tokens) and send no `X-CSRF` — without the provenance gate, enabling edge mode would 403
the cluster. A forged *absence* of provenance is only available to non-browser clients, which
are not CSRF victims.

**Mechanism 2 (undeclared edge safety net, `csrf.protectBrowserOriginatedRequests`, default
on).** A `REQUEST_TOKEN` on a request whose fetch metadata says `Sec-Fetch-Site: cross-site`,
with no `X-CSRF`, is rejected on unsafe methods regardless of configuration: cross-origin
script cannot attach an `Authorization` header (CORS preflight) and forms cannot at all, so
such a token can only have been injected by an intermediary nobody declared.

`API_KEY` and `CLUSTER_TOKEN` are **never** ambient under either mechanism: no proxy can mint
them and no browser can be induced to attach them cross-site. This keeps in-browser Swagger UI
usage with an API key working. The one documented cost: with edge mode enabled, an *in-browser*
client attaching its own bearer token (Swagger UI "try it out") must send `X-CSRF: 1` on
state-changing requests.

The GWT UI sends `X-CSRF: 1` on every API request (`RestDispatcher`) and on uploads
(`FileUploadSubmitter`).

## 5. The API-driven sign-in flow (Stroom as RP)

The auth flow is API-driven rather than redirect-driven so that any front-end — the GWT UI
served by Stroom, or a UI served by NGINX/Trunk — uses the same mechanism. `AppServlet` injects
a bootstrap script (the `@BOOTSTRAP@` placeholder in `app.html`) instead of loading the GWT
script directly; a UI served elsewhere ships the equivalent static bootstrap. `SignInServlet`
opts out (`useBootstrap() == false`) because it *is* the internal IdP's login form.

**Bootstrap** (`AppServlet#getBootstrapScript`): `fetch` the status endpoint →
`authenticated: true` → load GWT; `authenticated: false` with a `redirectUrl` → navigate to it;
`authenticated: false` with **no** `redirectUrl`, or a fetch error → one `sessionStorage`-guarded
full-page reload, then a terminal error (§6 explains why).

**`GET /api/auth/flow/v1/status?redirect_uri=...`** (`@Unauthenticated`, `Cache-Control:
no-store`) reports identity from, in order:

1. **The session** — via `toAuthenticatedResponse`: a `HasExpiry` identity with a past expiry
   falls through; a future expiry is reported as `expiresInSec`; no expiry information means
   assumed valid.
2. **The request itself** (§6) — a verifiable token in the headers.
3. **Neither** → in edge mode, `authenticated: false` with no `redirectUrl`; otherwise build the
   OIDC authentication request: single-use state + nonce + PKCE S256 challenge
   (`AuthenticationStateCache`, `OpenIdManager#createAuthUri`, plus any configured
   `authenticationRequestExtraParams` — reserved OIDC parameters cannot be overridden), and two
   cookies:
   - `STROOM_OIDC_STATE` (SameSite=Lax, HttpOnly, 600s) — binds the flow to the initiating
     browser (login-CSRF/forced-login defence). It holds up to five in-flight state ids,
     `~`-separated, so multiple tabs can each be mid-flow without clobbering one another.
   - `STROOM_OIDC_TARGET` (3600s) — where the user was heading. A convenience, not a security
     decision: it is re-validated as same-origin before use, exactly as the `redirect_uri`
     parameter it came from is (an off-origin value would be an open redirect and falls back to
     the application root).

**`GET /api/auth/flow/v1/signin-oidc?code=...&state=...`** (the callback the IdP redirects to):

1. The state must be bound to *this* browser (present in `STROOM_OIDC_STATE`); only the matched
   id is consumed, leaving other tabs' flows bound. An unbound or expired state does not
   dead-end: `restartFlow` sends the browser back to its remembered destination to start again,
   guarded by a 60s `STROOM_OIDC_RETRY` cookie so a genuinely broken setup cannot loop. The
   planted code is never redeemed either way — the login-CSRF defence holds.
2. The code is exchanged server-side
   (`StroomUserIdentityFactory#getAuthFlowUserIdentity` → `createAuthFlowUserIdentity`): the
   **only** place a `UserIdentity` is ever written into a session. The session id is rotated on
   privilege gain; the token is registered with `RefreshManager` for server-side refresh.
3. The response is an HTML meta-refresh to the initiating URI (not a 302), so the session
   cookie is committed before the browser navigates.

The browser holds only the httpOnly `STROOM_SESSION_ID` cookie; tokens never reach the UI.

## 6. Edge-proxy RP mode

When an authenticating proxy fronts Stroom, the proxy completes the flow and injects a verified
credential into every request: the AWS ALB signs the user's claims into `x-amzn-oidc-data`;
bearer-relay proxies (oauth2-proxy et al) forward the IdP's own token in `Authorization`.

**Recognition is unconditional.** `status()` consults the request token whether or not edge
mode is configured, because the token is verified identically to every API call (§7), every
other endpoint already accepts it, and gating it would break existing ALB deployments on
upgrade. Design consequences, deliberate:

- **No session is created** for a request-token identity, and no state/target cookies are set.
  `SecurityFilter` re-derives the identity per request from the headers — which is also how the
  proxy's own token refresh reaches Stroom, and every cookie avoided is header budget saved.
- **No session fixation surface** and nothing to expire out of step with the proxy.

**Flow suppression** (`edgeAuthentication.enabled`, requires `EXTERNAL_IDP` — validated at
startup):

- `status()` with no identity returns `authenticated: false` with **no** `redirectUrl` — there
  is no second RP to redirect to. The bootstrap answers a missing `redirectUrl` (or a fetch
  error, which is how a lapsed proxy session actually manifests: the proxy 302s the fetch
  cross-origin, which script cannot follow) with one guarded full-page reload, letting the proxy
  run its redirect as a top-level navigation.
- `callback()` throws `ForbiddenException` — no code can legitimately arrive, and a live
  code/state endpoint in a topology with no Stroom-owned flow is pure attack surface.
- **All browser access must traverse the proxy**; direct machine access (API keys, bearer
  tokens) is unaffected.

**Error handling intent** (`AuthFlowResourceImpl#getRequestTokenResponse`): a token that fails
authentication (unknown/disabled user *or* unverifiable token —
`AbstractUserIdentityFactory` wraps both in `AuthenticationException`) is terminal
(`ForbiddenException`) **only in edge mode**, because falling through would start a flow the
IdP silently re-completes — an endless bounce; the bootstrap's single reload still gives the
proxy one chance to refresh a stale token. Without an edge no bounce is possible, so the
failure falls through to a normal flow start and a genuinely unknown/disabled user fails at the
callback, as they always did. An identity that verifies but is expired falls through in both
modes.

## 7. Token verification (`StandardJwtContextFactory`)

`getTokenFromHeader` reads `x-amzn-oidc-data` first, then `Authorization`. A token is treated
as ALB-signed if it arrived in the ALB header **or** its JOSE `signer` header starts with
`arn:` — the latter because nodes relay ALB tokens to each other in the plain bearer header.

**Standard bearer path**: signature via the IdP's JWKS (`OpenIdPublicKeysSupplier`, from the
discovery document); expected issuers = `issuer` + `validIssuers`; audience matched against
`allowedAudiences` falling back to `clientId` (requiring the claim per
`audienceClaimRequired`); subject required; 30s clock skew. When `requiredAccessTokenType` is
configured, the JOSE `typ` must match (case-insensitively) so an id_token cannot be replayed as
an access token — applied to bearer tokens only, not to ALB tokens or the code-flow exchange.

**AWS ALB path** (`getAwsJwtContext`): the verification key is fetched from a URL built from
the token's own `kid` and the region parsed out of its `signer` ARN, via the
`publicKeyUriPattern` template (default: the commercial-region
`public-keys.auth.elb.${awsRegion}.amazonaws.com/${keyId}` endpoint; GovCloud deployments
override it). Trust does **not** come from the signature alone — the regional endpoint serves
keys for *every* ALB in the region, and an attacker's own ALB can mint tokens carrying any
issuer string via `authenticate-oidc`. Trust is pinned by `expectedSignerPrefixes`: the
`signer` ARN must start with a configured prefix (validated to reach at least the account id),
and with the default empty set **every ALB token is rejected**. Issuer is validated as on the
standard path; audience is validated when present but not required (ALB data tokens may carry
none); the payload claims come from the IdP's *user info* endpoint (it is not an ID token), so
`uniqueIdentityClaim` defaults to `sub`. Keys are cached (Caffeine, 1h). ALB JWTs carry
base64 padding, unlike RFC 7515 — the parser tolerates it.

**Algorithm constraints** (both paths): permit RSA/RSA-PSS/ECDSA families, blocking the two
alg-confusion vectors (`none`, and HMAC with a public key as the secret). The ALB signs ES256.

## 8. Machine traffic

- **Inter-node, as the processing user**: the internally-signed cluster token +
  `RUN_AS_USER_HEADER` (`StroomUserIdentityFactory#getClusterIdentity` — the single
  key-verified promotion path). Run-as-a-user calls downscope to that user so the receiving
  node enforces their permissions.
- **Inter-node, relaying a user token**: `getAuthHeaders` forwards the user's JWT (including
  ALB tokens) as a bearer header; verified as `REQUEST_TOKEN` on arrival. Never CSRF-challenged
  (§4's provenance gate).
- **stroom-proxy → stroom**: service-user token (client credentials) or API key.
- **Data receipt (`/datafeed` and legacy aliases), feed-status RPC, `/status`**:
  `@Unauthenticated` servlets — authenticated by their own mechanisms (certificates, tokens,
  API keys), bypassing the interactive machinery entirely. Whether an endpoint is
  unauthenticated is driven **solely by the `@Unauthenticated` annotation**; `noauth` path
  segments are historical aliases and determine nothing. In an edge deployment these paths must
  bypass the proxy's authenticate rule (they cannot complete an interactive sign-in) — but
  bypassing the proxy never bypasses Stroom's own checks.

## 9. Sessions, expiry and logout

**Stroom as RP**: one session, Stroom-owned. `RefreshManager` refreshes tokens server-side
before expiry; if refresh fails or the session dies, API calls return 401 and the UI restarts
the flow. Logout (`SessionResourceImpl#logout`) records the logoff event, clears and
invalidates the session, and returns the IdP's logout URL (`logoutEndpoint` — not part of the
discovery document, and absent from some providers, notably Google).

**Edge RP**: two sessions exist — the proxy's (governing whether requests reach Stroom at all)
and none of Stroom's. Ending only a Stroom session would therefore be a no-op logout: the next
request would silently re-authenticate. AWS documents the fix as the application's job, and
Stroom implements it: expire every request cookie matching the `logout.cookiesToExpire` name
prefixes (prefixes, because proxies shard big session cookies — `AWSELBAuthSessionCookie-0`,
`-1`, …), record the logoff event from the security context (there is no session identity to
key it on), and return `logout.signOutUrl` (the proxy's/IdP's end-session endpoint) as the
post-logout redirect. If unconfigured, a warning is logged at each logout. The post-logout
landing page must be on a path the proxy does not authenticate, or the flow simply restarts.
The response is reached via `HttpServletResponseHolder` (mirroring the request holder), because
the GWT-shared `SessionResource` interface cannot carry servlet `@Context` parameters.

## 10. Configuration surface

Under `appConfig.security.authentication`:

| Property | Purpose |
|---|---|
| `openId.*` | Provider settings — see stroom-docs for the full reference. Notable: `expectedSignerPrefixes` (mandatory for ALB tokens, §7), `publicKeyUriPattern` (ALB key endpoint), `authenticationRequestExtraParams` (provider-specific auth-request extras, e.g. Google's `access_type=offline`; reserved OIDC parameters are protected), `requiredAccessTokenType` (bearer `typ` pinning — leave unset when a proxy relays ID tokens) |
| `edgeAuthentication.enabled` | Declares the proxy as RP (§6). Startup-validated to require `EXTERNAL_IDP` |
| `edgeAuthentication.logout.cookiesToExpire` / `.signOutUrl` | Edge logout (§9) |
| `csrf.protectBrowserOriginatedRequests` | Mechanism 2 (§4), default on |
| `preventLogin`, caches, `maxApiKeyExpiryAge` | Pre-existing operational settings |

Deployment-level: `server.applicationConnectors[].maxRequestHeaderSize: 32KiB` is required
behind an edge proxy — sharded proxy session cookies plus injected token headers exceed Jetty's
8KiB default, failing in a way that looks like a network error.

## 11. Invariants and deployment prerequisites

Load-bearing assumptions, in decreasing order of "the code can enforce this":

1. **Exactly one RP per path** — enforced in code (flow suppression, callback disable).
2. **Ambient credentials are CSRF-checked; machine credentials never are** — enforced by the
   source + provenance classification. Changing `getApiCredential`'s resolution order or the
   provenance gate breaks this; the regression tests in `TestSecurityFilter` pin it.
3. **ALB tokens are trusted only from configured signers** — enforced, but only as good as the
   configured prefixes; prefer full ALB ARNs.
4. **Behind an edge, Stroom is unreachable except through the proxy, and the proxy overwrites
   the headers it injects** — *not verifiable from inside Stroom*. Deployment duty: network
   isolation plus header-overwriting proxy config. Token signature verification makes a forged
   header non-authenticating, but these remain necessary defence in depth.
5. **GETs are idempotent** — the CSRF checks exempt safe methods, as they always have.

## 12. Test map

| Behaviour | Pinned by |
|---|---|
| Flow start, state binding, multi-tab, restart guard, open-redirect defences | `TestAuthFlowResourceImpl` (flow sections) |
| Request-token recognition: no cookies/no state; expired falls through; edge 403 vs non-edge fall-through; `NO_IDP` skip; callback disabled in edge mode | `TestAuthFlowResourceImpl` (edge sections) |
| CSRF classification incl. the two regression cases (cluster/API-key never ambient; relayed token without provenance passes), mechanism 2 scoping, `Origin: null`, Swagger/API-key | `TestSecurityFilter` |
| Origin validation (host matching, ports, forwarded headers, referer fallback) | `TestSecurityFilter` |
| ALB token verification incl. signer pinning | `TestStandardJwtContextFactory` |
| Auth-request extras incl. reserved-parameter protection | `TestOpenIdManager` |
| Config validation (edge ⇒ `EXTERNAL_IDP`) and serde | `TestAuthenticationConfig`, `TestStroomOpenIdConfig` |

## 13. Future work (designed, deliberately not built)

**Non-AWS proxy-minted tokens** (GCP IAP, Cloudflare Access). The supported provider set does
not require them; bearer-relay proxies already cover the other providers with no new code. If
ever needed, the shape is an `EdgeTokenVerifier` interface (header name / is-this-proxy's-token
/ verify) extracted from the AWS-specific parts of `StandardJwtContextFactory`, selected by
config, with per-verifier algorithm constraints and audience requirements. Implementations
would be documentation-driven (contract tests from synthetic tokens; configuration escape
hatches for endpoints; strict-by-default on every ambiguity) since standing up every IdP ×
proxy combination is impractical. Note: neither publishes an OIDC discovery document, so the
`EXTERNAL_IDP` validation requiring `openIdConfigurationEndpoint` must be relaxed for
verifier-supplied issuer/keys.

Vendor contracts, pinned from vendor documentation 2026-08-05 (re-verify before building):

| | GCP IAP | Cloudflare Access |
|---|---|---|
| Header | `x-goog-iap-jwt-assertion` | `Cf-Access-Jwt-Assertion` (ignore the `CF_Authorization` cookie) |
| Algorithm | ES256 | RS256 |
| Keys | `https://www.gstatic.com/iap/verify/public_key-jwk` | `https://<team>.cloudflareaccess.com/cdn-cgi/access/certs` — current **and** previous keys; ~6-weekly rotation, 7-day overlap: refresh on unknown `kid`, match against `keys` not `public_cert` |
| `iss` | `https://cloud.google.com/iap` (fixed) | `https://<team>.cloudflareaccess.com` |
| `aud` | **Required** — the only binding to *your* deployment. App Engine `/projects/{n}/apps/{id}`; GCE/GKE `/projects/{n}/global/backendServices/{id}`; Cloud Run `/projects/{n}/locations/{region}/services/{name}` | **Required** — the application's AUD tag |
| Lifetime | Max 10 min (+2×30s skew); validate `exp` **and** `iat` | Standard `exp` |
| Notes | Health checks carry no JWT — exclude their paths | Logout at `/cdn-cgi/access/logout` |

**Class C (unsigned headers** — `X-Forwarded-User`, `OIDC_CLAIM_*`, oauth2-proxy's default
mode**) is deliberately unsupported**: no cryptographic binding means trusting every hop
unconditionally. If it is ever added it must require an explicit trusted-proxy allowlist and be
off by default.
