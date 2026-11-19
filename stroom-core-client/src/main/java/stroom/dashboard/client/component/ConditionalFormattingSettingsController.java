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

package stroom.dashboard.client.component;

import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.main.PaneDataModifier;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.query.api.ConditionalFormattingRule;
import stroom.query.client.result.component.ConditionalFormattingRuleEditorController;
import stroom.query.client.result.component.ConditionalFormattingRules;
import stroom.query.client.result.component.ConditionalFormattingRulesController;
import stroom.query.shared.QueryTablePreferences;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.stream.Collectors;

/// The Conditional Formatting tab of a Table's or Embedded Query's settings: the shared
/// [ConditionalFormattingRulesController], reading the rules from the pane's config and writing
/// them back.
public class ConditionalFormattingSettingsController extends ConditionalFormattingRulesController
        implements PaneDataModifier {

    private DashboardContext dashboardContext;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus           The application's event bus.
    /// @param view               The content.
    /// @param ruleEditorProvider Makes the dialog that adds or edits a rule.
    @Inject
    public ConditionalFormattingSettingsController(
            final EventBus eventBus,
            final ConditionalFormattingRules view,
            final Provider<ConditionalFormattingRuleEditorController> ruleEditorProvider) {
        super(eventBus, view, ruleEditorProvider);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        if (componentConfig.getSettings() instanceof final TableComponentSettings tableComponentSettings) {
            read(tableComponentSettings);
        } else if (componentConfig.getSettings() instanceof
                final EmbeddedQueryComponentSettings embeddedQueryComponentSettings) {
            read(embeddedQueryComponentSettings.getQueryTablePreferences());
        }
    }

    private void read(final TableComponentSettings settings) {
        // We have to deal in field names (aka column names) here as all the
        // exp tree code only has a single field/term name so can't cope with working with
        // ids and mapping to col name for the ui.
        read(settings
                .getColumns()
                .stream()
                .map(ConditionalFormattingRulesController::buildDsField)
                .collect(Collectors.toList()), settings.getConditionalFormattingRules());
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        final List<ConditionalFormattingRule> rules = write();
        if (componentConfig.getSettings() instanceof final TableComponentSettings oldSettings) {
            final TableComponentSettings newSettings = oldSettings
                    .copy()
                    .conditionalFormattingRules(rules)
                    .build();
            return componentConfig.copy().settings(newSettings).build();
        } else if (componentConfig.getSettings() instanceof final EmbeddedQueryComponentSettings oldSettings) {
            final QueryTablePreferences queryTablePreferences =
                    write(oldSettings.getQueryTablePreferences());
            final EmbeddedQueryComponentSettings newSettings = oldSettings
                    .copy()
                    .queryTablePreferences(queryTablePreferences)
                    .build();
            return componentConfig
                    .copy()
                    .settings(newSettings)
                    .build();
        }
        throw new RuntimeException("Unexpected type");
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        return isDirty();
    }

    @Override
    public DashboardContext getDashboardContext() {
        return dashboardContext;
    }

    @Override
    public void setDashboardContext(final DashboardContext dashboardContext) {
        this.dashboardContext = dashboardContext;
    }
}
