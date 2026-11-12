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

package stroom.query.common.v2;

import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.JoinTables.Registration;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestJoinTables {

    private static final QueryKey QUERY_KEY = new QueryKey("search");
    private static final UserRef OWNER = UserRef.builder().uuid("owner").subjectId("owner").build();

    private final JoinTables joinTables = new JoinTables(null, null);

    @Test
    void tablesAreKeptUntilTheirLastUserReleasesThem() {
        final JoinTable table = Mockito.mock(JoinTable.class);
        joinTables.register(QUERY_KEY, Query.builder().build(), List.of(table), OWNER);

        final Registration first = joinTables.acquire(QUERY_KEY).orElseThrow();
        final Registration second = joinTables.acquire(QUERY_KEY).orElseThrow();
        joinTables.release(QUERY_KEY, first);

        assertThat(joinTables.get(QUERY_KEY)).isPresent();
        Mockito.verify(table, Mockito.never()).close();

        joinTables.release(QUERY_KEY, second);

        assertThat(joinTables.get(QUERY_KEY)).isEmpty();
        Mockito.verify(table).close();
    }

    @Test
    void registerAndAcquireUsesTablesAlreadyHeldAndClosesTheNewOnes() {
        final JoinTable held = Mockito.mock(JoinTable.class);
        final JoinTable fetchedAgain = Mockito.mock(JoinTable.class);
        final Registration first = joinTables.registerAndAcquire(
                QUERY_KEY, Query.builder().build(), List.of(held), OWNER);

        final Registration second = joinTables.registerAndAcquire(
                QUERY_KEY, Query.builder().build(), List.of(fetchedAgain), OWNER);

        assertThat(second).isSameAs(first);
        assertThat(second.users()).hasValue(2);
        Mockito.verify(fetchedAgain).close();
        Mockito.verify(held, Mockito.never()).close();
    }

    @Test
    void registrationNoOneUsesIsRemovedWhenTheFetchHoldEnds() {
        final JoinTable table = Mockito.mock(JoinTable.class);
        final Registration hold = joinTables.registerAndAcquire(
                QUERY_KEY, Query.builder().build(), List.of(table), OWNER);

        joinTables.release(QUERY_KEY, hold);

        assertThat(joinTables.get(QUERY_KEY)).isEmpty();
        Mockito.verify(table).close();
    }

    @Test
    void registrationKeepsItsOwner() {
        joinTables.register(QUERY_KEY, Query.builder().build(), List.of(), OWNER);

        assertThat(joinTables.get(QUERY_KEY).orElseThrow().owner()).isEqualTo(OWNER);
    }

    @Test
    void acquiringTablesThatAreNotHeldGivesNothing() {
        assertThat(joinTables.acquire(QUERY_KEY)).isEmpty();
    }

    @Test
    void removeClosesTablesInUse() {
        final JoinTable table = Mockito.mock(JoinTable.class);
        joinTables.register(QUERY_KEY, Query.builder().build(), List.of(table), OWNER);
        final Registration registration = joinTables.acquire(QUERY_KEY).orElseThrow();

        joinTables.remove(QUERY_KEY);
        joinTables.release(QUERY_KEY, registration);

        Mockito.verify(table, Mockito.times(1)).close();
        assertThat(joinTables.get(QUERY_KEY)).isEmpty();
    }

    @Test
    void releasingAnOldRegistrationLeavesANewOneAlone() {
        joinTables.register(QUERY_KEY, Query.builder().build(), List.of(), OWNER);
        final Registration old = joinTables.acquire(QUERY_KEY).orElseThrow();
        final JoinTable table = Mockito.mock(JoinTable.class);
        joinTables.register(QUERY_KEY, Query.builder().build(), List.of(table), OWNER);

        joinTables.release(QUERY_KEY, old);

        assertThat(joinTables.get(QUERY_KEY)).isPresent();
        Mockito.verify(table, Mockito.never()).close();
    }
}
