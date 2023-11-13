package stroom.query.impl;

import stroom.query.language.functions.FunctionArg;
import stroom.query.language.functions.FunctionCategory;
import stroom.query.language.functions.FunctionDef;
import stroom.query.language.functions.FunctionFactory;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValErr;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValNumber;
import stroom.query.language.functions.ValString;
import stroom.query.shared.FunctionSignatureUtil;
import stroom.query.shared.QueryHelpFunctionSignature;
import stroom.query.shared.QueryHelpFunctionSignature.Arg;
import stroom.query.shared.QueryHelpFunctionSignature.OverloadType;
import stroom.query.shared.QueryHelpFunctionSignature.Type;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpTitle;
import stroom.query.shared.QueryHelpType;
import stroom.util.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage.ResultConsumer;
import stroom.util.string.StringMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Functions {

    private static final String ROOT_ID = "functions";
    private final Map<String, List<QueryHelpRow>> map;

    @Inject
    public Functions() {
        final Map<String, Set<QueryHelpRow>> localMap = new HashMap<>();

        // Flatten the nested FunctionDef objects into signatures
        final QueryHelpRow root = QueryHelpRow
                .builder()
                .type(QueryHelpType.TITLE)
                .id(ROOT_ID)
                .hasChildren(true)
                .title("Functions")
                .data(new QueryHelpTitle("A list of functions available to use in the Stroom Query Language."))
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
                        } catch (Exception e) {
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
                        final StringMatcher stringMatcher,
                        final ResultConsumer<QueryHelpRow> resultConsumer) {
        final List<QueryHelpRow> rows = map.getOrDefault(parentUuid, Collections.emptyList());
        final ResultPageBuilder<QueryHelpRow> builder =
                new ResultPageBuilder<>(pageRequest, Comparator.comparing(QueryHelpRow::getTitle));
        for (final QueryHelpRow row : rows) {
            if (row.isHasChildren()) {
                if (!hasChildren(row, stringMatcher)) {
                    if ("".equals(parentUuid) || stringMatcher.match(row.getTitle()).isPresent()) {
                        builder.add(row.copy().hasChildren(false).build());
                    }
                } else {
                    builder.add(row);
                }
            } else if ("".equals(parentUuid) || stringMatcher.match(row.getTitle()).isPresent()) {
                builder.add(row);
            }
        }
        for (final QueryHelpRow row : builder.build().getValues()) {
            if (!resultConsumer.add(row)) {
                break;
            }
        }
    }

    private boolean hasChildren(final QueryHelpRow parent, final StringMatcher stringMatcher) {
        final List<QueryHelpRow> rows = map.getOrDefault(parent.getId() + ".", Collections.emptyList());
        for (final QueryHelpRow row : rows) {
            if (stringMatcher.match(row.getTitle()).isPresent()) {
                return true;
            } else if (row.isHasChildren()) {
                if (hasChildren(row, stringMatcher)) {
                    return true;
                }
            }
        }
        return false;
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
                    .collect(Collectors.toList());

            final List<String> aliases = Arrays.stream(functionDef.aliases())
                    .filter(Objects::nonNull)
                    .filter(alias -> !alias.isEmpty())
                    .collect(Collectors.toList());

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
}
