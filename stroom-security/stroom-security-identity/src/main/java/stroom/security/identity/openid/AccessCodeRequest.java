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

package stroom.security.identity.openid;

class AccessCodeRequest {

    private final String scope;
    private final String responseType;
    private final String clientId;
    private final String redirectUri;
    private final String subject;
    private final String nonce;
    private final String state;
    private final String prompt;

    AccessCodeRequest(final String scope,
                      final String responseType,
                      final String clientId,
                      final String redirectUri,
                      final String subject,
                      final String nonce,
                      final String state,
                      final String prompt) {
        this.scope = scope;
        this.responseType = responseType;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.subject = subject;
        this.nonce = nonce;
        this.state = state;
        this.prompt = prompt;
    }

    public String getScope() {
        return scope;
    }

    public String getResponseType() {
        return responseType;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public String getSubject() {
        return subject;
    }

    public String getNonce() {
        return nonce;
    }

    public String getState() {
        return state;
    }

    public String getPrompt() {
        return prompt;
    }
}
