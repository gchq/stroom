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

package stroom.widget.util.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestNewTabLink {

    private static final String HIDDEN_HINT = "<span class=\"sr-only\"> (opens in new tab)</span>";

    @Test
    void linkOpensInANewTabAndSaysSo() {
        assertThat(NewTabLink.link("https://example.com/docs", "quick filter documentation").asString())
                .isEqualTo("<a href=\"https://example.com/docs\" target=\"_blank\" rel=\"noopener noreferrer\">"
                           + "quick filter documentation" + HIDDEN_HINT + "</a>");
    }

    @Test
    void linkEscapesItsTextAndUrl() {
        assertThat(NewTabLink.link("https://example.com/?a=1&b=\"2\"", "<b>Docs</b> & more").asString())
                // The URL's quotes are encoded and its ampersand escaped
                .isEqualTo("<a href=\"https://example.com/?a=1&amp;b=%222%22\" target=\"_blank\" "
                           + "rel=\"noopener noreferrer\">&lt;b&gt;Docs&lt;/b&gt; &amp; more" + HIDDEN_HINT + "</a>");
    }

    @Test
    void linkReplacesAnUnsafeUrl() {
        assertThat(NewTabLink.link("javascript:alert(1)", "Docs").asString())
                .startsWith("<a href=\"#\" ");
    }

    @Test
    void linkWithNoTextShowsTheUrl() {
        // Never an empty link
        assertThat(NewTabLink.link("https://example.com", null).asString())
                .isEqualTo("<a href=\"https://example.com\" target=\"_blank\" rel=\"noopener noreferrer\">"
                           + "https://example.com" + HIDDEN_HINT + "</a>");
        assertThat(NewTabLink.link("https://example.com", "").asString())
                .contains(">https://example.com" + HIDDEN_HINT + "</a>");
    }

    @Test
    void htmlBuilderAppendLinkUsesTheHelper() {
        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.appendTrustedString("See the ");
        htmlBuilder.appendLink("https://example.com", "documentation");

        assertThat(htmlBuilder.toSafeHtml().asString())
                .isEqualTo("See the " + NewTabLink.link("https://example.com", "documentation").asString());
    }

    @Test
    void contentIsTheEscapedTextAndTheHint() {
        assertThat(NewTabLink.content("Licence <MIT>").asString())
                .isEqualTo("Licence &lt;MIT&gt;" + HIDDEN_HINT);
    }

    @Test
    void hiddenHintIsTheHintAlone() {
        assertThat(NewTabLink.hiddenHint().asString())
                .isEqualTo(HIDDEN_HINT);
    }

    @Test
    void nameWithHintAddsTheHintOnce() {
        assertThat(NewTabLink.nameWithHint("Help on Data Types"))
                .isEqualTo("Help on Data Types (opens in new tab)");
        assertThat(NewTabLink.nameWithHint("Help on Data Types (opens in new tab)"))
                .isEqualTo("Help on Data Types (opens in new tab)");
        assertThat(NewTabLink.nameWithHint(null))
                .isEqualTo("(opens in new tab)");
    }

    @Test
    void addsTheHintToShowdownLinks() {
        // As Showdown renders a link with openLinksInNewWindow
        final String html = "<p>See <a href=\"https://example.com\" rel=\"noopener noreferrer\" "
                            + "target=\"_blank\">the docs</a>.</p>";

        assertThat(NewTabLink.addHintToNewTabLinks(html))
                .isEqualTo("<p>See <a href=\"https://example.com\" rel=\"noopener noreferrer\" "
                           + "target=\"_blank\">the docs" + HIDDEN_HINT + "</a>.</p>");
    }

    @Test
    void addsTheHintToEveryNewTabLink() {
        final String html = "<a target=\"_blank\" href=\"a\" rel=\"x\">A</a> and "
                            + "<A HREF='b' TARGET='_BLANK' REL='x'><em>B</em></A>";

        assertThat(NewTabLink.addHintToNewTabLinks(html))
                .isEqualTo("<a target=\"_blank\" href=\"a\" rel=\"x\">A" + HIDDEN_HINT + "</a> and "
                           + "<A HREF='b' TARGET='_BLANK' REL='x'><em>B</em>" + HIDDEN_HINT + "</A>");
    }

    @Test
    void leavesLinksThatOpenInTheSameTabAlone() {
        final String html = "<a href=\"#top\">Top</a> <a href=\"a\" target=\"_self\">Self</a> "
                            + "<a href=\"b\" data-target=\"_blank\">Data</a> <abbr title=\"x\">X</abbr>";

        assertThat(NewTabLink.addHintToNewTabLinks(html)).isSameAs(html);
    }

    @Test
    void doesNotAddTheHintTwice() {
        // e.g. a server built help link, which has the hint, rendered as Markdown
        final String once = NewTabLink.link("https://example.com", "count documentation").asString();

        assertThat(NewTabLink.addHintToNewTabLinks(once)).isEqualTo(once);
        assertThat(NewTabLink.addHintToNewTabLinks(NewTabLink.addHintToNewTabLinks(
                "<a href=\"x\" target=\"_blank\">X</a>")))
                .isEqualTo("<a href=\"x\" target=\"_blank\" rel=\"noopener noreferrer\">X" + HIDDEN_HINT + "</a>");
    }

    @Test
    void addsRelToANewTabLinkWithout() {
        assertThat(NewTabLink.addHintToNewTabLinks("<a href=\"x\" target = _blank>X</a>"))
                .isEqualTo("<a href=\"x\" target = _blank rel=\"noopener noreferrer\">X" + HIDDEN_HINT + "</a>");
    }

    @Test
    void ignoresAGreaterThanInAQuotedValue() {
        assertThat(NewTabLink.addHintToNewTabLinks("<a title=\"a > b\" target=\"_blank\">X</a>"))
                .isEqualTo("<a title=\"a > b\" target=\"_blank\" rel=\"noopener noreferrer\">X" + HIDDEN_HINT
                           + "</a>");
    }

    @Test
    void keepsTheIndexesOfTextWhoseLowerCaseIsLonger() {
        // 'İ' is two characters in lower case, which mustn't move where the hint goes
        assertThat(NewTabLink.addHintToNewTabLinks("İİ <a href=\"x\" target=\"_blank\" rel=\"r\">İ</a> İ"))
                .isEqualTo("İİ <a href=\"x\" target=\"_blank\" rel=\"r\">İ" + HIDDEN_HINT + "</a> İ");
    }

    @Test
    void leavesUnfinishedOrEmptyHtmlAlone() {
        assertThat(NewTabLink.addHintToNewTabLinks(null)).isNull();
        assertThat(NewTabLink.addHintToNewTabLinks("")).isEmpty();
        assertThat(NewTabLink.addHintToNewTabLinks("<a href=\"x\" target=\"_blank\">X"))
                .isEqualTo("<a href=\"x\" target=\"_blank\">X");
        assertThat(NewTabLink.addHintToNewTabLinks("<a href=\"x"))
                .isEqualTo("<a href=\"x");
    }
}
