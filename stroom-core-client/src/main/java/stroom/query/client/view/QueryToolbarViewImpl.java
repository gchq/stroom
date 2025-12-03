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

package stroom.query.client.view;

import stroom.preferences.client.UserPreferencesManager;
import stroom.query.api.ParamValues;
import stroom.query.api.TimeRange;
import stroom.query.client.presenter.QueryToolbarPresenter.QueryToolbarView;
import stroom.query.client.presenter.QueryToolbarUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.Severity;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class QueryToolbarViewImpl
        extends ViewWithUiHandlers<QueryToolbarUiHandlers>
        implements QueryToolbarView {

    private final Widget widget;

    @UiField
    InlineSvgButton errors;
    @UiField
    QueryButtons queryButtons;
    @UiField
    TimeRangeSelector timeRangeSelector;

    @Inject
    public QueryToolbarViewImpl(final Binder binder,
                                final UserPreferencesManager userPreferencesManager) {
        widget = binder.createAndBindUi(this);
        timeRangeSelector.setUtc(userPreferencesManager.isUtc());
        errors.setSvg(SvgImage.ERROR);
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
    public TimeRange getTimeRange() {
        return timeRangeSelector.getValue();
    }

    @Override
    public void setErrorsVisible(final boolean show) {
        errors.getElement().getStyle().setOpacity(show ? 1 : 0);
    }

    @Override
    public void setErrorSeverity(final Severity severity) {
        if (Severity.FATAL_ERROR.equals(severity) || Severity.ERROR.equals(severity)) {
            errors.setSvg(SvgImage.ERROR);
            errors.setTitle("Show Errors");
        } else if (Severity.WARNING.equals(severity)) {
            errors.setSvg(SvgImage.ALERT);
            errors.setTitle("Show Warning");
        } else if (Severity.INFO.equals(severity)) {
            errors.setSvg(SvgImage.INFO);
            errors.setTitle("Show Messages");
        }
    }

    @Override
    public QueryButtons getQueryButtons() {
        return queryButtons;
    }

    @UiHandler("errors")
    public void onErrors(final ClickEvent event) {
        if (getUiHandlers() != null && MouseUtil.isPrimary(event)) {
            getUiHandlers().showErrors();
        }
    }

    @UiHandler("timeRangeSelector")
    public void onTimeRangeSelector(final ValueChangeEvent<TimeRange> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTimeRange(event.getValue());
        }
    }

    @Override
    public void setParamValues(final ParamValues paramValues) {
        timeRangeSelector.setParamValues(paramValues);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, QueryToolbarViewImpl> {

    }
}
