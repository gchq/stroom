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

package stroom.preferences.client;

import stroom.preferences.client.UserPreferencesPresenter.UserPreferencesView;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.tab.client.presenter.TabBar;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public final class UserPreferencesViewImpl
        extends ViewWithUiHandlers<UserPreferencesUiHandlers>
        implements UserPreferencesView {

    @UiField
    TabBar tabBar;
    @UiField
    LayerContainer layerContainer;

    private final Widget widget;

    @UiField
    Button setAsDefault;
    @UiField
    Button revertToDefault;

    @Inject
    public UserPreferencesViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        setAsDefault.setIcon(SvgImage.OK);
        revertToDefault.setIcon(SvgImage.UNDO);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public TabBar getTabBar() {
        return tabBar;
    }

    @Override
    public LayerContainer getLayerContainer() {
        return layerContainer;
    }

    @Override
    public void setAsDefaultVisible(final boolean visible) {
        setAsDefault.setVisible(visible);
    }

    @UiHandler("setAsDefault")
    void onClickSetAsDefault(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSetAsDefault();
        }
    }

    @UiHandler("revertToDefault")
    void onClickRevertToDefault(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onRevertToDefault();
        }
    }

    public interface Binder extends UiBinder<Widget, UserPreferencesViewImpl> {

    }
}
