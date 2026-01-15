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

package stroom.widget.xsdbrowser.client.view;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SelectionMap {

    private final Map<XSDNode, Set<SelectableItem>> selectionMap = new HashMap<>();
    private XSDNode selectedItem;

    public void addSelectionItem(final XSDNode node, final SelectableItem selectable) {
        if (node != null && selectable != null) {
            Set<SelectableItem> items = selectionMap.get(node);
            if (items == null) {
                items = new HashSet<>();
                selectionMap.put(node, items);
            }

            items.add(selectable);
        }
    }

    public void setSelectedItem(final XSDNode item) {
        // Deselect current selected item if any.
        if (selectedItem != null) {
            final Set<SelectableItem> items = selectionMap.get(selectedItem);
            if (items != null) {
                for (final SelectableItem selectable : items) {
                    selectable.setSelected(false);
                }
            }
        }

        // Select new item.
        if (item != null) {
            final Set<SelectableItem> items = selectionMap.get(item);
            if (items != null) {
                for (final SelectableItem selectable : items) {
                    selectable.setSelected(true);
                }
            }
        }

        selectedItem = item;
    }
}
