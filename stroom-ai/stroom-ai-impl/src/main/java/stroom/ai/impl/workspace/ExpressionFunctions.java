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

import stroom.query.language.functions.FunctionArg;
import stroom.query.language.functions.FunctionCategory;
import stroom.query.language.functions.FunctionDef;
import stroom.query.language.functions.FunctionFactory;
import stroom.query.language.functions.FunctionSignature;
import stroom.query.language.functions.ValString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// What the model is told of Stroom's expression functions, for [TableExpression]s: their names by category, and
/// on request each one's arguments, from the same definitions as Stroom's editor's help, so they are always those
/// Stroom has.
public final class ExpressionFunctions {

    // Functions that reach outside the table (asking a model, reading a state store), or are of a dashboard
    // (its links, parameters and user), which aren't available.
    private static final Set<FunctionCategory> OUTSIDE = EnumSet.of(FunctionCategory.AI, FunctionCategory.LOOKUP,
            FunctionCategory.LINK, FunctionCategory.PARAM);
    private static final Set<String> OUTSIDE_NAMES = Set.of("param", "params");
    // Functions that pick out rows of a group, or count them, which aren't available as groups don't keep their
    // rows.
    private static final Set<FunctionCategory> PICKING_ROWS = EnumSet.of(FunctionCategory.SELECTION);
    private static final Set<String> PICKING_ROWS_NAMES = Set.of("countgroups");
    private static final String OUTSIDE_REASON = "reaches outside the table.";
    private static final String PICKING_ROWS_REASON = "picks out rows of a group, which aren't kept: use orderBy "
                                                      + "and limit instead.";
    private static final Pattern CALL = Pattern.compile("([A-Za-z][A-Za-z0-9]*)\\s*\\(");
    // An expression that is one function call, e.g. formatDate(${EventTime}, 'yyyy').
    private static final Pattern OUTER_CALL = Pattern.compile("\\s*([A-Za-z][A-Za-z0-9]*)\\s*\\((.*)\\)\\s*",
            Pattern.DOTALL);
    private static final int MAX_SUGGESTIONS = 3;
    // Names other languages give functions, for the function Stroom has instead.
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("avg", "average"),
            Map.entry("mean", "average"),
            Map.entry("len", "stringLength"),
            Map.entry("length", "stringLength"),
            Map.entry("strlen", "stringLength"),
            Map.entry("lower", "lowerCase"),
            Map.entry("upper", "upperCase"),
            Map.entry("substr", "substring"),
            Map.entry("trunc", "floor"),
            Map.entry("iif", "if"));

    private ExpressionFunctions() {
        // Static only.
    }

    /// @return The functions available, by category, e.g. "String: concat, substring, ...", a line each. One that
    /// takes no arguments is shown called, e.g. year(), to set it apart from those that take a value.
    public static String catalogue() {
        final Map<String, List<String>> byCategory = new TreeMap<>();
        for (final FunctionDef def : definitions()) {
            final Set<FunctionCategory> categories = categories(def);
            if (unavailableReason(def).isEmpty()) {
                final String category = categories.isEmpty()
                        ? "Other"
                        : categories.iterator().next().getName();
                byCategory.computeIfAbsent(category, key -> new ArrayList<>()).add(takesNoArguments(def)
                        ? def.name() + "()"
                        : def.name());
            }
        }
        return byCategory.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue().stream()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.joining(", ")))
                .collect(Collectors.joining("\n"));
    }

    /// @param names Functions' names (or aliases), in any case.
    /// @return What each takes and gives, and what it does.
    public static String help(final List<String> names) {
        final StringBuilder sb = new StringBuilder();
        for (final String name : names) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            final FunctionDef def = find(name).orElse(null);
            if (def == null) {
                final List<String> closest = closestNames(name.toLowerCase(Locale.ROOT));
                sb.append("There is no function '").append(name).append("'.");
                if (!closest.isEmpty()) {
                    sb.append(" Did you mean ").append(String.join(" or ", closest)).append('?');
                }
                continue;
            }
            describe(sb, def);
        }
        return sb.toString();
    }

    /// @param text An expression.
    /// @return Why it can't be worked out here, if it calls a function that isn't available, or empty.
    static Optional<String> unavailableIn(final String text) {
        final Matcher call = CALL.matcher(text);
        while (call.find()) {
            final FunctionDef def = find(call.group(1)).orElse(null);
            final Optional<String> reason = def == null
                    ? Optional.empty()
                    : unavailableReason(def);
            if (reason.isPresent()) {
                return Optional.of("The function " + def.name() + " isn't available here, as it " + reason.get());
            }
        }
        return Optional.empty();
    }

    /// Says which function an expression's value comes from, if it is one function call whose value is always
    /// text, e.g. `formatDate(floorHour(${EventTime}), 'yyyy-MM-dd HH:mm')` or `concat(${User}, '@', ${Host})`.
    /// Text is a number or a date only if it reads as one, so such a column formatted as a Number or Date Time
    /// may give no value to a chart.
    ///
    /// @param expression A column's expression, or null.
    /// @return The call, with its function's name as Stroom gives it and its arguments' text, or empty if the
    /// expression isn't such a call.
    public static Optional<TextCall> textCall(final String expression) {
        if (expression == null) {
            return Optional.empty();
        }
        final Matcher matcher = OUTER_CALL.matcher(expression);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        final List<String> arguments = arguments(matcher.group(2)).orElse(null);
        if (arguments == null) {
            // The brackets don't close at the end, e.g. upperCase(a) + lowerCase(b).
            return Optional.empty();
        }
        return find(matcher.group(1))
                .filter(ExpressionFunctions::alwaysText)
                .map(def -> new TextCall(def.name(), arguments));
    }

    // A function whose every signature gives text.
    private static boolean alwaysText(final FunctionDef def) {
        if (def.signatures().length == 0) {
            return isText(def.commonReturnType());
        }
        return Arrays.stream(def.signatures()).allMatch(signature -> isText(signature.returnType().length > 0
                ? signature.returnType()
                : def.commonReturnType()));
    }

    private static boolean isText(final Class<?>[] types) {
        return types.length > 0 && Arrays.stream(types).allMatch(ValString.class::equals);
    }

    // A call's arguments, split at the commas between them, or empty if its brackets close before the end (so
    // the text isn't one call's arguments). Commas within brackets or quoted text are kept.
    private static Optional<List<String>> arguments(final String text) {
        final List<String> arguments = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
                current.append(c);
            } else if (c == '(') {
                depth++;
                current.append(c);
            } else if (c == ')') {
                depth--;
                if (depth < 0) {
                    return Optional.empty();
                }
                current.append(c);
            } else if (c == ',' && depth == 0) {
                arguments.add(current.toString().strip());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (depth != 0 || quote != 0) {
            return Optional.empty();
        }
        if (!current.isEmpty() || !arguments.isEmpty()) {
            arguments.add(current.toString().strip());
        }
        return Optional.of(arguments);
    }

    /// @param name A name that isn't a function's, in lower case.
    /// @return The names of the available functions closest to it, closest first.
    static List<String> closestNames(final String name) {
        final String synonym = SYNONYMS.get(name);
        if (synonym != null) {
            return List.of(synonym);
        }
        // Short names are close to many others, so only a slip of one letter is taken for one of them.
        final int most = name.length() <= 4
                ? 1
                : name.length() / 3;
        return definitions().stream()
                .filter(def -> unavailableReason(def).isEmpty())
                .flatMap(def -> Stream.concat(Stream.of(def.name()), Arrays.stream(def.aliases()))
                        .map(alias -> Map.entry(def.name(), distance(name, alias.toLowerCase(Locale.ROOT)))))
                .filter(entry -> entry.getValue() <= most)
                .sorted(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .distinct()
                .limit(MAX_SUGGESTIONS)
                .toList();
    }

    private static void describe(final StringBuilder sb, final FunctionDef def) {
        final Optional<String> reason = unavailableReason(def);
        if (reason.isPresent()) {
            sb.append(def.name()).append(" isn't available here, as it ").append(reason.get());
            return;
        }
        if (!def.commonDescription().isBlank()) {
            sb.append(def.name()).append(": ").append(def.commonDescription()).append('\n');
        }
        for (final FunctionSignature signature : def.signatures()) {
            sb.append(def.name()).append('(')
                    .append(Arrays.stream(signature.args()).map(ExpressionFunctions::argument)
                            .collect(Collectors.joining(", ")))
                    .append(')');
            final String returns = returnType(signature, def);
            if (returns != null) {
                sb.append(" -> ").append(returns);
            }
            if (!signature.description().isBlank()) {
                sb.append(": ").append(signature.description());
            }
            sb.append('\n');
            for (final FunctionArg arg : signature.args()) {
                if (!arg.description().isBlank()) {
                    sb.append("  ").append(arg.name()).append(": ").append(arg.description());
                    if (arg.allowedValues().length > 0) {
                        sb.append(" One of: ").append(String.join(", ", arg.allowedValues())).append('.');
                    }
                    sb.append('\n');
                }
            }
        }
        if (def.aliases().length > 0) {
            sb.append("Also called: ").append(String.join(", ", def.aliases())).append('\n');
        }
        sb.setLength(sb.length() - 1);
    }

    private static String argument(final FunctionArg arg) {
        final String name = arg.name() + (arg.isVarargs()
                ? "..."
                : "");
        return arg.isOptional()
                ? "[" + name + "]"
                : name;
    }

    private static String returnType(final FunctionSignature signature, final FunctionDef def) {
        final Class<?>[] types = signature.returnType().length > 0
                ? signature.returnType()
                : def.commonReturnType();
        return types.length == 0
                ? null
                : Arrays.stream(types).map(ExpressionFunctions::typeName).collect(Collectors.joining(" or "));
    }

    // ValString is "String", Val is "Any".
    private static String typeName(final Class<?> type) {
        final String name = type.getSimpleName().replaceFirst("^Val", "");
        return name.isEmpty()
                ? "Any"
                : name;
    }

    // A function by its name or alias, which may be given called, e.g. year().
    private static Optional<FunctionDef> find(final String name) {
        final String wanted = name.strip().replaceFirst("\\(\\)$", "");
        return definitions().stream()
                .filter(def -> Stream.concat(Stream.of(def.name()), Arrays.stream(def.aliases()))
                        .anyMatch(alias -> alias.equalsIgnoreCase(wanted)))
                .findFirst();
    }

    private static boolean takesNoArguments(final FunctionDef def) {
        return Arrays.stream(def.signatures()).allMatch(signature -> signature.args().length == 0);
    }

    private static List<FunctionDef> definitions() {
        final List<FunctionDef> definitions = FunctionFactory.getFunctionDefinitions();
        definitions.sort(Comparator.comparing(FunctionDef::name, String.CASE_INSENSITIVE_ORDER));
        return definitions;
    }

    // A function's categories: its own, or else its signatures'.
    private static Set<FunctionCategory> categories(final FunctionDef def) {
        final Set<FunctionCategory> categories = new LinkedHashSet<>(Arrays.asList(def.commonCategory()));
        if (categories.isEmpty()) {
            for (final FunctionSignature signature : def.signatures()) {
                categories.addAll(Arrays.asList(signature.category()));
            }
        }
        return categories;
    }

    // Why a function can't be worked out over a table, or empty if it can.
    private static Optional<String> unavailableReason(final FunctionDef def) {
        final Set<FunctionCategory> categories = categories(def);
        final String name = def.name().toLowerCase(Locale.ROOT);
        if (!Collections.disjoint(categories, OUTSIDE) || OUTSIDE_NAMES.contains(name)) {
            return Optional.of(OUTSIDE_REASON);
        }
        if (!Collections.disjoint(categories, PICKING_ROWS) || PICKING_ROWS_NAMES.contains(name)) {
            return Optional.of(PICKING_ROWS_REASON);
        }
        return Optional.empty();
    }

    // The edit distance between two names.
    private static int distance(final String a, final String b) {
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            final int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                final int cost = a.charAt(i - 1) == b.charAt(j - 1)
                        ? 0
                        : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[b.length()];
    }

    // ---------------------------------------------------------------------------------------------------------

    /// A call of a function whose value is always text.
    ///
    /// @param function  The function's name, as Stroom gives it, e.g. `formatDate`.
    /// @param arguments Its arguments' text, in order.
    public record TextCall(String function, List<String> arguments) {

    }
}
