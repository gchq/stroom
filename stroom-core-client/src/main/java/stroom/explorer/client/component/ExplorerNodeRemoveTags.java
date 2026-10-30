/*
 * Copyright 2023 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.explorer.client.model.TagListModel;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.selection.client.SelectionList;
import stroom.widget.selection.client.SimpleSelectionItemWrapper;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// Picks tags to remove from several documents: those chosen in a selection list of several
/// choices ([SelectionList#setMultiSelect]) of the tags they have.
public class ExplorerNodeRemoveTags extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final TagListModel nodeTagsModel = new TagListModel();

    @UiField
    SelectionList<String, SimpleSelectionItemWrapper<String>> nodeTagsList;

    /// Makes an empty list.
    public ExplorerNodeRemoveTags() {
        initWidget(BINDER.createAndBindUi(this));
        nodeTagsList.setModel(nodeTagsModel);
    }

    /// Focuses the tags.
    public void focus() {
        nodeTagsList.focus();
    }

    /// @return The tags chosen, to remove from all the documents.
    public Set<String> getRemoveTags() {
        return new HashSet<>(TagListModel.getSelectedTags(nodeTagsList));
    }

    /// Shows the documents' tags, none chosen.
    ///
    /// @param nodeDocRefs The documents.
    /// @param nodeTags    The tags they have.
    public void setData(final List<DocRef> nodeDocRefs,
                        final Set<String> nodeTags) {
        final List<String> tagsList = NullSafe.stream(nodeTags)
                .sorted()
                .collect(Collectors.toList());
        // A new set of tags starts with none chosen
        nodeTagsList.getSelectionModel().clear();
        nodeTagsModel.setTags(nodeTagsList, tagsList);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ExplorerNodeRemoveTags> {

    }
}
