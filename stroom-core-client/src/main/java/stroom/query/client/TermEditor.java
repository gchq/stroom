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

package stroom.query.client;

import stroom.datasource.api.v2.ConditionSet;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.QueryField;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.item.client.BaseSelectionBox;
import stroom.item.client.SelectionBox;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.client.presenter.FieldInfoSelectionItem;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.StringUtil;
import stroom.util.shared.UserRef;
import stroom.widget.customdatebox.client.MyDateBox;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;

public class TermEditor extends Composite {

    private static final String ITEM_CLASS_NAME = "termEditor-item";
    private static final String DROPDOWN_CLASS_NAME = "dropdown";
    private static final String WIDE_CLASS_NAME = "wide";
    private static final String NARROW_CLASS_NAME = "narrow";

    private final FlowPanel layout;
    private final BaseSelectionBox<QueryField, FieldInfoSelectionItem> fieldListBox;
    private final SelectionBox<Condition> conditionListBox;
    private final Label andLabel;
    private final SuggestBox value;
    private final SuggestBox valueFrom;
    private final SuggestBox valueTo;
    private final MyDateBox date;
    private final MyDateBox dateFrom;
    private final MyDateBox dateTo;
    private final Widget docRefWidget;
    private final Widget userRefWidget;
    private final Label fieldTypeLabel;
    private final DocSelectionBoxPresenter docSelectionBoxPresenter;
    private final UserRefSelectionBoxPresenter userRefSelectionBoxPresenter;
    private final List<Widget> activeWidgets = new ArrayList<>();
    private final List<HandlerRegistration> registrations = new ArrayList<>();

    private Term term;
    private boolean reading;
    private boolean editing;
    private ExpressionUiHandlers uiHandlers;

    private final AsyncSuggestOracle suggestOracle;
    private FieldSelectionListModel fieldSelectionListModel;

    public TermEditor(final Provider<DocSelectionBoxPresenter> docRefProvider,
                      final Provider<UserRefSelectionBoxPresenter> userRefProvider) {
        final DocSelectionBoxPresenter docRefPresenter = docRefProvider.get();
        docRefPresenter.setRequiredPermissions(DocumentPermission.USE);
        docRefPresenter.getWidget().getElement().getStyle().setMargin(0, Unit.PX);
        suggestOracle = new AsyncSuggestOracle(docRefPresenter);

        this.docSelectionBoxPresenter = docRefPresenter;
        this.userRefSelectionBoxPresenter = userRefProvider.get();
        if (docSelectionBoxPresenter != null) {
            docRefWidget = docSelectionBoxPresenter.getWidget();
        } else {
            docRefWidget = new Label();
        }

        docRefWidget.addStyleName(ITEM_CLASS_NAME);
        docRefWidget.addStyleName("docRef");
        docRefWidget.setVisible(false);

        if (userRefSelectionBoxPresenter != null) {
            userRefWidget = userRefSelectionBoxPresenter.getWidget();
        } else {
            userRefWidget = new Label();
        }

        userRefWidget.addStyleName(ITEM_CLASS_NAME);
        userRefWidget.addStyleName("docRef");
        userRefWidget.setVisible(false);

        fieldListBox = createFieldBox();
        conditionListBox = createConditionBox();

        andLabel = createLabel(" and ");
        andLabel.setVisible(false);

        value = createTextBox(WIDE_CLASS_NAME);
        value.setVisible(false);
        valueFrom = createTextBox(NARROW_CLASS_NAME);
        valueFrom.setVisible(false);
        valueTo = createTextBox(NARROW_CLASS_NAME);
        valueTo.setVisible(false);

        date = createDateBox(NARROW_CLASS_NAME);
        date.setVisible(false);
        dateFrom = createDateBox(NARROW_CLASS_NAME);
        dateFrom.setVisible(false);
        dateTo = createDateBox(NARROW_CLASS_NAME);
        dateTo.setVisible(false);

        fieldTypeLabel = createFieldTypeLabel();

        final FlowPanel inner = new FlowPanel();
        inner.setStyleName("termEditor-inner");
        inner.add(fieldListBox);
        inner.add(conditionListBox);
        inner.add(value);
        inner.add(valueFrom);
        inner.add(date);
        inner.add(dateFrom);
        inner.add(andLabel);
        inner.add(valueTo);
        inner.add(dateTo);
        inner.add(docRefWidget);
        inner.add(userRefWidget);

        layout = new FlowPanel();
        layout.add(inner);
        layout.add(fieldTypeLabel);
        layout.setVisible(false);
        layout.setStyleName("termEditor-outer");

        initWidget(layout);
    }

    public void setUtc(final boolean utc) {
        date.setUtc(utc);
        dateFrom.setUtc(utc);
        dateTo.setUtc(utc);
    }

    public void init(final RestFactory restFactory,
                     final DocRef dataSource,
                     final FieldSelectionListModel fieldSelectionListModel) {
        suggestOracle.setRestFactory(restFactory);
        suggestOracle.setDataSource(dataSource);

        this.fieldSelectionListModel = fieldSelectionListModel;
        fieldListBox.setModel(fieldSelectionListModel);
    }

    public void startEdit(final Term term) {
        if (!editing) {
            this.term = term;

            read(term);

            Scheduler.get().scheduleDeferred(() -> {
                bind();
                layout.setVisible(true);
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

    private void read(final Term term) {
        reading = true;

        // Select the current value.
        conditionListBox.setValue(null);
        changeField(null, null, false);
        fieldSelectionListModel.findFieldByName(term.getField(), fieldInfo -> {
            fieldListBox.setValue(fieldInfo);
            changeField(fieldInfo, term.getCondition(), false);
        });

        reading = false;
    }

    private void write(final Term term) {
        final QueryField selectedField = fieldListBox.getValue();
        if (selectedField != null && conditionListBox.getValue() != null) {
            DocRef docRef = null;

            term.setField(selectedField.getFldName());
            term.setCondition(conditionListBox.getValue());

            final StringBuilder sb = new StringBuilder();
            for (final Widget widget : activeWidgets) {
                if (widget instanceof SuggestBox) {
                    sb.append(((SuggestBox) widget).getText());
                    sb.append(",");
                } else if (widget instanceof MyDateBox) {
                    sb.append(((MyDateBox) widget).getValue());
                    sb.append(",");
                } else if (widget.equals(docRefWidget)) {
                    if (docSelectionBoxPresenter != null) {
                        docRef = docSelectionBoxPresenter.getSelectedEntityReference();
                        if (docRef != null) {
                            sb.append(docRef.getName());
                        }
                    }
                    sb.append(",");
                } else if (widget.equals(userRefWidget)) {
                    if (userRefSelectionBoxPresenter != null) {
                        final UserRef userRef = userRefSelectionBoxPresenter.getSelected();
                        if (userRef != null) {
                            docRef = UserDocRefUtil.createDocRef(userRef);
                            sb.append(docRef.getName());
                        }
                    }
                    sb.append(",");
                }
            }

            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
            }

            term.setValue(StringUtil.trimWhitespace(sb.toString()));
            term.setDocRef(docRef);
        }
    }

    private void changeField(final QueryField field, final Condition condition, final boolean useDefaultCondition) {
        suggestOracle.setField(field);
        final List<Condition> conditions = getConditions(field);

        Condition selected = condition;
        conditionListBox.clear();
        conditionListBox.addItems(conditions);

        if (selected == null || !conditions.contains(selected)) {
            if (!useDefaultCondition && term.getCondition() != null && conditions.contains(term.getCondition())) {
                selected = term.getCondition();
            } else if (conditions.contains(Condition.IS_DOC_REF)) {
                selected = Condition.IS_DOC_REF;
            } else if (conditions.contains(Condition.IS_USER_REF)) {
                selected = Condition.IS_USER_REF;
            } else if (conditions.contains(Condition.USER_HAS_PERM)) {
                selected = Condition.USER_HAS_PERM;
            } else if (conditions.contains(Condition.EQUALS)) {
                selected = Condition.EQUALS;
            } else {
                selected = conditions.get(0);
            }
        }

        conditionListBox.setValue(selected);
        changeCondition(field, selected);

        if (field != null && field.getFldType() != null) {
            fieldTypeLabel.setText(field.getFldType().getShortTypeName());
            fieldTypeLabel.setTitle(field.getFldType().getDescription());
            fieldTypeLabel.setVisible(true);
        } else {
            fieldTypeLabel.setVisible(false);
        }
    }

    private List<Condition> getConditions(final QueryField field) {
        ConditionSet conditions;
        if (field != null && field.getConditionSet() != null) {
            conditions = field.getConditionSet();

        } else {
            FieldType fieldType = null;
            if (field != null) {
                fieldType = field.getFldType();
            }
            conditions = ConditionSet.getUiDefaultConditions(fieldType);
        }

        return conditions.getConditionList();
    }

    private void changeCondition(final QueryField field,
                                 final Condition condition) {
        final QueryField selectedField = fieldListBox.getValue();
        FieldType indexFieldType = null;
        if (selectedField != null && selectedField.getFldType() != null) {
            indexFieldType = selectedField.getFldType();
        }

        if (indexFieldType == null) {
            setActiveWidgets();

        } else {
            switch (condition) {
                case EQUALS:
                case NOT_EQUALS:
                case LESS_THAN:
                case LESS_THAN_OR_EQUAL_TO:
                case GREATER_THAN:
                case GREATER_THAN_OR_EQUAL_TO:
                    if (FieldType.DATE.equals(indexFieldType)) {
                        enterDateMode();
                    } else {
                        enterTextMode();
                    }
                    break;
//                case CONTAINS:
//                    enterTextMode();
//                    break;
                case IN:
                    enterTextMode();
                    break;
                case BETWEEN:
                    if (FieldType.DATE.equals(indexFieldType)) {
                        enterDateRangeMode();
                    } else {
                        enterTextRangeMode();
                    }
                    break;
                case IN_DICTIONARY:
                case IN_FOLDER:
                case IS_DOC_REF:
                    enterDocRefMode(field, condition);
                    break;
                case OF_DOC_REF:
                    enterDocRefMode(field, condition);
                    break;
                case IS_USER_REF:
                    enterUserRefMode(field, condition);
                    break;
                case USER_HAS_PERM:
                    enterUserRefMode(field, condition);
                    break;
                case USER_HAS_OWNER:
                    enterUserRefMode(field, condition);
                    break;
                case USER_HAS_DELETE:
                    enterUserRefMode(field, condition);
                    break;
                case USER_HAS_EDIT:
                    enterUserRefMode(field, condition);
                    break;
                case USER_HAS_VIEW:
                    enterUserRefMode(field, condition);
                    break;
                case USER_HAS_USE:
                    enterUserRefMode(field, condition);
                    break;
                case MATCHES_REGEX:
                    enterTextMode();
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

    private void enterDocRefMode(final QueryField field, final Condition condition) {
        setActiveWidgets(docRefWidget);

        if (docSelectionBoxPresenter != null) {
            docSelectionBoxPresenter.setAllowFolderSelection(false);
            if (Condition.IN_DICTIONARY.equals(condition)) {
                docSelectionBoxPresenter.setIncludedTypes("Dictionary");
            } else if (Condition.IN_FOLDER.equals(condition) || Condition.OF_DOC_REF.equals(condition)) {
                docSelectionBoxPresenter.setIncludedTypes("Folder");
                docSelectionBoxPresenter.setAllowFolderSelection(true);
            } else if (FieldType.DOC_REF.equals(field.getFldType())) {
                if (field.getDocRefType() != null) {
                    docSelectionBoxPresenter.setIncludedTypes(field.getDocRefType());
                } else {
                    docSelectionBoxPresenter.setAllowFolderSelection(true);
                }
            }
            docSelectionBoxPresenter.setSelectedEntityReference(term.getDocRef());
        }
    }

    private void enterUserRefMode(final QueryField field, final Condition condition) {
        setActiveWidgets(userRefWidget);

        if (userRefSelectionBoxPresenter != null) {
            userRefSelectionBoxPresenter.setSelected(UserDocRefUtil.createUserRef(term.getDocRef()));
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
    }

    private void bind() {
        final KeyDownHandler keyDownHandler = event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                if (uiHandlers != null) {
                    uiHandlers.search();
                }
            }
        };

        registerHandler(value.addKeyDownHandler(keyDownHandler));
        registerHandler(valueFrom.addKeyDownHandler(keyDownHandler));
        registerHandler(valueTo.addKeyDownHandler(keyDownHandler));

        registerHandler(date.addKeyDownHandler(keyDownHandler));
        registerHandler(dateFrom.addKeyDownHandler(keyDownHandler));
        registerHandler(dateTo.addKeyDownHandler(keyDownHandler));

        registerHandler(value.addValueChangeHandler(e -> fireDirty()));
        registerHandler(valueFrom.addValueChangeHandler(e -> fireDirty()));
        registerHandler(valueTo.addValueChangeHandler(e -> fireDirty()));
        registerHandler(date.addValueChangeHandler(e -> fireDirty()));
        registerHandler(dateFrom.addValueChangeHandler(e -> fireDirty()));
        registerHandler(dateTo.addValueChangeHandler(e -> fireDirty()));

        registerHandler(date.addValueChangeHandler(event -> fireDirty()));
        registerHandler(dateFrom.addValueChangeHandler(event -> fireDirty()));
        registerHandler(dateTo.addValueChangeHandler(event -> fireDirty()));

        if (docSelectionBoxPresenter != null) {
            registerHandler(docSelectionBoxPresenter.addDataSelectionHandler(event -> {
                final DocRef selection = docSelectionBoxPresenter.getSelectedEntityReference();
                if (!EqualsUtil.isEquals(term.getDocRef(), selection)) {
                    write(term);
                    fireDirty();
                }
            }));

        }

        registerHandler(fieldListBox.addValueChangeHandler(event -> {
            if (!reading) {
                write(term);
                changeField(event.getValue(), conditionListBox.getValue(), true);
                fireDirty();
            }
        }));
        registerHandler(conditionListBox.addValueChangeHandler(event -> {
            if (!reading) {
                write(term);
                changeCondition(fieldListBox.getValue(), event.getValue());
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

    private BaseSelectionBox<QueryField, FieldInfoSelectionItem> createFieldBox() {
        final BaseSelectionBox<QueryField, FieldInfoSelectionItem> fieldListBox =
                new BaseSelectionBox<QueryField, FieldInfoSelectionItem>();
        fieldListBox.addStyleName(ITEM_CLASS_NAME);
        fieldListBox.addStyleName(DROPDOWN_CLASS_NAME);
        fieldListBox.addStyleName("field");
        fieldListBox.addStyleName("termEditor-item");
        return fieldListBox;
    }

    private SelectionBox<Condition> createConditionBox() {
        final SelectionBox<Condition> conditionListBox = new SelectionBox<>();
        conditionListBox.addStyleName(ITEM_CLASS_NAME);
        conditionListBox.addStyleName(DROPDOWN_CLASS_NAME);
        conditionListBox.addStyleName("condition");
        return conditionListBox;
    }

    private SuggestBox createTextBox(final String widthClassName) {
        final SuggestBox textBox = new SuggestBox(suggestOracle);
        textBox.addDomHandler(e -> {
            if (!textBox.isSuggestionListShowing()) {
                textBox.showSuggestionList();
            }
        }, ClickEvent.getType());
        textBox.addStyleName(ITEM_CLASS_NAME);
        textBox.addStyleName(widthClassName);
        textBox.addStyleName("textBox");
        return textBox;
    }

    private MyDateBox createDateBox(final String widthClassName) {
        final MyDateBox dateBox = new MyDateBox();
        dateBox.addStyleName(ITEM_CLASS_NAME);
        dateBox.addStyleName(widthClassName);
        return dateBox;
    }

    private Label createLabel(final String text) {
        final Label label = new Label(text, false);
        label.addStyleName("termEditor-label");
        return label;
    }

    private Label createFieldTypeLabel() {
        final Label label = new Label("", false);
        label.addStyleName("termEditor-label");
        label.addStyleName("fieldType");
        label.setVisible(false);
        return label;
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
}
