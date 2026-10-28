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

package stroom.ai.client.component;

import stroom.svg.shared.SvgImage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestContextChip {

    @Test
    void testDescriptionIsText() {
        final String html = ContextChip.toHtml(SvgImage.CLIPBOARD, "<img src=x onerror=alert(1)> & co")
                .asString();

        assertThat(html)
                .startsWith("<span class='svgIcon'>" + SvgImage.CLIPBOARD.getSvg() + "</span> ")
                .endsWith("&lt;img src=x onerror=alert(1)&gt; &amp; co")
                .doesNotContain("<img");
    }

    @Test
    void testPlainDescription() {
        assertThat(ContextChip.toHtml(SvgImage.CLIPBOARD, "Orders table").asString())
                .endsWith("</span> Orders table");
    }
}
