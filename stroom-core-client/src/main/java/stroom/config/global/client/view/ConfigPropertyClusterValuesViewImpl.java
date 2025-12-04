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

package stroom.config.global.client.view;

import stroom.config.global.client.presenter.ConfigPropertyClusterValuesPresenter;
import stroom.config.global.client.presenter.ConfigPropertyClusterValuesUiHandlers;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ConfigPropertyClusterValuesViewImpl
        extends ViewWithUiHandlers<ConfigPropertyClusterValuesUiHandlers>
        implements ConfigPropertyClusterValuesPresenter.ConfigPropertyClusterValuesView {

    private final Widget widget;

    @UiField
    SimplePanel dataGrid;


    private Widget list;

    @Inject
    ConfigPropertyClusterValuesViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setList(final Widget widget) {
        this.list = widget;
        dataGrid.setWidget(widget);
    }

    @Override
    public void removeFromSlot(final Object slot, final Widget content) {

    }

    @Override
    public void setInSlot(final Object slot, final Widget content) {
        if (ConfigPropertyClusterValuesPresenter.LIST.equals(slot)) {
            dataGrid.setWidget(content);
        }
    }

    public interface Binder extends UiBinder<Widget, ConfigPropertyClusterValuesViewImpl> {

    }
}
