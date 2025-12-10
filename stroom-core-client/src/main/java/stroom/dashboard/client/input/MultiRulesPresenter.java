/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.dashboard.client.input;

import stroom.dashboard.client.input.MultiRulesPresenter.MultiRulesView;
import stroom.dashboard.client.main.AbstractSettingsTabPresenter;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.client.table.cf.RulesPresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TableFilterComponentSettings;
import stroom.document.client.event.DirtyEvent;
import stroom.query.api.Column;
import stroom.query.api.ColumnRef;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiRulesPresenter extends AbstractSettingsTabPresenter<MultiRulesView> {

    private final Provider<RulesPresenter> rulesPresenterProvider;
    private Map<String, List<ConditionalFormattingRule>> conditionalFormattingRules = new HashMap<>();
    private Set<ColumnRef> selectedColumns = new HashSet<>();
    private final Map<String, RulesPresenter> rulesPresenterMap = new HashMap<>();
    private Map<ColumnRef, Column> allColumns = new HashMap<>();
    private boolean dirty;
    private final List<HandlerRegistration> registrations = new ArrayList<>();

    @Inject
    public MultiRulesPresenter(final EventBus eventBus,
                               final MultiRulesView view,
                               final Provider<RulesPresenter> rulesPresenterProvider) {
        super(eventBus, view);
        this.rulesPresenterProvider = rulesPresenterProvider;
    }

    @Override
    protected void onUnbind() {
        reset();
    }

    public void setAllColumns(final Map<ColumnRef, Column> allColumns) {
        this.allColumns = allColumns;
    }

    public void setSelectedColumns(final Set<ColumnRef> selectedColumns) {
        this.selectedColumns = selectedColumns;
        update();
    }

    private void update() {
        final FlowPanel outer = getView().getPanel();
        outer.clear();
        selectedColumns.stream().sorted(Comparator.comparing(ColumnRef::getName)).forEach(column -> {
            final List<ConditionalFormattingRule> rules = conditionalFormattingRules.get(column.getId());

            final Label label = new Label(column.getName(), false);
            label.addStyleName("table-filter-label");

            final Column col = allColumns.get(column);
            if (col != null) {
                final QueryField field = TablePresenter.buildDsField(col);

                final RulesPresenter rulesPresenter = rulesPresenterMap
                        .computeIfAbsent(column.getId(), k -> {
                            final RulesPresenter presenter = rulesPresenterProvider.get();
                            registrations.add(presenter.addDirtyHandler(e -> setDirty(true)));
                            return presenter;
                        });
                rulesPresenter.read(Collections.singletonList(field), rules);

                final FlowPanel panel = new FlowPanel();
                panel.addStyleName("table-filter-panel");
                panel.add(label);
                panel.add(rulesPresenter.getWidget());

                outer.add(panel);
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
            DirtyEvent.fire(this, dirty);
        }
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        final TableFilterComponentSettings settings = (TableFilterComponentSettings) componentConfig.getSettings();
        this.conditionalFormattingRules = new HashMap<>(NullSafe.map(settings.getConditionalFormattingRules()));
        reset();
        update();
    }

    private void reset() {
        rulesPresenterMap.clear();
        registrations.forEach(HandlerRegistration::removeHandler);
        registrations.clear();
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
            final RulesPresenter rulesPresenter = rulesPresenterMap.get(column.getId());
            if (rulesPresenter != null) {
                map.put(column.getId(), rulesPresenter.write());
            }
        });
        return map;
    }

    public interface MultiRulesView extends View {

        FlowPanel getPanel();
    }
}
