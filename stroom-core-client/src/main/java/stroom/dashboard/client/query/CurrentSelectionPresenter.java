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

package stroom.dashboard.client.query;

import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.query.CurrentSelectionPresenter.CurrentSelectionView;
import stroom.data.client.presenter.CopyTextUtil;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.user.client.ui.HTML;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class CurrentSelectionPresenter
        extends MyPresenterWidget<CurrentSelectionView> {

    private Consumer<String> insertHandler;

    @Inject
    public CurrentSelectionPresenter(final EventBus eventBus,
                                     final CurrentSelectionView view) {
        super(eventBus, view);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getView().getHtml().addDomHandler(e ->
                CopyTextUtil.onClick(e.getNativeEvent(), this, insertHandler), MouseDownEvent.getType()));
    }

    public void refresh(final DashboardContext dashboardContext,
                        final boolean showInsert) {
        getView().getHtml().setHTML(dashboardContext.toSafeHtml(showInsert));
    }

    public void setInsertHandler(final Consumer<String> insertHandler) {
        this.insertHandler = insertHandler;
    }

    public interface CurrentSelectionView extends View {

        HTML getHtml();
    }
}
