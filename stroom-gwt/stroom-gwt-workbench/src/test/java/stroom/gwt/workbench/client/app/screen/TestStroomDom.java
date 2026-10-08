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


package stroom.gwt.workbench.client.app.screen;

import stroom.gwt.workbench.framework.client.play.TextMatch;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStroomDom {

    @Test
    void testButtonMatchesTheTextOnce() {
        final TextMatch ok = StroomDom.button("OK");

        assertThat(ok.matches("OK")).isTrue();
        assertThat(ok.matches("OK OK")).isFalse();
        assertThat(ok.matches("OK OK OK")).isFalse();
        assertThat(ok.matches("OKAY")).isFalse();
        assertThat(ok.matches("Not OK")).isFalse();
    }

    @Test
    void testButtonQuotesTheText() {
        final TextMatch saveAs = StroomDom.button("Save As...");

        assertThat(saveAs.matches("Save As...")).isTrue();
        assertThat(saveAs.matches("Save As!!!")).isFalse();
    }

    @Test
    void testButtonRefusesNull() {
        assertThatThrownBy(() -> StroomDom.button(null))
                .isInstanceOf(NullPointerException.class);
    }
}
