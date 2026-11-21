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

package stroom.dashboard.client.flexlayout;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestLayoutTabParents {

    @Test
    void tabsMovedToATemporaryPaneAreGivenBackTheirOwnPanes() {
        // A layout of two panes side by side, the second split again.
        final MutableTabConfig table = new MutableTabConfig("table", true);
        final MutableTabConfig chart = new MutableTabConfig("chart", true);
        final MutableTabConfig text = new MutableTabConfig("text", true);
        final MutableTabLayoutConfig left = new MutableTabLayoutConfig();
        left.add(table);
        final MutableTabLayoutConfig top = new MutableTabLayoutConfig();
        top.add(chart);
        final MutableTabLayoutConfig bottom = new MutableTabLayoutConfig();
        bottom.add(text);
        final MutableSplitLayoutConfig right = new MutableSplitLayoutConfig(1);
        right.add(top);
        right.add(bottom);
        final MutableSplitLayoutConfig root = new MutableSplitLayoutConfig(0);
        root.add(left);
        root.add(right);

        // Maximising puts every visible tab in a temporary pane, as FlexLayout.maximiseTabs does.
        final MutableTabLayoutConfig maximised = new MutableTabLayoutConfig();
        maximised.add(table);
        maximised.add(chart);
        maximised.add(text);
        assertThat(table.getParent()).isSameAs(maximised);

        LayoutTabParents.reparentTabs(root);

        assertThat(table.getParent()).isSameAs(left);
        assertThat(chart.getParent()).isSameAs(top);
        assertThat(text.getParent()).isSameAs(bottom);
    }

    @Test
    void removingATabAfterRestoringRemovesItFromItsPane() {
        final MutableTabConfig table = new MutableTabConfig("table", true);
        final MutableTabConfig chart = new MutableTabConfig("chart", true);
        final MutableTabLayoutConfig pane = new MutableTabLayoutConfig();
        pane.add(table);
        pane.add(chart);
        new MutableTabLayoutConfig().add(table);

        LayoutTabParents.reparentTabs(pane);
        // Removing a component's tab goes through its parent, as FlexLayout does.
        table.getParent().remove(table);

        assertThat(pane.getTabs()).containsExactly(chart);
    }

    @Test
    void anEmptyOrMissingLayoutIsLeftAlone() {
        LayoutTabParents.reparentTabs(null);
        final MutableTabLayoutConfig empty = new MutableTabLayoutConfig();
        LayoutTabParents.reparentTabs(empty);

        assertThat(empty.getTabs()).isEmpty();
    }
}
