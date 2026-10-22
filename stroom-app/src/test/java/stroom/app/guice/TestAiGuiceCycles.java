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

package stroom.app.guice;

import stroom.config.app.AppConfig;
import stroom.config.app.Config;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Stage;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.LinkedKeyBinding;
import io.dropwizard.core.setup.Environment;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Checks that the AI chat's and MCP endpoint's classes don't depend on each other in a circle that only a
/// `Provider` would break. Guice can only break such a circle by proxying an interface, and fails when the
/// object is first made if a class is in it (seen: BuildPlan, StroomBuildFacts, BuildChecks, GeneratedDrift
/// and TranslationDocs, once MappingDrift was no longer an interface between them). Building the injector in
/// `Stage.TOOL` makes no objects, so it doesn't find this itself: the graph is walked instead.
class TestAiGuiceCycles {

    private static final List<String> PACKAGES = List.of("stroom.ai.", "stroom.mcp.");

    @Test
    void theAiAndMcpClassesHaveNoCircularDependencies() {
        final BootStrapModule bootStrapModule = new BootStrapModule(new Config(new AppConfig()),
                new Environment("Test Environment"), Path.of("DUMMY"));
        final Injector injector = Guice.createInjector(Stage.TOOL, new AbstractModule() {
            @Override
            protected void configure() {
                install(bootStrapModule);
                install(new AppModule());
            }
        });

        final Map<Key<?>, Set<Key<?>>> graph = dependencyGraph(injector);
        final List<List<Key<?>>> cycles = cycles(graph);

        assertThat(cycles.stream().map(TestAiGuiceCycles::describe).toList())
                .as("Circular dependencies between AI/MCP classes (inject a Provider to break one)")
                .isEmpty();
    }

    private static Map<Key<?>, Set<Key<?>>> dependencyGraph(final Injector injector) {
        final Map<Key<?>, Set<Key<?>>> graph = new HashMap<>();
        for (final Binding<?> binding : injector.getAllBindings().values()) {
            final Key<?> key = binding.getKey();
            if (!isOurs(key)) {
                continue;
            }
            final Set<Key<?>> dependencies = graph.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
            if (binding instanceof final LinkedKeyBinding<?> linked) {
                dependencies.add(linked.getLinkedKey());
            }
            if (binding instanceof final HasDependencies hasDependencies) {
                for (final Dependency<?> dependency : hasDependencies.getDependencies()) {
                    // A Provider is not made until it is asked for, so it breaks a circle.
                    if (isOurs(dependency.getKey())) {
                        dependencies.add(dependency.getKey());
                    }
                }
            }
        }
        return graph;
    }

    private static boolean isOurs(final Key<?> key) {
        final String name = key.getTypeLiteral().getRawType().getName();
        return PACKAGES.stream().anyMatch(name::startsWith);
    }

    private static List<List<Key<?>>> cycles(final Map<Key<?>, Set<Key<?>>> graph) {
        final List<List<Key<?>>> cycles = new ArrayList<>();
        final Set<Key<?>> done = new HashSet<>();
        for (final Key<?> start : graph.keySet()) {
            walk(start, graph, new ArrayList<>(), new HashSet<>(), done, cycles);
        }
        return cycles;
    }

    private static void walk(final Key<?> key,
                             final Map<Key<?>, Set<Key<?>>> graph,
                             final List<Key<?>> path,
                             final Set<Key<?>> onPath,
                             final Set<Key<?>> done,
                             final List<List<Key<?>>> cycles) {
        if (onPath.contains(key)) {
            final List<Key<?>> cycle = new ArrayList<>(path.subList(path.indexOf(key), path.size()));
            cycle.add(key);
            cycles.add(cycle);
            return;
        }
        if (done.contains(key)) {
            return;
        }
        path.add(key);
        onPath.add(key);
        for (final Key<?> dependency : graph.getOrDefault(key, Set.of())) {
            walk(dependency, graph, path, onPath, done, cycles);
        }
        onPath.remove(key);
        path.removeLast();
        done.add(key);
    }

    private static String describe(final List<Key<?>> cycle) {
        return String.join(" -> ", cycle.stream()
                .map(key -> key.getTypeLiteral().getRawType().getSimpleName())
                .toList());
    }
}
