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

package stroom.ai.impl.vis;

import stroom.ai.impl.vis.ChartLibraries.EChartsInstall;
import stroom.ai.shared.AiCreateVisualisation;
import stroom.dashboard.impl.visualisation.VisualisationStore;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/// Prepares a Visualisation the agent planned, when the user clicks its `create_visualisation` button: puts the
/// ECharts adapter in place, builds the Visualisation's settings from the plan, and says where to suggest creating
/// it (the dashboard's or query's folder) and how to wire it up. The browser then opens Stroom's New Visualisation
/// dialog; nothing is created here except the adapter.
///
/// A chart that replaces one Stroom AI generated earlier is different: its Visualisation is rewritten for the new
/// chart and renamed here, saved at once, if the user may edit it; the browser then shows it in the same vis
/// component, unsaved.
public class VisualisationActions {

    private final ChartLibraries chartLibraries;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<ExplorerService> explorerServiceProvider;
    private final Provider<VisualisationStore> visualisationStoreProvider;
    private final SecurityContext securityContext;

    @Inject
    public VisualisationActions(final ChartLibraries chartLibraries,
                                final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                                final Provider<ExplorerService> explorerServiceProvider,
                                final Provider<VisualisationStore> visualisationStoreProvider,
                                final SecurityContext securityContext) {
        this.chartLibraries = chartLibraries;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.explorerServiceProvider = explorerServiceProvider;
        this.visualisationStoreProvider = visualisationStoreProvider;
        this.securityContext = securityContext;
    }

    /// Prepares a planned Visualisation.
    ///
    /// @param target   The dashboard or query it is for.
    /// @param name     The name to suggest for it.
    /// @param planJson The plan, as kept in the button.
    /// @return What the browser needs to create it and wire it up; for a chart replacing another, its rewritten
    /// Visualisation and the vis component to show it in.
    /// @throws IllegalStateException saying why it can't be made now, e.g. ECharts is no longer installed, or the
    /// user can't edit the Visualisation it replaces.
    public AiCreateVisualisation prepare(final DocRef target, final String name, final String planJson) {
        final VisualisationPlan plan;
        try {
            plan = VisualisationPlan.fromJson(planJson);
        } catch (final IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        final ChartKind kind = plan.chartKind();
        final EChartsInstall echarts = chartLibraries.findECharts()
                .orElseThrow(() -> new IllegalStateException("ECharts is no longer installed as a Script doc, so "
                                                             + "the chart can't be drawn. Ask an administrator to "
                                                             + "install it."));
        if (!chartLibraries.currentUserCanUse(echarts.script())) {
            throw new IllegalStateException("You don't have Use permission on Script '"
                                            + echarts.script().getName() + "' (ECharts), so the chart wouldn't "
                                            + "load for you. Ask an administrator for it.");
        }
        final DocRef adapter = chartLibraries.ensureAdapter(echarts);
        final String settings = EChartsTemplate.settingsJson(kind, plan.mappings(),
                VisualisationPlan.normaliseStyle(kind, plan.style()),
                VisualisationPlan.normaliseOptions(plan.options()));
        final ExplorerNode folder = explorerNodeServiceProvider.get().getParent(target)
                .map(VisualisationActions::inExplorerTree)
                .orElse(null);
        final String description = "Generated by Stroom AI: a " + kind.displayName().toLowerCase()
                                   + (plan.isEmbeddedQuery()
                ? " of embedded query '" + plan.table() + "' on dashboard '" + target.getName() + "'"
                : plan.isQuery()
                ? " of query '" + target.getName() + "'"
                : " of table '" + plan.table() + "' on dashboard '" + target.getName() + "'")
                                   + ", drawn with ECharts " + echarts.version() + ". Change its columns and "
                                   + "style in its settings, or ask Stroom AI to tailor it.";

        if (plan.isEmbeddedQuery()) {
            // The browser puts the show clause in the embedded query: tableId is its component id.
            return new AiCreateVisualisation(name, folder, EChartsTemplate.FUNCTION_NAME, adapter, settings,
                    description, target, plan.componentId(), plan.table(), null, null, null, plan.snapshotHash(),
                    showArguments(kind, plan.mappings()), plan.baseQuery(), null, null);
        }
        if (plan.isQuery()) {
            return new AiCreateVisualisation(name, folder, EChartsTemplate.FUNCTION_NAME, adapter, settings,
                    description, target, null, null, null, null, null, null, showArguments(kind, plan.mappings()),
                    plan.baseQuery(), null, null);
        }
        if (plan.isReplacement()) {
            final DocRef replaced = rewrite(plan.replace().visualisation(), name, adapter, settings, description);
            // The component's name is only given if it changes.
            final String componentName = plan.componentName().equals(plan.replace().componentName())
                    ? null
                    : plan.componentName();
            return new AiCreateVisualisation(replaced.getName(), null, EChartsTemplate.FUNCTION_NAME, adapter,
                    settings, description, target, plan.componentId(), plan.table(), componentName,
                    replacementSettings(kind, plan.mappings()), null, plan.snapshotHash(), null, null, replaced,
                    plan.replace().componentId());
        }
        return new AiCreateVisualisation(name, folder, EChartsTemplate.FUNCTION_NAME, adapter, settings,
                description, target, plan.componentId(), plan.table(), plan.componentName(),
                componentSettings(kind, plan.mappings()),
                NullSafe.nonBlankStringElse(plan.placement(), VisualisationPlan.BELOW),
                plan.snapshotHash(), null, null, null, null);
    }

    // Rewrites a Visualisation Stroom AI generated for the new chart, and renames it, as the user. Saved at once.
    private DocRef rewrite(final DocRef visualisation,
                           final String name,
                           final DocRef adapter,
                           final String settings,
                           final String description) {
        if (!chartLibraries.isGenerated(visualisation)) {
            throw new IllegalStateException("Visualisation '" + visualisation.getName() + "' no longer exists, or "
                                            + "isn't a chart Stroom AI generated, so it can't be replaced.");
        }
        if (!securityContext.hasDocumentPermission(visualisation, DocumentPermission.EDIT)) {
            throw new IllegalStateException("You don't have Edit permission on Visualisation '"
                                            + visualisation.getName() + "', so it can't be replaced.");
        }
        final VisualisationStore store = visualisationStoreProvider.get();
        final VisualisationDoc doc = store.readDocument(visualisation);
        store.writeDocument(doc.copy()
                .functionName(EChartsTemplate.FUNCTION_NAME)
                .scriptRef(adapter)
                .settings(settings)
                .description(description)
                .build());
        if (name.equals(doc.getName())) {
            return doc.asDocRef();
        }
        final ExplorerNode node = explorerNodeServiceProvider.get().getNode(visualisation)
                .orElseThrow(() -> new IllegalStateException("Visualisation '" + visualisation.getName()
                                                             + "' isn't in the explorer tree, so it can't be "
                                                             + "renamed."));
        return explorerServiceProvider.get().rename(node, name).getDocRef();
    }

    /// The settings of a vis component whose chart is replaced: the column mapped to each role of the new kind,
    /// and every other kind's roles emptied, so columns mapped for the old chart don't linger.
    ///
    /// @param kind     The new chart kind.
    /// @param mappings The column mapped to each of its roles, by role id.
    /// @return The settings JSON, an empty value removing a setting.
    static String replacementSettings(final ChartKind kind, final Map<String, String> mappings) {
        final Map<String, String> settings = new LinkedHashMap<>();
        for (final ChartKind other : ChartKind.values()) {
            for (final ChartRole role : other.roles()) {
                settings.put(role.id(), "");
            }
        }
        for (final ChartRole role : kind.roles()) {
            settings.put(role.id(), NullSafe.string(NullSafe.get(mappings, map -> map.get(role.id()))).strip());
        }
        return JsonUtil.writeValueAsString(settings, false);
    }

    // A folder as the explorer tree holds it, under the System root, so Stroom's New Visualisation dialog finds
    // and selects it: the tree tells nodes apart by their root as well as their UUID.
    static ExplorerNode inExplorerTree(final ExplorerNode folder) {
        return folder.copy()
                .rootNodeUuid(ExplorerConstants.SYSTEM_DOC_REF.getUuid())
                .build();
    }

    /// The vis component's settings: the column mapped to each role, in role order.
    ///
    /// @param kind     The chart kind.
    /// @param mappings The column mapped to each role, by role id.
    /// @return The settings JSON.
    static String componentSettings(final ChartKind kind, final Map<String, String> mappings) {
        final Map<String, String> settings = new LinkedHashMap<>();
        for (final ChartRole role : kind.roles()) {
            final String column = NullSafe.get(mappings, map -> map.get(role.id()));
            if (NullSafe.isNonBlankString(column)) {
                settings.put(role.id(), column);
            }
        }
        return JsonUtil.writeValueAsString(settings, false);
    }

    /// The arguments of a StroomQL `show` clause: each mapped role and its column, the column quoted.
    ///
    /// @param kind     The chart kind.
    /// @param mappings The column mapped to each role, by role id.
    /// @return The arguments, e.g. `source = "Sender", target = "Recipient"`.
    static String showArguments(final ChartKind kind, final Map<String, String> mappings) {
        return kind.roles().stream()
                .filter(role -> NullSafe.isNonBlankString(NullSafe.get(mappings, map -> map.get(role.id()))))
                .map(role -> role.id() + " = " + quote(mappings.get(role.id())))
                .collect(Collectors.joining(", "));
    }

    /// Quotes text as a StroomQL string.
    ///
    /// @param text The text.
    /// @return The text in double quotes, with any quotes or backslashes in it escaped.
    public static String quote(final String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
