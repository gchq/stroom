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

package stroom.security.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.item.client.ItemListBox;
import stroom.security.client.presenter.DocumentPermissionsPresenter;
import stroom.security.shared.ChangeDocumentPermissionsAction;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public final class DocumentPermissionsViewImpl extends ViewImpl implements DocumentPermissionsPresenter.DocumentPermissionsView {
    private final Widget widget;
    @UiField
    DockLayoutPanel layout;
    @UiField
    ResizeSimplePanel tabs;
    @UiField
    Grid cascadeGrid;
    @UiField
    ItemListBox<ChangeDocumentPermissionsAction.Cascade> cascade;

    @Inject
    public DocumentPermissionsViewImpl(final EventBus eventBus, final Binder binder) {
        widget = binder.createAndBindUi(this);
        cascade.addItems(ChangeDocumentPermissionsAction.Cascade.values());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setTabsView(View view) {
        tabs.setWidget(view.asWidget());
    }

    @Override
    public ItemListBox<ChangeDocumentPermissionsAction.Cascade> getCascade() {
        return cascade;
    }

    @Override
    public void setCascasdeVisible(boolean visible) {
        layout.setWidgetHidden(cascadeGrid, !visible);
    }

    public interface Binder extends UiBinder<Widget, DocumentPermissionsViewImpl> {
    }
}
