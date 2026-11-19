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

package stroom.query.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAbstractHtmlBuilder {

    @Test
    void appendLinkOpensInANewTabAndSaysSo() {
        final String html = new DetailBuilder()
                .appendLink("https://example.com/docs#count", "count documentation")
                .toString();

        assertThat(html).isEqualTo("<div><a href=\"https://example.com/docs#count\" target=\"_blank\" "
                                   + "rel=\"noopener noreferrer\">count documentation"
                                   + "<span class=\"sr-only\"> (opens in new tab)</span></a>");
    }

    @Test
    void appendLinkWithNoTitleUsesTheUrl() {
        // Never an empty link. A DetailBuilder starts with its div
        final String html = new DetailBuilder()
                .appendLink("https://example.com", "")
                .toString();

        assertThat(html).isEqualTo("<div><a href=\"https://example.com\" target=\"_blank\" "
                                   + "rel=\"noopener noreferrer\">https://example.com"
                                   + "<span class=\"sr-only\"> (opens in new tab)</span></a>");
    }

    @Test
    void appendLinkNeedsAUrl() {
        assertThatThrownBy(() -> new DetailBuilder().appendLink(null, "x"))
                .isInstanceOf(NullPointerException.class);
    }
}
