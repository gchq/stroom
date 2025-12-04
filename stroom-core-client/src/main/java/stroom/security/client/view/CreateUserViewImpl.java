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

package stroom.security.client.view;

import stroom.item.client.SelectionBox;
import stroom.security.client.presenter.CreateUserPresenter.CreateType;
import stroom.security.client.presenter.CreateUserPresenter.CreateUserView;
import stroom.security.client.presenter.CreateUserUiHandlers;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public class CreateUserViewImpl
        extends ViewWithUiHandlers<CreateUserUiHandlers>
        implements CreateUserView {

    private final Widget widget;

    @UiField
    SelectionBox<CreateType> createTypes;
    @UiField
    SimplePanel panel;

    @Inject
    public CreateUserViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setCreateTypesVisible(final boolean visible) {
        createTypes.setVisible(visible);
    }

    @Override
    public void setCreateTypes(final List<CreateType> createTypes) {
        this.createTypes.clear();
        this.createTypes.addItems(createTypes);
    }

    @Override
    public void setCreateType(final CreateType createType) {
        this.createTypes.setValue(createType);
    }

    @Override
    public CreateType getCreateType() {
        return createTypes.getValue();
    }

    @Override
    public void setTypeView(final View view) {
        panel.setWidget(view.asWidget());
    }

    @UiHandler("createTypes")
    void createTypes(final ValueChangeEvent<CreateType> event) {
        getUiHandlers().onTypeChange();
    }

    public interface Binder extends UiBinder<Widget, CreateUserViewImpl> {

    }
}
