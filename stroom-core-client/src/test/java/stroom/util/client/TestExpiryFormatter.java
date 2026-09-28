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

package stroom.util.client;

import stroom.preferences.client.DateTimeFormatter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TestExpiryFormatter {

    @Test
    void testNullExpiry() {
        final DateTimeFormatter dateTimeFormatter = mock(DateTimeFormatter.class);
        final ExpiryFormatter expiryFormatter = new ExpiryFormatter(dateTimeFormatter);

        assertThat(expiryFormatter.formatWithDuration(null, 1_000L).asString())
                .isEmpty();

        verifyNoInteractions(dateTimeFormatter);
    }

    @Test
    void testExpired() {
        final DateTimeFormatter dateTimeFormatter = mock(DateTimeFormatter.class);
        final ExpiryFormatter expiryFormatter = new ExpiryFormatter(dateTimeFormatter);
        final long expiryMs = System.currentTimeMillis() - 1_000L;
        when(dateTimeFormatter.format(expiryMs)).thenReturn("formatted expiry");

        assertThat(expiryFormatter.formatWithDuration(expiryMs, 1_000L).asString())
                .isEqualTo("<span class=\"dataGridAlertText\">formatted expiry (EXPIRED)</span>");

        verify(dateTimeFormatter).format(expiryMs);
        verifyNoMoreInteractions(dateTimeFormatter);
    }

    @Test
    void testExpiringWithinAlertThreshold() {
        final DateTimeFormatter dateTimeFormatter = mock(DateTimeFormatter.class);
        final ExpiryFormatter expiryFormatter = new ExpiryFormatter(dateTimeFormatter);
        final long expiryMs = System.currentTimeMillis() + 1_000L;
        when(dateTimeFormatter.formatWithDuration(expiryMs)).thenReturn("formatted duration");

        assertThat(expiryFormatter.formatWithDuration(expiryMs, 10_000L).asString())
                .isEqualTo("<span class=\"dataGridAlertText\">formatted duration</span>");

        verify(dateTimeFormatter).formatWithDuration(expiryMs);
    }

    @Test
    void testOutsideAlertThreshold() {
        final DateTimeFormatter dateTimeFormatter = mock(DateTimeFormatter.class);
        final ExpiryFormatter expiryFormatter = new ExpiryFormatter(dateTimeFormatter);
        final long expiryMs = System.currentTimeMillis() + 10_000L;
        when(dateTimeFormatter.formatWithDuration(expiryMs)).thenReturn("formatted duration");

        assertThat(expiryFormatter.formatWithDuration(expiryMs, 1_000L).asString())
                .isEqualTo("formatted duration");

        verify(dateTimeFormatter).formatWithDuration(expiryMs);
    }
}
