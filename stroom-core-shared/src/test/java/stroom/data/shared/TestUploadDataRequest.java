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

package stroom.data.shared;

import stroom.util.json.JsonUtil;
import stroom.util.shared.ResourceKey;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestUploadDataRequest {

    @Test
    void testNullFeedSourceDefaultsToSelectedFeed() {
        final UploadDataRequest request = createRequest(null);

        assertThat(request.getFeedSource())
                .isEqualTo(UploadFeedSource.SELECTED_FEED);
    }

    @Test
    void testMissingFeedSourceInJsonDefaultsToSelectedFeed() {
        // Requests from callers that pre-date the feed source choice.
        final String json = """
                {
                  "feedName": "MY_FEED",
                  "streamTypeName": "Raw Events",
                  "fileName": "test.zip"
                }""";

        final UploadDataRequest request = JsonUtil.readValue(json, UploadDataRequest.class);

        assertThat(request.getFeedSource())
                .isEqualTo(UploadFeedSource.SELECTED_FEED);
    }

    @Test
    void testSerialisation() {
        final UploadDataRequest request = createRequest(UploadFeedSource.ZIP_FEEDS);

        final String json = JsonUtil.writeValueAsString(request);
        final UploadDataRequest request2 = JsonUtil.readValue(json, UploadDataRequest.class);

        assertThat(request2.getFeedSource())
                .isEqualTo(UploadFeedSource.ZIP_FEEDS);
        assertThat(request2.getFeedName())
                .isEqualTo("MY_FEED");
        assertThat(request2.getKey())
                .isEqualTo(request.getKey());
    }

    private UploadDataRequest createRequest(final UploadFeedSource feedSource) {
        return new UploadDataRequest(
                new ResourceKey("1234", "test.zip"),
                "MY_FEED",
                StreamTypeNames.RAW_EVENTS,
                null,
                null,
                "test.zip",
                feedSource);
    }
}
