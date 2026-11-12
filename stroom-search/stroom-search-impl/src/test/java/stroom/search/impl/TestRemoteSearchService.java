/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.impl;

import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.JoinTables;
import stroom.query.common.v2.JoinTables.Registration;
import stroom.security.api.SecurityContext;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The serving of a search's join tables to another node.
class TestRemoteSearchService {

    private static final QueryKey QUERY_KEY = new QueryKey("search");
    private static final UserRef OWNER = UserRef.builder().uuid("owner").subjectId("owner").build();
    private static final UserRef OTHER = UserRef.builder().uuid("other").subjectId("other").build();

    private JoinTables joinTables;
    private SecurityContext securityContext;
    private Registration registration;
    private RemoteSearchService service;

    @BeforeEach
    void setUp() {
        joinTables = Mockito.mock(JoinTables.class);
        registration = new Registration(Query.builder().build(), List.of(), OWNER, new AtomicInteger());
        Mockito.when(joinTables.acquire(QUERY_KEY)).thenReturn(Optional.of(registration));
        securityContext = Mockito.mock(SecurityContext.class);
        service = new RemoteSearchService(null, null, null, null, null, null, securityContext, joinTables, null);
    }

    @Test
    void ownerGetsTheTables() throws IOException {
        Mockito.when(securityContext.getUserRef()).thenReturn(OWNER);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        service.writeJoinTables(QUERY_KEY.getUuid(), outputStream);

        assertThat(outputStream.size()).isPositive();
        Mockito.verify(joinTables).release(QUERY_KEY, registration);
    }

    @Test
    void processingUserGetsTheTables() throws IOException {
        Mockito.when(securityContext.isProcessingUser()).thenReturn(true);
        Mockito.when(securityContext.getUserRef()).thenReturn(OTHER);
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        service.writeJoinTables(QUERY_KEY.getUuid(), outputStream);

        assertThat(outputStream.size()).isPositive();
    }

    @Test
    void anotherUserIsRefusedAndTheTablesAreReleased() {
        Mockito.when(securityContext.getUserRef()).thenReturn(OTHER);

        assertThatThrownBy(() -> service.writeJoinTables(QUERY_KEY.getUuid(), new ByteArrayOutputStream()))
                .isInstanceOf(PermissionException.class);
        Mockito.verify(joinTables).release(QUERY_KEY, registration);
    }

    @Test
    void searchWithNoTablesHereIsAnError() {
        Mockito.when(joinTables.acquire(QUERY_KEY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.writeJoinTables(QUERY_KEY.getUuid(), new ByteArrayOutputStream()))
                .isInstanceOf(SearchException.class)
                .hasMessageContaining("no join tables");
    }
}
