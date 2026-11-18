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

import stroom.ai.impl.TestToolPermissionMatrix.Case;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/// Checks that the permissions each built-in tool declares ([stroom.ai.impl.tools.StroomTool#permissions()]) agree
/// with the checks it makes, so the metadata can't drift from what the tools do:
///
/// * the permissions [TestToolPermissionMatrix] finds a tool needs (its cases, which the matrix shows change what
///   the tool does) are declared;
/// * the permissions a tool asks Stroom about when it is called, with every permission held, are exactly those
///   declared, worked out by calling it as the matrix does (and with a few more calls that reach checks the
///   matrix's calls don't);
/// * a permission declared as required is one without which every call of the tool is refused or limited.
class TestToolPermissionMetadata {

    /// More calls, beyond those the matrix makes, that reach checks the matrix's calls don't.
    private static final Map<String, List<String>> PROBES = Map.of(
            // A UUID of no document: read_document then says whether it could have been a processor filter.
            "read_document", List.of("{\"uuid\":\"no-such-uuid\"}"),
            "processor_tasks", List.of("{\"mode\":\"failures\"}", "{\"mode\":\"summary\"}"));

    /// Permissions a tool declares that this world can't make it check, with why. Kept as short as possible.
    private static final Map<String, Map<AppPermission, String>> NOT_REACHED_HERE = Map.of(
            "processor_tasks", Map.of(AppPermission.VIEW_DATA_PERMISSION,
                    "checked only once there are Error streams to read, and this world has no processing tasks"),
            "explain_processing", Map.of(AppPermission.VIEW_SYSTEM_INFO_PERMISSION,
                    "checked only when nodes claim their own tasks, and this world uses the master queue"));

    /// Document permissions a tool declares that this world can't make it check, with why. Kept as short as
    /// possible.
    private static final Map<String, Map<DocumentPermission, String>> DOCUMENTS_NOT_REACHED_HERE = Map.of(
            "propose_visualisation", Map.of(
                    DocumentPermission.USE, "checked on the chart library's script once a chart is planned, which "
                                            + "needs a numeric column this world's dashboard table doesn't have",
                    DocumentPermission.EDIT, "checked only when a chart Stroom AI generated is replaced"));

    @TempDir
    Path tempDir;

    @Test
    void declaredPermissionsAgreeWithTheChecksTheToolsMake() {
        final List<String> toolNames;
        final Function<String, ToolPermissions> declared;
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            toolNames = world.toolNames();
            declared = world.builtInTools()::permissions;
        }
        assertThat(NOT_REACHED_HERE.keySet()).isSubsetOf(toolNames);
        assertThat(DOCUMENTS_NOT_REACHED_HERE.keySet()).isSubsetOf(toolNames);
        assertThat(PROBES.keySet()).isSubsetOf(toolNames);

        for (final String tool : toolNames) {
            final ToolPermissions permissions = declared.apply(tool);
            final List<Case> cases = TestToolPermissionMatrix.CASES.stream()
                    .filter(testCase -> testCase.tool().equals(tool))
                    .toList();

            // What the matrix finds the tool needs is declared.
            final Set<AppPermission> matrixApp = EnumSet.noneOf(AppPermission.class);
            final Set<DocumentPermission> matrixDocuments = EnumSet.noneOf(DocumentPermission.class);
            cases.stream()
                    .flatMap(testCase -> testCase.needs().stream())
                    .forEach(need -> {
                        if (need.appPermission() != null) {
                            matrixApp.add(need.appPermission());
                        } else {
                            matrixDocuments.add(need.documentPermission());
                        }
                    });
            assertThat(permissions.appPermissions())
                    .as("the app permissions %s declares, against the matrix", tool)
                    .containsAll(matrixApp);
            assertThat(permissions.documentPermissions())
                    .as("the document permissions %s declares, against the matrix", tool)
                    .containsAll(matrixDocuments);

            // What the tool asks about when called is exactly what it declares.
            final List<String> calls = calls(tool, cases);
            final Set<AppPermission> checkedApp = EnumSet.noneOf(AppPermission.class);
            final Set<DocumentPermission> checkedDocuments = EnumSet.noneOf(DocumentPermission.class);
            for (final String arguments : calls) {
                try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
                    world.prepareFor(tool);
                    world.run(tool, arguments);
                    checkedApp.addAll(world.appPermissionsChecked());
                    checkedDocuments.addAll(world.documentPermissionsChecked());
                }
            }
            final Set<AppPermission> notReached = NOT_REACHED_HERE.getOrDefault(tool, Map.of()).keySet();
            for (final AppPermission permission : notReached) {
                assertThat(checkedApp)
                        .as("%s is said not to check %s here, which it now does", tool, permission)
                        .doesNotContain(permission);
            }
            checkedApp.addAll(notReached);
            final Set<DocumentPermission> documentsNotReached = DOCUMENTS_NOT_REACHED_HERE
                    .getOrDefault(tool, Map.of()).keySet();
            for (final DocumentPermission permission : documentsNotReached) {
                assertThat(checkedDocuments)
                        .as("%s is said not to check %s here, which it now does", tool, permission)
                        .doesNotContain(permission);
            }
            checkedDocuments.addAll(documentsNotReached);
            assertThat(permissions.appPermissions())
                    .as("the app permissions %s declares, against those it checks when called with %s", tool,
                            calls)
                    .containsExactlyInAnyOrderElementsOf(checkedApp);
            assertThat(permissions.documentPermissions())
                    .as("the document permissions %s declares, against those it checks when called with %s",
                            tool, calls)
                    .containsExactlyInAnyOrderElementsOf(checkedDocuments);

            // Without a permission it requires, every call is refused or limited.
            for (final AppPermission required : permissions.requiredAppPermissions()) {
                assertThat(matrixApp)
                        .as("%s requires %s, so the matrix shows it", tool, required)
                        .contains(required);
                for (final String arguments : calls) {
                    assertThat(run(tool, arguments, without(required)))
                            .as("%s %s without %s", tool, arguments, required)
                            .isNotEqualTo(run(tool, arguments, EnumSet.allOf(AppPermission.class)));
                }
            }
        }
    }

    @Test
    void theMatrixCoversEveryToolThatDeclaresAPermission() {
        final List<String> withPermissions = new ArrayList<>();
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            for (final String tool : world.toolNames()) {
                if (!world.builtInTools().permissions(tool).isNone()) {
                    withPermissions.add(tool);
                }
            }
        }
        final Set<String> inMatrix = TestToolPermissionMatrix.CASES.stream()
                .map(Case::tool)
                .collect(Collectors.toSet());

        assertThat(inMatrix).containsExactlyInAnyOrderElementsOf(withPermissions);
    }

    @Test
    void aToolNotBuiltInHasNoPermissions() {
        try (BuiltInToolWorld world = BuiltInToolWorld.withEverything(tempDir)) {
            assertThat(world.builtInTools().permissions("some_mcp_tool")).isSameAs(ToolPermissions.NONE);
        }
    }

    // The arguments the tool is called with: those of each of the matrix's cases (or its fixed arguments if the
    // matrix has none), and any probes.
    private static List<String> calls(final String tool, final List<Case> cases) {
        final List<String> calls = new ArrayList<>();
        if (cases.isEmpty()) {
            calls.add(BuiltInToolWorld.ARGUMENTS.getOrDefault(tool, "{}"));
        } else {
            cases.stream()
                    .map(Case::arguments)
                    .filter(Objects::nonNull)
                    .forEach(calls::add);
        }
        calls.addAll(PROBES.getOrDefault(tool, List.of()));
        return calls.stream().distinct().toList();
    }

    private String run(final String tool, final String arguments, final Set<AppPermission> appPermissions) {
        try (BuiltInToolWorld world = new BuiltInToolWorld(tempDir, appPermissions,
                EnumSet.allOf(DocumentPermission.class), false)) {
            world.prepareFor(tool);
            return world.normalise(world.run(tool, arguments));
        }
    }

    private static Set<AppPermission> without(final AppPermission permission) {
        final Set<AppPermission> permissions = EnumSet.allOf(AppPermission.class);
        permissions.remove(permission);
        return permissions;
    }
}
