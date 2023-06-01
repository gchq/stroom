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

package stroom.analytics.client.view;

import stroom.analytics.client.presenter.AnalyticDataShardsPresenter.AnalyticDataShardsView;
import stroom.analytics.client.presenter.AnalyticDataShardsUiHandlers;
import stroom.query.api.v2.TimeRange;
import stroom.query.client.view.QueryButtons;
import stroom.query.client.view.TimeRangeSelector;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AnalyticDataShardsViewImpl
        extends ViewWithUiHandlers<AnalyticDataShardsUiHandlers>
        implements AnalyticDataShardsView {

    private final Widget widget;

    @UiField
    SimplePanel shardList;
    @UiField
    InlineSvgButton warnings;
    @UiField
    QueryButtons queryButtons;
    @UiField
    SimplePanel tableContainer;
    @UiField
    TimeRangeSelector timeRangeSelector;

    @Inject
    public AnalyticDataShardsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setListView(final View view) {
        shardList.setWidget(view.asWidget());
    }

    @Override
    public void setTimeRange(final TimeRange timeRange) {
        timeRangeSelector.setValue(timeRange);
    }

    @Override
    public TimeRange getTimeRange() {
        return timeRangeSelector.getValue();
    }

    @Override
    public void setWarningsVisible(final boolean show) {
        warnings.getElement().getStyle().setOpacity(show ? 1 : 0);
    }

    @Override
    public QueryButtons getQueryButtons() {
        return queryButtons;
    }

    @Override
    public void setTable(final View view) {
        view.asWidget().addStyleName("dashboard-panel overflow-hidden");
        tableContainer.setWidget(view.asWidget());
    }

    @UiHandler("warnings")
    public void onWarnings(final ClickEvent event) {
        if (getUiHandlers() != null && MouseUtil.isPrimary(event)) {
            getUiHandlers().showWarnings();
        }
    }

    @UiHandler("timeRangeSelector")
    public void onTimeRangeSelector(final ValueChangeEvent<TimeRange> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTimeRange(event.getValue());
        }
    }

    public interface Binder extends UiBinder<Widget, AnalyticDataShardsViewImpl> {

    }
}
