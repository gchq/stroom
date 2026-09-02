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

package stroom.analytics.shared;

import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestNotificationStreamDestination {

    @Test
    void isUsingSourceFeed_streamingAndAsked() {
        assertThat(destination(true).isUsingSourceFeed(AnalyticProcessType.STREAMING))
                .isTrue();
    }

    @Test
    void isUsingSourceFeed_streamingAndNotAsked() {
        assertThat(destination(false).isUsingSourceFeed(AnalyticProcessType.STREAMING))
                .isFalse();
    }

    /**
     * Only a streaming rule has a source stream to take a feed from, so every other processing type ignores
     * the option rather than failing at run time trying to honour it.
     */
    @ParameterizedTest
    @EnumSource(value = AnalyticProcessType.class, names = "STREAMING", mode = EnumSource.Mode.EXCLUDE)
    void isUsingSourceFeed_notStreaming(final AnalyticProcessType analyticProcessType) {
        assertThat(destination(true).isUsingSourceFeed(analyticProcessType))
                .isFalse();
    }

    /**
     * A report has no processing type of its own on the destination, so a null must not be honoured either.
     */
    @Test
    void isUsingSourceFeed_nullProcessType() {
        assertThat(destination(true).isUsingSourceFeed(null))
                .isFalse();
    }

    private NotificationStreamDestination destination(final boolean useSourceFeedIfPossible) {
        return NotificationStreamDestination
                .builder()
                .destinationFeed(new DocRef("Feed", "test-uuid", "TEST_FEED"))
                .useSourceFeedIfPossible(useSourceFeedIfPossible)
                .build();
    }
}
