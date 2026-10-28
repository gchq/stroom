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

package stroom.statistics.impl.sql.client.component;

import stroom.docref.DocRef;
import stroom.statistics.impl.sql.client.State;
import stroom.statistics.impl.sql.client.State.Field;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// Shows a statistic store's fields in its Fields tab, from the state its tabs share, and adds,
/// renames and removes them. A change to the fields changes the custom roll-ups, so it tells
/// whatever shows them ([#setOnFieldsChange]).
public class StatisticsFieldListController extends Controller {

    private final StatisticsFieldList view;
    private final Provider<StatisticsFieldEditController> fieldEditProvider;
    private State state = new State();
    private Runnable onFieldsChange = () -> {
    };

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The tab.
    /// @param fieldEditProvider Makes the New Field and Edit Field dialog.
    @Inject
    public StatisticsFieldListController(final EventBus eventBus,
                                         final StatisticsFieldList view,
                                         final Provider<StatisticsFieldEditController> fieldEditProvider) {
        super(eventBus);
        this.view = view;
        this.fieldEditProvider = fieldEditProvider;
        initWidget(view);
        view.setOnNew(this::onAdd);
        view.setOnEdit(this::onEdit);
        view.setOnRemove(this::onRemove);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    /// @param state The fields and custom roll-ups the statistic store's tabs share.
    public void setState(final State state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    /// @param onFieldsChange What to run when the fields change, e.g. to show the custom roll-ups'
    ///                       columns again.
    public void setOnFieldsChange(final Runnable onFieldsChange) {
        this.onFieldsChange = Objects.requireNonNull(onFieldsChange, "onFieldsChange");
    }

    /// Shows the store's fields, from the shared state.
    ///
    /// @param docRef The store.
    /// @param doc    The store.
    public void read(final DocRef docRef, final StatisticStoreDoc doc) {
        view.setStoreName(docRef.getName());
        refresh();
    }

    private void onAdd() {
        final Field field = state.createField("");
        final Set<String> otherFieldNames = state.getFields().stream()
                .map(Field::getName)
                .collect(Collectors.toSet());
        fieldEditProvider.get().edit(this, "New Field", field, otherFieldNames, () -> {
            state.addField(field);
            onFieldsChanged();
        });
    }

    private void onEdit() {
        final Field field = view.getSelectionModel().getSelected();
        if (field != null) {
            // The other fields' names, which this one may not take
            final Set<String> otherFieldNames = state.getFields().stream()
                    .map(Field::getName)
                    .collect(Collectors.toSet());
            otherFieldNames.remove(field.getName());
            fieldEditProvider.get().edit(this, "Edit Field", field, otherFieldNames, this::onFieldsChanged);
        }
    }

    private void onRemove() {
        final List<Field> selected = view.getSelectionModel().getSelectedItems();
        if (!NullSafe.isEmptyCollection(selected)) {
            state.removeFields(selected);
            view.getSelectionModel().clear();
            refresh();
            onFieldsChange.run();
            markChanged();
        }
    }

    private void onFieldsChanged() {
        state.sortFields();
        refresh();
        onFieldsChange.run();
        markChanged();
    }

    private void refresh() {
        view.setFields(state.getFields());
    }
}
