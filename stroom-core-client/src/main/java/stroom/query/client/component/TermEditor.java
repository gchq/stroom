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

package stroom.query.client.component;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.datasource.QueryOptionSet;
import stroom.query.api.datasource.QueryOptionUtil;
import stroom.query.client.FieldInfoSelectionItem;
import stroom.query.client.Term;
import stroom.query.client.TermValueSuggestions;
import stroom.query.client.model.FieldSelectionListModel;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.Suggestions;
import stroom.util.shared.NullSafe;
import stroom.util.shared.StringUtil;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.component.DateTimeBox;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;
import stroom.widget.selection.client.BaseSelectionBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.selection.client.SuggestionBox;
import stroom.widget.text.client.Label;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/// A term's editor: its field, its condition, and the boxes for the value the condition takes (a
/// text, a range, a date or a range of dates, a document or a user). Its controller
/// ([stroom.query.client.component.TermEditorController]) attaches the document and user pickers
/// and the date boxes' dialogs, gives it the [Pickers] it reads and writes them through and that
/// open the value picker and the query options dialog, and fetches the values it suggests.
public class TermEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    // Marks the query options' button while the term's options aren't the defaults, as a table's
    // filter button is marked while it filters
    private static final String OPTIONS_CHANGED = "icon-colour__blue";

    @UiField
    FlowPanel inner;
    @UiField
    BaseSelectionBox<QueryField, FieldInfoSelectionItem> fieldListBox;
    @UiField
    SelectionBox<Condition> conditionListBox;
    @UiField
    Label andLabel;
    @UiField
    SuggestionBox value;
    @UiField
    SuggestionBox valueFrom;
    @UiField
    SuggestionBox valueTo;
    @UiField
    DateTimeBox date;
    @UiField
    DateTimeBox dateFrom;
    @UiField
    DateTimeBox dateTo;
    @UiField
    DropDownBox docRefBox;
    @UiField
    DropDownBox userRefBox;
    @UiField
    Toolbar termActions;
    @UiField
    ToolbarButton valuesButton;
    @UiField
    ToolbarButton optionsButton;
    @UiField
    Label fieldTypeLabel;

    private final TermValueSuggestions suggestions = new TermValueSuggestions();
    private final List<Widget> activeWidgets = new ArrayList<>();
    private final List<HandlerRegistration> registrations = new ArrayList<>();

    private Term term;
    private boolean reading;
    private boolean editing;
    private Pickers pickers;
    private Runnable onChange = () -> {
    };
    private Runnable onSearch = () -> {
    };

    private DocRef dataSource;
    private FieldSelectionListModel fieldSelectionListModel;
    // Narrows what the condition list offers beyond the field's own ConditionSet. The quick
    // filter's Advanced Query dialog uses it to offer only conditions its syntax can write back.
    private Predicate<Condition> conditionFilter = condition -> true;

    /// Makes the editor, hidden until a term is edited.
    public TermEditor() {
        initWidget(BINDER.createAndBindUi(this));
        // A term's parts have no labels, so each is named for what it is
        AccessibleName.set(fieldListBox, "Field");
        AccessibleName.set(conditionListBox, "Condition");
        initValueBox(value, "Value");
        initValueBox(valueFrom, "From");
        initValueBox(valueTo, "To");
        // A time or an expression such as day(); Enter runs the query, Alt+Down opens the dialog
        AccessibleName.set(date, "Date");
        AccessibleName.set(dateFrom, "From");
        AccessibleName.set(dateTo, "To");
        valuesButton.setOnClick(this::showValuePicker);
        optionsButton.setOnClick(this::showOptions);
    }

    /// @param pickers The document and user pickers, which its controller attached to the boxes.
    public void setPickers(final Pickers pickers) {
        this.pickers = pickers;
    }

    /// Cancels a request for suggestions still waiting for what is typed to settle, so nothing is
    /// fetched once the editor's controller has gone.
    public void cancelSuggestions() {
        value.cancel();
        valueFrom.cancel();
        valueTo.cancel();
    }

    /// @param fetcher Fetches the values to suggest for what is typed (see [TermValueSuggestions]).
    public void setSuggestionFetcher(final BiConsumer<FetchSuggestionsRequest, Consumer<Suggestions>> fetcher) {
        suggestions.setFetcher(fetcher);
    }

    /// @param helpUrl Where the field filter's help is, which its tooltip links to.
    public void setFieldFilterHelpUrl(final String helpUrl) {
        fieldListBox.registerPopupTextProvider(() ->
                QuickFilterTooltipUtil.createTooltip("Field Filter", helpUrl));
    }

    /// @return The document picker's box, for its controller to attach to.
    public DropDownBox getDocRefBox() {
        return docRefBox;
    }

    /// @return The user picker's box, for its controller to attach to.
    public DropDownBox getUserRefBox() {
        return userRefBox;
    }

    /// @return The date boxes, whose dialogs its controller opens.
    public List<DateTimeBox> getDateBoxes() {
        return Arrays.asList(date, dateFrom, dateTo);
    }

    /// @param onChange Run when the user changes the term.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onSearch Run when Enter is pressed in a value, to run the query.
    public void setOnSearch(final Runnable onSearch) {
        this.onSearch = onSearch;
    }

    /// Sets what the term's field is chosen from, and its values suggested from.
    ///
    /// @param dataSource              The data source.
    /// @param fieldSelectionListModel The data source's fields.
    public void init(final DocRef dataSource,
                     final FieldSelectionListModel fieldSelectionListModel) {
        suggestions.setDataSource(dataSource);
        this.dataSource = dataSource;

        this.fieldSelectionListModel = fieldSelectionListModel;
        fieldListBox.setModel(fieldSelectionListModel);
    }

    /// @param conditionFilter Which conditions are offered, beyond the field's own.
    public void setConditionFilter(final Predicate<Condition> conditionFilter) {
        this.conditionFilter = Objects.requireNonNullElse(conditionFilter, condition -> true);
    }

    /// Edits a term, showing the editor once it is laid out.
    ///
    /// @param term The term.
    public void startEdit(final Term term) {
        if (!editing) {
            this.term = term;
            read(term);
            Scheduler.get().scheduleDeferred(() -> {
                bind();
                asWidget().setVisible(true);
            });
            editing = true;
        }
    }

    /// @return Whether a field's own popup is open: a value's suggestions (whose keys, e.g. Escape
    /// to close it, are the field's). A date's dialog is modal, so takes the keys itself.
    public boolean isPopupShowing() {
        return value.isListShowing()
               || valueFrom.isListShowing()
               || valueTo.isListShowing();
    }

    /// Closes the fields' own popups (see [#isPopupShowing]).
    public void hidePopups() {
        value.hideList();
        valueFrom.hideList();
        valueTo.hideList();
    }

    /// Stops editing, writing what was entered into the term, and hides the editor.
    public void endEdit() {
        if (editing) {
            write(term);
            unbind();
            asWidget().setVisible(false);
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

    /// Writes what is entered into the term being edited, if one is.
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
                if (widget instanceof SuggestionBox) {
                    sb.append(((SuggestionBox) widget).getText());
                    sb.append(",");
                } else if (widget instanceof DateTimeBox) {
                    sb.append(((DateTimeBox) widget).getText());
                    sb.append(",");
                } else if (widget.equals(docRefBox)) {
                    if (pickers != null) {
                        docRef = pickers.getDocRef();
                        if (docRef != null) {
                            sb.append(docRef.getName());
                        }
                    }
                    sb.append(",");
                } else if (widget.equals(userRefBox)) {
                    if (pickers != null) {
                        final UserRef userRef = pickers.getUserRef();
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
        suggestions.setField(field);
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
            conditionListBox.addStyleName("term-editor__item--condition-wide");
        } else {
            conditionListBox.removeStyleName("term-editor__item--condition-wide");
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
        return conditions.getConditionList()
                .stream()
                .filter(conditionFilter)
                .collect(Collectors.toList());
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
                    enterUserRefMode();
                    break;
                default:
                    enterTextMode();
                    break;
            }
        }
        updateValuesButton(selectedField, condition);
    }

    // Shows the value picker's button for a field whose data source can list its most common
    // values, when the condition takes its values from the value box
    private void updateValuesButton(final QueryField field, final Condition condition) {
        final boolean supported = field != null
                                  && field.supportsTopValues()
                                  && (Condition.EQUALS.equals(condition)
                                      || Condition.NOT_EQUALS.equals(condition)
                                      || Condition.IN.equals(condition));
        valuesButton.setVisible(supported);
        updateTermActions();
    }

    // Shows the query options' button only for a field whose data source offers field-level query
    // options, and marks it when the term's options aren't the defaults
    private void updateOptionsButton(final QueryField field) {
        final QueryOptionSet queryOptionSet = NullSafe.get(field, QueryField::getQueryOptionSet);
        optionsButton.setVisible(queryOptionSet != null);
        final boolean changed = queryOptionSet != null
                                && QueryOptionUtil.hasNonDefaultOptions(queryOptionSet, term.getOptions());
        optionsButton.setStyleName(OPTIONS_CHANGED, changed);
        optionsButton.setTitle(changed
                ? "Query Options (Changed)"
                : "Query Options");
        updateTermActions();
    }

    // The buttons' toolbar takes no room, nor a tab stop, while it has none to show
    private void updateTermActions() {
        termActions.setVisible(valuesButton.isVisible() || optionsButton.isVisible());
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
        setActiveWidgets(docRefBox);
        if (pickers != null) {
            pickers.showDocRef(field, condition, term.getDocRef());
        }
    }

    private void enterUserRefMode() {
        setActiveWidgets(userRefBox);
        if (pickers != null) {
            pickers.showUserRef(UserDocRefUtil.createUserRef(term.getDocRef()));
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
                value.setValue(vals[0]);
                valueFrom.setValue(vals[0]);
            }
            if (vals.length > 1) {
                valueTo.setValue(vals[1]);
            }
        }
    }

    private void updateDateBoxes() {
        if (term.getValue() != null) {
            // Set the current data.
            final String[] vals = term.getValue().split(",");
            if (vals.length > 0) {
                date.setText(vals[0]);
                dateFrom.setText(vals[0]);
            }
            if (vals.length > 1) {
                dateTo.setText(vals[1]);
            }
        }
    }

    private void bind() {
        final KeyDownHandler keyDownHandler = event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                onSearch.run();
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

        if (pickers != null) {
            registerHandler(pickers.addDocRefListener(() -> {
                final DocRef selection = pickers.getDocRef();
                if (!Objects.equals(term.getDocRef(), selection)) {
                    write(term);
                    fireDirty();
                }
            }));
        }

        registerHandler(fieldListBox.addValueChangeHandler(event -> {
            if (!reading) {
                write(term);
                // Keep only the options the new field offers
                term.setOptions(QueryOptionUtil.normalise(
                        NullSafe.get(event.getValue(), QueryField::getQueryOptionSet),
                        term.getOptions()));
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

    private void showOptions() {
        final QueryField field = fieldListBox.getValue();
        if (pickers != null && field != null && field.getQueryOptionSet() != null) {
            pickers.showQueryOptions(field, term.getOptions(), options -> {
                term.setOptions(options);
                updateOptionsButton(field);
                fireDirty();
            });
        }
    }

    private void showValuePicker() {
        final QueryField field = fieldListBox.getValue();
        if (pickers != null && field != null && dataSource != null) {
            // An 'in' term holds several values, which are ticked in the picker
            pickers.showValuePicker(
                    valuesButton.getElement(),
                    dataSource,
                    field,
                    Condition.IN.equals(conditionListBox.getValue()),
                    this::getTermValues,
                    this::insertValue);
        }
    }

    // Puts a picked value in the value box, in place of its text for '=' and '!='. For 'in' the
    // value is added to the list, or removed if the list holds it, and the picker keeps the focus,
    // so the user can carry on picking. The data source has already quoted the value as its query
    // syntax needs.
    private void insertValue(final String pickedValue) {
        if (Condition.IN.equals(conditionListBox.getValue())) {
            value.setText(TermValueList.toggle(value.getText(), pickedValue));
        } else {
            value.setText(pickedValue);
            value.focus();
        }
        fireDirty();
    }

    // The values in an 'in' term's list, for the picker to tick
    private Set<String> getTermValues() {
        return Condition.IN.equals(conditionListBox.getValue())
                ? new HashSet<>(TermValueList.parse(value.getText()))
                : new HashSet<>();
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

    // A value's box suggests the field's values (it shows them when clicked, as it does when typed in)
    private void initValueBox(final SuggestionBox box, final String name) {
        box.setAccessibleName(name);
        box.setSource(suggestions);
    }

    private void fireDirty() {
        if (!reading) {
            write(term);
            onChange.run();
        }
    }

    /// @return The row of its fields, on which the item it edits draws its looks (e.g. being
    /// selected).
    public Element getRow() {
        return inner.getElement();
    }


    // --------------------------------------------------------------------------------


    /// The document and user pickers a term's value may be chosen with, which the editor's
    /// controller attaches to its boxes.
    ///
    /// It also opens the term's popups: the value picker and the query options dialog.
    public interface Pickers {

        /// Shows a document in the document picker, offering the documents the condition takes.
        ///
        /// @param field     The term's field.
        /// @param condition The term's condition.
        /// @param docRef    The document, or null for none.
        void showDocRef(QueryField field, Condition condition, DocRef docRef);

        /// @return The document chosen, or null for none.
        DocRef getDocRef();

        /// @param listener Told when the user chooses a document.
        /// @return The registration.
        HandlerRegistration addDocRefListener(Runnable listener);

        /// @param userRef The user to show in the user picker, or null for none.
        void showUserRef(UserRef userRef);

        /// @return The user chosen, or null for none.
        UserRef getUserRef();

        /// Shows the dialog for the field-level query options of a term's field.
        ///
        /// @param field   The term's field, which has a [QueryOptionSet].
        /// @param options The options the term holds, or null for none.
        /// @param onOk    Given the options for the term to hold when OK is pressed: only those that
        ///                differ from their defaults, or null if none do.
        void showQueryOptions(QueryField field, Map<String, String> options, Consumer<Map<String, String>> onOk);

        /// Shows the value picker below an element, listing the field's most common values.
        ///
        /// @param target      The element it is shown below, the button that opens it.
        /// @param dataSource  The data source to fetch values from.
        /// @param field       The term's field.
        /// @param multiSelect Whether the term holds several values, as an 'in' term does.
        /// @param termValues  Gives the values the term holds, to tick when several can be picked.
        /// @param onPick      Given each value picked, as the term must hold it.
        void showValuePicker(Element target,
                             DocRef dataSource,
                             QueryField field,
                             boolean multiSelect,
                             Supplier<Set<String>> termValues,
                             Consumer<String> onPick);
    }


    interface Binder extends UiBinder<Widget, TermEditor> {

    }
}
