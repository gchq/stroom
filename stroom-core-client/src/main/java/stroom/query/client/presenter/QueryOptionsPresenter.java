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

import stroom.query.api.datasource.QueryField;
import stroom.query.api.datasource.QueryOptionDefinition;
import stroom.query.api.datasource.QueryOptionSet;
import stroom.query.api.datasource.QueryOptionUtil;
import stroom.query.client.presenter.QueryOptionsPresenter.QueryOptionsView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/// A dialog for editing the field-level query options of an expression term. The controls are built from
/// the option definitions of the field's {@link QueryOptionSet}, which the field's data source provides.
public class QueryOptionsPresenter extends MyPresenterWidget<QueryOptionsView> {

    private QueryOptionSet queryOptionSet;

    @Inject
    public QueryOptionsPresenter(final EventBus eventBus, final QueryOptionsView view) {
        super(eventBus, view);
    }

    /// Shows the dialog for a field.
    ///
    /// @param field    The field of the term, which must have a {@link QueryOptionSet}.
    /// @param options  The options currently stored in the term, or null.
    /// @param consumer Receives the options to store in the term when the user presses OK: only those
    ///                 that differ from their defaults, or null if none do.
    public void show(final QueryField field,
                     final Map<String, String> options,
                     final Consumer<Map<String, String>> consumer) {
        queryOptionSet = field.getQueryOptionSet();
        getView().setDefinitions(queryOptionSet.getDefinitions(), this::updateDisabledOptions);
        getView().setValues(QueryOptionUtil.resolve(queryOptionSet, options));
        updateDisabledOptions();

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Query Options - " + field.getFldName())
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    e.hide();
                    if (e.isOk()) {
                        consumer.accept(QueryOptionUtil.normalise(queryOptionSet, getView().getValues()));
                    }
                })
                .fire();
    }

    private void updateDisabledOptions() {
        getView().setDisabledOptions(QueryOptionUtil.getDisabledOptionIds(queryOptionSet, getView().getValues()));
    }


    // --------------------------------------------------------------------------------


    public interface QueryOptionsView extends View, Focus {

        /// Creates a control for each option, replacing any existing controls.
        ///
        /// @param definitions   The options, in display order.
        /// @param changeHandler Called whenever the user changes an option's value.
        void setDefinitions(List<QueryOptionDefinition> definitions, Runnable changeHandler);

        /// @param values The value of each option, keyed by option id.
        void setValues(Map<String, String> values);

        /// @return The value of each option, keyed by option id.
        Map<String, String> getValues();

        /// Disables the given options, which don't apply to the other options' current values, setting them
        /// to their default values, and enables all others.
        ///
        /// @param disabledOptionIds The ids of the options to disable.
        void setDisabledOptions(Set<String> disabledOptionIds);
    }
}
