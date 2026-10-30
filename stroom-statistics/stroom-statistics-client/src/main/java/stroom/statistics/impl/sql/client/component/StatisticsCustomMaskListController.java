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

import stroom.alert.client.event.ConfirmEvent;
import stroom.docref.DocRef;
import stroom.statistics.impl.sql.client.StatisticFieldsAndMasks;
import stroom.statistics.impl.sql.client.StatisticFieldsAndMasks.Mask;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/// Shows a statistic store's custom roll-ups in its Custom Roll-ups tab, from the state its tabs
/// share, and adds, generates and removes them. There is always the permutation that rolls nothing
/// up.
public class StatisticsCustomMaskListController extends Controller {

    private final StatisticsCustomMaskList view;
    private StatisticFieldsAndMasks state = new StatisticFieldsAndMasks();

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The tab.
    @Inject
    public StatisticsCustomMaskListController(final EventBus eventBus, final StatisticsCustomMaskList view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnNew(this::onAdd);
        view.setOnAutoGenerate(this::onAutoGenerate);
        view.setOnRemove(this::onRemove);
        view.setOnMaskChange(this::markChanged);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    /// @param state The fields and custom roll-ups the statistic store's tabs share.
    public void setState(final StatisticFieldsAndMasks state) {
        this.state = Objects.requireNonNull(state, "state");
        view.setMasks(state.getMasks());
    }

    /// Shows the store's custom roll-ups, from the shared state.
    ///
    /// @param docRef The store.
    /// @param doc    The store.
    public void read(final DocRef docRef, final StatisticStoreDoc doc) {
        view.setStoreName(docRef.getName());
        update();
    }

    /// Shows the roll-ups again, with a column per field, e.g. once the fields change on the Fields
    /// tab.
    public void update() {
        state.addNoRollUpPerm();
        view.setFields(state.getFields());
        view.setMasks(state.getMasks());
    }

    private void onAdd() {
        state.addMask(new HashSet<>());
        view.setMasks(state.getMasks());
        markChanged();
    }

    private void onAutoGenerate() {
        ConfirmEvent.fire(this,
                "Are you sure you want to clear the existing roll-ups and generate all possible "
                + "permutations for the field list?",
                result -> {
                    if (result && !isDisposed()) {
                        state.allPermutations();
                        update();
                        markChanged();
                    }
                });
    }

    private void onRemove() {
        final List<Mask> selected = view.getSelectionModel().getSelectedItems();
        if (selected != null && !selected.isEmpty()) {
            state.removeMasks(selected);
            // The grid always has the no roll-up row, so removing the last permutation puts it back
            state.addNoRollUpPerm();
            view.getSelectionModel().clear();
            view.setMasks(state.getMasks());
            markChanged();
        }
    }
}
