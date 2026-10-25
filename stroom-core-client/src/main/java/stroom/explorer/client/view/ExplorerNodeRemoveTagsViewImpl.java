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

package stroom.explorer.client.view;

import stroom.docref.DocRef;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter.ExplorerNodeRemoveTagsView;
import stroom.item.client.SelectionList;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.view.HideRequestUiHandlers;
import stroom.widget.presenter.client.ViewWithUiHandlers;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// Picks tags to remove from several documents: those chosen in a selection list of several
/// choices ([SelectionList#setMultiSelect]) of the tags they have.
public class ExplorerNodeRemoveTagsViewImpl
        extends ViewWithUiHandlers<HideRequestUiHandlers>
        implements ExplorerNodeRemoveTagsView {

    private final Widget widget;
    private final TagListModel nodeTagsModel = new TagListModel();

    @UiField
    SelectionList<String, SimpleSelectionItemWrapper<String>> nodeTagsList;

    @Inject
    public ExplorerNodeRemoveTagsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());
        nodeTagsList.setMultiSelect(true);
        nodeTagsList.setModel(nodeTagsModel);
    }

    @Override
    public void focus() {
        nodeTagsList.focus();
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public Set<String> getRemoveTags() {
        return new HashSet<>(TagListModel.getSelectedTags(nodeTagsList));
    }

    @Override
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


    public interface Binder extends UiBinder<Widget, ExplorerNodeRemoveTagsViewImpl> {

    }
}
