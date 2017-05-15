/*
 * Copyright 2016 Crown Copyright
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

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.explorer.client.presenter.ExplorerTreePresenter;
import stroom.explorer.client.presenter.ExplorerTreeUiHandlers;
import stroom.widget.button.client.GlyphButton;
import stroom.widget.button.client.GlyphIcons;
import stroom.widget.dropdowntree.client.view.QuickFilter;

public class ExplorerTreeViewImpl extends ViewWithUiHandlers<ExplorerTreeUiHandlers>
        implements ExplorerTreePresenter.ExplorerTreeView {
    public interface Binder extends UiBinder<Widget, ExplorerTreeViewImpl> {
    }

    private final Widget widget;

    @UiField(provided = true)
    GlyphButton newItem;
    @UiField(provided = true)
    GlyphButton deleteItem;
    @UiField
    QuickFilter nameFilter;
    @UiField
    SimplePanel treeContainer;
    @UiField(provided = true)
    GlyphButton typeFilter;

    @Inject
    public ExplorerTreeViewImpl(final Binder binder) {
        newItem = GlyphButton.create(GlyphIcons.NEW_ITEM);
        deleteItem = GlyphButton.create(GlyphIcons.DELETE);
        typeFilter = GlyphButton.create(GlyphIcons.FILTER);
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setDeleteEnabled(final boolean enabled) {
        deleteItem.setEnabled(enabled);
    }

    @UiHandler("newItem")
    void onNewItemClick(final MouseDownEvent event) {
        getUiHandlers().newItem(newItem.getElement());
    }

    @UiHandler("deleteItem")
    void onDeleteItemClick(final MouseDownEvent event) {
        getUiHandlers().deleteItem();
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeNameFilter(nameFilter.getText());
    }

    @UiHandler("typeFilter")
    void onFilterClick(final MouseDownEvent event) {
        getUiHandlers().showTypeFilter(event);
    }

    @Override
    public void setCellTree(final Widget cellTree) {
        treeContainer.setWidget(cellTree);
    }
}
