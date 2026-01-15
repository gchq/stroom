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

package stroom.dashboard.client.main;

import stroom.dashboard.client.flexlayout.TabLayout;
import stroom.dashboard.client.main.RenameTabPresenter.RenameTabView;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasText;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.function.Consumer;

public class RenameTabPresenter
        extends MyPresenterWidget<RenameTabView>
        implements HidePopupRequestEvent.Handler {

    private DashboardPresenter dashboardPresenter;
    private TabLayout tabLayout;
    private String componentName;
    private Consumer<String> nameChangeConsumer;

    @Inject
    public RenameTabPresenter(final EventBus eventBus, final RenameTabView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getNameBox().addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                HidePopupRequestEvent.builder(this).fire();
            }
        }));
    }

    public void show(final DashboardPresenter dashboardPresenter,
                     final TabLayout flexLayout,
                     final String componentName,
                     final Consumer<String> nameChangeConsumer) {
        this.dashboardPresenter = dashboardPresenter;
        this.tabLayout = flexLayout;
        this.componentName = componentName;
        this.nameChangeConsumer = nameChangeConsumer;

        getView().getName().setText(componentName);

        final PopupSize popupSize = PopupSize.resizableX();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Rename Tab")
                .onShow(e -> getView().focus())
                .onHideRequest(this)
                .fire();
    }

    @Override
    public void onHideRequest(final HidePopupRequestEvent e) {
        if (e.isOk()) {
            final String name = getView().getName().getText();
            if (name != null && !name.trim().isEmpty() && !Objects.equals(name, componentName)) {
                nameChangeConsumer.accept(name);

//                tabLayout.clear();
                tabLayout.refresh();
                dashboardPresenter.onDirty();
            }
        }
        e.hide();
    }

    public String getName() {
        return getView().getName().getText();
    }

    public interface RenameTabView extends View, Focus {

        HasText getName();

        HasKeyDownHandlers getNameBox();
    }
}
