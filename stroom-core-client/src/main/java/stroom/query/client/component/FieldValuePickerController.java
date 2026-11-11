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

package stroom.query.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.DateTimeSettingsFactory;
import stroom.query.client.model.FieldValuesScope;
import stroom.query.shared.FetchFieldValuesRequest;
import stroom.query.shared.FieldValueCount;
import stroom.query.shared.FieldValues;
import stroom.query.shared.SuggestionsResource;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.popup.client.PopupType;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Shows the value picker ([FieldValuePicker]) as a popup below what opens it, for one use: a
/// field's most common values, from the term's data source, each with the share of records that
/// have it, from which the user picks a value for a term. For an `in` term several values can be
/// picked: each picked value is added to the term, or removed if the term holds it, and the picker
/// stays open. Otherwise picking a value closes it.
public class FieldValuePickerController extends Controller {

    private static final SuggestionsResource SUGGESTIONS_RESOURCE = GWT.create(SuggestionsResource.class);
    private static final String COMMA = ",";

    private final FieldValuePicker view;
    private final RestFactory restFactory;
    private final DateTimeSettingsFactory dateTimeSettingsFactory;

    private DocRef dataSource;
    private QueryField field;
    private boolean multiSelect;
    private TimeRange timeRange;
    private ExpressionOperator expression;
    private Supplier<Set<String>> termValues;
    private Consumer<String> onPick;
    private String lastFilter;
    // Identifies the latest request, so a reply to an earlier one (e.g. for an earlier filter) is
    // ignored
    private int requestCount;

    /// Made by GIN for one use, with the popup's content.
    ///
    /// @param eventBus                The application's event bus, which the popup is shown on.
    /// @param view                    The popup's content.
    /// @param restFactory             Fetches the values.
    /// @param dateTimeSettingsFactory Gives the user's date and time settings, which the time range is
    ///                                resolved with.
    @Inject
    public FieldValuePickerController(final EventBus eventBus,
                                      final FieldValuePicker view,
                                      final RestFactory restFactory,
                                      final DateTimeSettingsFactory dateTimeSettingsFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
        initWidget(view);
        view.setOnFilterChange(this::onFilterChange);
        view.setOnPick(this::pick);
        view.setFilterHelp(() -> HtmlBuilder.builder()
                .bold("Filter Values")
                .br()
                .append("Lists the most common values that contain the text entered.")
                .toSafeHtml());
    }

    /// Shows the picker below an element, owned by `opener` until it is closed, and fetches the
    /// values.
    ///
    /// @param opener      What shows it.
    /// @param target      The element it is shown below, e.g. the button that opens it.
    /// @param dataSource  The data source to fetch values from.
    /// @param field       The field to list the values of.
    /// @param multiSelect Whether the term holds several values, as an `in` term does. If so, each
    ///                    value is ticked when the term holds it, picking one adds it to the term or
    ///                    removes it, and the picker stays open. Values holding a comma can't be
    ///                    picked, as the term's values are separated by commas.
    /// @param scope       The time range and query expression to count values across, or null for
    ///                    all time.
    /// @param termValues  Gives the values the term holds, as the term holds them, to tick when
    ///                    several values can be picked.
    /// @param onPick      Given the value picked, as the data source says the term must hold it to
    ///                    match (e.g. quoted).
    public void show(final HasControllerNode opener,
                     final Element target,
                     final DocRef dataSource,
                     final QueryField field,
                     final boolean multiSelect,
                     final FieldValuesScope scope,
                     final Supplier<Set<String>> termValues,
                     final Consumer<String> onPick) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.field = Objects.requireNonNull(field, "field");
        this.multiSelect = multiSelect;
        this.timeRange = NullSafe.get(scope, FieldValuesScope::getTimeRange);
        this.expression = NullSafe.get(scope, FieldValuesScope::getExpression);
        this.termValues = termValues;
        this.onPick = Objects.requireNonNull(onPick, "onPick");

        view.setFilterShown(isFilterable(field));
        view.setPickable(value -> !multiSelect || !value.getValue().contains(COMMA));
        view.setTicked(multiSelect
                ? value -> getTermValues().contains(getTermValue(value))
                : null);

        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.POPUP)
                .caption(multiSelect
                        ? "Pick Values"
                        : "Pick a Value")
                .popupPosition(new PopupPosition(new Rect(target).grow(3), PopupLocation.BELOW))
                .addAutoHidePartner(target)
                .onShow(e -> view.focus())
                .fire();
        fetch(null);
    }

    // Values can only be filtered by text for fields that hold text
    private static boolean isFilterable(final QueryField field) {
        final FieldType fieldType = field.getFldType();
        return FieldType.KEYWORD.equals(fieldType) || FieldType.TEXT.equals(fieldType);
    }

    private void onFilterChange(final String filter) {
        final String trimmed = NullSafe.isBlankString(filter)
                ? null
                : filter.trim();
        if (!Objects.equals(trimmed, lastFilter)) {
            lastFilter = trimmed;
            fetch(trimmed);
        }
    }

    private void fetch(final String filter) {
        final int requestId = ++requestCount;
        final FetchFieldValuesRequest request = new FetchFieldValuesRequest(
                dataSource,
                field,
                filter,
                timeRange,
                expression,
                dateTimeSettingsFactory.getDateTimeSettings());
        restFactory
                .create(SUGGESTIONS_RESOURCE)
                .method(res -> res.fetchFieldValues(request))
                .onSuccess(result -> {
                    if (requestId == requestCount) {
                        showValues(result);
                    }
                })
                .onFailure(error -> {
                    if (requestId == requestCount) {
                        view.setMessage("Unable to fetch values: " + error.getMessage());
                    }
                })
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    private void showValues(final FieldValues fieldValues) {
        if (fieldValues.getValues().isEmpty()) {
            view.setMessage("No values found");
        } else {
            // The data source describes what it counted across when it used more than the time
            // range, e.g. the query's own time conditions
            final String description;
            if (fieldValues.isAllTime()) {
                description = "All time";
            } else if (fieldValues.getScope() != null) {
                description = fieldValues.getScope();
            } else {
                description = NullSafe.getOrElse(timeRange, TimeRange::getName, "Time range");
            }
            view.setValues(fieldValues.getValues(), fieldValues.getTotalCount(), description);
        }
    }

    private void pick(final FieldValueCount value) {
        if (multiSelect) {
            onPick.accept(getTermValue(value));
            // Ticks or unticks the value as the term now holds it; the user carries on picking
            view.redraw();
        } else {
            ControllerDialog.hide(this, false, true);
            onPick.accept(getTermValue(value));
        }
    }

    private Set<String> getTermValues() {
        return NullSafe.set(NullSafe.get(termValues, Supplier::get));
    }

    // The data source gives the value as a term must hold it to match, e.g. quoted, where that
    // differs from the value
    private static String getTermValue(final FieldValueCount value) {
        return value.getTermValue() != null
                ? value.getTermValue()
                : value.getValue();
    }
}
