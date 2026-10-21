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

package stroom.planb.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class TestPlanBStoreNames {

    private static final String OWN_TYPE = "FloorMapEventStore";
    private static final String PLAN_B_TYPE = "StateStore";
    private static final String TRACES_TYPE = "Traces";

    private DocFinder docFinder;
    private SecurityContext securityContext;
    private PlanBStoreNames planBStoreNames;

    @BeforeEach
    void setUp() {
        docFinder = Mockito.mock(DocFinder.class);
        Mockito.when(docFinder.findByName(anyString(), anyString())).thenReturn(List.of());
        securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.asProcessingUserResult(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        planBStoreNames = new PlanBStoreNames(
                securityContext,
                () -> docFinder,
                Set.of(OWN_TYPE, PLAN_B_TYPE, TRACES_TYPE));
    }

    /// Regression test: names were only checked within one store type, but Plan B resolves a name
    /// across every Plan B type, so sharing a name with a store of another type broke lookups of both.
    @Test
    void aNameUsedByAStoreOfAnotherTypeIsTaken() {
        Mockito.when(docFinder.findByName(PLAN_B_TYPE, "events"))
                .thenReturn(List.of(new DocRef(PLAN_B_TYPE, "plan-b-uuid", "events")));

        assertThat(planBStoreNames.isTakenByAnotherType("events", OWN_TYPE)).isTrue();
    }

    @Test
    void aNameFreeInEveryOtherTypeIsNotTaken() {
        assertThat(planBStoreNames.isTakenByAnotherType("events", OWN_TYPE)).isFalse();
    }

    /// Its own type is the document store's to check, against its uncached persistence.
    @Test
    void theOwnTypeIsNotConsulted() {
        Mockito.when(docFinder.findByName(OWN_TYPE, "events"))
                .thenReturn(List.of(new DocRef(OWN_TYPE, "own-uuid", "events")));

        assertThat(planBStoreNames.isTakenByAnotherType("events", OWN_TYPE)).isFalse();
        Mockito.verify(docFinder, Mockito.never()).findByName(Mockito.eq(OWN_TYPE), anyString());
    }

    /// A store the caller cannot see still breaks name resolution, so the lookup is made as the
    /// processing user, as Plan B's own resolution is.
    @Test
    void theLookupIsMadeAsTheProcessingUser() {
        planBStoreNames.isTakenByAnotherType("events", OWN_TYPE);
        planBStoreNames.namesOfOtherTypes(OWN_TYPE);

        Mockito.verify(securityContext, Mockito.times(2)).asProcessingUserResult(any());
    }

    @Test
    void namesOfOtherTypesLeavesOutTheOwnType() {
        Mockito.when(docFinder.findByName(PLAN_B_TYPE, "*", true))
                .thenReturn(List.of(new DocRef(PLAN_B_TYPE, "1", "events")));
        Mockito.when(docFinder.findByName(TRACES_TYPE, "*", true))
                .thenReturn(List.of(new DocRef(TRACES_TYPE, "2", "traces")));
        Mockito.when(docFinder.findByName(OWN_TYPE, "*", true))
                .thenReturn(List.of(new DocRef(OWN_TYPE, "3", "mine")));

        assertThat(planBStoreNames.namesOfOtherTypes(OWN_TYPE))
                .containsExactlyInAnyOrder("events", "traces");
    }
}
