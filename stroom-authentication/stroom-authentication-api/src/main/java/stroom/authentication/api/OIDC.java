/*
 * Copyright 2020 Crown Copyright
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

package stroom.authentication.api;

import javax.ws.rs.core.UriBuilder;
import java.util.Set;

public class OIDC {
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String CODE = "code";
    public static final String GRANT_TYPE = "grant_type";
    public static final String NONCE = "nonce";
    public static final String PROMPT = "prompt";
    public static final String REDIRECT_URI = "redirect_uri";
    public static final String RESPONSE_TYPE = "response_type";
    public static final String SCOPE = "scope";
    public static final String STATE = "state";

    public static final String GRANT_TYPE__AUTHORIZATION_CODE = "authorization_code";
    public static final String RESPONSE_TYPE__CODE = "code";
    public static final String SCOPE__OPENID = "openid";
    public static final String SCOPE__EMAIL = "email";

    public static final String ID_TOKEN = "id_token";

    private static final Set<String> RESERVED_PARAMS = Set.of(
            CLIENT_ID,
            CLIENT_SECRET,
            CODE,
            GRANT_TYPE,
            NONCE,
            PROMPT,
            REDIRECT_URI,
            RESPONSE_TYPE,
            SCOPE,
            STATE
    );

    public static String removeOIDCParams(final String url) {
        final UriBuilder uriBuilder = UriBuilder.fromUri(url);

        // When the auth service has performed authentication it will redirect
        // back to the current URL with some additional parameters (e.g.
        // `state` and `accessCode`). It is important that these parameters are
        // not provided by our redirect URL else the redirect URL that the
        // authentication service redirects back to may end up with multiple
        // copies of these parameters which will confuse Stroom as it will not
        // know which one of the param values to use (i.e. which were on the
        // original redirect request and which have been added by the
        // authentication service). For this reason we will cleanse the URL of
        // any reserved parameters here. The authentication service should do
        // the same to the redirect URL before adding its additional
        // parameters.
        RESERVED_PARAMS.forEach(param -> uriBuilder.replaceQueryParam(param, new Object[0]));

        return uriBuilder.build().toString();
    }
}
