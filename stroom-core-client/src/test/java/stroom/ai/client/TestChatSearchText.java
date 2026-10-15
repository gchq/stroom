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

package stroom.ai.client;

import stroom.ai.client.ChatSearchText.Part;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestChatSearchText {

    @Test
    void everyMatchIsMarkedIgnoringCase() {
        assertThat(ChatSearchText.split("Firewall errors on the FIREWALL", "firewall"))
                .containsExactly(new Part("Firewall", true),
                        new Part(" errors on the ", false),
                        new Part("FIREWALL", true));
    }

    @Test
    void textWithoutAMatchIsOnePart() {
        assertThat(ChatSearchText.split("Disk space", "fw1")).containsExactly(new Part("Disk space", false));
        assertThat(ChatSearchText.split("Disk space", null)).containsExactly(new Part("Disk space", false));
        assertThat(ChatSearchText.split("Disk space", "  ")).containsExactly(new Part("Disk space", false));
    }

    @Test
    void noTextGivesNoParts() {
        assertThat(ChatSearchText.split(null, "x")).isEmpty();
        assertThat(ChatSearchText.split("", "x")).isEmpty();
    }

    @Test
    void theSearchIsTrimmedAndMatchesDontOverlap() {
        assertThat(ChatSearchText.split("aaaa", " aa "))
                .containsExactly(new Part("aa", true), new Part("aa", true));
        // Characters special to HTML or patterns are just text.
        assertThat(ChatSearchText.split("a <b> 100%", "<b>"))
                .containsExactly(new Part("a ", false), new Part("<b>", true), new Part(" 100%", false));
    }
}
