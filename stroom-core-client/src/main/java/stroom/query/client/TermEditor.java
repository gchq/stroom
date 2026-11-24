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

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.shared.ExplorerConstants;
import stroom.item.client.BaseSelectionBox;
import stroom.item.client.SelectionBox;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.datasource.QueryOptionSet;
import stroom.query.api.datasource.QueryOptionUtil;
import stroom.query.client.presenter.FieldInfoSelectionItem;
import stroom.query.client.presenter.FieldSelectionListModel;
import stroom.query.client.presenter.FieldValuePickerPresenter;
import stroom.query.client.presenter.FieldValuesScope;
import stroom.query.client.presenter.QueryOptionsPresenter;
import stroom.security.client.presenter.UserRefSelectionBoxPresenter;
import stroom.security.shared.DocumentPermission;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.StringUtil;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.SvgButton;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;

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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class TermEditor extends Composite {

    private static final String ITEM_CLASS_NAME = "termEditor-item";
    private static final String DROPDOWN_CLASS_NAME = "dropdown";
    private static final String WIDE_CLASS_NAME = "wide";
    private static final String NARROW_CLASS_NAME = "narrow";
    // Highlights the options button when the term has options other than the defaults
    private static final String ON_CLASS_NAME = "on";

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
    private final SvgButton optionsButton;
    private final SvgButton valuesButton;
    private final Provider<QueryOptionsPresenter> queryOptionsPresenterProvider;
    private final Provider<FieldValuePickerPresenter> fieldValuePickerPresenterProvider;
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
    private RestFactory restFactory;
    private DocRef dataSource;
    private Supplier<FieldValuesScope> scopeSupplier;

    public TermEditor(final Provider<DocSelectionBoxPresenter> docRefProvider,
                      final Provider<UserRefSelectionBoxPresenter> userRefProvider,
                      final Provider<QueryOptionsPresenter> queryOptionsPresenterProvider,
                      final Provider<FieldValuePickerPresenter> fieldValuePickerPresenterProvider,
                      final UiConfigCache uiConfigCache) {
        this.queryOptionsPresenterProvider = queryOptionsPresenterProvider;
        this.fieldValuePickerPresenterProvider = fieldValuePickerPresenterProvider;
        final DocSelectionBoxPresenter docRefPresenter = docRefProvider.get();
        docRefPresenter.setRequiredPermissions(DocumentPermission.USE);
        docRefPresenter.getWidget().getElement().getStyle().setMargin(0, Unit.PX);
        suggestOracle = new AsyncSuggestOracle(docRefPresenter);

        this.docSelectionBoxPresenter = docRefPresenter;
        this.userRefSelectionBoxPresenter = userRefProvider.get();
        docRefWidget = docSelectionBoxPresenter.getWidget();
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

        fieldListBox = createFieldBox(uiConfigCache);
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
        optionsButton = createOptionsButton();
        valuesButton = createValuesButton();

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
        inner.add(valuesButton);
        inner.add(optionsButton);

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
        this.restFactory = restFactory;
        this.dataSource = dataSource;

        this.fieldSelectionListModel = fieldSelectionListModel;
        fieldListBox.setModel(fieldSelectionListModel);
    }

    /// Sets where the value picker gets what to count values across, e.g. the dashboard's time range and the
    /// query's expression. Without one, values are counted across all time.
    ///
    /// @param scopeSupplier Supplies the current scope, or null.
    public void setValueScopeSupplier(final Supplier<FieldValuesScope> scopeSupplier) {
        this.scopeSupplier = scopeSupplier;
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

        // See if the field is the same, if so then do not bother fetching field info from the server.
        if (term.getField() != null && Objects.equals(
                NullSafe.get(fieldListBox, BaseSelectionBox::getValue, QueryField::getFldName),
                term.getField())) {
            changeField(fieldListBox.getValue(), term.getCondition(), false);
        } else if (term.getField() == null) {
            // If the field is null then set the field box to null and update the other controls.
            fieldListBox.setValue(null);
            changeField(null, term.getCondition(), false);
        } else {
            // If the field is not null then go and fetch the field info and do a full update.
            fieldListBox.setValue(null);
            changeField(null, term.getCondition(), false);
            fieldSelectionListModel.findFieldByName(term.getField(), fieldInfo -> {
                fieldListBox.setValue(fieldInfo);
                changeField(fieldInfo, term.getCondition(), false);
            });
        }

        reading = false;
    }

    public void write() {
        if (editing) {
            write(term);
        }
    }

    private void write(final Term term) {
        final QueryField selectedField = fieldListBox.getValue();
        final Condition condition = conditionListBox.getValue();
        if (selectedField != null && condition != null) {
            DocRef docRef = null;

            term.setField(selectedField.getFldName());
            term.setCondition(condition);

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
            } else if (conditions.contains(Condition.CONTAINS)) {
                selected = Condition.CONTAINS;
            } else if (conditions.contains(Condition.EQUALS)) {
                selected = Condition.EQUALS;
            } else {
                selected = conditions.get(0);
            }
        }

        // Make the condition box wider if needed.
        if (conditions.contains(Condition.MATCHES_REGEX_CASE_SENSITIVE)) {
            conditionListBox.addStyleName("condition-wide");
        } else {
            conditionListBox.removeStyleName("condition-wide");
        }

        conditionListBox.setValue(selected);
        NullSafe.consume(selected, Condition::getDescription, conditionListBox::setTitle);
        changeCondition(field, selected);
        updateOptionsButton(field);

        if (NullSafe.nonNull(field, QueryField::getFldType)) {
            fieldTypeLabel.setText(field.getFldType().getShortTypeName());
            fieldTypeLabel.setTitle(field.getFldType().getDescription());
            fieldTypeLabel.setVisible(true);
        } else {
            fieldTypeLabel.setText("");
            fieldTypeLabel.setTitle("");
            fieldTypeLabel.setVisible(false);
        }
    }

    private List<Condition> getConditions(final QueryField field) {
        final ConditionSet conditions = NullSafe.getOrElseGet(
                field,
                QueryField::getConditionSet,
                () -> {
                    FieldType fieldType = null;
                    if (field != null) {
                        fieldType = field.getFldType();
                    }
                    return ConditionSet.getUiDefaultConditions(fieldType);
                }
        );
        return conditions.getConditionList();
    }

    private void changeCondition(final QueryField field,
                                 final Condition condition) {
        final QueryField selectedField = fieldListBox.getValue();
        final FieldType indexFieldType = NullSafe.get(selectedField, QueryField::getFldType);

        if (indexFieldType == null) {
            setActiveWidgets();
        } else {
            switch (condition) {
                case EQUALS,
                     NOT_EQUALS,
                     LESS_THAN,
                     LESS_THAN_OR_EQUAL_TO,
                     GREATER_THAN,
                     GREATER_THAN_OR_EQUAL_TO:
                    enterTextOrDateMode(indexFieldType);
                    break;
                case BETWEEN:
                    enterTextOrDateRangeMode(indexFieldType);
                    break;
                case IN_DICTIONARY,
                     IN_FOLDER,
                     IS_DOC_REF,
                     IS_NOT_DOC_REF,
                     OF_DOC_REF:
                    enterDocRefMode(field, condition);
                    break;
                case IS_USER_REF,
                     IS_NOT_USER_REF,
                     USER_HAS_PERM,
                     USER_HAS_OWNER,
                     USER_HAS_DELETE,
                     USER_HAS_EDIT,
                     USER_HAS_VIEW,
                     USER_HAS_USE:
                    enterUserRefMode(field, condition);
                    break;
                default:
                    enterTextMode();
                    break;
            }
        }
        updateValuesButton(selectedField, condition);
    }

    // Shows the value picker button for fields whose data source can list their most common values, when the
    // condition takes values from the text box.
    private void updateValuesButton(final QueryField field, final Condition condition) {
        final boolean supported = field != null
                                  && field.supportsTopValues()
                                  && (Condition.EQUALS.equals(condition)
                                      || Condition.NOT_EQUALS.equals(condition)
                                      || Condition.IN.equals(condition));
        valuesButton.setVisible(supported);
    }

    private void enterTextOrDateMode(final FieldType indexFieldType) {
        if (FieldType.DATE.equals(indexFieldType)) {
            enterDateMode();
        } else {
            enterTextMode();
        }
    }

    private void enterTextOrDateRangeMode(final FieldType indexFieldType) {
        if (FieldType.DATE.equals(indexFieldType)) {
            enterDateRangeMode();
        } else {
            enterTextRangeMode();
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
                docSelectionBoxPresenter.setIncludedTypes(ExplorerConstants.FOLDER_LIKE);
                docSelectionBoxPresenter.setAllowFolderSelection(true);
            } else if (FieldType.DOC_REF.equals(field.getFldType())) {
                if (field.getDocRefType() != null) {
                    docSelectionBoxPresenter.setIncludedTypes(field.getDocRefType());
                } else {
                    docSelectionBoxPresenter.setAllowFolderSelection(true);
                }
            }
            docSelectionBoxPresenter.setSelectedEntityReference(term.getDocRef(), true);
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

        if (docSelectionBoxPresenter != null) {
            registerHandler(docSelectionBoxPresenter.addDataSelectionHandler(event -> {
                final DocRef selection = docSelectionBoxPresenter.getSelectedEntityReference();
                if (!Objects.equals(term.getDocRef(), selection)) {
                    write(term);
                    fireDirty();
                }
            }));
        }

        registerHandler(fieldListBox.addValueChangeHandler(event -> {
            if (!reading) {
                write(term);
                // Keep only the options that the new field supports
                term.setOptions(QueryOptionUtil.normalise(
                        NullSafe.get(event.getValue(), QueryField::getQueryOptionSet),
                        term.getOptions()));
                changeField(event.getValue(), conditionListBox.getValue(), true);
                fireDirty();
            }
        }));
        registerHandler(optionsButton.addClickHandler(event -> showOptions()));
        registerHandler(valuesButton.addClickHandler(event -> showValuePicker()));
        registerHandler(conditionListBox.addValueChangeHandler(event -> {
            if (!reading) {
                write(term);
                changeCondition(fieldListBox.getValue(), event.getValue());
                fireDirty();
            }
        }));
    }

    private void showOptions() {
        final QueryField field = fieldListBox.getValue();
        if (field != null && field.getQueryOptionSet() != null) {
            queryOptionsPresenterProvider.get().show(field, term.getOptions(), options -> {
                term.setOptions(options);
                updateOptionsButton(field);
                fireDirty();
            });
        }
    }

    private void showValuePicker() {
        final QueryField field = fieldListBox.getValue();
        if (field != null && restFactory != null && dataSource != null) {
            // An `in` term holds several values, so they are ticked in the picker
            fieldValuePickerPresenterProvider.get().show(
                    valuesButton.getElement(),
                    restFactory,
                    dataSource,
                    field,
                    Condition.IN.equals(conditionListBox.getValue()),
                    NullSafe.get(scopeSupplier, Supplier::get),
                    this::getTermValues,
                    this::insertValue);
        }
    }

    // Puts a picked value in the value box, replacing its text for `=` and `!=`. For `in`, the value is added to
    // the list, or removed if the list holds it, and the picker stays open with the focus, so the value box isn't
    // focused. The data source has already quoted the value as its query syntax needs.
    private void insertValue(final String pickedValue) {
        if (Condition.IN.equals(conditionListBox.getValue())) {
            final List<String> listValues = getListValues();
            if (!listValues.remove(pickedValue)) {
                listValues.add(pickedValue);
            }
            value.setText(String.join(Condition.IN_CONDITION_DELIMITER + " ", listValues));
        } else {
            value.setText(pickedValue);
            value.setFocus(true);
        }
        fireDirty();
    }

    // The values in an `in` term's list, in order, for the picker to tick
    private Set<String> getTermValues() {
        return Condition.IN.equals(conditionListBox.getValue())
                ? new HashSet<>(getListValues())
                : new HashSet<>();
    }

    private List<String> getListValues() {
        final List<String> listValues = new ArrayList<>();
        final String currentText = StringUtil.trimWhitespace(value.getText());
        if (NullSafe.isNonBlankString(currentText)) {
            for (final String item : currentText.split(Condition.IN_CONDITION_DELIMITER)) {
                final String trimmed = StringUtil.trimWhitespace(item);
                if (NullSafe.isNonBlankString(trimmed)) {
                    listValues.add(trimmed);
                }
            }
        }
        return listValues;
    }

    // Shows the options button only for fields whose data source supports field-level query options, and
    // highlights it when the term has options other than the defaults.
    private void updateOptionsButton(final QueryField field) {
        final QueryOptionSet queryOptionSet = NullSafe.get(field, QueryField::getQueryOptionSet);
        optionsButton.setVisible(queryOptionSet != null);
        if (queryOptionSet != null && QueryOptionUtil.hasNonDefaultOptions(queryOptionSet, term.getOptions())) {
            optionsButton.getElement().addClassName(ON_CLASS_NAME);
            optionsButton.setTitle("Query options (changed from the defaults)");
        } else {
            optionsButton.getElement().removeClassName(ON_CLASS_NAME);
            optionsButton.setTitle("Query options");
        }
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

    private BaseSelectionBox<QueryField, FieldInfoSelectionItem> createFieldBox(final UiConfigCache uiConfigCache) {
        final BaseSelectionBox<QueryField, FieldInfoSelectionItem> fieldListBox =
                new BaseSelectionBox<QueryField, FieldInfoSelectionItem>();
        fieldListBox.addStyleName(ITEM_CLASS_NAME);
        fieldListBox.addStyleName(DROPDOWN_CLASS_NAME);
        fieldListBox.addStyleName("field");
        fieldListBox.addStyleName("termEditor-item");
        uiConfigCache.get(uiConfig ->
                NullSafe.consume(uiConfig, ExtendedUiConfig::getHelpUrl, helpUrl ->
                        fieldListBox.registerPopupTextProvider(() ->
                                QuickFilterTooltipUtil.createTooltip("Field Filter", helpUrl))));
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

    private SvgButton createOptionsButton() {
        final SvgButton button = SvgButton.create(SvgPresets.SETTINGS);
        button.addStyleName("termEditor-button");
        button.setTitle("Query options");
        button.setVisible(false);
        return button;
    }

    private SvgButton createValuesButton() {
        final SvgButton button = SvgButton.create(
                new Preset(SvgImage.VALUE_FILTER, "Pick from the most common values", true));
        button.addStyleName("termEditor-button");
        button.setVisible(false);
        return button;
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
            write(term);
            if (uiHandlers != null) {
                uiHandlers.onChange();
            }
        }
    }

    public void setUiHandlers(final ExpressionUiHandlers uiHandlers) {
        this.uiHandlers = uiHandlers;
    }
}
