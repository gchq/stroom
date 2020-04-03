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

package stroom.authentication;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Singleton
public class SessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);

    Map<String, Session> sessions = new HashMap<>();

    private Client logoutClient = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));

    public static String createAccessCode() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        String accessCode = Base64.getUrlEncoder().encodeToString(bytes);
        return accessCode;
    }

    public Optional<Session> get(String id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public void logout(String sessionId) {
        Optional<Session> session = get(sessionId);

        if (!session.isPresent()) {
            // We might get a logout for a session that doesn't exist - e.g. if there's been a bounce. It's
            // not necessarily an error and we need to handle it gracefully.
            LOGGER.warn("Tried to log out of a session that doesn't exist: " + sessionId);
            return;
        }

        session.get().setAuthenticated(false);

        session.get().getRelyingParties().forEach(relyingParty -> {
            // Not all relying parties can have a logout URI, i.e. remote web apps.
            // So we need to check for null here.
            if (relyingParty.getLogoutUri() != null) {
                String logoutUrl = relyingParty.getLogoutUri() + "/" + sessionId;
                Response response = logoutClient
                        .target(logoutUrl)
                        .request()
                        .get();
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                    throw new RuntimeException("Unable to log out a relying party! I tried the following URL: " + logoutUrl);
                }
            }
        });

        sessions.remove(sessionId);
    }

    public Session create(String sessionId) {
        Session session = new Session();
        session.setSessionId(sessionId);
        sessions.put(sessionId, session);
        return session;
    }

    public Optional<RelyingParty> getByAccessCode(String accessCode) {
        for (Session session : sessions.values()) {
            for (RelyingParty relyingParty : session.getRelyingParties()) {
                if (relyingParty.getAccessCode() != null) {
                    if (relyingParty.getAccessCode().equals(accessCode)) {
                        return Optional.of(relyingParty);
                    }
                }
            }
        }
        return Optional.empty();
    }
}
