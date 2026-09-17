/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.processor.impl;

import stroom.node.api.NodeInfo;
import stroom.processor.impl.ProcessorProfileCache.ProfileResult;
import stroom.processor.shared.ProcessorFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * gh-5699 Phase 1. Eligibility is the question the master node currently cannot ask - it has to
 * sweep every enabled node because it does not know who will want work next - so these cover the
 * cases where a node must decide it is not allowed to process a filter.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestEligibleFilters {

    private static final String THIS_NODE = "node1";
    private static final String PROFILE_NAME = "myProfile";
    private static final Instant NOW = Instant.parse("2026-08-06T14:30:00Z");

    private static final ProfileResult ZERO = new ProfileResult(0, 0);
    private static final ProfileResult UNLIMITED = new ProfileResult(Integer.MAX_VALUE, Integer.MAX_VALUE);

    @Mock
    private PrioritisedFilters prioritisedFilters;
    @Mock
    private ProcessorProfileCache processorProfileCache;
    @Mock
    private NodeInfo nodeInfo;

    private EligibleFilters eligibleFilters;

    @BeforeEach
    void setUp() {
        when(nodeInfo.getThisNodeName()).thenReturn(THIS_NODE);
        eligibleFilters = new EligibleFilters(prioritisedFilters, processorProfileCache, nodeInfo);
    }

    @Test
    void filtersWithNoProfileAreAlwaysEligible() {
        final ProcessorFilter filter = filter(1, null);
        when(prioritisedFilters.get()).thenReturn(List.of(filter));

        assertThat(eligibleFilters.getEligibleFilters(NOW))
                .describedAs("no profile means any node may process it at any time")
                .containsExactly(filter);
    }

    @Test
    void filterLimitedToOtherNodesIsNotEligible() {
        final ProcessorFilter filter = filter(1, PROFILE_NAME);
        when(prioritisedFilters.get()).thenReturn(List.of(filter));
        // What the profile cache returns for a node outside the group, a disabled group, or a
        // time outside the profile's periods.
        when(processorProfileCache.getProfile(THIS_NODE, PROFILE_NAME, NOW)).thenReturn(ZERO);

        assertThat(eligibleFilters.getEligibleFilters(NOW)).isEmpty();
    }

    @Test
    void clusterThreadLimitOfZeroIsNotEligible() {
        final ProcessorFilter filter = filter(1, PROFILE_NAME);
        when(prioritisedFilters.get()).thenReturn(List.of(filter));
        // Node threads available but the cluster is capped at zero - still nothing this node may do.
        when(processorProfileCache.getProfile(THIS_NODE, PROFILE_NAME, NOW))
                .thenReturn(new ProfileResult(10, 0));

        assertThat(eligibleFilters.getEligibleFilters(NOW)).isEmpty();
    }

    @Test
    void anUnresolvableProfileIsNotEligible() {
        final ProcessorFilter filter = filter(1, PROFILE_NAME);
        when(prioritisedFilters.get()).thenReturn(List.of(filter));
        when(processorProfileCache.getProfile(THIS_NODE, PROFILE_NAME, NOW))
                .thenThrow(new RuntimeException("Processor profile called 'myProfile' not found"));

        assertThat(eligibleFilters.getEligibleFilters(NOW))
                .describedAs("a filter with a profile is governed by that profile alone, so an "
                             + "unresolvable profile must not fall back to unprofiled behaviour")
                .isEmpty();
    }

    @Test
    void filterAtItsProcessingTaskLimitIsStillEligible() {
        // maxProcessingTasks bounds how many of the filter's tasks may run at once, which is a
        // claiming concern. Treating it as eligibility would hide the filter's remaining work.
        final ProcessorFilter filter = ProcessorFilter
                .builder()
                .id(1)
                .maxProcessingTasks(1)
                .build();
        when(prioritisedFilters.get()).thenReturn(List.of(filter));

        assertThat(eligibleFilters.getEligibleFilters(NOW)).containsExactly(filter);
    }

    @Test
    void ineligibleFiltersAreDroppedAndPriorityOrderIsKept() {
        final ProcessorFilter allowed1 = filter(1, null);
        final ProcessorFilter blocked = filter(2, PROFILE_NAME);
        final ProcessorFilter allowed2 = filter(3, "otherProfile");
        // PrioritisedFilters hands them over highest priority first; that order must survive.
        when(prioritisedFilters.get()).thenReturn(List.of(allowed1, blocked, allowed2));
        when(processorProfileCache.getProfile(THIS_NODE, PROFILE_NAME, NOW)).thenReturn(ZERO);
        when(processorProfileCache.getProfile(THIS_NODE, "otherProfile", NOW)).thenReturn(UNLIMITED);

        assertThat(eligibleFilters.getEligibleFilters(NOW)).containsExactly(allowed1, allowed2);
    }

    private ProcessorFilter filter(final int id, final String profileName) {
        return ProcessorFilter
                .builder()
                .id(id)
                .profileName(profileName)
                .build();
    }
}
