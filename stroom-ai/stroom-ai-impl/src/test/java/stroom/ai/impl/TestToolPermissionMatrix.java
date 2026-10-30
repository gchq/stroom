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

package stroom.ai.impl;

import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/// Fixes, in a reference file, what each built-in tool that checks a permission does with and without it: the
/// permissions it needs, whether it refuses without each, and exactly what it says. Every tool is also called with
/// no permissions at all and as an administrator, and every tool is checked to still be offered to the model
/// whatever the user's permissions (the tools refuse when called; they are not hidden).
///
/// The tools run over [BuiltInToolWorld], where a document permission is held on every document and implies the
/// ones below it, as Stroom's do (View implies Use), so "without View" means Use only, and "without Use" means no
/// document permission at all.
class TestToolPermissionMatrix {

    private static final String REFERENCE = "permissions-audit-consent/tool-permissions.txt";
    private static final int MAX_SHOWN = 600;

    /// The tools whose output changes with the time, so can't be compared between runs.
    static final Set<String> TIME_TOOLS = Set.of("get_current_date", "get_current_time",
            "get_current_datetime");

    /// Each tool that checks a permission, with the arguments that reach the check and the permissions it needs.
    static final List<Case> CASES = List.of(
            new Case("config_properties", null, List.of(Need.MANAGE_PROPERTIES)),
            new Case("server_tasks", null, List.of(Need.MANAGE_TASKS)),
            new Case("fetch_events", null, List.of(Need.VIEW_DATA)),
            new Case("preview_extraction", null, List.of(Need.STEPPING, Need.VIEW_DATA, Need.VIEW)),
            new Case("stream_meta", "{\"feeds\":[\"TEST-FEED\"],\"parts\":true}", List.of(Need.VIEW_DATA)),
            new Case("processor_tasks", null, List.of(Need.MANAGE_PROCESSORS)),
            new Case("explain_processing", null, List.of(Need.MANAGE_PROCESSORS)),
            new Case("explain_retention", null, List.of(Need.MANAGE_POLICIES, Need.MANAGE_JOBS)),
            new Case("volumes", null, List.of(Need.MANAGE_VOLUMES)),
            new Case("folder_contents", null, List.of(Need.VIEW)),
            new Case("folder_contents", "{\"folder\":\"folder-uuid\",\"show\":[\"processors\"]}",
                    List.of(Need.VIEW, Need.MANAGE_PROCESSORS)),
            new Case("document_links", null, List.of(Need.VIEW)),
            new Case("read_document", null, List.of(Need.VIEW)),
            new Case("document_info", null, List.of(Need.VIEW)),
            new Case("document_documentation", null, List.of(Need.VIEW)),
            new Case("get_instructions", null, List.of(Need.VIEW)),
            new Case("xslt_lookups", null, List.of(Need.VIEW)),
            new Case("find_in_content", null, List.of(Need.VIEW)),
            new Case("list_data_sources", null, List.of(Need.VIEW, Need.USE)),
            new Case("describe_data_source", null, List.of(Need.VIEW, Need.USE)),
            new Case("validate_stroomql", null, List.of(Need.USE)),
            new Case("search_events", "{\"index\":\"index-uuid\",\"where\":[{\"field\":\"UserId\","
                                      + "\"value\":\"alice\"}]}", List.of(Need.USE)),
            new Case("propose_query", null, List.of(Need.USE)),
            new Case("propose_dashboard", null, List.of(Need.USE)),
            new Case("propose_query_change", null, List.of(Need.VIEW, Need.USE)),
            new Case("propose_code_change", null, List.of(Need.VIEW)),
            new Case("propose_dashboard_change", null, List.of(Need.VIEW)),
            new Case("propose_extraction_pipeline", null, List.of(Need.VIEW)),
            new Case("propose_documentation_change", null, List.of(Need.VIEW)),
            new Case("current_results", null, List.of(Need.USE)),
            new Case("elastic_index_templates", null, List.of(Need.VIEW)));

    /// What the reference file says of tools whose checks are made elsewhere, outside this world.
    private static final Map<String, String> NOTES = Map.of(
            "run_stroomql", "its data source is checked by QueryService when the search runs, mocked here",
            "ask_user", "it fails here, as McpElicitations is mocked");

    @TempDir
    Path tempDir;

    @Test
    void eachToolRefusesWithoutThePermissionItNeeds() {
        final StringBuilder sb = new StringBuilder();
        sb.append("""
                # What each built-in tool that checks a permission does with and without it.
                # 'everything' is every app permission and Owner on every document, but not an administrator;
                # 'without X' is everything but X (without View is Use only; without Use is no document
                # permission); 'nothing' is no permission at all; 'admin' is an administrator granted nothing.
                # Output that differs from 'everything' is shown in full (up to %d characters).
                """.formatted(MAX_SHOWN));
        final Set<String> covered = new TreeSet<>();
        for (final Case testCase : CASES) {
            covered.add(testCase.tool());
            final String arguments = testCase.arguments();
            sb.append("\n## ").append(testCase.tool()).append(' ').append(arguments).append('\n');
            sb.append("Needs: ").append(String.join(", ", testCase.needs().stream().map(Need::label).toList()))
                    .append('\n');
            final String withEverything = run(testCase, everything(), allDocumentPermissions(), false);
            sb.append("- everything: ").append(firstLine(withEverything)).append('\n');
            for (final Need need : testCase.needs()) {
                final String without = run(testCase, need.appPermissionsWithout(), need.documentPermissionsWithout(),
                        false);
                // The tool does check it: its output differs without it.
                assertThat(without)
                        .as("%s without %s", testCase.tool(), need.label())
                        .isNotEqualTo(withEverything);
                sb.append("- without ").append(need.label()).append(": ").append(compare(without, withEverything))
                        .append('\n');
            }
            final String nothing = run(testCase, Set.of(), Set.of(), false);
            sb.append("- nothing: ").append(compare(nothing, withEverything)).append('\n');
            final String admin = run(testCase, Set.of(), Set.of(), true);
            sb.append("- admin: ").append(compare(admin, withEverything)).append('\n');
        }

        // Every other tool has no check of its own: what it does is the same whatever the user's permissions.
        sb.append("\n## Tools with no permission check of their own (the same with everything and with nothing)\n");
        try (BuiltInToolWorld names = BuiltInToolWorld.withEverything(tempDir)) {
            for (final String tool : names.toolNames()) {
                if (covered.contains(tool)) {
                    continue;
                }
                if (TIME_TOOLS.contains(tool)) {
                    sb.append("- ").append(tool).append(" (its output is the time, so not compared)\n");
                    continue;
                }
                final Case testCase = new Case(tool, null, List.of());
                final String withEverything = run(testCase, everything(), allDocumentPermissions(), false);
                final String nothing = run(testCase, Set.of(), Set.of(), false);
                sb.append("- ").append(tool).append(": ").append(withEverything.equals(nothing)
                        ? "same"
                        : "DIFFERS: " + shown(nothing));
                if (NOTES.containsKey(tool)) {
                    sb.append(" (").append(NOTES.get(tool)).append(')');
                }
                sb.append('\n');
            }
        }
        GoldenFiles.assertMatches(REFERENCE, sb.toString());
    }

    @Test
    void everyToolIsOfferedWhateverTheUsersPermissions() {
        final List<String> withEverything;
        final List<String> all;
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            all = world.toolNames();
            withEverything = offered(world);
        }
        final List<String> withNothing;
        try (BuiltInToolWorld world = new BuiltInToolWorld(tempDir, Set.of(), Set.of(), false)) {
            withNothing = offered(world);
        }

        // A tool in a group is offered as the tool that turns its group on.
        assertThat(new TreeSet<>(withEverything)).containsExactlyElementsOf(OfferedToolNames.withNoGroupsOn(all));
        assertThat(withNothing).containsExactlyElementsOf(withEverything);
    }

    private List<String> offered(final BuiltInToolWorld world) {
        final McpAgentChat agentChat = world.agentChat(Mockito.mock(ToolConsent.class),
                Mockito.mock(AiChatRunManager.class));
        final BuiltInToolWorld.ScriptedModel model = new BuiltInToolWorld.ScriptedModel(List.of());
        BuiltInToolWorld.chat(agentChat, model, McpToolset.EMPTY);
        return model.firstOffered();
    }

    private String run(final Case testCase,
                       final Set<AppPermission> appPermissions,
                       final Set<DocumentPermission> documentPermissions,
                       final boolean admin) {
        try (BuiltInToolWorld world = new BuiltInToolWorld(tempDir, appPermissions, documentPermissions, admin)) {
            world.prepareFor(testCase.tool());
            return world.normalise(world.run(testCase.tool(), testCase.arguments()));
        }
    }

    private static String compare(final String output, final String withEverything) {
        return output.equals(withEverything)
                ? "runs (the same as with everything)"
                : "REFUSED or limited: " + shown(output);
    }

    private static String firstLine(final String output) {
        final String first = output.lines().findFirst().orElse("");
        return "runs: " + (first.length() > 200
                ? first.substring(0, 200) + "…"
                : first);
    }

    private static String shown(final String output) {
        final String text = output.length() > MAX_SHOWN
                ? output.substring(0, MAX_SHOWN) + "…"
                : output;
        return text.strip().replace("\n", "\n    ");
    }

    private static Set<AppPermission> everything() {
        return EnumSet.allOf(AppPermission.class);
    }

    private static Set<DocumentPermission> allDocumentPermissions() {
        return EnumSet.allOf(DocumentPermission.class);
    }

    /// A tool, the arguments it is called with, and the permissions it needs.
    ///
    /// @param tool      The tool's name.
    /// @param given     Its arguments, or null for its fixed arguments ([BuiltInToolWorld#ARGUMENTS]).
    /// @param needs     The permissions it needs.
    record Case(String tool, String given, List<Need> needs) {

        /// @return The arguments the tool is called with.
        String arguments() {
            return given != null
                    ? given
                    : BuiltInToolWorld.ARGUMENTS.getOrDefault(tool, "{}");
        }
    }

    /// A permission a tool can need.
    enum Need {
        MANAGE_PROPERTIES("Manage Properties", AppPermission.MANAGE_PROPERTIES_PERMISSION, null),
        MANAGE_TASKS("Manage Tasks", AppPermission.MANAGE_TASKS_PERMISSION, null),
        MANAGE_PROCESSORS("Manage Processors", AppPermission.MANAGE_PROCESSORS_PERMISSION, null),
        MANAGE_VOLUMES("Manage Volumes", AppPermission.MANAGE_VOLUMES_PERMISSION, null),
        MANAGE_POLICIES("Manage Policies", AppPermission.MANAGE_POLICIES_PERMISSION, null),
        MANAGE_JOBS("Manage Jobs", AppPermission.MANAGE_JOBS_PERMISSION, null),
        VIEW_DATA("View Data", AppPermission.VIEW_DATA_PERMISSION, null),
        STEPPING("Stepping", AppPermission.STEPPING_PERMISSION, null),
        VIEW("View (document)", null, DocumentPermission.VIEW),
        USE("Use (document)", null, DocumentPermission.USE);

        private final String label;
        private final AppPermission appPermission;
        private final DocumentPermission documentPermission;

        Need(final String label, final AppPermission appPermission, final DocumentPermission documentPermission) {
            this.label = label;
            this.appPermission = appPermission;
            this.documentPermission = documentPermission;
        }

        /// @return The permission's name, as Stroom shows it.
        String label() {
            return label;
        }

        /// @return The app permission, or null if this is a document permission.
        AppPermission appPermission() {
            return appPermission;
        }

        /// @return The document permission, or null if this is an app permission.
        DocumentPermission documentPermission() {
            return documentPermission;
        }

        /// @return Every app permission but this one.
        Set<AppPermission> appPermissionsWithout() {
            final Set<AppPermission> permissions = EnumSet.allOf(AppPermission.class);
            if (appPermission != null) {
                permissions.remove(appPermission);
            }
            return permissions;
        }

        /// @return The document permissions held without this one. Document permissions imply those below them,
        /// so without one the user holds only those below it.
        Set<DocumentPermission> documentPermissionsWithout() {
            if (documentPermission == null) {
                return EnumSet.allOf(DocumentPermission.class);
            }
            final List<DocumentPermission> below = new ArrayList<>();
            for (final DocumentPermission permission : DocumentPermission.values()) {
                if (!permission.isEqualOrHigher(documentPermission)) {
                    below.add(permission);
                }
            }
            return below.isEmpty()
                    ? EnumSet.noneOf(DocumentPermission.class)
                    : EnumSet.copyOf(below);
        }
    }
}
