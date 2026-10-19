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
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;

/// Applies a change to a dashboard component's settings that the AI chat proposed (see the server's
/// `DashboardSettings`), as the component's Settings dialog would, and puts a component's settings back as they
/// were if the change is undone. Values were checked by the server; anything else is refused here.
public final class AiComponentSettings {

    /// The value given for no document or component.
    static final String NONE = "none";

    private AiComponentSettings() {
        // Static methods only.
    }

    /// @param component A component.
    /// @return Its name and settings now, to put back with [#restore(Component, ComponentConfig)], or null if it
    /// can't be.
    static ComponentConfig snapshot(final Component component) {
        if (!(component instanceof AbstractComponentPresenter<?>)) {
            return null;
        }
        final ComponentConfig config = component.write();
        // A table's columns are changed in place, so they are copied.
        return config.getSettings() instanceof final TableComponentSettings table
                ? config.copy().settings(table.copy()
                        .columns(new ArrayList<>(NullSafe.list(table.getColumns())))
                        .build()).build()
                : config;
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

    /// Changes one of a component's settings.
    ///
    /// @param component The component.
    /// @param setting   The setting's key.
    /// @param value     Its value, as the server checked it.
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
