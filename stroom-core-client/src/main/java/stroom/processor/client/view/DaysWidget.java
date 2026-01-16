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

package stroom.processor.client.view;

import stroom.util.shared.time.Day;
import stroom.util.shared.time.Days;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashSet;
import java.util.Set;

public final class DaysWidget extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final Widget widget;

    @UiField
    Label mon;
    @UiField
    Label tue;
    @UiField
    Label wed;
    @UiField
    Label thu;
    @UiField
    Label fri;
    @UiField
    Label sat;
    @UiField
    Label sun;

    private final Set<Day> value = new HashSet<>();

    public DaysWidget() {
        widget = BINDER.createAndBindUi(this);
        initWidget(widget);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    public Days getValue() {
        return Days.create(value);
    }

    public void setValue(final Days selected) {
        this.value.clear();
        if (selected != null && selected.getDays() != null) {
            this.value.addAll(selected.getDays());
        }
        updateStyles();
    }

    @UiHandler("mon")
    public void onMon(final ClickEvent e) {
        toggleDay(Day.MONDAY);
    }

    @UiHandler("tue")
    public void onTue(final ClickEvent e) {
        toggleDay(Day.TUESDAY);
    }

    @UiHandler("wed")
    public void onWed(final ClickEvent e) {
        toggleDay(Day.WEDNESDAY);
    }

    @UiHandler("thu")
    public void onThu(final ClickEvent e) {
        toggleDay(Day.THURSDAY);
    }

    @UiHandler("fri")
    public void onFri(final ClickEvent e) {
        toggleDay(Day.FRIDAY);
    }

    @UiHandler("sat")
    public void onSat(final ClickEvent e) {
        toggleDay(Day.SATURDAY);
    }

    @UiHandler("sun")
    public void onSun(final ClickEvent e) {
        toggleDay(Day.SUNDAY);
    }

    private void toggleDay(final Day day) {
        if (value.contains(day)) {
            value.remove(day);
        } else {
            value.add(day);
        }
        updateStyles();
    }

    private void updateStyles() {
        updateStyle(mon, Day.MONDAY);
        updateStyle(tue, Day.TUESDAY);
        updateStyle(wed, Day.WEDNESDAY);
        updateStyle(thu, Day.THURSDAY);
        updateStyle(fri, Day.FRIDAY);
        updateStyle(sat, Day.SATURDAY);
        updateStyle(sun, Day.SUNDAY);
    }

    private void updateStyle(final Label label, final Day day) {
        if (value.contains(day)) {
            label.getElement().addClassName("day-selected");
        } else {
            label.getElement().removeClassName("day-selected");
        }
    }

    public interface Binder extends UiBinder<Widget, DaysWidget> {

    }
}
