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
 */

package stroom.dashboard.client.table;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.RowFormatter;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dashboard.client.table.FormatPresenter.FormatView;
import stroom.dashboard.shared.Format.Type;
import stroom.dashboard.shared.TimeZone;
import stroom.dashboard.shared.TimeZone.Use;
import stroom.item.client.ItemListBox;
import stroom.item.client.StringListBox;
import stroom.widget.tickbox.client.view.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import java.util.Arrays;
import java.util.List;

public class FormatViewImpl extends ViewWithUiHandlers<FormatUihandlers>implements FormatView {
    public static final List<String> STANDARD_FORMATS = Arrays
            .asList("yyyy-MM-dd'T'HH:mm:ss.SSSXX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS xx",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS xxx",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS VV",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS",
                    "dd/MM/yyyy HH:mm:ss",
                    "dd/MM/yy HH:mm:ss",
                    "MM/dd/yyyy HH:mm:ss",
                    "d MMM yyyy HH:mm:ss",
                    "yyyy-MM-dd",
                    "dd/MM/yyyy",
                    "dd/MM/yy",
                    "MM/dd/yyyy",
                    "d MMM yyyy");

    private static final int ROW_COUNT = 8;
    private final Widget widget;
    @UiField
    Grid grid;
    @UiField
    ItemListBox<Type> type;
    @UiField
    ValueSpinner decimalPlaces;
    @UiField
    TickBox separate;
    @UiField
    StringListBox format;
    @UiField
    TickBox custom;
    @UiField
    TextBox text;
    @UiField
    ItemListBox<TimeZone.Use> timeZoneUse;
    @UiField
    StringListBox timeZoneId;
    @UiField
    ValueSpinner timeZoneOffsetHours;
    @UiField
    ValueSpinner timeZoneOffsetMinutes;
    @UiField
    TickBox wrap;

    @Inject
    public FormatViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        decimalPlaces.setValue(0);
        decimalPlaces.setMin(0);
        decimalPlaces.setMax(10);

        format.addItems(STANDARD_FORMATS);

        final RowFormatter formatter = grid.getRowFormatter();
        for (int i = 1; i <= ROW_COUNT; i++) {
            formatter.setVisible(i, false);
        }

        timeZoneUse.addItem(TimeZone.Use.LOCAL);
        timeZoneUse.addItem(TimeZone.Use.UTC);
        timeZoneUse.addItem(TimeZone.Use.ID);
        timeZoneUse.addItem(TimeZone.Use.OFFSET);

        timeZoneOffsetHours.setMin(-12);
        timeZoneOffsetHours.setMax(12);
        timeZoneOffsetHours.setValue(0);
        timeZoneOffsetHours.setMinStep(1);
        timeZoneOffsetHours.setMaxStep(1);

        timeZoneOffsetMinutes.setMin(0);
        timeZoneOffsetMinutes.setMax(59);
        timeZoneOffsetMinutes.setValue(0);
        timeZoneOffsetMinutes.setMinStep(30);
        timeZoneOffsetMinutes.setMaxStep(30);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setTypes(final List<Type> types) {
        this.type.addItems(types);
    }

    @Override
    public void setType(final Type type) {
        this.type.setSelectedItem(type);

        final RowFormatter formatter = grid.getRowFormatter();
        if (Type.NUMBER.equals(type)) {
            for (int i = 1; i <= ROW_COUNT; i++) {
                formatter.setVisible(i, i <= 2);
            }
        } else if (Type.DATE_TIME.equals(type)) {
            for (int i = 1; i <= ROW_COUNT - 2; i++) {
                formatter.setVisible(i, i > 2);
            }
            formatter.setVisible(7, TimeZone.Use.ID.equals(this.timeZoneUse.getSelectedItem()));
            formatter.setVisible(8, TimeZone.Use.OFFSET.equals(this.timeZoneUse.getSelectedItem()));
        } else {
            for (int i = 1; i <= ROW_COUNT; i++) {
                formatter.setVisible(i, false);
            }
        }
    }

    @Override
    public int getDecimalPlaces() {
        return this.decimalPlaces.getValue();
    }

    @Override
    public void setDecimalPlaces(final int decimalPlaces) {
        this.decimalPlaces.setValue(decimalPlaces);
    }

    @Override
    public boolean isUseSeparator() {
        return this.separate.getBooleanValue();
    }

    @Override
    public void setUseSeparator(final boolean useSeparator) {
        this.separate.setBooleanValue(useSeparator);
    }

    @Override
    public String getPattern() {
        if (custom.getBooleanValue()) {
            return text.getText();
        }

        return format.getSelected();
    }

    @Override
    public void setPattern(final String pattern) {
        String text = pattern;
        if (text == null || text.trim().length() == 0) {
            text = STANDARD_FORMATS.get(0);
        }

        if (!text.equals(this.format.getSelected())) {
            this.format.setSelected(text);
        }

        final boolean custom = this.format.getSelectedIndex() == -1;
        this.custom.setBooleanValue(custom);
        this.text.setEnabled(custom);
        this.text.setText(text);
    }

    @Override
    public void setTimeZoneIds(final List<String> timeZoneIds) {
        this.timeZoneId.addItems(timeZoneIds);
    }

    @Override
    public Use getTimeZoneUse() {
        return this.timeZoneUse.getSelectedItem();
    }

    @Override
    public void setTimeZoneUse(final Use use) {
        this.timeZoneUse.setSelectedItem(use);
    }

    @Override
    public String getTimeZoneId() {
        return this.timeZoneId.getSelected();
    }

    @Override
    public void setTimeZoneId(final String timeZoneId) {
        this.timeZoneId.setSelected(timeZoneId);
    }

    @Override
    public Integer getTimeZoneOffsetHours() {
        final int val = this.timeZoneOffsetHours.getValue();
        if (val == 0) {
            return null;
        }
        return val;
    }

    @Override
    public void setTimeZoneOffsetHours(final Integer timeZoneOffsetHours) {
        if (timeZoneOffsetHours == null) {
            this.timeZoneOffsetHours.setValue(0);
        } else {
            this.timeZoneOffsetHours.setValue(timeZoneOffsetHours);
        }
    }

    @Override
    public Integer getTimeZoneOffsetMinutes() {
        final int val = this.timeZoneOffsetMinutes.getValue();
        if (val == 0) {
            return null;
        }
        return val;
    }

    @Override
    public void setTimeZoneOffsetMinutes(final Integer timeZoneOffsetMinutes) {
        if (timeZoneOffsetMinutes == null) {
            this.timeZoneOffsetMinutes.setValue(0);
        } else {
            this.timeZoneOffsetMinutes.setValue(timeZoneOffsetMinutes);
        }
    }

    @Override
    public boolean isWrap() {
        return wrap.getBooleanValue();
    }

    @Override
    public void setWrap(final boolean wrap) {
        this.wrap.setBooleanValue(wrap);
    }

    @UiHandler("custom")
    public void onTickBoxClick(final ValueChangeEvent<TickBoxState> event) {
        text.setEnabled(custom.getBooleanValue());
    }

    @UiHandler("type")
    public void onTypeChange(final SelectionEvent<Type> event) {
        getUiHandlers().onTypeChange(type.getSelectedItem());
    }

    @UiHandler("format")
    public void onFormatChange(final ChangeEvent event) {
        setPattern(this.format.getSelected());
    }

    @UiHandler("timeZoneUse")
    public void onTimeZoneUseChange(final SelectionEvent<TimeZone.Use> event) {
        setType(this.type.getSelectedItem());
    }

    public interface Binder extends UiBinder<Widget, FormatViewImpl> {
    }
}
