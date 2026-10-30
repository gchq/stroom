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

package stroom.query.client;

import stroom.widget.hyperlink.client.LinkValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestHyperlinkName {

    @Test
    void testTextNamesTheLink() {
        final LinkValue hyperlink = link(" Orange ", "https://colours.example.com/orange", "browser");

        assertThat(HyperlinkName.getDisplayText(hyperlink)).isEqualTo("Orange");
        assertThat(HyperlinkName.getAccessibleName(hyperlink)).isNull();
    }

    @Test
    void testTextThatOnlyMentionsAUrlNamesTheLink() {
        // Text with spaces is a sentence, not just a URL
        final LinkValue hyperlink = link("See https://example.com for more", "https://example.com", null);

        assertThat(HyperlinkName.getAccessibleName(hyperlink)).isNull();
    }

    @Test
    void testUrlTextIsNamedByHostAndPath() {
        final LinkValue hyperlink = link(
                "https://example.com/docs/page?q=1#top",
                "https://example.com/docs/page?q=1#top",
                null);

        // Shown as it is, but named briefly
        assertThat(HyperlinkName.getDisplayText(hyperlink)).isEqualTo("https://example.com/docs/page?q=1#top");
        assertThat(HyperlinkName.getAccessibleName(hyperlink)).isEqualTo("example.com/docs/page");
    }

    @Test
    void testUrlTextDifferentToHrefIsNamedByHref() {
        final LinkValue hyperlink = link("http://old.example.com", "https://new.example.com/landing/", null);

        assertThat(HyperlinkName.getAccessibleName(hyperlink)).isEqualTo("new.example.com/landing");
    }

    @Test
    void testTextThatIsItsAddressButForTrailingSlash() {
        final LinkValue hyperlink = link("https://example.com/a/", "https://example.com/a", null);

        assertThat(HyperlinkName.getAccessibleName(hyperlink)).isEqualTo("example.com/a");
    }

    @Test
    void testTextThatIsItsRelativeAddress() {
        // The address with no scheme is already brief, so it names the link as it is shown
        final LinkValue hyperlink = link("/stroom/a", "/stroom/a", "tab");

        assertThat(HyperlinkName.getAccessibleName(hyperlink)).isNull();
    }

    @Test
    void testUrlTextWithTitleIsNamedByTitle() {
        final LinkValue hyperlink = link("https://example.com/a", "https://example.com/a", "tab|Example Page");

        assertThat(HyperlinkName.getAccessibleName(hyperlink)).isEqualTo("Example Page");
    }

    @Test
    void testEmptyTextShowsHostAndPath() {
        final LinkValue hyperlink = link("", "https://user:secret@example.com:8443/a/b/", "browser");

        // Not dropped: shown, and named, by its host and path
        assertThat(HyperlinkName.getDisplayText(hyperlink)).isEqualTo("example.com:8443/a/b");
        assertThat(HyperlinkName.getAccessibleName(hyperlink)).isNull();
    }

    @Test
    void testBlankTextShowsTitle() {
        final LinkValue hyperlink = link("   ", "https://example.com/a", "dialog| My Dialog ");

        assertThat(HyperlinkName.getDisplayText(hyperlink)).isEqualTo("My Dialog");
    }

    @Test
    void testNullTextShowsWhatAStroomLinkOpens() {
        final LinkValue hyperlink = link(null, "?uuid=abc&params=x", "dashboard");

        assertThat(HyperlinkName.getDisplayText(hyperlink)).isEqualTo("Dashboard");
    }

    @ParameterizedTest
    @CsvSource({
            "dashboard, Dashboard",
            "DATA, Data",
            "stepping, Stepping",
            "annotation, Annotation"
    })
    void testStroomLinkTypes(final String type, final String expected) {
        assertThat(HyperlinkName.getDisplayText(link("", "id=1", type))).isEqualTo(expected);
    }

    @Test
    void testRelativeAddressIsShownAsItIs() {
        assertThat(HyperlinkName.getDisplayText(link("", "/stroom/ui/page", "tab")))
                .isEqualTo("/stroom/ui/page");
    }

    @Test
    void testUnknownTypeIsShownByAddress() {
        assertThat(HyperlinkName.getDisplayText(link("", "id=1", "nonsense")))
                .isEqualTo("id=1");
    }

    @Test
    void testNothingToNameItBy() {
        assertThat(HyperlinkName.getDisplayText(link("", "", null))).isEqualTo(HyperlinkName.DEFAULT_NAME);
        assertThat(HyperlinkName.getDisplayText(link(null, null, null))).isEqualTo(HyperlinkName.DEFAULT_NAME);
        // A title separator with no title
        assertThat(HyperlinkName.getDisplayText(link(null, null, "tab|"))).isEqualTo(HyperlinkName.DEFAULT_NAME);
    }

    @ParameterizedTest
    @CsvSource({
            "https://example.com, example.com",
            "https://example.com/, example.com",
            "https://example.com/a/b?c=d, example.com/a/b",
            "https://example.com#frag, example.com",
            "ftp://user@files.example.com/pub, files.example.com/pub",
            "https://example.com/a@b, example.com/a@b"
    })
    void testGetHostAndPath(final String url, final String expected) {
        assertThat(HyperlinkName.getHostAndPath(url)).isEqualTo(expected);
    }

    @Test
    void testGetHostAndPathOfEmptyUrl() {
        assertThat(HyperlinkName.getHostAndPath("https://")).isNull();
        assertThat(HyperlinkName.getHostAndPath("https://?q=1")).isNull();
    }

    @Test
    void testUrlWithNoHostIsNamedByHref() {
        final LinkValue hyperlink = link("", "https://", null);

        assertThat(HyperlinkName.getDisplayText(hyperlink)).isEqualTo("https://");
    }

    private static LinkValue link(final String text, final String href, final String type) {
        return LinkValue.builder()
                .text(text)
                .href(href)
                .type(type)
                .build();
    }
}
