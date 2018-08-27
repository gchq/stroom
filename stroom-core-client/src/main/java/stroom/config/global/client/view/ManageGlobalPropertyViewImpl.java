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

package stroom.config.global.client.view;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.config.global.client.presenter.ManageGlobalPropertyPresenter;
import stroom.config.global.client.presenter.ManageGlobalPropertyPresenter.ManageGlobalPropertyView;
import stroom.config.global.client.presenter.ManageGlobalPropertyUiHandlers;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.layout.client.view.ResizeSimplePanel;

public class ManageGlobalPropertyViewImpl extends ViewWithUiHandlers<ManageGlobalPropertyUiHandlers> implements ManageGlobalPropertyView {
    @UiField
    QuickFilter nameFilter;
    @UiField
    ResizeSimplePanel dataGrid;
    private Widget widget;

    @Inject
    public ManageGlobalPropertyViewImpl(final EventBus eventBus, final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (ManageGlobalPropertyPresenter.LIST.equals(slot)) {
            dataGrid.setWidget(content);
        }
    }

    @UiHandler("nameFilter")
    void onFilterChange(final ValueChangeEvent<String> event) {
        getUiHandlers().changeNameFilter(nameFilter.getText());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    public interface Binder extends UiBinder<Widget, ManageGlobalPropertyViewImpl> {
    }
}
