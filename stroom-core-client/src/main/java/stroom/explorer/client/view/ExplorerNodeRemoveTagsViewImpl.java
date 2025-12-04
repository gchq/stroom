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

package stroom.explorer.client.view;

import stroom.docref.DocRef;
import stroom.explorer.client.presenter.ExplorerNodeRemoveTagsPresenter.ExplorerNodeRemoveTagsView;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ExplorerNodeRemoveTagsViewImpl
        extends ViewWithUiHandlers<HideRequestUiHandlers>
        implements ExplorerNodeRemoveTagsView {

    private final Widget widget;
    private Set<String> nodeTags = new HashSet<>();
    private String selectedValue = null;

    @UiField
    ListBox nodeTagsListBox;
    private List<DocRef> docRefs;

    @Inject
    public ExplorerNodeRemoveTagsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());
        nodeTagsListBox.setMultipleSelect(true);

        nodeTagsListBox.addKeyDownHandler(this::handleKeyDownEvent);
    }

    private void handleKeyDownEvent(final KeyDownEvent event) {
        final int keyCode = event.getNativeEvent().getKeyCode();
        if (KeyCodes.KEY_J == keyCode) {
            final int selectedIndex = nodeTagsListBox.getSelectedIndex();
            final int lastIdx = nodeTagsListBox.getItemCount() - 1;
            final int newIdx;
            if (selectedIndex == -1) {
                newIdx = 0;
            } else if (selectedIndex == lastIdx) {
                newIdx = selectedIndex;
            } else {
                newIdx = selectedIndex + 1;
            }
            nodeTagsListBox.setSelectedIndex(newIdx);
        } else if (KeyCodes.KEY_K == keyCode) {
            final int selectedIndex = nodeTagsListBox.getSelectedIndex();
            final int newIdx;
            if (selectedIndex == -1) {
                newIdx = 0;
            } else if (selectedIndex == 0) {
                newIdx = selectedIndex;
            } else {
                newIdx = selectedIndex - 1;
            }
            nodeTagsListBox.setSelectedIndex(newIdx);
        }
    }

    @Override
    public void focus() {
        nodeTagsListBox.setFocus(true);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public Set<String> getRemoveTags() {

        if (nodeTagsListBox.getItemCount() > 0
                && nodeTagsListBox.getSelectedValue() != null) {
            if (nodeTagsListBox.isMultipleSelect()) {
                final Set<String> removeTags = new HashSet<>(nodeTagsListBox.getItemCount());
                for (int i = 0; i < nodeTagsListBox.getItemCount(); i++) {
                    if (nodeTagsListBox.isItemSelected(i)) {
                        removeTags.add(nodeTagsListBox.getValue(i));
                    }
                }
                return removeTags;
            } else {
                return Collections.singleton(nodeTagsListBox.getSelectedValue());
            }
        } else {
            return Collections.emptySet();
        }
    }

    private void updateNodeTagsListBoxContents() {
        //noinspection SimplifyStreamApiCallChains
        final List<String> tagsList = NullSafe.stream(nodeTags)
                .sorted()
                .collect(Collectors.toList());

        nodeTagsListBox.clear();
        for (final String string : tagsList) {
            nodeTagsListBox.addItem(string);
        }
    }

    @Override
    public void setData(final List<DocRef> nodeDocRefs,
                        final Set<String> nodeTags) {

        this.nodeTags.clear();
        this.docRefs = nodeDocRefs;
        this.nodeTags.addAll(NullSafe.set(nodeTags));
        updateNodeTagsListBoxContents();
    }

    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, ExplorerNodeRemoveTagsViewImpl> {

    }
}
