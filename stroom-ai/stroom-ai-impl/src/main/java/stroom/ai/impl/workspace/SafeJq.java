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

package stroom.ai.impl.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.Function;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Output;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/// Runs jq queries given by the model, locked down:
///
/// - no access to the environment ({@code env}, {@code $ENV}), other input ({@code input}, {@code inputs}),
///   debugging output or modules;
/// - no regular expression functions, as their matching cannot be stopped part way (use grep or the table tool);
/// - every built-in function checks the tool call's time limit, and counts towards a limit on the work a query may
///   do, so a query that loops or builds something huge is stopped.
public final class SafeJq {

    /// The most built-in function calls and outputs a query may make.
    static final long MAX_OPERATIONS = 5_000_000;

    private static final Set<String> BLOCKED = Set.of(
            "env/0",
            "input/0",
            "inputs/0",
            "input_filename/0",
            "input_line_number/0",
            "debug/0",
            "debug/1",
            "debug_scope/0",
            "stderr/0",
            "halt/0",
            "halt_error/0",
            "halt_error/1",
            "_match_impl/3",
            "_sub_impl/3",
            "test/1",
            "test/2",
            "match/1",
            "match/2",
            "capture/1",
            "capture/2",
            "scan/1",
            "scan/2",
            "split/2",
            "splits/1",
            "splits/2",
            "sub/2",
            "sub/3",
            "gsub/2",
            "gsub/3");

    private static final Scope ROOT_SCOPE = createRootScope();
    private static final ThreadLocal<AtomicLong> OPERATIONS = new ThreadLocal<>();

    private SafeJq() {
        // Static only.
    }

    private static Scope createRootScope() {
        final Scope builtins = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, builtins);
        final Scope scope = Scope.newEmptyScope();
        for (final Map.Entry<String, Function> entry : builtins.getLocalFunctions().entrySet()) {
            final String key = entry.getKey();
            scope.addFunction(key, BLOCKED.contains(key)
                    ? blocked(key)
                    : guarded(entry.getValue()));
        }
        // Make sure anything blocked that the loader did not define cannot be defined some other way.
        for (final String key : BLOCKED) {
            if (scope.getLocalFunctions().get(key) == null) {
                scope.addFunction(key, blocked(key));
            }
        }
        scope.setValue("ENV", JsonNodeFactory.instance.objectNode());
        scope.setModuleLoader(null);
        return scope;
    }

    private static Function guarded(final Function function) {
        return (scope, args, in, path, output, version) -> {
            tick();
            function.apply(scope, args, in, path, (value, valuePath) -> {
                tick();
                output.emit(value, valuePath);
            }, version);
        };
    }

    private static Function blocked(final String key) {
        final String name = key.substring(0, key.indexOf('/'));
        return (scope, args, in, path, output, version) -> {
            throw new JsonQueryException(name + " is not available here. Use grep or the table tool to match "
                                         + "patterns.");
        };
    }

    private static void tick() {
        final AtomicLong operations = OPERATIONS.get();
        if (operations != null && operations.incrementAndGet() > MAX_OPERATIONS) {
            throw new WorkspaceException("The jq query did too much work and was stopped. Make it simpler, or "
                                         + "work on a smaller part of the data.");
        }
        if (operations != null && operations.get() % 1024 == 0) {
            ToolDeadline.check();
        }
    }

    /// @param query A jq query.
    /// @return The query, compiled.
    /// @throws WorkspaceException if it is not a valid query.
    public static JsonQuery compile(final String query) {
        if (query == null || query.isBlank()) {
            throw new WorkspaceException("Give a jq query, e.g. '.events[] | .host'.");
        }
        if (query.length() > 2_000) {
            throw new WorkspaceException("The jq query may be at most 2000 characters.");
        }
        try {
            return JsonQuery.compile(query, Versions.JQ_1_6);
        } catch (final JsonQueryException e) {
            throw new WorkspaceException("'" + query + "' is not a valid jq query: " + e.getMessage());
        }
    }

    /// Runs a query on some JSON.
    ///
    /// @param query  The compiled query.
    /// @param input  The JSON.
    /// @param output Given each result.
    /// @throws WorkspaceException if the query fails or does too much work.
    public static void run(final JsonQuery query, final JsonNode input, final Output output) {
        final AtomicLong previous = OPERATIONS.get();
        OPERATIONS.set(new AtomicLong());
        try {
            query.apply(Scope.newChildScope(ROOT_SCOPE), input, value -> {
                tick();
                output.emit(value);
            });
        } catch (final JsonQueryException e) {
            throw new WorkspaceException("The jq query failed: " + e.getMessage());
        } catch (final StackOverflowError e) {
            throw new WorkspaceException("The jq query recursed too deeply and was stopped.");
        } finally {
            if (previous == null) {
                OPERATIONS.remove();
            } else {
                OPERATIONS.set(previous);
            }
        }
    }

    /// Runs a query on some JSON.
    ///
    /// @return The results.
    public static List<JsonNode> run(final JsonQuery query, final JsonNode input) {
        final List<JsonNode> results = new ArrayList<>();
        run(query, input, results::add);
        return results;
    }
}
