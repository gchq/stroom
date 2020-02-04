/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.auth;

public class RelyingParty {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RelyingParty.class);
    private String clientId;
    private String clientSecret;
    private String clientUri;
    private String logoutUri;
    private String accessCode;
    private String idToken;
    private String nonce;
    private String state;
    private String redirectUrl;
    public RelyingParty(String clientId) {
        this.clientId = clientId;
    }

    public boolean accessCodesMatch(String accessCodeToMatch) {
        return this.accessCode != null && accessCode.equals(accessCodeToMatch);
    }

    public void forgetIdToken() {
        this.idToken = null;
    }

    public void forgetAccessCode() {
        this.accessCode = null;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getLogoutUri() {
        return logoutUri;
    }

    public void setLogoutUri(String logoutUri) {
        this.logoutUri = logoutUri;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getClientUri() {
        return clientUri;
    }
}