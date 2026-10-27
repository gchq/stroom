/*
 * Copyright 2025 Crown Copyright
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

package stroom.dashboard.client.component;

import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TableFilterComponentSettings;
import stroom.query.api.Column;
import stroom.query.api.ColumnRef;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// A Table Filter's conditional formatting tab ([MultiRules]): a set of rules for each column it
/// filters, told the columns by the Basic tab ([#setAllColumns], [#setSelectedColumns]).
public class MultiRulesController extends SettingsTabController {

    private final MultiRules view;

    private final Provider<RulesController> rulesProvider;
    private Map<String, List<ConditionalFormattingRule>> conditionalFormattingRules = new HashMap<>();
    private Set<ColumnRef> selectedColumns = new HashSet<>();
    private final Map<String, RulesController> rulesByColumnId = new HashMap<>();
    private Map<ColumnRef, Column> allColumns = new HashMap<>();
    private boolean dirty;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus      The application's event bus.
    /// @param view          The content.
    /// @param rulesProvider Makes a column's rules.
    @Inject
    public MultiRulesController(final EventBus eventBus,
                                final MultiRules view,
                                final Provider<RulesController> rulesProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.rulesProvider = rulesProvider;
    }

    /// @param allColumns The columns of the table filtered, by reference.
    public void setAllColumns(final Map<ColumnRef, Column> allColumns) {
        this.allColumns = allColumns;
    }

    /// Shows the rules of the columns chosen.
    ///
    /// @param selectedColumns The columns the filter shows.
    public void setSelectedColumns(final Set<ColumnRef> selectedColumns) {
        this.selectedColumns = selectedColumns;
        update();
    }

    private void update() {
        view.clearColumns();
        selectedColumns.stream().sorted(Comparator.comparing(ColumnRef::getName)).forEach(column -> {
            final List<ConditionalFormattingRule> rules = conditionalFormattingRules.get(column.getId());

            final Column col = allColumns.get(column);
            if (col != null) {
                final QueryField field = TablePresenter.buildDsField(col);

                final RulesController columnRules = rulesByColumnId
                        .computeIfAbsent(column.getId(), k -> {
                            final RulesController newRules = make(rulesProvider);
                            newRules.setOnDirtyChange(() -> setDirty(true));
                            return newRules;
                        });
                columnRules.read(Collections.singletonList(field), rules);

                view.addColumn(column.getName(), columnRules);
            }
        });
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        return dirty;
    }

    private void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
        }
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        final TableFilterComponentSettings settings = (TableFilterComponentSettings) componentConfig.getSettings();
        this.conditionalFormattingRules = new HashMap<>(NullSafe.map(settings.getConditionalFormattingRules()));
        reset();
        update();
    }

    // The columns' rules go when the filter's settings are read again
    private void reset() {
        rulesByColumnId.values().forEach(this::release);
        rulesByColumnId.clear();
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        final TableFilterComponentSettings oldSettings = (TableFilterComponentSettings) componentConfig.getSettings();
        final TableFilterComponentSettings newSettings = writeSettings(oldSettings);
        return componentConfig.copy().settings(newSettings).build();
    }

    private TableFilterComponentSettings writeSettings(final TableFilterComponentSettings settings) {
        return settings
                .copy()
                .conditionalFormattingRules(write())
                .build();
    }

    private Map<String, List<ConditionalFormattingRule>> write() {
        final Map<String, List<ConditionalFormattingRule>> map = new HashMap<>();
        selectedColumns.forEach(column -> {
            final RulesController rules = rulesByColumnId.get(column.getId());
            if (rules != null) {
                map.put(column.getId(), rules.write());
            }
        });
        return map;
    }
}
