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

package stroom.explorer.client.component;

import stroom.item.client.SelectionList;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.item.client.SimpleSelectionListModel;

import java.util.List;
import java.util.stream.Collectors;

/// The tags in one of the explorer's tag lists (Edit Tags' known tags and document tags, and Remove
/// Tags' existing tags), each a [SelectionList] of several choices. None has a quick filter of its
/// own: the known tags are filtered by the field above them, and a quick filter would hide tags
/// that are still chosen, which the dialog would then act on unseen.
class TagListModel extends SimpleSelectionListModel<String> {

    @Override
    public boolean displayFilter() {
        return false;
    }

    /// Shows the tags in a list, keeping those chosen that are still in it, so that what is acted on
    /// is only ever what can be seen.
    ///
    /// @param list The list that shows this model.
    /// @param tags The tags to show, in the order to show them.
    void setTags(final SelectionList<String, SimpleSelectionItemWrapper<String>> list,
                 final List<String> tags) {
        final List<SimpleSelectionItemWrapper<String>> keep = list.getSelectionModel()
                .getSelectedItems()
                .stream()
                .filter(item -> tags.contains(item.getItem()))
                .collect(Collectors.toList());
        clear();
        addItems(tags);
        list.getSelectionModel().setSelectedItems(keep);
        list.refresh(false, false);
    }

    /// @param list The list that shows this model.
    /// @return The tags chosen in the list.
    static List<String> getSelectedTags(final SelectionList<String, SimpleSelectionItemWrapper<String>> list) {
        return list.getSelectionModel()
                .getSelectedItems()
                .stream()
                .map(SimpleSelectionItemWrapper::getItem)
                .collect(Collectors.toList());
    }
}
