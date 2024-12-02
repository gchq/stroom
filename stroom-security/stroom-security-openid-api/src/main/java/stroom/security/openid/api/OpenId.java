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

package stroom.security.openid.api;

public class OpenId {

    public static final String AUTH_USER = "authuser";
    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String CODE = "code";
    public static final String GRANT_TYPE = "grant_type";
    public static final String NONCE = "nonce";
    public static final String LOGIN_PROMPT = "login";

    public static final String POST_LOGOUT_REDIRECT_URI = "post_logout_redirect_uri";
    public static final String PROMPT = "prompt";
    public static final String REDIRECT_URI = "redirect_uri";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String RESPONSE_TYPE = "response_type";
    public static final String SCOPE = "scope";
    public static final String STATE = "state";

    public static final String GRANT_TYPE__AUTHORIZATION_CODE = "authorization_code";
    public static final String RESPONSE_TYPE__CODE = "code";
    public static final String SCOPE__OPENID = "openid";
    public static final String SCOPE__EMAIL = "email";

    public static final String ID_TOKEN = "id_token";
}
