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

package stroom.svg.shared;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TestSvgImage {

    // An icon is drawn inline beside a label, e.g. in an explorer tree row, so any text an icon
    // holds (a <title>, a <desc> or an editor's <metadata>) is added to the row's name, and a
    // <style> styles the whole page. Inkscape may write these with a namespace prefix, e.g.
    // <svg:title>, which the browser still reads.
    private static final Pattern TEXT_ELEMENT_PATTERN = Pattern.compile(
            "(?i)<(\\w+:)?(title|desc|metadata)\\b");
    private static final Pattern STYLE_ELEMENT_PATTERN = Pattern.compile(
            "(?i)<(\\w+:)?style\\b");

    /// Checks that no icon carries text of its own, which would be added to the name of
    /// whatever it is drawn beside.
    @Test
    void noIconHasText() {
        assertThat(iconsMatching(TEXT_ELEMENT_PATTERN)).isEmpty();
    }

    /// Checks that no icon carries a style sheet, which would apply to the whole page.
    @Test
    void noIconHasAStyleSheet() {
        assertThat(iconsMatching(STYLE_ELEMENT_PATTERN)).isEmpty();
    }

    /// Checks the patterns find namespace-prefixed and upper case elements, and nothing that only
    /// starts with the same letters or is an attribute.
    @Test
    void thePatternsMatchPrefixedAndUpperCaseElements() {
        assertThat(TEXT_ELEMENT_PATTERN.matcher("<svg:title>x</svg:title>").find()).isTrue();
        assertThat(TEXT_ELEMENT_PATTERN.matcher("<DESC>x</DESC>").find()).isTrue();
        assertThat(TEXT_ELEMENT_PATTERN.matcher("<metadata id=\"m\">").find()).isTrue();
        assertThat(TEXT_ELEMENT_PATTERN.matcher("<titlebar/>").find()).isFalse();
        assertThat(STYLE_ELEMENT_PATTERN.matcher("<svg:style>.st0{}</svg:style>").find()).isTrue();
        assertThat(STYLE_ELEMENT_PATTERN.matcher("<path style=\"fill:#fff\"/>").find()).isFalse();
    }

    private static List<String> iconsMatching(final Pattern pattern) {
        return Arrays.stream(SvgImage.values())
                .filter(svgImage -> pattern.matcher(svgImage.getSvg()).find())
                .map(SvgImage::getRelativePathStr)
                .toList();
    }
}
