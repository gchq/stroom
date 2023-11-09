package stroom.dashboard.impl;

import stroom.dashboard.shared.FunctionSignature;
import stroom.dashboard.shared.FunctionSignature.Arg;
import stroom.dashboard.shared.FunctionSignature.OverloadType;
import stroom.dashboard.shared.FunctionSignature.Type;
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
import stroom.util.NullSafe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FunctionServiceImpl implements FunctionService {

    private final List<FunctionSignature> signatures;

    @Inject
    public FunctionServiceImpl() {
        // Flatten the nested FunctionDef objects into signatures
        this.signatures = FunctionFactory.getFunctionDefinitions()
                .stream()
                .flatMap(this::convertFunctionDef)
                .collect(Collectors.toList());
    }

    @Override
    public List<FunctionSignature> getSignatures() {
        return signatures;
    }

    private Stream<FunctionSignature> convertFunctionDef(final FunctionDef functionDef) {
        if (functionDef != null) {
            try {
                final Map<List<String>, Long> countsByCategoryPath = Arrays.stream(functionDef.signatures())
                        .collect(Collectors.groupingBy(
                                sig -> buildCategoryPath(functionDef, sig),
                                Collectors.counting()));

                return Arrays.stream(functionDef.signatures())
                        .map(functionSignature ->
                                convertSignature(functionDef, functionSignature, countsByCategoryPath));
            } catch (Exception e) {
                throw new RuntimeException("Error converting FunctionDef " + functionDef.name(), e);
            }
        } else {
            return null;
        }
    }

    private static FunctionSignature convertSignature(
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
                    .map(FunctionServiceImpl::convertArg)
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

            return new FunctionSignature(
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
