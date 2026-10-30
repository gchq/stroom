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

package stroom.annotation.client.component;

import stroom.annotation.shared.EventId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestAddEventLinkController {

    @Test
    void testStreamAndEvent() {
        final EventId eventId = AddEventLinkController.parse("1:2");
        assertThat(eventId.getStreamId()).isEqualTo(1L);
        assertThat(eventId.getEventId()).isEqualTo(2L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1", "1:2:3", "a:b", " 1:2", "1:"})
    void testNotAnEventId(final String text) {
        assertThat(AddEventLinkController.parse(text)).isNull();
    }
}
