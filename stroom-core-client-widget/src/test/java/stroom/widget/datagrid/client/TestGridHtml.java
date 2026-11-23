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

package stroom.widget.datagrid.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestGridHtml {

    // As TickBoxCell draws it
    private static final String TICK_BOX = "<div title=\"Ticked\" class=\"tick-box tick-box--cell\" "
                                           + "role=\"checkbox\" aria-checked=\"true\" aria-readonly=\"false\">"
                                           + "<span class=\"tick-box__mark\"><svg></svg></span></div>";

    /// A cell or header of only a tick box is one.
    @Test
    void onlyATickBox() {
        assertThat(GridHtml.isOnlyTickBox(TICK_BOX)).isTrue();
    }

    /// Space round the tick box doesn't matter.
    @Test
    void onlyATickBoxWithSpaceRoundIt() {
        assertThat(GridHtml.isOnlyTickBox("  " + TICK_BOX + "\n")).isTrue();
    }

    /// A tick box with text after it, e.g. a value, isn't only a tick box.
    @Test
    void aTickBoxWithTextAfterIt() {
        assertThat(GridHtml.isOnlyTickBox(TICK_BOX + "alpha")).isFalse();
    }

    /// A tick box with text before it isn't only a tick box.
    @Test
    void aTickBoxWithTextBeforeIt() {
        assertThat(GridHtml.isOnlyTickBox("alpha" + TICK_BOX)).isFalse();
    }

    /// A tick box inside other content, as the column values filter draws it, isn't only a tick box.
    @Test
    void aTickBoxInsideOtherContent() {
        assertThat(GridHtml.isOnlyTickBox("<div class=\"field-value\">" + TICK_BOX + "<span>alpha</span></div>"))
                .isFalse();
    }

    /// Content with no tick box, or none at all, isn't a tick box.
    @Test
    void noTickBox() {
        assertThat(GridHtml.isOnlyTickBox("<div>alpha</div>")).isFalse();
        assertThat(GridHtml.isOnlyTickBox("")).isFalse();
    }
}
