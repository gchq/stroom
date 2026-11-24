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

package stroom.analytics.impl;

import stroom.query.api.ResultRequest;
import stroom.query.api.SearchRequest;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.Sizes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestScheduledQueryAnalyticExecutable {

    private static final Sizes DEFAULT_SIZES = Sizes.create(List.of(1_000_000L, 100L, 10L, 1L));

    @Test
    void limitBelowTheDefaultIsRaisedToTheDefaultAtTheTopLevelOnly() {
        // A single limit is the limit of every level, so the lower levels keep it.
        assertThat(raisedLimits(List.of(5L))).containsExactly(1_000_000L, 5L);
        assertThat(raisedLimits(List.of(5L, 3L))).containsExactly(1_000_000L, 3L);
    }

    @Test
    void limitAboveTheDefaultIsKept() {
        assertThat(raisedLimits(List.of(2_000_000L))).containsExactly(2_000_000L, 2_000_000L);
        assertThat(raisedLimits(List.of(2_000_000L, 50L))).containsExactly(2_000_000L, 50L);
    }

    @Test
    void tableWithNoLimitKeepsNone() {
        assertThat(raisedLimits(null)).isNull();
    }

    private static List<Long> raisedLimits(final List<Long> maxResults) {
        final SearchRequest searchRequest = SearchRequest.builder()
                .addResultRequests(ResultRequest.builder()
                        .componentId("table")
                        .addMappings(TableSettings.builder().maxResults(maxResults).build())
                        .build())
                .build();
        return ScheduledQueryAnalyticExecutable.raiseRowLimits(searchRequest, DEFAULT_SIZES)
                .getResultRequests()
                .getFirst()
                .getMappings()
                .getFirst()
                .getMaxResults();
    }
}
