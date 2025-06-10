/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.dashboard.client.query;

import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.query.CurrentSelectionPresenter.CurrentSelectionView;
import stroom.data.client.presenter.CopyTextUtil;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.user.client.ui.HTML;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class CurrentSelectionPresenter
        extends MyPresenterWidget<CurrentSelectionView> {

    private DashboardContext dashboardContext;

    @Inject
    public CurrentSelectionPresenter(final EventBus eventBus,
                                     final CurrentSelectionView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getView().getHtml().addDomHandler(e ->
                CopyTextUtil.onClick(e.getNativeEvent(), this), MouseDownEvent.getType()));
    }

    public void show(final DashboardContext dashboardContext) {
        setDashboardContext(dashboardContext);
        refresh();

        final HandlerRegistration handlerRegistration = dashboardContext
                .addContextChangeHandler(e -> refresh());

        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(600, 800))
                .caption("Current Selection")
                .modal(false)
                .onHide(e -> handlerRegistration.removeHandler())
                .fire();
    }

    public void setDashboardContext(final DashboardContext dashboardContext) {
        this.dashboardContext = dashboardContext;
    }

    public void refresh() {
        getView().getHtml().setHTML(dashboardContext.toSafeHtml());
    }

    public interface CurrentSelectionView extends View {

        HTML getHtml();
    }
}
