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

import stroom.query.client.view.QueryButtons;
import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.v2.TimeRange;
import stroom.query.client.view.TimeRangeSelector;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class DashboardViewImpl extends ViewWithUiHandlers<DashboardUiHandlers>
        implements DashboardPresenter.DashboardView {

    private final Widget widget;

    @UiField
    Button addPanelButton;
    @UiField
    Button addInputButton;
    @UiField
    Button constraintsButton;
    @UiField
    Button designModeButton;
    @UiField
    QueryButtons queryButtons;
    @UiField
    SimplePanel content;
    @UiField
    TimeRangeSelector timeRangeSelector;

    @Inject
    public DashboardViewImpl(final Binder binder,
                             final UserPreferencesManager userPreferencesManager) {
        widget = binder.createAndBindUi(this);
        timeRangeSelector.setUtc(userPreferencesManager.isUtc());
        setReadOnly(true);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setTimeRange(final TimeRange timeRange) {
        timeRangeSelector.setValue(timeRange);
    }

    @Override
    public void setContent(final Widget content) {
        this.content.setWidget(content);
    }

    @Override
    public void setEmbedded(final boolean embedded) {
        content.getElement().getStyle().clearTop();
        if (embedded) {
            content.getElement().getStyle().setTop(0, Unit.PX);
        }
    }

    @Override
    public void setReadOnly(final boolean readOnly) {
        addPanelButton.setEnabled(!readOnly);
        addInputButton.setEnabled(!readOnly);
    }

    @Override
    public void setDesignMode(final boolean designMode) {
        if (designMode) {
            designModeButton.setText("Exit Design Mode");
            widget.addStyleName("dashboard__designMode");
        } else {
            designModeButton.setText("Enter Design Mode");
            widget.removeStyleName("dashboard__designMode");
        }
    }

    @Override
    public QueryButtons getQueryButtons() {
        return queryButtons;
    }

    @UiHandler("addPanelButton")
    public void onAddPanelButtonClick(final ClickEvent event) {
        if (MouseUtil.isPrimary(event)) {
            if (getUiHandlers() != null) {
                getUiHandlers().onAddPanel(event);
            }
        }
    }

    @UiHandler("addInputButton")
    public void onAddInputButtonClick(final ClickEvent event) {
        if (MouseUtil.isPrimary(event)) {
            if (getUiHandlers() != null) {
                getUiHandlers().onAddInput(event);
            }
        }
    }

    @UiHandler("constraintsButton")
    public void onConstraintsButtonClick(final ClickEvent event) {
        if (MouseUtil.isPrimary(event)) {
            if (getUiHandlers() != null) {
                getUiHandlers().onConstraints(event);
            }
        }
    }

    @UiHandler("designModeButton")
    public void onDesignModeButtonClick(final ClickEvent event) {
        if (MouseUtil.isPrimary(event)) {
            if (getUiHandlers() != null) {
                getUiHandlers().onDesign(event);
            }
        }
    }

    @UiHandler("timeRangeSelector")
    public void onTimeRangeSelector(final ValueChangeEvent<TimeRange> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTimeRange(event.getValue());
        }
    }

    public interface Binder extends UiBinder<Widget, DashboardViewImpl> {

    }
}
