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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Session {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Session.class);

    private String sessionId;
    private boolean isAuthenticated;
    private String userEmail;
    private Map<String, RelyingParty> relyingParties = new HashMap<>();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        isAuthenticated = authenticated;
    }

    public RelyingParty getOrCreateRelyingParty(String clientId) {
        if (relyingParties.containsKey(clientId)) {
            return relyingParties.get(clientId);
        }

        RelyingParty newRelyingParty = new RelyingParty(clientId);
        relyingParties.put(clientId, newRelyingParty);
        return newRelyingParty;
    }

    public RelyingParty getRelyingParty(String requestingClientId) {
        return relyingParties.get(requestingClientId);
    }

    public Collection<RelyingParty> getRelyingParties() {
        return this.relyingParties.values();
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserEmail() {
        return userEmail;
    }
}
