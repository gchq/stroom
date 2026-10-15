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

class TestSvgMarkup {

    @Test
    void addsClassesAndHidesAnSvgWithNoClass() {
        assertThat(SvgMarkup.decorative("<svg viewBox=\"0 0 1 1\"><path/></svg>", "a b"))
                .isEqualTo("<svg aria-hidden=\"true\" class=\"a b\" viewBox=\"0 0 1 1\"><path/></svg>");
    }

    @Test
    void addsToTheClassesAnSvgHas() {
        assertThat(SvgMarkup.decorative("<svg class=\"fa fa-times\" viewBox=\"0 0 1 1\"></svg>", "icon"))
                .isEqualTo("<svg aria-hidden=\"true\" class=\"icon fa fa-times\" viewBox=\"0 0 1 1\"></svg>");
    }

    @Test
    void ignoresAClassOfAnElementInsideTheSvg() {
        // Only the svg element's own attributes are changed
        assertThat(SvgMarkup.decorative("<svg viewBox=\"0 0 1 1\"><g class=\"x\"/></svg>", "icon"))
                .isEqualTo("<svg aria-hidden=\"true\" class=\"icon\" viewBox=\"0 0 1 1\"><g class=\"x\"/></svg>");
    }

    @Test
    void leavesMarkupWithNoSvgAlone() {
        assertThat(SvgMarkup.decorative("<div></div>", "icon")).isEqualTo("<div></div>");
    }

    @Test
    void keepsWhatComesBeforeTheSvg() {
        assertThat(SvgMarkup.decorative("<?xml version=\"1.0\"?><svg></svg>", "icon"))
                .isEqualTo("<?xml version=\"1.0\"?><svg aria-hidden=\"true\" class=\"icon\"></svg>");
    }
}
