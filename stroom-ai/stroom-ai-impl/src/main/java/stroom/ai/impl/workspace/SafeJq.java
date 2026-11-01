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

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.Expression;
import net.thisptr.jackson.jq.Function;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Output;
import net.thisptr.jackson.jq.PathOutput;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Version;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.exception.JsonQueryException;
import net.thisptr.jackson.jq.path.Path;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Runs jq queries given by the model, locked down:
///
/// - no access to the environment ({@code env}, {@code $ENV}), other input ({@code input}, {@code inputs}),
///   debugging output or modules;
/// - regular expression functions ({@code test}, {@code match}, {@code sub}...) match through [SafeJqRegex], so a
///   pattern that backtracks badly is stopped at the time limit;
/// - every built-in function checks the tool call's time limit, and counts towards a limit on the work a query may
///   do, so a query that loops or builds something huge is stopped.
public final class SafeJq {

    /// The most built-in function calls and outputs a query may make.
    static final long MAX_OPERATIONS = 5_000_000;
    /// The longest a query may be, in characters: long enough for a query that rebuilds a large document.
    static final int MAX_QUERY_LENGTH = 10_000;

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
            "halt_error/1");

    private static final Scope ROOT_SCOPE = createRootScope();
    // jq's errors for a path that doesn't fit the data.
    private static final Pattern INDEXED = Pattern.compile("Cannot index (\\w+) with (?:string )?(\"[^\"]*\"|\\w+)");
    private static final Pattern ITERATED = Pattern.compile("Cannot iterate over (\\w+)");
    private static final Pattern NOT_APPLICABLE = Pattern.compile("is not applicable to NULL");
    private static final ThreadLocal<AtomicLong> OPERATIONS = new ThreadLocal<>();

    private SafeJq() {
        // Static only.
    }

    private static Scope createRootScope() {
        final Scope builtins = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, builtins);
        // The built-ins jq's regular expression functions are defined on, replaced by ones that can be stopped. As
        // jq's own definitions look them up by name, they use these.
        builtins.addFunction("_match_impl/3", SafeJqRegex::match);
        builtins.addFunction("_sub_impl/3", SafeJqRegex::sub);
        // jq's own functions this jq lacks, which the agent reached for (seen: IN and todate, a failed call each).
        builtins.addFunction("todate/0", builtins.getFunction("todateiso8601", 0));
        builtins.addFunction("fromdate/0", builtins.getFunction("fromdateiso8601", 0));
        builtins.addFunction("IN/1", SafeJq::in);
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

    // IN(s): whether the input is one of the values s gives, as jq 1.6 defines it (any(s == .; .)).
    private static void in(final Scope scope,
                           final List<Expression> args,
                           final JsonNode in,
                           final Path path,
                           final PathOutput output,
                           final Version version) throws JsonQueryException {
        final boolean[] found = {false};
        args.getFirst().apply(scope, in, value -> {
            if (!found[0] && value.equals(in)) {
                found[0] = true;
            }
        });
        output.emit(BooleanNode.valueOf(found[0]), null);
    }

    /// @param message What jq said of a failed query.
    /// @return For a query whose path doesn't fit the data (a key read from text, an array iterated that isn't
    /// there), what it means and how to look before trying again; else empty. Seen: 29 of 77 failed jq calls were
    /// such guesses at the data's shape, often several in a row.
    public static String pathAdvice(final String message) {
        final Matcher index = INDEXED.matcher(NullSafe.string(message));
        if (index.find()) {
            final String key = index.group(2).startsWith("\"")
                    ? index.group(2)
                    : "a " + index.group(2) + " index";
            return " A step reads " + key + " from " + article(index.group(1)) + ", so the data there "
                   + "isn't shaped as the query expects. Look before guessing again: run the query up to the step "
                   + "before, ending `| type`, `| keys` or `| .[0]`. `?` (or `// empty`) skips values of another "
                   + "kind.";
        }
        final Matcher iterate = ITERATED.matcher(NullSafe.string(message));
        if (iterate.find()) {
            return "null".equals(iterate.group(1))
                    ? " The key iterated isn't there (or is null) in some values: `.key[]?` skips them, and `keys` "
                      + "on the step before shows what is there."
                    : " The value iterated is " + article(iterate.group(1)) + ", not an array or object: run the "
                      + "query up to the step before, ending `| type`, to see what it is.";
        }
        return NOT_APPLICABLE.matcher(NullSafe.string(message)).find()
                ? " The value is missing (null) there: run the query up to the step before, ending `| keys`, to "
                  + "see what is there, or skip it with `?`."
                : "";
    }

    private static String article(final String type) {
        final String lower = type.toLowerCase(Locale.ROOT);
        return (lower.startsWith("a") || lower.startsWith("o")
                ? "an "
                : "a ") + lower;
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
            throw new JsonQueryException(name + " is not available here.");
        };
    }

    /// Counts one step of a query's work, stopping it if it has done too much or run out of time.
    static void tick() {
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
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new WorkspaceException("The jq query may be at most " + MAX_QUERY_LENGTH + " characters.");
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
            throw new WorkspaceException("The jq query failed: " + e.getMessage() + pathAdvice(e.getMessage()));
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
