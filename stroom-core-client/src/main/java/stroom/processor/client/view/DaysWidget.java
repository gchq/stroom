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
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashSet;
import java.util.Set;

public final class DaysWidget extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final Widget widget;

    @UiField
    CustomCheckBox mon;
    @UiField
    CustomCheckBox tue;
    @UiField
    CustomCheckBox wed;
    @UiField
    CustomCheckBox thu;
    @UiField
    CustomCheckBox fri;
    @UiField
    CustomCheckBox sat;
    @UiField
    CustomCheckBox sun;

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
        update();
    }

    @UiHandler("mon")
    public void onMon(final ValueChangeEvent<Boolean> e) {
        toggleDay(Day.MONDAY);
    }

    @UiHandler("tue")
    public void onTue(final ValueChangeEvent<Boolean> e) {
        toggleDay(Day.TUESDAY);
    }

    @UiHandler("wed")
    public void onWed(final ValueChangeEvent<Boolean> e) {
        toggleDay(Day.WEDNESDAY);
    }

    @UiHandler("thu")
    public void onThu(final ValueChangeEvent<Boolean> e) {
        toggleDay(Day.THURSDAY);
    }

    @UiHandler("fri")
    public void onFri(final ValueChangeEvent<Boolean> e) {
        toggleDay(Day.FRIDAY);
    }

    @UiHandler("sat")
    public void onSat(final ValueChangeEvent<Boolean> e) {
        toggleDay(Day.SATURDAY);
    }

    @UiHandler("sun")
    public void onSun(final ValueChangeEvent<Boolean> e) {
        toggleDay(Day.SUNDAY);
    }

    private void toggleDay(final Day day) {
        if (value.contains(day)) {
            value.remove(day);
        } else {
            value.add(day);
        }
        update();
    }

    private void update() {
        update(mon, Day.MONDAY);
        update(tue, Day.TUESDAY);
        update(wed, Day.WEDNESDAY);
        update(thu, Day.THURSDAY);
        update(fri, Day.FRIDAY);
        update(sat, Day.SATURDAY);
        update(sun, Day.SUNDAY);
    }

    private void update(final CustomCheckBox checkBox, final Day day) {
        checkBox.setValue(value.contains(day));
    }

    public interface Binder extends UiBinder<Widget, DaysWidget> {

    }
}
