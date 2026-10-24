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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiUserMarkdown {

    @Test
    void xmlTypedInAMessageIsShownNotTakenAsHtml() {
        // Seen: <Data Name="mentions"> ... </Data> sent in a message showed as nothing, the sanitiser removing it.
        assertThat(AiUserMarkdown.escapeHtml("can't we have <Data Name=\"mentions\"><Data Name=\"m\" Value=\"a\"/>"
                                             + "</Data> & more?"))
                .isEqualTo("can't we have &lt;Data Name=\"mentions\">&lt;Data Name=\"m\" Value=\"a\"/>&lt;/Data> "
                           + "&amp; more?");
    }

    @Test
    void codeIsLeftAsMarkdownShowsIt() {
        assertThat(AiUserMarkdown.escapeHtml("""
                inline `<Data Name="x"/>` and ``a `<b>` c``
                ```xml
                <Data Name="x"/>
                ```
                    <indented/>
                after <tag>"""))
                .isEqualTo("""
                        inline `<Data Name="x"/>` and ``a `<b>` c``
                        ```xml
                        <Data Name="x"/>
                        ```
                            <indented/>
                        after &lt;tag>""");
    }

    @Test
    void autolinksAndPlainTextAreLeftAlone() {
        assertThat(AiUserMarkdown.escapeHtml("see <https://example.com/a?b=c> or a < b")).isEqualTo(
                "see <https://example.com/a?b=c> or a &lt; b");
        assertThat(AiUserMarkdown.escapeHtml("an unclosed `tick and <x>")).isEqualTo("an unclosed `tick and &lt;x>");
        assertThat(AiUserMarkdown.escapeHtml("")).isEmpty();
        assertThat(AiUserMarkdown.escapeHtml(null)).isNull();
    }
}
