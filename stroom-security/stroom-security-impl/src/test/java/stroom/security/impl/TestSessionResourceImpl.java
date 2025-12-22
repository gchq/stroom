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

package stroom.security.impl;

import stroom.security.mock.MockSecurityContext;
import stroom.security.shared.SessionDetails;
import stroom.security.shared.SessionListResponse;
import stroom.security.shared.SessionResource;
import stroom.test.common.TestUtil;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.UserRef;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestSessionResourceImpl extends AbstractResourceTest<SessionResource> {

    @Mock
    private SessionListService sessionListService;

    @Override
    public SessionResource getRestResource() {
        return new SessionResourceImpl(
                TestUtil.mockProvider(OpenIdManager.class),
                TestUtil.mockProvider(HttpServletRequest.class),
                TestUtil.mockProvider(AuthenticationEventLog.class),
                () -> sessionListService,
                TestUtil.mockProvider(StroomUserIdentityFactory.class),
                MockSecurityContext::new);

    }

    @Override
    public String getResourceBasePath() {
        return SessionResource.BASE_PATH;
    }

    @Test
    void list_namedNode() {

        final String subPath = ResourcePaths.buildPath(SessionResource.LIST_PATH_PART);

        final UserRef userRef = UserRef.builder().uuid("user1").subjectId("user1").build();
        final SessionListResponse expectedResponse = new SessionListResponse(List.of(
                new SessionDetails(userRef,
                        123L,
                        456L,
                        "agent1",
                        "node1"),
                new SessionDetails(userRef,
                        123L,
                        456L,
                        "agent1",
                        "node1")));

        when(sessionListService.listSessions(Mockito.anyString()))
                .thenReturn(expectedResponse);


        final SessionListResponse response = doGetTest(
                subPath,
                SessionListResponse.class,
                expectedResponse,
                webTarget -> UriBuilderUtil.addParam(webTarget, "nodeName", "node1"));

        verify(sessionListService).listSessions(Mockito.anyString());
    }

    @Test
    void list_allNodes() {

        final String subPath = ResourcePaths.buildPath(SessionResource.LIST_PATH_PART);

        final UserRef userRef = UserRef.builder().uuid("user1").subjectId("user1").build();
        final SessionListResponse expectedResponse = new SessionListResponse(List.of(
                new SessionDetails(userRef,
                        123L,
                        456L,
                        "agent1",
                        "node1"),
                new SessionDetails(userRef,
                        123L,
                        456L,
                        "agent1",
                        "node1")));

        when(sessionListService.listSessions())
                .thenReturn(expectedResponse);

        final SessionListResponse response = doGetTest(
                subPath,
                SessionListResponse.class,
                expectedResponse);

        verify(sessionListService).listSessions();
    }
}
