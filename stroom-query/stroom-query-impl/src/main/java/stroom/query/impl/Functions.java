/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.impl;

import stroom.query.language.functions.FunctionArg;
import stroom.query.language.functions.FunctionCategory;
import stroom.query.language.functions.FunctionDef;
import stroom.query.language.functions.FunctionFactory;
import stroom.query.language.functions.FunctionSignature;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValErr;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValNumber;
import stroom.query.language.functions.ValString;
import stroom.query.shared.CompletionItem;
import stroom.query.shared.CompletionSnippet;
import stroom.query.shared.CompletionsRequest;
import stroom.query.shared.InsertType;
import stroom.query.shared.QueryHelpDetail;
import stroom.query.shared.QueryHelpFunctionSignature;
import stroom.query.shared.QueryHelpFunctionSignature.Arg;
import stroom.query.shared.QueryHelpFunctionSignature.OverloadType;
import stroom.query.shared.QueryHelpFunctionSignature.Type;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;
import stroom.ui.config.shared.UiConfig;
import stroom.util.collections.TrimmedSortedList;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.resultpage.ResultPageBuilder;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.string.AceStringMatcher;
import stroom.util.string.AceStringMatcher.AceMatchResult;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class Functions {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(Functions.class);

    private static final String ROOT_ID = "functions";
    public static final int INITIAL_SCORE = 200;

    private final Provider<UiConfig> uiConfigProvider;
    private final Map<String, List<QueryHelpRow>> map;
    private volatile List<CompletionItem> functionCompletions = null;

    @Inject
    public Functions(final Provider<UiConfig> uiConfigProvider) {
        this.uiConfigProvider = uiConfigProvider;

        final Map<String, Set<QueryHelpRow>> localMap = new HashMap<>();

        // Flatten the nested FunctionDef objects into signatures
        final QueryHelpRow root = QueryHelpRow
                .builder()
                .type(QueryHelpType.TITLE)
                .id(ROOT_ID)
                .hasChildren(true)
                .title("Functions")
                .build();
        localMap.computeIfAbsent("", k -> new HashSet<>()).add(root);

        FunctionFactory.getFunctionDefinitions()
                .forEach(functionDef -> {
                    if (functionDef != null) {
                        try {
                            final Map<List<String>, Long> countsByCategoryPath = Arrays
                                    .stream(functionDef.signatures())
                                    .collect(Collectors.groupingBy(
                                            sig -> buildCategoryPath(functionDef, sig),
                                            Collectors.counting()));

                            Arrays.stream(functionDef.signatures()).forEach(functionSignature -> {
                                final QueryHelpFunctionSignature row =
                                        convertSignature(functionDef, functionSignature, countsByCategoryPath);
                                addSignature(localMap, row);
                            });
                        } catch (final Exception e) {
                            LOGGER.error(e.getMessage(), e);
                            throw new RuntimeException("Error converting FunctionDef " + functionDef.name(), e);
                        }
                    }
                });

        this.map = new HashMap<>();
        localMap.forEach((k, v) -> {
            final List<QueryHelpRow> list = new ArrayList<>(v);
            list.sort(Comparator.comparing(QueryHelpRow::getTitle));
            this.map.put(k, list);
        });
    }

    private void addSignature(final Map<String, Set<QueryHelpRow>> map,
                              final QueryHelpFunctionSignature sig) {
        final String functionName = sig.getName();
        final List<String> categoryPath = sig.getCategoryPath();
        final StringBuilder idBuilder = new StringBuilder(ROOT_ID);
        Set<QueryHelpRow> rows = map.computeIfAbsent(ROOT_ID + ".", k -> new HashSet<>());
        for (final String category : categoryPath) {
            idBuilder.append(".");
            idBuilder.append(category);
            final String id = idBuilder.toString();
            final QueryHelpRow row = QueryHelpRow
                    .builder()
                    .type(QueryHelpType.TITLE)
                    .id(id)
                    .hasChildren(true)
                    .title(category)
                    .build();
            rows.add(row);
            rows = map.computeIfAbsent(id + ".", k -> new HashSet<>());
        }

        final boolean isOverloadedInCategory = OverloadType.OVERLOADED_IN_CATEGORY.equals(
                sig.getOverloadType());
        if (isOverloadedInCategory) {
            // If a function has overloads then we need to create an extra category for the overloads
            idBuilder.append(".");
            idBuilder.append(functionName);
            final String id = idBuilder.toString();
            final QueryHelpRow row = QueryHelpRow
                    .builder()
                    .type(QueryHelpType.TITLE)
                    .id(id)
                    .hasChildren(true)
                    .title(functionName)
                    .build();
            rows.add(row);
            rows = map.computeIfAbsent(id + ".", k -> new HashSet<>());
        }

        final String title = isOverloadedInCategory
                ? FunctionSignatureUtil.buildSignatureStr(functionName, sig.getArgs())
//                .replaceAll("^" + functionName, "...")
                : functionName;

        idBuilder.append(".");
        idBuilder.append(title);
        final QueryHelpRow row = QueryHelpRow
                .builder()
                .type(QueryHelpType.FUNCTION)
                .id(idBuilder.toString())
                .title(title)
                .data(sig)
                .build();
        rows.add(row);
    }

    public void addRows(final PageRequest pageRequest,
                        final String parentUuid,
                        final Predicate<String> predicate,
                        final ResultPageBuilder<QueryHelpRow> resultPageBuilder) {
        final List<QueryHelpRow> rows = map.getOrDefault(parentUuid, Collections.emptyList());
        final TrimmedSortedList<QueryHelpRow> trimmedSortedList =
                new TrimmedSortedList<>(pageRequest, Comparator.comparing(QueryHelpRow::getTitle));
        for (final QueryHelpRow row : rows) {
            if (row.isHasChildren()) {
                if (!hasChildren(row, predicate)) {
                    if (match(row, predicate)) {
                        trimmedSortedList.add(row.copy().hasChildren(false).build());
                    }
                } else {
                    trimmedSortedList.add(row);
                }
            } else if (match(row, predicate)) {
                trimmedSortedList.add(row);
            }
        }

        final List<QueryHelpRow> list = trimmedSortedList.getList();
        for (final QueryHelpRow row : list) {
            resultPageBuilder.add(row);
        }
    }

    private boolean hasChildren(final QueryHelpRow parent, final Predicate<String> predicate) {
        final List<QueryHelpRow> rows = map.getOrDefault(parent.getId() + ".", Collections.emptyList());
        for (final QueryHelpRow row : rows) {
            if (match(row, predicate)) {
                return true;
            } else if (row.isHasChildren()) {
                if (hasChildren(row, predicate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean match(final QueryHelpRow row, final Predicate<String> predicate) {
        String name = row.getTitle();
        if (row.getData() instanceof final QueryHelpFunctionSignature queryHelpFunctionSignature) {
            name = queryHelpFunctionSignature.getName();
        }
        return predicate.test(name);
    }

    private static QueryHelpFunctionSignature convertSignature(
            final FunctionDef functionDef,
            final stroom.query.language.functions.FunctionSignature functionSignature,
            final Map<List<String>, Long> countsByCategoryPath) {

        if (functionSignature != null) {

            // The sig can override the types/descriptions set at the func def level
            final Type returnType = functionSignature.returnType().length > 0
                    ? convertType(functionSignature.returnType())
                    : convertType(functionDef.commonReturnType());

            final String returnDescription = !functionSignature.returnDescription().isEmpty()
                    ? convertString(functionSignature.returnDescription())
                    : convertString(functionDef.commonReturnDescription());

            final String description = !functionSignature.description().isEmpty()
                    ? convertString(functionSignature.description())
                    : convertString(functionDef.commonDescription());

            final List<Arg> args = Arrays.stream(functionSignature.args())
                    .filter(Objects::nonNull)
                    .map(Functions::convertArg)
                    .toList();

            final List<String> aliases = Arrays.stream(functionDef.aliases())
                    .filter(Objects::nonNull)
                    .filter(alias -> !alias.isEmpty())
                    .toList();

            final List<String> categoryPath = buildCategoryPath(functionDef, functionSignature);

            final OverloadType overloadType;
            if (NullSafe.test(countsByCategoryPath.get(categoryPath), count -> count > 1)) {
                overloadType = OverloadType.OVERLOADED_IN_CATEGORY;
            } else {
                final long totalSigCount = countsByCategoryPath.values()
                        .stream()
                        .mapToLong(Long::longValue)
                        .sum();
                if (totalSigCount == 1) {
                    overloadType = OverloadType.NOT_OVERLOADED;
                } else {
                    overloadType = OverloadType.OVERLOADED_GLOBALLY;
                }
            }
            final String helpAnchor = functionDef.helpAnchor() == null
                                      || FunctionDef.UNDEFINED.equals(functionDef.helpAnchor())
                    ? null
                    : functionDef.helpAnchor();

            return new QueryHelpFunctionSignature(
                    functionDef.name(),
                    helpAnchor,
                    aliases,
                    categoryPath,
                    args,
                    returnType,
                    returnDescription,
                    description,
                    overloadType);
        } else {
            return null;
        }
    }

    private static List<String> buildCategoryPath(
            final FunctionDef functionDef,
            final stroom.query.language.functions.FunctionSignature functionSignature) {

        final String category = functionSignature.category().length > 0
                ? convertCategory(functionSignature.category())
                : convertCategory(functionDef.commonCategory());

        final String[] subCategories = functionSignature.subCategories().length > 0
                ? functionSignature.subCategories()
                : functionDef.commonSubCategories();

        final List<String> categoryPath = new ArrayList<>();
        categoryPath.add(category);
        categoryPath.addAll(Arrays.asList(subCategories));
        return categoryPath;
    }

    private static Arg convertArg(final FunctionArg functionArg) {

        if (functionArg != null) {
            return new Arg(
                    convertString(functionArg.name()),
                    convertType(functionArg.argType()),
                    functionArg.isOptional(),
                    functionArg.isVarargs(),
                    functionArg.minVarargsCount(),
                    convertString(functionArg.description()),
                    convertStringArray(functionArg.allowedValues()),
                    convertString(functionArg.defaultValue()));
        } else {
            return null;
        }
    }

    private static String convertString(final String str) {
        return str != null && !str.isEmpty()
                ? str
                : null;
    }

    private static List<String> convertStringArray(final String[] arr) {
        if (arr != null) {
            return Arrays.asList(arr);
        } else {
            return Collections.emptyList();
        }
    }

    private static String convertCategory(final FunctionCategory[] categories) {
        if (categories.length == 1) {
            return categories[0].getName();
        } else {
            throw new RuntimeException("Too many types");
        }
    }

    private static Type convertType(final Class<? extends Val>[] types) {
        if (types.length == 1) {
            final Class<? extends Val> type = types[0];
            return convertType(type);
        } else {
            throw new RuntimeException("Too many types");
        }
    }

    private static Type convertType(final Class<? extends Val> type) {
        if (ValBoolean.class.equals(type)) {
            return Type.BOOLEAN;
        } else if (ValDouble.class.equals(type)) {
            return Type.DOUBLE;
        } else if (ValErr.class.equals(type)) {
            return Type.ERROR;
        } else if (ValInteger.class.equals(type)) {
            return Type.INTEGER;
        } else if (ValLong.class.equals(type)) {
            return Type.LONG;
        } else if (ValNull.class.equals(type)) {
            return Type.NULL;
        } else if (ValString.class.equals(type)) {
            return Type.STRING;
        } else if (ValNumber.class.equals(type)) {
            return Type.NUMBER;
        } else if (Val.class.equals(type)) {
            return Type.UNKNOWN;
        } else {
            return Type.UNKNOWN;
        }
    }

    /**
     * Pkg private for testing
     */
    List<CompletionItem> buildFunctionCompletions() {
        final List<CompletionItem> completionItems = new ArrayList<>();
        for (final FunctionDef functionDef : FunctionFactory.getFunctionDefinitions()) {
            if (functionDef != null) {
                if (isSymbolicFunction(functionDef.name())) {
                    // We don't want to see all the funcs like +, > !=, etc.
                    continue;
                }

                try {
                    final Map<List<String>, Long> countsByCategoryPath = Arrays
                            .stream(functionDef.signatures())
                            .collect(Collectors.groupingBy(
                                    sig -> buildCategoryPath(functionDef, sig),
                                    Collectors.counting()));

                    for (final FunctionSignature functionSignature : functionDef.signatures()) {
                        final QueryHelpFunctionSignature queryHelpSig =
                                convertSignature(functionDef, functionSignature, countsByCategoryPath);
                        final String completionName = FunctionSignatureUtil.buildSignatureStr(
                                queryHelpSig.getName(), queryHelpSig.getArgs());
                        final NamedSignature namedSig = new NamedSignature(completionName, queryHelpSig);
                        completionItems.add(createCompletionSnippet(namedSig, INITIAL_SCORE));
                    }
                } catch (final Exception e) {
                    throw new RuntimeException("Error converting FunctionDef " + functionDef.name(), e);
                }
            }
        }
        return Collections.unmodifiableList(completionItems);
    }

    private List<CompletionItem> getFunctionCompletions() {
        if (functionCompletions == null) {
            synchronized (this) {
                if (functionCompletions == null) {
                    functionCompletions = buildFunctionCompletions();
                }
            }
        }
        return functionCompletions;
    }

    public void addCompletions(final CompletionsRequest request,
                               final int maxCompletions,
                               final List<CompletionItem> resultList) {

        try {
            final List<CompletionItem> functionCompletions = getFunctionCompletions();

            if (functionCompletions.size() > maxCompletions) {
                final List<AceMatchResult<CompletionItem>> matchResults = AceStringMatcher.filterCompletions(
                                functionCompletions,
                                request.getPattern(),
                                INITIAL_SCORE,
                                CompletionItem::getCaption)
                        .stream()
                        .sorted(AceStringMatcher.SCORE_DESC_THEN_NAME_COMPARATOR)
                        .toList();

                LOGGER.debug(() -> LogUtil.message("Found {} match results, from {} items, maxCompletions {}",
                        matchResults.size(), functionCompletions.size(), maxCompletions));

                matchResults.stream()
                        .limit(maxCompletions)
                        .map(AceMatchResult::item)
                        .forEach(resultList::add);
            } else {
                LOGGER.debug(() -> LogUtil.message("Found {} match results using offset {}, maxCompletions {}",
                        functionCompletions.size(), maxCompletions));
                functionCompletions.stream()
                        .limit(maxCompletions)
                        .forEach(resultList::add);
            }
        } catch (final Exception e) {
            LOGGER.error("Error adding function completions: {}", e.getMessage(), e);
        }
    }

    private CompletionSnippet createCompletionSnippet(final NamedSignature namedSignature,
                                                      final int score) {
        final String name = namedSignature.completionName;
        final QueryHelpFunctionSignature signature = namedSignature.signature;
        final String snippetText = FunctionSignatureUtil.buildSnippetText(signature.getName(), signature.getArgs());
        final String meta;
        if ("Value".equals(signature.getPrimaryCategory())) {
            meta = signature.getPrimaryCategory();
        } else if (signature.getArgs().isEmpty()) {
            meta = signature.getPrimaryCategory() + " Value";
        } else {
            meta = "Func (" + signature.getPrimaryCategory() + ")";
        }
        final String html = buildInfoHtml(signature);

        return new CompletionSnippet(
                name,
                snippetText,
                score,
                meta,
                html);
    }

    private boolean addArgsBlockToInfo(final QueryHelpFunctionSignature signature,
                                       final DetailBuilder htmlBuilder) {
        final AtomicBoolean addedContent = new AtomicBoolean(false);
        addedContent.set(!signature.getArgs().isEmpty());

        htmlBuilder.elem("div", "queryHelpDetail-table", div ->
                div.elem("table", table -> {
                    // Add heading row.
                    table.elem("tr", tr -> {
                        tr.elem("th", th -> th.append("Parameter"));
                        tr.elem("th", th -> th.append("Type"));
                        tr.elem("th", th -> th.append("Description"));
                    });

                    // Add details.
                    signature.getArgs()
                            .forEach(arg -> {
                                final String argName;

                                if (arg.isVarargs()) {
                                    argName = arg.getName() + "1...N";
                                } else if (arg.isOptional()) {
                                    argName = "[" + arg.getName() + "]";
                                } else {
                                    argName = arg.getName();
                                }

                                final StringBuilder descriptionBuilder = new StringBuilder();
                                descriptionBuilder.append(arg.getDescription());
                                if (!arg.getAllowedValues().isEmpty()) {
                                    appendSpaceIfNeeded(descriptionBuilder)
                                            .append("Allowed values: ")
                                            .append(arg.getAllowedValues()
                                                    .stream()
                                                    .map(str -> "\"" + str + "\"")
                                                    .collect(Collectors.joining(", ")))
                                            .append(".");
                                }

                                if (arg.getDefaultValue() != null && !arg.getDefaultValue().isEmpty()) {
                                    appendSpaceIfNeeded(descriptionBuilder)
                                            .append("Default value: '")
                                            .append(arg.getDefaultValue())
                                            .append("'.");
                                }

                                if (arg.isOptional()) {
                                    appendSpaceIfNeeded(descriptionBuilder)
                                            .append("Optional argument.");
                                }

                                table.elem("tr", tr -> {
                                    tr.elem("td", td -> td.append(argName));
                                    tr.elem("td", td -> td.append(convertType(arg.getArgType())));
                                    tr.elem("td", td -> td.append(descriptionBuilder.toString()));
                                });
                            });
                    if (signature.getReturnType() != null) {
                        if (!signature.getArgs().isEmpty()) {
                            table.elem("tr", tr -> {
                            });
                        }
                        table.elem("tr", tr -> {
                            tr.elem("td", td -> td.append("Return"));
                            tr.elem("td", td -> td.append(convertType(signature.getReturnType())));
                            tr.elem("td", td -> td.append(signature.getReturnDescription()));
                        });

                        addedContent.set(true);
                    }
                }));

        return addedContent.get();
    }

    private String convertType(final Type type) {
        final String number = "Number";
        return switch (type) {
            case LONG, DOUBLE, INTEGER, NUMBER -> number;
            case STRING -> "Text";
            default -> type.getName();
        };
    }

    private StringBuilder appendSpaceIfNeeded(final StringBuilder stringBuilder) {
        if (!stringBuilder.isEmpty()) {
            stringBuilder.append(" ");
        }
        return stringBuilder;
    }

    public String buildInfoHtml(final QueryHelpFunctionSignature signature) {
        final DetailBuilder detail = new DetailBuilder();
        if (signature != null) {
            detail.title(FunctionSignatureUtil.buildSignatureStr(signature.getName(), signature.getArgs()));

            if (signature.getDescription() != null && !signature.getDescription().isEmpty()) {
                detail.elem("p", "queryHelpDetail-description", p -> p
                        .append(signature.getDescription()));
            }

            final boolean addedArgs = addArgsBlockToInfo(signature, detail);

            if (addedArgs) {
                detail.emptyElem("br");
            }

            final List<String> aliases = signature.getAliases();
            if (!aliases.isEmpty()) {
                detail.elem("p", p -> p
                        .append("Aliases: " + String.join(", ", aliases)));
            }

            final UiConfig uiConfig = uiConfigProvider.get();
            if (uiConfig.getHelpUrl() != null && uiConfig.getHelpSubPathStroomQueryLanguage() != null) {
                addHelpLinkToInfo(signature, uiConfig.getHelpUrl() +
                                             uiConfig.getHelpSubPathStroomQueryLanguage(), detail);
            }
        }
        return detail.build();
    }

    private static void addHelpLinkToInfo(final QueryHelpFunctionSignature signature,
                                          final String helpUrlBase,
                                          final DetailBuilder htmlBuilder) {
        htmlBuilder.append("For more information see the ");
        htmlBuilder.appendLink(
                helpUrlBase +
                signature.getPrimaryCategory().toLowerCase().replace(' ', '-') +
                "#" +
                functionSignatureToAnchor(signature),
                "Help Documentation");
        htmlBuilder.append(".");
    }

    private static String functionSignatureToAnchor(final QueryHelpFunctionSignature signature) {
        final String helpAnchor = signature.getHelpAnchor();
        if (NullSafe.isBlankString(helpAnchor)) {
            return functionNameToAnchor(signature.getName());
        } else {
            return helpAnchor;
        }
    }

    private static String functionNameToAnchor(final String name) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final char chr : name.toCharArray()) {
            if (Character.isUpperCase(chr)) {
                stringBuilder.append("-")
                        .append(String.valueOf(chr).toLowerCase());
            } else {
                stringBuilder.append(chr);
            }
        }
        return stringBuilder.toString();
    }

    public Optional<QueryHelpDetail> fetchDetail(final QueryHelpRow row) {
        if (ROOT_ID.equals(row.getId())) {
            final InsertType insertType = InsertType.NOT_INSERTABLE;
            final String documentation = "A list of functions available to use in the Stroom Query Language.";
            return Optional.of(new QueryHelpDetail(insertType, null, documentation));

        } else if (row.getId().startsWith(ROOT_ID + ".") && row.getData() instanceof
                final QueryHelpFunctionSignature signature) {
            final InsertType insertType = InsertType.snippet(row.getTitle());
            final String insertText = FunctionSignatureUtil.buildSnippetText(signature.getName(), signature.getArgs());
            final String html = buildInfoHtml(signature);
            return Optional.of(new QueryHelpDetail(insertType, insertText, html));
        }

        return Optional.empty();
    }

    private static boolean isSymbolicFunction(final String name) {
        final int len = name.length();
        if (len == 1) {
            return !Character.isAlphabetic(name.charAt(0));
        } else if (name.length() <= 2) {
            return !Character.isAlphabetic(name.charAt(0))
                   && !Character.isAlphabetic(name.charAt(1));
        } else {
            return false;
        }
    }


    // --------------------------------------------------------------------------------


    private record NamedSignature(String completionName,
                                  QueryHelpFunctionSignature signature) {

    }
}
