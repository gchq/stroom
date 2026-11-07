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

package stroom.query.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.client.presenter.FieldValuePickerPresenter.FieldValuePickerView;
import stroom.query.shared.FetchFieldValuesRequest;
import stroom.query.shared.FieldValues;
import stroom.query.shared.SuggestionsResource;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// A popup listing the most common values of a field, each with the share of documents that have it, from
/// which the user can pick a value for an expression term.
public class FieldValuePickerPresenter extends MyPresenterWidget<FieldValuePickerView> {

    private static final SuggestionsResource SUGGESTIONS_RESOURCE = GWT.create(SuggestionsResource.class);

    private final DateTimeSettingsFactory dateTimeSettingsFactory;

    private RestFactory restFactory;
    private DocRef dataSource;
    private QueryField field;
    private boolean multiSelect;
    private TimeRange timeRange;
    private ExpressionOperator expression;
    private Consumer<String> valueConsumer;
    private Supplier<Set<String>> termValuesSupplier;
    private String lastFilter;
    // Identifies the latest request so that responses to earlier ones, e.g. for a previous filter, or after the
    // picker has closed, are ignored
    private int requestCount;

    @Inject
    public FieldValuePickerPresenter(final EventBus eventBus,
                                     final FieldValuePickerView view,
                                     final DateTimeSettingsFactory dateTimeSettingsFactory) {
        super(eventBus, view);
        this.dateTimeSettingsFactory = dateTimeSettingsFactory;
    }

    /// Shows the picker below an element.
    ///
    /// @param target          The element to show the picker below, e.g. the button that opened it.
    /// @param restFactory     Used to fetch the values.
    /// @param dataSource      The data source to fetch values from.
    /// @param field           The field to list values of.
    /// @param multiSelect     Whether the term holds several values, as an `in` term does. If so, each value has a
    ///                        tick box showing whether the term holds it, clicking a value adds it to the term or
    ///                        removes it, and the picker stays open. Values containing a comma can't be picked, as
    ///                        the term's values are separated by commas. Otherwise, picking a value closes the
    ///                        picker.
    /// @param scope           The time range and query expression to count values across, or null for all time.
    /// @param termValues      Supplies the values the term holds, as a term holds them, to tick when several values
    ///                        can be picked.
    /// @param valueConsumer   Receives the picked value, as the data source says a term must hold it to match.
    ///                        When several values can be picked, it adds the value to the term, or removes it if
    ///                        the term holds it.
    public void show(final Element target,
                     final RestFactory restFactory,
                     final DocRef dataSource,
                     final QueryField field,
                     final boolean multiSelect,
                     final FieldValuesScope scope,
                     final Supplier<Set<String>> termValues,
                     final Consumer<String> valueConsumer) {
        this.restFactory = restFactory;
        this.dataSource = dataSource;
        this.field = field;
        this.multiSelect = multiSelect;
        this.timeRange = NullSafe.get(scope, FieldValuesScope::getTimeRange);
        this.expression = NullSafe.get(scope, FieldValuesScope::getExpression);
        this.valueConsumer = valueConsumer;
        this.termValuesSupplier = termValues;

        getView().init(isFilterable(field), this::onFilterChange, this::pick, this::hide);
        lastFilter = null;
        fetchValues(null);

        final Rect relativeRect = new Rect(target).grow(3);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.POPUP)
                .popupPosition(new PopupPosition(relativeRect, PopupLocation.BELOW))
                .onShow(e -> getView().focus())
                .onHide(e -> cancelPendingRequest())
                .fire();
    }

    /// Values can be filtered with a regular expression only if the field holds strings.
    private static boolean isFilterable(final QueryField field) {
        final FieldType fieldType = field.getFldType();
        return FieldType.KEYWORD.equals(fieldType) || FieldType.TEXT.equals(fieldType);
    }

    private void onFilterChange(final String filter) {
        final String trimmed = NullSafe.isBlankString(filter)
                ? null
                : filter.trim();
        if (Objects.equals(trimmed, lastFilter)) {
            return;
        }
        lastFilter = trimmed;
        // The filter box waits for the user to stop typing before reporting a change
        fetchValues(trimmed);
    }

    private void fetchValues(final String filter) {
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
                        getView().setMessage("Unable to fetch values: " + error.getMessage());
                    }
                })
                .taskMonitorFactory(getView().getTaskMonitorFactory())
                .exec();
    }

    private void showValues(final FieldValues fieldValues) {
        if (fieldValues.getValues().isEmpty()) {
            getView().setMessage("No values found");
        } else {
            // The data source describes the scope when it used more than the time range, e.g. the query's
            // own time conditions
            final String scope;
            if (fieldValues.isAllTime()) {
                scope = "All time";
            } else if (fieldValues.getScope() != null) {
                scope = fieldValues.getScope();
            } else {
                scope = NullSafe.getOrElse(timeRange, TimeRange::getName, "Time range");
            }
            getView().setValues(fieldValues, scope, multiSelect, getTermValues());
        }
    }

    private void pick(final String value) {
        if (multiSelect) {
            valueConsumer.accept(value);
            // Tick or untick the value as the term now holds it, and keep the focus here so that the user can
            // carry on picking
            getView().setSelectedValues(getTermValues());
            getView().focus();
        } else {
            hide();
            valueConsumer.accept(value);
        }
    }

    private Set<String> getTermValues() {
        return NullSafe.set(NullSafe.get(termValuesSupplier, Supplier::get));
    }

    // Requests can't be aborted once sent, so a response that arrives after the picker has closed, or after a
    // newer request, is ignored.
    private void cancelPendingRequest() {
        requestCount++;
    }

    private void hide() {
        HidePopupRequestEvent.builder(this).fire();
    }


    // --------------------------------------------------------------------------------


    public interface FieldValuePickerView extends View, Focus {

        /// Prepares the view for a field.
        ///
        /// @param filterable    Whether to show the filter box.
        /// @param filterHandler Called with the filter text as the user types.
        /// @param pickHandler   Called with the value the user picks.
        /// @param closeHandler  Called when the user asks to close the picker, by pressing Escape in an empty
        ///                      filter box.
        void init(boolean filterable,
                  Consumer<String> filterHandler,
                  Consumer<String> pickHandler,
                  Runnable closeHandler);

        /// Lists values, replacing any message.
        ///
        /// @param fieldValues The values and the number of documents they were counted across.
        /// @param scope       A description of the documents counted, e.g. the time range name.
        /// @param multiSelect Whether several values can be picked. If so, each value has a tick box, and values
        ///                    containing a comma are shown disabled.
        /// @param termValues  The values the term holds, as a term holds them, to tick when several values can be
        ///                    picked.
        void setValues(FieldValues fieldValues, String scope, boolean multiSelect, Set<String> termValues);

        /// Ticks the values the term holds and unticks the rest, when several values can be picked.
        ///
        /// @param termValues The values the term holds, as a term holds them.
        void setSelectedValues(Set<String> termValues);

        /// Shows a message, e.g. that there are no values, in place of the values.
        ///
        /// @param message The message.
        void setMessage(String message);

        /// @return The task monitor that shows a loading indicator while values are being fetched.
        TaskMonitorFactory getTaskMonitorFactory();
    }
}
