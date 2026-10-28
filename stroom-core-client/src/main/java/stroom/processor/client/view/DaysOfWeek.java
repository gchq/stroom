/*
 * Copyright 2026 Crown Copyright
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
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Widget;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/// The days of the week, a tick box each (`days-of-week.css`, markup `DaysOfWeek.ui.xml`).
public final class DaysOfWeek extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TickBox mon;
    @UiField
    TickBox tue;
    @UiField
    TickBox wed;
    @UiField
    TickBox thu;
    @UiField
    TickBox fri;
    @UiField
    TickBox sat;
    @UiField
    TickBox sun;

    private final Set<Day> value = new HashSet<>();
    private Consumer<Days> onChange;

    public DaysOfWeek() {
        initWidget(BINDER.createAndBindUi(this));
        listen(mon, Day.MONDAY);
        listen(tue, Day.TUESDAY);
        listen(wed, Day.WEDNESDAY);
        listen(thu, Day.THURSDAY);
        listen(fri, Day.FRIDAY);
        listen(sat, Day.SATURDAY);
        listen(sun, Day.SUNDAY);
    }

    private void listen(final TickBox checkBox, final Day day) {
        checkBox.setOnChange(ticked -> toggleDay(day));
    }

    /// @param onChange Told the days chosen when the user ticks or unticks one, or null for nothing.
    public void setOnChange(final Consumer<Days> onChange) {
        this.onChange = onChange;
    }

    /// Makes the days read only: they can be read and stay in the tab order, but can't be ticked
    /// or unticked. They look like the normal tick boxes with grey ticks.
    ///
    /// @param readOnly Whether the days are read only.
    public void setReadOnly(final boolean readOnly) {
        for (final TickBox day : days()) {
            day.setReadOnly(readOnly);
        }
    }

    /// Enables or disables every day.
    ///
    /// @param enabled Whether the days can be changed.
    public void setEnabled(final boolean enabled) {
        for (final TickBox day : days()) {
            day.setEnabled(enabled);
        }
    }

    private List<TickBox> days() {
        return List.of(mon, tue, wed, thu, fri, sat, sun);
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

    private void toggleDay(final Day day) {
        if (value.contains(day)) {
            value.remove(day);
        } else {
            value.add(day);
        }
        update();
        if (onChange != null) {
            onChange.accept(getValue());
        }
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

    private void update(final TickBox checkBox, final Day day) {
        checkBox.setValue(value.contains(day));
    }

    public interface Binder extends UiBinder<Widget, DaysOfWeek> {

    }
}
