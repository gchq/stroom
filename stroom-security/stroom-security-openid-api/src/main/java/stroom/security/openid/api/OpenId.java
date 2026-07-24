/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.openid.api;

import java.util.List;

public class OpenId {

    public static final String AUTH_USER = "authuser";
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String CODE = "code";
    public static final String KEY_ID = "kid";
    public static final String GRANT_TYPE = "grant_type";
    public static final String NONCE = "nonce";
    public static final String LOGIN_PROMPT = "login";

    public static final String POST_LOGOUT_REDIRECT_URI = "post_logout_redirect_uri";
    public static final String PROMPT = "prompt";
    public static final String REDIRECT_URI = "redirect_uri";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String RESPONSE_TYPE = "response_type";

    /**
     * PKCE (RFC 7636). The client sends {@link #CODE_CHALLENGE} and {@link #CODE_CHALLENGE_METHOD} on the
     * authorization request and the matching {@link #CODE_VERIFIER} when redeeming the code, proving it is
     * the same party that began the flow.
     */
    public static final String CODE_CHALLENGE = "code_challenge";
    public static final String CODE_CHALLENGE_METHOD = "code_challenge_method";
    public static final String CODE_VERIFIER = "code_verifier";
    public static final String CODE_CHALLENGE_METHOD__S256 = "S256";

    /**
     * The JOSE {@code typ} header value that marks a token as an OAuth 2.0 access token (RFC 9068).
     * Only a token carrying this may authenticate a request; id, refresh and reset tokens do not.
     */
    public static final String TOKEN_TYPE__ACCESS = "at+jwt";
    public static final String SCOPE = "scope";
    public static final String STATE = "state";

    public static final String GRANT_TYPE__AUTHORIZATION_CODE = "authorization_code";
    public static final String GRANT_TYPE__CLIENT_CREDENTIALS = "client_credentials";
    public static final String GRANT_TYPE__REFRESH_TOKEN = "refresh_token";
    public static final String RESPONSE_TYPE__CODE = "code";
    public static final String SCOPE__OPENID = "openid";
    public static final String SCOPE__EMAIL = "email";

    public static final List<String> DEFAULT_REQUEST_SCOPES = List.of(
            OpenId.SCOPE__OPENID,
            OpenId.SCOPE__EMAIL);

    public static final List<String> DEFAULT_CLIENT_CREDENTIALS_SCOPES = List.of(
            OpenId.SCOPE__OPENID);

    /**
     * A claim holding a set of audience values.
     */
    public static final String CLAIM__AUDIENCE = "aud";
    /**
     * The authorized party - the party to which the token was issued, i.e. the OAuth client. Set to the
     * client id, as issued by Keycloak and other providers, so the relying party sees a familiar token.
     */
    public static final String CLAIM__AUTHORIZED_PARTY = "azp";
    /**
     * The time the end-user authentication occurred, as seconds since the epoch. On a refreshed id token
     * this remains the time of the original login, not the time of the refresh.
     */
    public static final String CLAIM__AUTH_TIME = "auth_time";
    /**
     * Subject - Identifier for the End-User at the Issuer.
     * <p>
     * NOTE: Not all IDPs use this, e.g. Azure AD use 'oid', so refer to {@link OpenIdConfiguration} for
     * which claims to use.
     * </p>
     */
    public static final String CLAIM__SUBJECT = "sub";
    /**
     * End-User's preferred e-mail address.
     */
    public static final String CLAIM__EMAIL = "email";
    /**
     * Shorthand name by which the End-User wishes to be referred to at the RP, such as janedoe or j.doe.
     * This value MAY be any valid JSON string including special characters such as @, /,
     * or whitespace. The RP MUST NOT rely upon this value being unique
     * <p>
     * NOTE: Not all IDPs use this, e.g. Azure AD use 'upn', so refer to {@link OpenIdConfiguration} for
     * which claims to use.
     * </p>
     */
    public static final String CLAIM__PREFERRED_USERNAME = "preferred_username";

    /**
     * End-User's full name in displayable form including all name parts, possibly including titles and suffixes,
     * ordered according to the End-User's locale and preferences.
     */
    public static final String CLAIM__NAME = "name";

    public static final String ID_TOKEN = "id_token";
}
