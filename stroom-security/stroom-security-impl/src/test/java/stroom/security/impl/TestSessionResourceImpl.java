package stroom.security.impl;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import stroom.security.impl.session.SessionDetails;
import stroom.security.impl.session.SessionListResponse;
import stroom.security.impl.session.SessionListService;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.util.shared.ResourcePaths;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestSessionResourceImpl extends AbstractResourceTest<SessionResource> {

    @Mock
    private AuthenticationEventLog authenticationEventLog;

    @Mock
    private SessionListService sessionListService;

    @Override
    public SessionResource getRestResource() {
        return new SessionResourceImpl(authenticationEventLog, sessionListService);
    }

    @Override
    public String getResourceBasePath() {
        return SessionResource.BASE_PATH;
    }

    @Test
    void list_namedNode() {

        final String subPath = ResourcePaths.buildPath(SessionResource.LIST_PATH_PART);

        final SessionListResponse expectedResponse = new SessionListResponse(List.of(
            new SessionDetails("user1", 123L, 456L, "agent1", "node1"),
            new SessionDetails("user1", 123L, 456L, "agent1", "node1")));

        when(sessionListService.listSessions(Mockito.anyString()))
            .thenReturn(expectedResponse);


        final SessionListResponse response = doGetTest(
            subPath,
            SessionListResponse.class,
            expectedResponse,
            webTarget -> webTarget.queryParam("nodeName", "node1"));

        verify(sessionListService).listSessions(Mockito.anyString());
    }

    @Test
    void list_allNodes() {

        final String subPath = ResourcePaths.buildPath(SessionResource.LIST_PATH_PART);

        final SessionListResponse expectedResponse = new SessionListResponse(List.of(
            new SessionDetails("user1", 123L, 456L, "agent1", "node1"),
            new SessionDetails("user1", 123L, 456L, "agent1", "node1")));

        when(sessionListService.listSessions())
            .thenReturn(expectedResponse);

        final SessionListResponse response = doGetTest(subPath, SessionListResponse.class, expectedResponse);

        verify(sessionListService).listSessions();
    }
}