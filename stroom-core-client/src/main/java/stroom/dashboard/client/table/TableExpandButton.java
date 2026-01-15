/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dashboard.client.table;

import stroom.query.api.GroupSelection;
import stroom.svg.client.SvgPresets;
import stroom.widget.button.client.InlineSvgButton;

import java.util.HashSet;

public class TableExpandButton extends InlineSvgButton {

    private TableExpandButton() {
    }

    public static TableExpandButton create() {
        final TableExpandButton button = new TableExpandButton();
        button.setSvg(SvgPresets.EXPAND_ALL.getSvgImage());
        button.setTitle("Expand");
        button.setEnabled(false);
        return button;
    }


    public GroupSelection expand(final GroupSelection groupSelection, final int maxDepth) {
        return groupSelection.copy()
                .closedGroups(new HashSet<>())
                .expand(maxDepth)
                .build();
    }

    public void update(final GroupSelection groupSelection, final int maxDepth) {
        final int expandedDepth = groupSelection.getExpandedDepth();
        final boolean enableExpandButton = maxDepth > 0 &&
            (expandedDepth < maxDepth || groupSelection.hasClosedGroups());
        setEnabled(enableExpandButton);
        setTitle(enableExpandButton ? "Expand Level " + Math.min(maxDepth, expandedDepth + 1) : "Expand");
    }
}
