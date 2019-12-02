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

package stroom.dashboard.client.main;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.client.flexlayout.TabLayout;
import stroom.dashboard.client.main.RenameTabPresenter.RenameTabView;
import stroom.dashboard.shared.ComponentConfig;
import stroom.util.shared.EqualsUtil;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

public class RenameTabPresenter extends MyPresenterWidget<RenameTabView> implements PopupUiHandlers {
    private DashboardPresenter dashboardPresenter;
    private TabLayout tabLayout;
    private ComponentConfig componentConfig;

    @Inject
    public RenameTabPresenter(final EventBus eventBus, final RenameTabView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getNameBox().addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                onHideRequest(false, true);
            }
        }));
    }

    public void show(final DashboardPresenter dashboardPresenter, final TabLayout flexLayout, final ComponentConfig componentConfig) {
        this.dashboardPresenter = dashboardPresenter;
        this.tabLayout = flexLayout;
        this.componentConfig = componentConfig;

        getView().getName().setText(componentConfig.getName());

        final PopupSize popupSize = new PopupSize(250, 78, 250, 78, 1000, 78, true);
        ShowPopupEvent.fire(this, this, PopupType.OK_CANCEL_DIALOG, popupSize, "Rename Field", this);
        getView().focus();
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final String name = getView().getName().getText();
            if (name != null && !name.trim().isEmpty() && !EqualsUtil.isEquals(name, componentConfig.getName())) {
                componentConfig.setName(name);
//                tabLayout.clear();
                tabLayout.refresh();
                dashboardPresenter.onDirty();
            }
        }

        HidePopupEvent.fire(dashboardPresenter, this);
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
    }

    public String getName() {
        return getView().getName().getText();
    }

    public interface RenameTabView extends View {
        HasText getName();

        HasKeyDownHandlers getNameBox();

        void focus();
    }
}
