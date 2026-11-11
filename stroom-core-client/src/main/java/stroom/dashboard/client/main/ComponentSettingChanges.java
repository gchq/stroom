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

package stroom.dashboard.client.main;

import stroom.dashboard.client.query.QueryPresenter;
import stroom.dashboard.client.table.TableChange;
import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSelectionHandler;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.EmbeddedQueryComponentSettings;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.util.shared.NullSafe;
import stroom.util.shared.RandomId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Applies a change to one of a dashboard component's settings made outside its Settings dialog (e.g. a change
/// proposed from elsewhere and checked there), as the dialog would, and puts a component's name and settings back
/// as they were if the change is undone. Settings and values it doesn't know are refused.
public final class ComponentSettingChanges {

    /// The value given for no document or component.
    static final String NONE = "none";

    private ComponentSettingChanges() {
        // Static methods only.
    }

    /// @param component A component.
    /// @return Its name and settings now, to put back with [#restore(Component, ComponentConfig)], or null if it
    /// can't be.
    static ComponentConfig snapshot(final Component component) {
        if (!(component instanceof AbstractComponentPresenter<?>)) {
            return null;
        }
        // Not write(), which would write over the settings with what the Settings dialog last showed.
        final ComponentConfig config = component.getComponentConfig();
        if (config.getSettings() instanceof final TableComponentSettings table) {
            // A table's columns are changed in place, so they are copied.
            return config.copy().settings(table.copy()
                    .columns(new ArrayList<>(NullSafe.list(table.getColumns())))
                    .build()).build();
        }
        if (component instanceof final QueryPresenter query
            && config.getSettings() instanceof final QueryComponentSettings settings) {
            // The query keeps its expression in its editor until it is written.
            return config.copy().settings(settings.copy().expression(query.getExpression()).build()).build();
        }
        return config;
    }

    /// Brings the Settings dialogs of changed components up to date with their settings.
    ///
    /// @param components The components.
    static void syncSettingsDialogs(final Iterable<Component> components) {
        for (final Component component : components) {
            if (component instanceof final AbstractComponentPresenter<?> presenter) {
                presenter.syncSettingsDialog();
            }
        }
    }

    /// Puts a component's name and settings back as they were, as unsaved edits.
    ///
    /// @param component The component.
    /// @param config    Its name and settings, from [#snapshot(Component)].
    static void restore(final Component component, final ComponentConfig config) {
        if (config == null || !(component instanceof final AbstractComponentPresenter<?> presenter)) {
            return;
        }
        component.setComponentName(config.getName());
        final ComponentSettings settings = config.getSettings();
        if (settings instanceof final TableComponentSettings table) {
            TableChange.restore(component, table);
        } else {
            presenter.applySettings(settings);
            if (component instanceof final QueryPresenter query
                && settings instanceof final QueryComponentSettings querySettings) {
                query.setExpression(querySettings.getExpression());
            }
        }
    }

    /// Adds a selection handler to a component, so a chart's selection filters it: a query's search (re-run with the
    /// filter as the selection changes), or a table's rows (an embedded query takes either).
    ///
    /// @param component  The query, embedded query or table.
    /// @param mode       What it filters: [TableChange#QUERY_FILTER] or [TableChange#TABLE_FILTER].
    /// @param expression The filter, whose values name the chart's selection.
    /// @return Why it couldn't be added, or null if it was.
    static String addSelectionFilter(final Component component,
                                     final String mode,
                                     final ExpressionOperator expression) {
        if (!(component instanceof final AbstractComponentPresenter<?> presenter)) {
            return "The component can't be filtered.";
        }
        if (expression == null) {
            return "The filter can't be read.";
        }
        final ComponentSelectionHandler handler = ComponentSelectionHandler.builder()
                .id(RandomId.createId(5))
                .expression(expression)
                .enabled(true)
                .build();
        final boolean query = TableChange.QUERY_FILTER.equals(mode);
        final ComponentSettings current = presenter.getSettings();
        final ComponentSettings changed;
        if (current instanceof final TableComponentSettings table && !query) {
            changed = table.copy().selectionFilter(with(table.getSelectionFilter(), handler)).build();
        } else if (current instanceof final QueryComponentSettings settings && query) {
            changed = settings.copy().selectionQuery(with(settings.getSelectionQuery(), handler)).build();
        } else if (current instanceof final EmbeddedQueryComponentSettings embedded) {
            changed = query
                    ? embedded.copy().selectionQuery(with(embedded.getSelectionQuery(), handler)).build()
                    : embedded.copy().selectionFilter(with(embedded.getSelectionFilter(), handler)).build();
        } else {
            return query
                    ? "Only a query's search can be filtered by a query filter."
                    : "Only a table's rows can be filtered by a table filter.";
        }
        presenter.applySettings(changed);
        return null;
    }

    /// Removes a selection handler from a component: from its query filters or its table filters, whichever has it.
    ///
    /// @param component The query, embedded query or table.
    /// @param id        The handler's id.
    /// @return Why it couldn't be removed, or null if it was.
    static String removeSelectionFilter(final Component component, final String id) {
        if (!(component instanceof final AbstractComponentPresenter<?> presenter)) {
            return "The component has no selection filters.";
        }
        if (id == null) {
            return "The selection filter's id is missing.";
        }
        final ComponentSettings current = presenter.getSettings();
        final ComponentSettings changed;
        if (current instanceof final TableComponentSettings table && has(table.getSelectionFilter(), id)) {
            changed = table.copy().selectionFilter(without(table.getSelectionFilter(), id)).build();
        } else if (current instanceof final QueryComponentSettings settings && has(settings.getSelectionQuery(), id)) {
            changed = settings.copy().selectionQuery(without(settings.getSelectionQuery(), id)).build();
        } else if (current instanceof final EmbeddedQueryComponentSettings embedded
                   && has(embedded.getSelectionQuery(), id)) {
            changed = embedded.copy().selectionQuery(without(embedded.getSelectionQuery(), id)).build();
        } else if (current instanceof final EmbeddedQueryComponentSettings embedded
                   && has(embedded.getSelectionFilter(), id)) {
            changed = embedded.copy().selectionFilter(without(embedded.getSelectionFilter(), id)).build();
        } else {
            return "It has no selection filter '" + id + "'.";
        }
        presenter.applySettings(changed);
        return null;
    }

    /// Renames keys of a chart's selection in a component's selection filters (its query filters and its table
    /// filters), so they follow the chart once it is converted to another kind, e.g. a line chart's x as a bar
    /// chart's category.
    ///
    /// @param component A component.
    /// @param chartId   The chart's id.
    /// @param renamed   The keys renamed, by old key.
    /// @return True if any of its filters named a key renamed, and so was changed.
    static boolean renameSelections(final Component component,
                                    final String chartId,
                                    final Map<String, String> renamed) {
        if (!(component instanceof final AbstractComponentPresenter<?> presenter)) {
            return false;
        }
        final ComponentSettings changed = withSelectionsRenamed(presenter.getSettings(), chartId, renamed);
        if (changed == null) {
            return false;
        }
        presenter.applySettings(changed);
        return true;
    }

    /// @param settings A component's settings.
    /// @param chartId  A chart's id.
    /// @param renamed  Keys of the chart's selection renamed, by old key.
    /// @return The settings with the keys renamed in their selection filters (query and table), or null if none
    /// names one.
    static ComponentSettings withSelectionsRenamed(final ComponentSettings settings,
                                                   final String chartId,
                                                   final Map<String, String> renamed) {
        if (settings instanceof final TableComponentSettings table) {
            final List<ComponentSelectionHandler> filters = renamed(table.getSelectionFilter(), chartId, renamed);
            return filters == null
                    ? null
                    : table.copy().selectionFilter(filters).build();
        }
        if (settings instanceof final QueryComponentSettings query) {
            final List<ComponentSelectionHandler> filters = renamed(query.getSelectionQuery(), chartId, renamed);
            return filters == null
                    ? null
                    : query.copy().selectionQuery(filters).build();
        }
        if (settings instanceof final EmbeddedQueryComponentSettings embedded) {
            final List<ComponentSelectionHandler> queryFilters = renamed(embedded.getSelectionQuery(), chartId,
                    renamed);
            final List<ComponentSelectionHandler> tableFilters = renamed(embedded.getSelectionFilter(), chartId,
                    renamed);
            return queryFilters == null && tableFilters == null
                    ? null
                    : embedded.copy()
                            .selectionQuery(queryFilters != null
                                    ? queryFilters
                                    : embedded.getSelectionQuery())
                            .selectionFilter(tableFilters != null
                                    ? tableFilters
                                    : embedded.getSelectionFilter())
                            .build();
        }
        return null;
    }

    // The handlers with the chart's selection keys renamed, or null if none names one.
    private static List<ComponentSelectionHandler> renamed(final List<ComponentSelectionHandler> handlers,
                                                           final String chartId,
                                                           final Map<String, String> renamed) {
        boolean any = false;
        final List<ComponentSelectionHandler> list = new ArrayList<>();
        for (final ComponentSelectionHandler handler : NullSafe.list(handlers)) {
            final ExpressionOperator expression = DashboardDescriptions.withSelections(handler.getExpression(),
                    chartId, renamed);
            if (expression != handler.getExpression()) {
                any = true;
                list.add(handler.copy().expression(expression).build());
            } else {
                list.add(handler);
            }
        }
        return any
                ? list
                : null;
    }

    private static boolean has(final List<ComponentSelectionHandler> handlers, final String id) {
        return NullSafe.list(handlers).stream().anyMatch(handler -> id.equals(handler.getId()));
    }

    private static List<ComponentSelectionHandler> without(final List<ComponentSelectionHandler> handlers,
                                                           final String id) {
        final List<ComponentSelectionHandler> list = new ArrayList<>();
        for (final ComponentSelectionHandler handler : NullSafe.list(handlers)) {
            if (!id.equals(handler.getId())) {
                list.add(handler);
            }
        }
        return list;
    }

    private static List<ComponentSelectionHandler> with(final List<ComponentSelectionHandler> handlers,
                                                        final ComponentSelectionHandler handler) {
        final List<ComponentSelectionHandler> list = new ArrayList<>(NullSafe.list(handlers));
        list.add(handler);
        return list;
    }

    /// Changes one of a component's settings.
    ///
    /// @param component The component.
    /// @param setting   The setting's key.
    /// @param value     Its value: true or false, a number, numbers separated by commas, or a duration.
    /// @param ref       For a setting naming a component, that component's id, or null.
    /// @param doc       For a setting naming a document, the document, or null.
    /// @return Why it couldn't be changed, or null if it was.
    static String apply(final Component component,
                        final String setting,
                        final String value,
                        final String ref,
                        final DocRef doc) {
        if (!(component instanceof final AbstractComponentPresenter<?> presenter)) {
            return "The component's settings can't be changed.";
        }
        final ComponentSettings current = presenter.getSettings();
        final ComponentSettings changed;
        try {
            if (current instanceof final TableComponentSettings table) {
                changed = table(table, setting, value, ref, doc);
            } else if (current instanceof final QueryComponentSettings query) {
                changed = query(query, setting, value, doc);
            } else if (current instanceof final EmbeddedQueryComponentSettings embeddedQuery) {
                changed = embeddedQuery(embeddedQuery, setting, value);
            } else if (current instanceof final TextComponentSettings text) {
                changed = text(text, setting, value, ref, doc);
            } else {
                changed = null;
            }
        } catch (final RuntimeException e) {
            return "The value of " + setting + " isn't one it takes.";
        }
        if (changed == null) {
            return "The component has no setting '" + setting + "' that can be changed.";
        }
        presenter.applySettings(changed);
        return null;
    }

    /// @return A table's settings with one changed, or null if it has no such setting or the value is missing.
    static ComponentSettings table(final TableComponentSettings settings,
                                           final String setting,
                                           final String value,
                                           final String ref,
                                           final DocRef doc) {
        final TableComponentSettings.Builder builder = settings.copy();
        switch (setting) {
            case "pageSize" -> builder.pageSize(Integer.parseInt(value));
            case "maxResults" -> {
                final List<Long> limits = new ArrayList<>();
                for (final String part : value.split(",")) {
                    limits.add(Long.parseLong(part.trim()));
                }
                builder.maxResults(limits);
            }
            case "showDetail" -> builder.showDetail(Boolean.parseBoolean(value));
            case "extractValues" -> builder.extractValues(Boolean.parseBoolean(value));
            case "extractionPipeline" -> {
                if (doc == null) {
                    return null;
                }
                builder.useDefaultExtractionPipeline(false).extractionPipeline(doc).extractValues(true);
            }
            case "showValueFilters" -> builder.showValueFilters(Boolean.parseBoolean(value));
            case "query" -> {
                if (ref == null) {
                    return null;
                }
                builder.queryId(ref);
            }
            case "maxStringFieldLength" -> builder.maxStringFieldLength(Integer.parseInt(value))
                    .overrideMaxStringFieldLength(true);
            default -> {
                return null;
            }
        }
        return builder.build();
    }

    /// @return A query's settings with one changed, or null if it has no such setting or the value is missing.
    static ComponentSettings query(final QueryComponentSettings settings,
                                           final String setting,
                                           final String value,
                                           final DocRef doc) {
        final Automate automate = settings.getAutomate() != null
                ? settings.getAutomate()
                : Automate.builder().build();
        final QueryComponentSettings.Builder builder = settings.copy();
        switch (setting) {
            case "dataSource" -> {
                if (doc == null) {
                    return null;
                }
                builder.dataSource(doc);
            }
            case "queryOnOpen" -> builder.automate(automate.copy().open(Boolean.parseBoolean(value)).build());
            case "autoRefresh" -> builder.automate(automate.copy().refresh(Boolean.parseBoolean(value)).build());
            case "refreshInterval" -> builder.automate(automate.copy().refreshInterval(value).build());
            default -> {
                return null;
            }
        }
        return builder.build();
    }

    /// @return An embedded query's settings with one changed, or null if it has no such setting.
    static ComponentSettings embeddedQuery(final EmbeddedQueryComponentSettings settings,
                                           final String setting,
                                           final String value) {
        final Automate automate = settings.getAutomate() != null
                ? settings.getAutomate()
                : Automate.builder().build();
        final EmbeddedQueryComponentSettings.Builder builder = settings.copy();
        switch (setting) {
            case "queryOnOpen" -> builder.automate(automate.copy().open(Boolean.parseBoolean(value)).build());
            case "autoRefresh" -> builder.automate(automate.copy().refresh(Boolean.parseBoolean(value)).build());
            case "refreshInterval" -> builder.automate(automate.copy().refreshInterval(value).build());
            default -> {
                return null;
            }
        }
        return builder.build();
    }

    /// @return A text component's settings with one changed, or null if it has no such setting.
    static ComponentSettings text(final TextComponentSettings settings,
                                          final String setting,
                                          final String value,
                                          final String ref,
                                          final DocRef doc) {
        final TextComponentSettings.Builder builder = settings.copy();
        switch (setting) {
            case "table" -> builder.tableId(NONE.equals(value)
                    ? null
                    : ref);
            case "pipeline" -> builder.pipeline(NONE.equals(value)
                    ? null
                    : doc);
            case "showAsHtml" -> builder.showAsHtml(Boolean.parseBoolean(value));
            case "showStepping" -> builder.showStepping(Boolean.parseBoolean(value));
            default -> {
                return null;
            }
        }
        return builder.build();
    }
}
