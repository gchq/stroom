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

package stroom.widget.datagrid.client.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestEmptyMessage {

    @Test
    void testDefault_tableSaysNoData() {
        final EmptyMessage message = new EmptyMessage();

        assertThat(message.getText(true)).isEqualTo(EmptyMessage.NO_DATA);
    }

    @Test
    void testDefault_listOrTreeSaysNoItems() {
        final EmptyMessage message = new EmptyMessage();

        assertThat(message.getText(false)).isEqualTo(EmptyMessage.NO_ITEMS);
    }

    @Test
    void testOwn_replacesTheDefault() {
        final EmptyMessage message = new EmptyMessage();
        message.setOwn("No API keys");

        assertThat(message.getText(true)).isEqualTo("No API keys");
        assertThat(message.getText(false)).isEqualTo("No API keys");
    }

    @Test
    void testOwnNull_turnsTheMessageOff() {
        final EmptyMessage message = new EmptyMessage();
        message.setOwn(null);

        assertThat(message.getText(true)).isNull();
        assertThat(message.getText(false)).isNull();
    }

    @Test
    void testFiltered_saysNoMatches() {
        final EmptyMessage message = new EmptyMessage();
        message.setFiltered(true);

        assertThat(message.getText(true)).isEqualTo(EmptyMessage.NO_MATCHES);
        assertThat(message.getText(false)).isEqualTo(EmptyMessage.NO_MATCHES);
    }

    @Test
    void testFiltered_replacesTheScreensOwnToo() {
        final EmptyMessage message = new EmptyMessage();
        message.setOwn("No API keys");
        message.setFiltered(true);

        assertThat(message.getText(true)).isEqualTo(EmptyMessage.NO_MATCHES);
    }

    @Test
    void testFiltered_staysOffWhenTurnedOff() {
        final EmptyMessage message = new EmptyMessage();
        message.setOwn(null);
        message.setFiltered(true);

        assertThat(message.getText(true)).isNull();
    }

    @Test
    void testFilterCleared_goesBack() {
        final EmptyMessage message = new EmptyMessage();
        message.setFiltered(true);
        message.setFiltered(false);

        assertThat(message.getText(true)).isEqualTo(EmptyMessage.NO_DATA);
    }

    @Test
    void testIsShown_onlyWithNoRowsAndAnExactCount() {
        assertThat(EmptyMessage.isShown(0, true, EmptyMessage.NO_DATA)).isTrue();
        // Still loading: the count isn't known yet
        assertThat(EmptyMessage.isShown(0, false, EmptyMessage.NO_DATA)).isFalse();
        assertThat(EmptyMessage.isShown(3, true, EmptyMessage.NO_DATA)).isFalse();
        // Turned off
        assertThat(EmptyMessage.isShown(0, true, null)).isFalse();
    }
}
