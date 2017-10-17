package stroom.security.server;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gwt.user.client.Window;
import org.springframework.stereotype.Component;
import stroom.apiclients.AuthenticationServiceClient;
import stroom.auth.service.ApiException;
import stroom.security.SecurityContext;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Manages user sessions.
 *
 * Sessions here are associated with sessions in the AuthenticationService and at the User Agent.
 *
 * Because of the authentication flow, the user might leave Stroom to authenticate elsewhere,
 * or perform some other action. When they return they will have a new jSessionId,
 * which is how Stroom manages it's internal sessions. But actually this jSessionId
 * is part of the larger session. So this records the mappings of jSessionIds against
 * sessions, as well as providing recall for sessions.
 */
@Component
public class SessionManager {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SessionManager.class);

    SetMultimap<String, String> sessionIdToJSessionId = HashMultimap.create();
    Map<String, String> jSessionIdToSessionId = new HashMap<>();
    private SecurityContext securityContext;

    private AuthenticationServiceClient authenticationServiceClient;
    private final AuthenticationService authenticationService;

    @Inject
    public SessionManager(
            SecurityContext securityContext,
            AuthenticationService authenticationService,
            AuthenticationServiceClient authenticationServiceClient){
        this.securityContext = securityContext;
        this.authenticationService = authenticationService;
        this.authenticationServiceClient = authenticationServiceClient;
    }

    public void add(String sessionId, String jSessionId) {
        //The jSessionId might be suffixed with 'node<x>'. We don't want this.
        String plainJSessionId = jSessionId.split("\\.")[0];
        sessionIdToJSessionId.put(sessionId, plainJSessionId);
        jSessionIdToSessionId.put(plainJSessionId, sessionId);
    }

    public void forget(String sessionId){
        sessionIdToJSessionId.removeAll(sessionId);
        List<String> jSessionIdsToRemove = jSessionIdToSessionId.entrySet().stream()
                .filter(entry -> entry.getValue().equals(sessionId))
                .map(entry -> entry.getKey())
                .collect(Collectors.toList());
        jSessionIdsToRemove.forEach(jSessionIdToRemove -> jSessionIdToSessionId.remove(jSessionIdToRemove));
    }

    public String getSession(String jSessionId){
        return jSessionIdToSessionId.get(jSessionId);
    }

    public void logout() {
        // We need to let the AuthenticationService know that a logout has been initiated.
        // We don't need to call `authenticationService.logout()` here because this will
        // be called by the call to AuthenticationService.
        LOGGER.info("Logging out user.");
        String jSessionId = securityContext.getJSessionId();
        String sessionId = getSession(jSessionId);
        try {
            authenticationServiceClient.getAuthServiceApi().logout(sessionId);
        } catch (ApiException e) {
            LOGGER.error("Unable to log user out!");
        }
    }
}
