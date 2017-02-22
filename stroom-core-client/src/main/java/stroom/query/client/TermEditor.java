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

package stroom.query.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;
import stroom.datasource.api.DataSourceField;
import stroom.datasource.api.DataSourceField.DataSourceFieldType;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.item.client.ItemListBox;
import stroom.query.api.DocRef;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.EqualsUtil;
import stroom.widget.customdatebox.client.MyDateBox;

import java.util.ArrayList;
import java.util.List;

public class TermEditor extends Composite {
    private static final int WIDE_VALUE = 400;
    private static final int NARROW_VALUE = 175;

    private static Resources resources;

    private final FlowPanel layout;
    private final ItemListBox<DataSourceField> fieldListBox;
    private final ItemListBox<Condition> conditionListBox;
    private final Label andLabel;
    private final TextBox value;
    private final TextBox valueFrom;
    private final TextBox valueTo;
    private final MyDateBox date;
    private final MyDateBox dateFrom;
    private final MyDateBox dateTo;
    private final Widget dictionaryWidget;
    private final EntityDropDownPresenter dictionaryPresenter;
    private final List<Widget> activeWidgets = new ArrayList<>();
    private final List<HandlerRegistration> registrations = new ArrayList<>();

    private ExpressionTerm term;
    private List<DataSourceField> indexFields;
    private boolean reading;
    private boolean editing;
    private ExpressionUiHandlers uiHandlers;

    public TermEditor(final EntityDropDownPresenter dictionaryPresenter) {
        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.style().ensureInjected();
        }

        this.dictionaryPresenter = dictionaryPresenter;

        if (dictionaryPresenter != null) {
            dictionaryWidget = dictionaryPresenter.getWidget();
        } else {
            dictionaryWidget = new Label();
        }

        fixStyle(dictionaryWidget, 200);
        dictionaryWidget.getElement().getStyle().setMarginTop(1, Unit.PX);
        dictionaryWidget.setVisible(false);

        fieldListBox = createFieldBox();
        conditionListBox = createConditionBox();

        andLabel = createLabel(" and ");
        andLabel.setVisible(false);

        value = createTextBox(WIDE_VALUE);
        value.setVisible(false);
        valueFrom = createTextBox(NARROW_VALUE);
        valueFrom.setVisible(false);
        valueTo = createTextBox(NARROW_VALUE);
        valueTo.setVisible(false);

        date = createDateBox(NARROW_VALUE);
        date.setVisible(false);
        dateFrom = createDateBox(NARROW_VALUE);
        dateFrom.setVisible(false);
        dateTo = createDateBox(NARROW_VALUE);
        dateTo.setVisible(false);

        layout = new FlowPanel();
        layout.add(fieldListBox);
        layout.add(conditionListBox);
        layout.add(value);
        layout.add(valueFrom);
        layout.add(date);
        layout.add(dateFrom);
        layout.add(andLabel);
        layout.add(valueTo);
        layout.add(dateTo);
        layout.add(dictionaryWidget);

        layout.setVisible(false);
        layout.setStyleName(resources.style().layout());
        initWidget(layout);
    }

    public void setFields(final List<DataSourceField> indexFields) {
        this.indexFields = indexFields;
        fieldListBox.clear();
        if (indexFields != null) {
            fieldListBox.addItems(indexFields);
        }
    }

    public void startEdit(final ExpressionTerm term) {
        if (!editing) {
            this.term = term;

            read(term);

            Scheduler.get().scheduleDeferred(new ScheduledCommand() {
                @Override
                public void execute() {
                    bind();
                    layout.setVisible(true);
                }
            });

            editing = true;
        }
    }

    public void endEdit() {
        if (editing) {
            write(term);
            unbind();
            layout.setVisible(false);
            editing = false;
        }
    }

    private void read(final ExpressionTerm term) {
        reading = true;

        // Select the current value.
        DataSourceField termField = null;
        if (indexFields != null && indexFields.size() > 0) {
            termField = indexFields.get(0);
            for (final DataSourceField field : indexFields) {
                if (field.getName().equals(term.getField())) {
                    termField = field;
                    break;
                }
            }
        }

        fieldListBox.setSelectedItem(termField);
        changeField(termField);

        reading = false;
    }

    private void write(final ExpressionTerm term) {
        if (fieldListBox.getSelectedItem() != null && conditionListBox.getSelectedItem() != null) {
            DocRef dictionaryRef = null;

            term.setField(fieldListBox.getSelectedItem().getName());
            term.setCondition(conditionListBox.getSelectedItem());

            final StringBuilder sb = new StringBuilder();
            for (final Widget widget : activeWidgets) {
                if (widget instanceof TextBox) {
                    sb.append(((TextBox) widget).getText());
                    sb.append(",");
                } else if (widget instanceof MyDateBox) {
                    sb.append(((MyDateBox) widget).getText());
                    sb.append(",");
                } else if (widget.equals(dictionaryWidget)) {
                    if (dictionaryPresenter != null) {
                        dictionaryRef = dictionaryPresenter.getSelectedEntityReference();
                        if (dictionaryRef != null) {
                            sb.append(dictionaryRef.getName());
                        }
                    }
                    sb.append(",");
                }
            }

            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }

            term.setValue(sb.toString());
            if (dictionaryRef == null) {
                term.setDictionary(null);
            } else {
                term.setDictionary(dictionaryRef);
            }
        }
    }

    private void changeField(final DataSourceField field) {
        final List<Condition> conditions = getConditions(field);

        conditionListBox.clear();
        conditionListBox.addItems(conditions);

        // Set the condition.
        Condition selected = conditions.get(0);
        if (term.getCondition() != null && conditions.contains(term.getCondition())) {
            selected = term.getCondition();
        }

        conditionListBox.setSelectedItem(selected);
        changeCondition(selected);
    }

    private List<Condition> getConditions(final DataSourceField field) {
        final List<Condition> conditions = new ArrayList<>();

        if (field == null) {
            conditions.add(Condition.CONTAINS);
            conditions.add(Condition.IN);
            conditions.add(Condition.IN_DICTIONARY);

        } else {
            conditions.addAll(field.getConditions());
        }

        return conditions;
    }

    private void changeCondition(final Condition condition) {
        DataSourceFieldType indexFieldType = null;
        if (fieldListBox.getSelectedItem() != null) {
            indexFieldType = fieldListBox.getSelectedItem().getType();
        }

        if (indexFieldType == null) {
            setActiveWidgets();

        } else {
            switch (condition) {
                case EQUALS:
                    if (DataSourceFieldType.DATE_FIELD.equals(indexFieldType)) {
                        enterDateMode();
                    } else {
                        enterTextMode();
                    }
                    break;
                case CONTAINS:
                    enterTextMode();
                    break;
                case IN:
                    enterTextMode();
                    break;
                case BETWEEN:
                    if (DataSourceFieldType.DATE_FIELD.equals(indexFieldType)) {
                        enterDateRangeMode();
                    } else {
                        enterTextRangeMode();
                    }
                    break;
                case LESS_THAN:
                    if (DataSourceFieldType.DATE_FIELD.equals(indexFieldType)) {
                        enterDateMode();
                    } else {
                        enterTextMode();
                    }
                    break;
                case LESS_THAN_OR_EQUAL_TO:
                    if (DataSourceFieldType.DATE_FIELD.equals(indexFieldType)) {
                        enterDateMode();
                    } else {
                        enterTextMode();
                    }
                    break;
                case GREATER_THAN:
                    if (DataSourceFieldType.DATE_FIELD.equals(indexFieldType)) {
                        enterDateMode();
                    } else {
                        enterTextMode();
                    }
                    break;
                case GREATER_THAN_OR_EQUAL_TO:
                    if (DataSourceFieldType.DATE_FIELD.equals(indexFieldType)) {
                        enterDateMode();
                    } else {
                        enterTextMode();
                    }
                    break;
                case IN_DICTIONARY:
                    enterDictionaryMode();
                    break;
            }
        }
    }

    private void enterTextMode() {
        setActiveWidgets(value);
        value.setText(term.getValue());
    }

    private void enterTextRangeMode() {
        setActiveWidgets(valueFrom, andLabel, valueTo);
        updateTextBoxes();
    }

    private void enterDateMode() {
        setActiveWidgets(date);
        updateDateBoxes();
    }

    private void enterDateRangeMode() {
        setActiveWidgets(dateFrom, andLabel, dateTo);
        updateDateBoxes();
    }

    private void enterDictionaryMode() {
        setActiveWidgets(dictionaryWidget);

        if (dictionaryPresenter != null) {
            dictionaryPresenter.setSelectedEntityReference(term.getDictionary());
        }
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

    private void updateTextBoxes() {
        if (term.getValue() != null) {
            // Set the current data.
            final String[] vals = term.getValue().split(",");
            if (vals.length > 0) {
                if (value != null) {
                    value.setValue(vals[0]);
                }
                if (valueFrom != null) {
                    valueFrom.setValue(vals[0]);
                }
            }
            if (vals.length > 1) {
                if (valueTo != null) {
                    valueTo.setValue(vals[1]);
                }
            }
        }
    }

    private void updateDateBoxes() {
        if (term.getValue() != null) {
            // Set the current data.
            final String[] vals = term.getValue().split(",");
            if (vals.length > 0) {
                if (date != null) {
                    date.setValue(vals[0]);
                }
                if (dateFrom != null) {
                    dateFrom.setValue(vals[0]);
                }
            }
            if (vals.length > 1) {
                if (dateTo != null) {
                    dateTo.setValue(vals[1]);
                }
            }
        }


//        if (term.getValue() != null) {
//            // Set the current data.
//            final String[] vals = term.getValue().split(",");
//            if (vals.length > 0) {
//                if (date != null) {
//                    final Date d = date.getFormat().parse(date, vals[0], false);
//                    if (d != null) {
//                        date.setValue(d);
//                    }
//                }
//                if (dateFrom != null) {
//                    final Date d = dateFrom.getFormat().parse(dateFrom, vals[0], false);
//                    if (d != null) {
//                        dateFrom.setValue(d);
//                    }
//                }
//            }
//            if (vals.length > 1) {
//                if (dateTo != null) {
//                    final Date d = dateTo.getFormat().parse(dateTo, vals[1], false);
//                    if (d != null) {
//                        dateTo.setValue(d);
//                    }
//                }
//            }
//        }
    }

    private void bind() {
        final KeyDownHandler keyDownHandler = new KeyDownHandler() {
            @Override
            public void onKeyDown(final KeyDownEvent event) {
                if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                    if (uiHandlers != null) {
                        uiHandlers.search();
                    }
                }
            }
        };
        final KeyPressHandler keyPressHandler = new KeyPressHandler() {
            @Override
            public void onKeyPress(final KeyPressEvent event) {
                fireDirty();
            }
        };
//        final ValueChangeHandler<Date> dateChangeHandler = new ValueChangeHandler<Date>() {
//            @Override
//            public void onValueChange(final ValueChangeEvent<Date> event) {
//                fireDirty();
//            }
//        };
//        final ValueChangeHandler<String> changeHandler -> {fireDirty()};

        registerHandler(value.addKeyDownHandler(keyDownHandler));
        registerHandler(value.addKeyPressHandler(keyPressHandler));
        registerHandler(valueFrom.addKeyDownHandler(keyDownHandler));
        registerHandler(valueFrom.addKeyPressHandler(keyPressHandler));
        registerHandler(valueTo.addKeyDownHandler(keyDownHandler));
        registerHandler(valueTo.addKeyPressHandler(keyPressHandler));

        registerHandler(date.addKeyDownHandler(keyDownHandler));
        registerHandler(date.addKeyPressHandler(keyPressHandler));
        registerHandler(dateFrom.addKeyDownHandler(keyDownHandler));
        registerHandler(dateFrom.addKeyPressHandler(keyPressHandler));
        registerHandler(dateTo.addKeyDownHandler(keyDownHandler));
        registerHandler(dateTo.addKeyPressHandler(keyPressHandler));

        registerHandler(date.addValueChangeHandler(event -> fireDirty()));
        registerHandler(dateFrom.addValueChangeHandler(event -> fireDirty()));
        registerHandler(dateTo.addValueChangeHandler(event -> fireDirty()));


//        registerHandler(date.addValueChangeHandler(dateChangeHandler));
//        registerHandler(dateFrom.addValueChangeHandler(dateChangeHandler));
//        registerHandler(dateTo.addValueChangeHandler(dateChangeHandler));

        if (dictionaryPresenter != null) {
            registerHandler(dictionaryPresenter.addDataSelectionHandler(event -> {
                final DocRef selection = dictionaryPresenter.getSelectedEntityReference();
                if (!EqualsUtil.isEquals(term.getDictionary(), selection)) {
                    write(term);
                    fireDirty();
                }
            }));
        }

        registerHandler(fieldListBox.addSelectionHandler(event -> {
            if (!reading) {
                write(term);
                changeField(event.getSelectedItem());
                fireDirty();
            }
        }));
        registerHandler(conditionListBox.addSelectionHandler(event -> {
            if (!reading) {
                write(term);
                changeCondition(event.getSelectedItem());
                fireDirty();
            }
        }));
    }

    private void unbind() {
        for (final HandlerRegistration handlerRegistration : registrations) {
            handlerRegistration.removeHandler();
        }
        registrations.clear();
    }

    private void registerHandler(final HandlerRegistration handlerRegistration) {
        registrations.add(handlerRegistration);
    }

    private ItemListBox<DataSourceField> createFieldBox() {
        final ItemListBox<DataSourceField> fieldListBox = new ItemListBox<>();
        fixStyle(fieldListBox, 160);
        return fieldListBox;
    }

    private ItemListBox<Condition> createConditionBox() {
        final ItemListBox<Condition> conditionListBox = new ItemListBox<>();
        fixStyle(conditionListBox, 100);
        return conditionListBox;
    }

    private TextBox createTextBox(final int width) {
        final TextBox textBox = new TextBox();
        fixStyle(textBox, width);
        return textBox;
    }

    private MyDateBox createDateBox(final int width) {
        final MyDateBox dateBox = new MyDateBox();
        fixStyle(dateBox, width);
        return dateBox;
    }

    private Label createLabel(final String text) {
        final Label label = new Label(text, false);
        label.addStyleName(resources.style().label());
        return label;
    }

    private void fixStyle(final Widget widget, final int width) {
        widget.addStyleName(resources.style().item());
        widget.getElement().getStyle().setWidth(width, Unit.PX);
    }

    private void fireDirty() {
        if (!reading) {
            if (uiHandlers != null) {
                uiHandlers.fireDirty();
            }
        }
    }

    public void setUiHandlers(final ExpressionUiHandlers uiHandlers) {
        this.uiHandlers = uiHandlers;
    }

    public interface Style extends CssResource {
        String layout();

        String item();

        String label();
    }

    public interface Resources extends ClientBundle {
        @Source("TermEditor.css")
        Style style();
    }
}
