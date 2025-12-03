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

import stroom.query.api.ParamUtil;
import stroom.query.api.ParamValues;
import stroom.query.api.TimeRange;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.view.HideRequest;
import stroom.widget.popup.client.view.HideRequestUiHandlers;
import stroom.widget.popup.client.view.OkCancelContent;
import stroom.widget.popup.client.view.SimplePopupLayout;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;

public class TimeRangeSelector extends Composite implements HasValue<TimeRange>, Focus {

    private final Label label;
    private final PopupPanel popup;
    private final TimeRangePopup timeRangePopup;
    private TimeRange value = TimeRanges.ALL_TIME;
    private ParamValues paramValues;

    public TimeRangeSelector() {
        timeRangePopup = GWT.create(TimeRangePopup.class);

        label = new Label(getText(), false);
        label.setStyleName("timeRange-selector");
        label.setTitle("Time range to apply to all queries in this dashboard.\n" +
                       "The selected time range applies to the time field applicable to each query's data source.");

        this.popup = new PopupPanel(false);

        final HideRequestUiHandlers hideRequestUiHandlers = new HideRequestUiHandlers() {
            @Override
            public void hideRequest(final HideRequest request) {
                if (request.isOk()) {
                    setValue(timeRangePopup.getValue(), true);
                }
                popup.hide();
                request.getCancelHandler().run();
            }
        };
        final OkCancelContent okCancelContent = new OkCancelContent(hideRequestUiHandlers);
        okCancelContent.setContent(timeRangePopup.asWidget());

        final SimplePopupLayout simplePopupLayout = new SimplePopupLayout();
        simplePopupLayout.setContent(okCancelContent);

        popup.addAutoHidePartner(label.getElement());
        popup.setWidget(simplePopupLayout);
        popup.setStyleName("timeRange-popup");

        label.addClickHandler(event -> {
            timeRangePopup.setValue(value, false);
            popup.showRelativeTo(label);
        });

        initWidget(label);
    }

    @Override
    public void focus() {
        label.getElement().focus();
    }

    public void setUtc(final boolean utc) {
        timeRangePopup.setUtc(utc);
    }

    @Override
    public TimeRange getValue() {
        return value;
    }

    @Override
    public void setValue(final TimeRange value) {
        setValue(value, false);
    }

    @Override
    public void setValue(TimeRange value, final boolean fireEvents) {
        if (value == null) {
            value = TimeRanges.ALL_TIME;
        }

        this.value = value;
        label.setText(getText());
        if (fireEvents) {
            ValueChangeEvent.fire(this, value);
        }
    }

    private String getText() {
        if (paramValues == null) {
            return value.toString();
        } else if (value.getName() != null) {
            return value.getName();
        } else {
            final String from = ParamUtil.replaceParameters(value.getFrom(), paramValues);
            final String to = ParamUtil.replaceParameters(value.getTo(), paramValues);
            if (NullSafe.isBlankString(from) && NullSafe.isBlankString(to)) {
                return TimeRanges.ALL_TIME.toString();
            } else if (NullSafe.isNonBlankString(from)) {
                return "After " + from;
            } else {
                return "Before " + to;
            }
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<TimeRange> handler) {
        return addHandler(handler, ValueChangeEvent.getType());
    }

    public void setParamValues(final ParamValues paramValues) {
        this.paramValues = paramValues;
        label.setText(getText());
    }
}
