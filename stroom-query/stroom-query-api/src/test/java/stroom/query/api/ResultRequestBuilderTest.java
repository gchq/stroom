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

package stroom.query.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultRequestBuilderTest {
    @Test
    void doesBuild() {
        // Given
        final String componentId = "someComponent";
        final ResultRequest.Fetch fetch = ResultRequest.Fetch.CHANGES;
        final String openGroup0 = "someOpenGroup0";
        final String openGroup1 = "someOpenGroup1";
        final Long rangeLength = 70L;
        final Long rangeOffset = 1000L;
        final String queryId0 = "someQueryId0";
        final String queryId1 = "someQueryId1";
        final String queryId2 = "someQueryId2";

        // When
        final ResultRequest resultRequest = ResultRequest
                .builder()
                .componentId(componentId)
                .fetch(fetch)
                .addOpenGroups(openGroup0, openGroup1)
                .requestedRange(OffsetRange
                        .builder()
                        .length(rangeLength)
                        .offset(rangeOffset)
                        .build())
                .addMappings(TableSettings
                        .builder()
                        .queryId(queryId0)
                        .build())
                .addMappings(TableSettings
                        .builder()
                        .queryId(queryId1)
                        .build())
                .addMappings(TableSettings
                        .builder()
                        .queryId(queryId2)
                        .build())
                .build();

        // Then
        assertThat(resultRequest.getComponentId()).isEqualTo(componentId);
        assertThat(resultRequest.getFetch()).isEqualTo(fetch);

        assertThat(resultRequest.getOpenGroups()).hasSize(2);
        assertThat(resultRequest.getOpenGroups().contains(openGroup0)).isTrue();
        assertThat(resultRequest.getOpenGroups().contains(openGroup1)).isTrue();

        assertThat(resultRequest.getRequestedRange()).isNotNull();
        assertThat(resultRequest.getRequestedRange().getLength()).isEqualTo(rangeLength);
        assertThat(resultRequest.getRequestedRange().getOffset()).isEqualTo(rangeOffset);

        assertThat(resultRequest.getMappings()).hasSize(3);
        assertThat(resultRequest.getMappings().get(0).getQueryId()).isEqualTo(queryId0);
        assertThat(resultRequest.getMappings().get(1).getQueryId()).isEqualTo(queryId1);
        assertThat(resultRequest.getMappings().get(2).getQueryId()).isEqualTo(queryId2);
    }
}
