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

package stroom.dataconnection.client.presenter;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestConnectionPropertiesText {

    @Test
    void propertiesAreWrittenOnePerLineInNameOrder() {
        assertThat(ConnectionPropertiesText.format(Map.of("useCursorFetch", "true", "connectTimeout", "5000")))
                .isEqualTo("connectTimeout=5000\nuseCursorFetch=true");
    }

    @Test
    void noPropertiesAreNoText() {
        assertThat(ConnectionPropertiesText.format(null)).isEmpty();
        assertThat(ConnectionPropertiesText.format(Map.of())).isEmpty();
    }

    @Test
    void linesAreReadAsNameAndValue() {
        assertThat(ConnectionPropertiesText.parse(" useCursorFetch = true \n\n connectTimeout=5000\n"))
                .containsExactly(Map.entry("connectTimeout", "5000"), Map.entry("useCursorFetch", "true"));
    }

    @Test
    void valueMayHoldAnEqualsSign() {
        assertThat(ConnectionPropertiesText.parse("options=-c search_path=reports"))
                .containsExactly(Map.entry("options", "-c search_path=reports"));
    }

    @Test
    void lineWithNoEqualsSignIsANameWithAnEmptyValue() {
        assertThat(ConnectionPropertiesText.parse("tcpKeepAlive")).containsExactly(Map.entry("tcpKeepAlive", ""));
    }

    @Test
    void lineWithNoNameIsSkipped() {
        assertThat(ConnectionPropertiesText.parse("=true\nssl=true")).containsExactly(Map.entry("ssl", "true"));
    }

    @Test
    void textRoundTrips() {
        final Map<String, String> properties = Map.of("a", "1", "b", "x=y");

        assertThat(ConnectionPropertiesText.parse(ConnectionPropertiesText.format(properties)))
                .isEqualTo(properties);
    }

    @Test
    void noTextIsNoProperties() {
        assertThat(ConnectionPropertiesText.parse(null)).isEmpty();
        assertThat(ConnectionPropertiesText.parse("  \n ")).isEmpty();
    }
}
