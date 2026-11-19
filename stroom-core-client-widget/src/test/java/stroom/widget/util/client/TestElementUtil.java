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

class TestElementUtil {

    @Test
    void aBlockDoesNotMatchItsElements() {
        // A link's open icon is inside the link, so finding the link from the icon must pass it by
        assertThat(ElementUtil.containsClassName("icon-wrap doc-ref-link__open", "doc-ref-link")).isFalse();
        assertThat(ElementUtil.containsClassName("column-header__label", "column-header")).isFalse();
    }

    @Test
    void aClassIsFoundAmongOthers() {
        assertThat(ElementUtil.containsClassName("doc-ref-link", "doc-ref-link")).isTrue();
        assertThat(ElementUtil.containsClassName("max doc-ref-link dock-max", "doc-ref-link")).isTrue();
        assertThat(ElementUtil.containsClassName("  doc-ref-link\tother ", "doc-ref-link")).isTrue();
    }

    @Test
    void partOfANameIsNotFound() {
        assertThat(ElementUtil.containsClassName("doc-ref-link", "ref-link")).isFalse();
        assertThat(ElementUtil.containsClassName("doc-ref-link", "doc")).isFalse();
    }

    @Test
    void nothingIsFoundWithoutClassesOrAName() {
        assertThat(ElementUtil.containsClassName(null, "doc-ref-link")).isFalse();
        assertThat(ElementUtil.containsClassName("", "doc-ref-link")).isFalse();
        assertThat(ElementUtil.containsClassName("doc-ref-link", "")).isFalse();
        assertThat(ElementUtil.containsClassName("doc-ref-link", null)).isFalse();
    }
}
