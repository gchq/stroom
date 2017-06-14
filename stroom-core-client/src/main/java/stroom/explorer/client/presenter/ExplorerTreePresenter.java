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

package stroom.explorer.client.presenter;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.explorer.client.event.ExplorerTreeDeleteEvent;
import stroom.explorer.client.event.ExplorerTreeSelectEvent;
import stroom.explorer.client.event.HighlightExplorerItemEvent;
import stroom.explorer.client.event.OpenExplorerTabEvent;
import stroom.explorer.client.event.RefreshExplorerTreeEvent;
import stroom.explorer.client.event.ShowNewMenuEvent;
import stroom.explorer.shared.ExplorerData;
import stroom.security.client.event.CurrentUserChangedEvent;
import stroom.security.client.event.CurrentUserChangedEvent.CurrentUserChangedHandler;
import stroom.security.shared.DocumentPermissionNames;
import stroom.widget.button.client.SvgIcons;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.tab.client.presenter.Icon;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.SelectionType;

public class ExplorerTreePresenter
        extends MyPresenter<ExplorerTreePresenter.ExplorerTreeView, ExplorerTreePresenter.ExplorerTreeProxy>
        implements ExplorerTreeUiHandlers, RefreshExplorerTreeEvent.Handler, HighlightExplorerItemEvent.Handler,
        CurrentUserChangedHandler, TabData {

    private static final String EXPLORER = "Explorer";

    private final DocumentTypeCache documentTypeCache;
    private final TypeFilterPresenter typeFilterPresenter;
    private final ExplorerTree explorerTree;

    @Inject
    public ExplorerTreePresenter(final EventBus eventBus, final ExplorerTreeView view, final ExplorerTreeProxy proxy,
                                 final ClientDispatchAsync dispatcher, final DocumentTypeCache documentTypeCache,
                                 final TypeFilterPresenter typeFilterPresenter) {
        super(eventBus, view, proxy);
        this.documentTypeCache = documentTypeCache;
        this.typeFilterPresenter = typeFilterPresenter;

        view.setUiHandlers(this);

        explorerTree = new ExplorerTree(dispatcher, true) {
            @Override
            protected void doSelect(final ExplorerData selection, final SelectionType selectionType) {
                super.doSelect(selection, selectionType);
                getView().setDeleteEnabled(explorerTree.getSelectionModel().getSelectedItems().size() > 0);
            }
        };

        // Add views.
        view.setCellTree(explorerTree);
    }

    @Override
    protected void onBind() {
        super.onBind();

        // Register for refresh events.
        registerHandler(getEventBus().addHandler(RefreshExplorerTreeEvent.getType(), this));

        // Register for highlight events.
        registerHandler(getEventBus().addHandler(HighlightExplorerItemEvent.getType(), this));

        registerHandler(typeFilterPresenter.addDataSelectionHandler(event -> explorerTree.setIncludedTypeSet(typeFilterPresenter.getIncludedTypes())));

        // Fire events from the explorer tree globally.
        registerHandler(explorerTree.getSelectionModel().addSelectionHandler(event -> getEventBus().fireEvent(new ExplorerTreeSelectEvent(explorerTree.getSelectionModel(), event.getSelectionType()))));
        registerHandler(explorerTree.addContextMenuHandler(event -> getEventBus().fireEvent(event)));
    }

    @Override
    public void newItem(final Element element) {
        final int x = element.getAbsoluteLeft() - 1;
        final int y = element.getAbsoluteTop() + element.getOffsetHeight() + 1;

        ShowNewMenuEvent.fire(this, element, x, y);
    }

    @Override
    public void deleteItem() {
        if (explorerTree.getSelectionModel().getSelectedItems().size() > 0) {
            ExplorerTreeDeleteEvent.fire(this);
        }
    }

    @Override
    public void changeNameFilter(final String name) {
        explorerTree.changeNameFilter(name);
    }

    @Override
    public void showTypeFilter(final MouseDownEvent event) {
        final Element target = event.getNativeEvent().getEventTarget().cast();

        final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft() - 1,
                target.getAbsoluteTop() + target.getClientHeight() + 2);
        ShowPopupEvent.fire(this, typeFilterPresenter, PopupType.POPUP, popupPosition, null, target);
    }

    @ProxyEvent
    @Override
    public void onCurrentUserChanged(final CurrentUserChangedEvent event) {
        documentTypeCache.clear();
        // Set the data for the type filter.
        documentTypeCache.fetch().onSuccess(typeFilterPresenter::setDocumentTypes);

        explorerTree.getTreeModel().reset();
        explorerTree.getTreeModel().setRequiredPermissions(DocumentPermissionNames.READ);
        explorerTree.getTreeModel().setIncludedTypeSet(null);

        // Show the tree.
        forceReveal();
    }

    @Override
    protected void revealInParent() {
        explorerTree.getTreeModel().refresh();
        OpenExplorerTabEvent.fire(this, this, this);
    }

    @Override
    public void onHighlight(final HighlightExplorerItemEvent event) {
        explorerTree.setSelectedItem(event.getItem());
        explorerTree.getTreeModel().setEnsureVisible(event.getItem());
        explorerTree.getTreeModel().refresh();
    }

    @Override
    public void onRefresh(final RefreshExplorerTreeEvent event) {
        explorerTree.getTreeModel().refresh();
    }

    public void refresh() {
        explorerTree.getTreeModel().refresh();
    }

    @Override
    public String getLabel() {
        return EXPLORER;
    }

    @Override
    public Icon getIcon() {
        return SvgIcons.EXPLORER;
    }

    @Override
    public boolean isCloseable() {
        return false;
    }

    public interface ExplorerTreeView extends View, HasUiHandlers<ExplorerTreeUiHandlers> {
        void setCellTree(Widget widget);

        void setDeleteEnabled(boolean enable);
    }

    @ProxyCodeSplit
    public interface ExplorerTreeProxy extends Proxy<ExplorerTreePresenter> {
    }
}
