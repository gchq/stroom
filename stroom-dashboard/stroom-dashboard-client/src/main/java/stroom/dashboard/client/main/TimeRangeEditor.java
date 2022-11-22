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

package stroom.dashboard.client.main;

import stroom.item.client.ItemListBox;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.TimeRange;
import stroom.query.client.ExpressionUiHandlers;
import stroom.widget.customdatebox.client.MyDateBox;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.InputEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TimeRangeEditor extends Composite {

    private static final int NARROW_VALUE = 175;

    private final FlowPanel layout;
    private final ItemListBox<Condition> conditionListBox;
    private final Label andLabel;
    private final MyDateBox date;
    private final MyDateBox dateFrom;
    private final MyDateBox dateTo;
    private final List<HandlerRegistration> registrations = new ArrayList<>();
    private ExpressionUiHandlers uiHandlers;
    private TimeRange timeRange;
    private boolean reading;

    private final List<Widget> activeWidgets = new ArrayList<>();

    public TimeRangeEditor() {
        conditionListBox = createConditionBox();

        andLabel = createLabel(" and ");
        andLabel.setVisible(false);

        date = createDateBox(NARROW_VALUE);
        date.setVisible(false);
        dateFrom = createDateBox(NARROW_VALUE);
        dateFrom.setVisible(false);
        dateTo = createDateBox(NARROW_VALUE);
        dateTo.setVisible(false);

        layout = new FlowPanel();
        layout.add(conditionListBox);
        layout.add(date);
        layout.add(dateFrom);
        layout.add(andLabel);
        layout.add(dateTo);

        layout.setStyleName("termEditor-layout");
        initWidget(layout);
    }

    public void setUtc(final boolean utc) {
        date.setUtc(utc);
        dateFrom.setUtc(utc);
        dateTo.setUtc(utc);
    }

    public void read(final TimeRange timeRange) {
        this.timeRange = timeRange;
        reading = true;
        conditionListBox.setSelectedItem(null);
        changeField(timeRange, false);
    }

    public TimeRange write() {
        final Condition condition = conditionListBox.getSelectedItem();

        final String from = normalise(dateFrom.getValue());
        final String to = normalise(dateTo.getValue());

        String name = TimeRanges.ALL_TIME.getName();
        if (from != null && to != null) {
            name = "Between " + from + " and " + to;
        } else if (from != null) {
            name = "After " + from;
        } else if (to != null) {
            name = "Before " + to;
        }

        final TimeRange range = new TimeRange(name, condition, from, to);
        // See if this is a quick select range.
        for (final TimeRange timeRange : TimeRanges.ALL_RANGES) {
            if (timeRange.equals(range)) {
                return timeRange;
            }
        }
        return range;
    }

    private String normalise(final String string) {
        if (string != null && string.trim().length() > 0) {
            return string.trim();
        }
        return null;
    }

    private void changeField(final TimeRange timeRange, final boolean useDefaultCondition) {
        final List<Condition> conditions = getConditions();

        Condition selected = conditionListBox.getSelectedItem();
        conditionListBox.clear();
        conditionListBox.addItems(conditions);

        if (selected == null || !conditions.contains(selected)) {
            if (!useDefaultCondition && timeRange.getCondition() != null &&
                    conditions.contains(timeRange.getCondition())) {
                selected = timeRange.getCondition();
            } else if (conditions.contains(Condition.IS_DOC_REF)) {
                selected = Condition.IS_DOC_REF;
            } else if (conditions.contains(Condition.EQUALS)) {
                selected = Condition.EQUALS;
            } else {
                selected = conditions.get(0);
            }
        }

        conditionListBox.setSelectedItem(selected);
        changeCondition(selected);
    }

    private List<Condition> getConditions() {
        return Arrays.asList(
                Condition.EQUALS,
                Condition.GREATER_THAN,
                Condition.GREATER_THAN_OR_EQUAL_TO,
                Condition.LESS_THAN,
                Condition.LESS_THAN_OR_EQUAL_TO,
                Condition.BETWEEN
        );
    }

    private void changeCondition(final Condition condition) {
        switch (condition) {
            case EQUALS:
                enterDateMode();
                break;
            case BETWEEN:
                enterDateRangeMode();
                break;
            case LESS_THAN:
                enterDateMode();
                break;
            case LESS_THAN_OR_EQUAL_TO:
                enterDateMode();
                break;
            case GREATER_THAN:
                enterDateMode();
                break;
            case GREATER_THAN_OR_EQUAL_TO:
                enterDateMode();
                break;
        }
    }

    private void enterDateMode() {
        setActiveWidgets(date);
        updateDateBoxes();
    }

    private void enterDateRangeMode() {
        setActiveWidgets(dateFrom, andLabel, dateTo);
        updateDateBoxes();
    }

    private void setActiveWidgets(final Widget... widgets) {
        for (final Widget widget : activeWidgets) {
            widget.setVisible(false);
        }
        activeWidgets.clear();
        for (final Widget widget : widgets) {
            activeWidgets.add(widget);
            widget.setVisible(true);
        }
    }

    private void updateDateBoxes() {
        dateFrom.setValue(timeRange.getFrom());
        dateTo.setValue(timeRange.getTo());
    }

    private void bind() {
        final KeyDownHandler keyDownHandler = event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                if (uiHandlers != null) {
                    uiHandlers.search();
                }
            }
        };
        registerHandler(date.addKeyDownHandler(keyDownHandler));
        registerHandler(dateFrom.addKeyDownHandler(keyDownHandler));
        registerHandler(dateTo.addKeyDownHandler(keyDownHandler));

        registerHandler(date.addDomHandler(e -> fireDirty(), InputEvent.getType()));
        registerHandler(dateFrom.addDomHandler(e -> fireDirty(), InputEvent.getType()));
        registerHandler(dateTo.addDomHandler(e -> fireDirty(), InputEvent.getType()));

        registerHandler(date.addValueChangeHandler(event -> fireDirty()));
        registerHandler(dateFrom.addValueChangeHandler(event -> fireDirty()));
        registerHandler(dateTo.addValueChangeHandler(event -> fireDirty()));
    }

    private void registerHandler(final HandlerRegistration handlerRegistration) {
        registrations.add(handlerRegistration);
    }

    private ItemListBox<Condition> createConditionBox() {
        final ItemListBox<Condition> conditionListBox = new ItemListBox<>();
        fixStyle(conditionListBox, 120);
        return conditionListBox;
    }

    private MyDateBox createDateBox(final int width) {
        final MyDateBox dateBox = new MyDateBox();
        fixStyle(dateBox, width);
        return dateBox;
    }

    private Label createLabel(final String text) {
        final Label label = new Label(text, false);
        label.addStyleName("termEditor-label");
        return label;
    }

    private void fixStyle(final Widget widget, final int width) {
        widget.addStyleName("termEditor-item");
        widget.getElement().getStyle().setWidth(width, Unit.PX);
    }

    private void fireDirty() {
        if (!reading) {
            if (uiHandlers != null) {
                uiHandlers.fireDirty();
            }
        }
    }
}
