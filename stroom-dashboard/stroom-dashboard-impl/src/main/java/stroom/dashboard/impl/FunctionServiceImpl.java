package stroom.dashboard.impl;

import stroom.dashboard.expression.v1.FunctionArg;
import stroom.dashboard.expression.v1.FunctionDef;
import stroom.dashboard.shared.FunctionDefinition;
import stroom.dashboard.shared.FunctionDefinition.Arg;
import stroom.dashboard.shared.FunctionDefinition.Signature;
import stroom.dashboard.shared.FunctionDefinition.Type;
import stroom.dashboard.expression.v1.FunctionFactory;
import stroom.dashboard.expression.v1.FunctionSignature;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValBoolean;
import stroom.dashboard.expression.v1.ValDouble;
import stroom.dashboard.expression.v1.ValErr;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Singleton
public class FunctionServiceImpl implements FunctionService {
    private final List<FunctionDefinition> functionDefinitions;
    private final FunctionFactory functionFactory;

    @Inject
    public FunctionServiceImpl(final FunctionFactory functionFactory) {
        this.functionFactory = functionFactory;
        this.functionDefinitions = functionFactory.getFunctionDefinitions()
                .stream()
                .map(FunctionServiceImpl::convertFunctionDef)
                .collect(Collectors.toList());
    }

    @Override
    public List<FunctionDefinition> getFunctionDefinitions() {
        return functionDefinitions;
    }

    @Override
    public FunctionFactory getFunctionFactory() {
        return functionFactory;
    }

    private static FunctionDefinition convertFunctionDef(final FunctionDef functionDef) {
        if (functionDef != null) {

            try {
                return new FunctionDefinition(
                        convertString(functionDef.name()),
                        Arrays.stream(functionDef.aliases())
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()),
                        functionDef.category().getName(),
                        convertString(functionDef.description()),
                        Arrays.stream(functionDef.signatures())
                                .filter(Objects::nonNull)
                                .map(FunctionServiceImpl::convertSignature)
                                .collect(Collectors.toList()));
            } catch (Exception e) {
                throw new RuntimeException("Error converting FunctionDef " + functionDef.name(), e);
            }
        } else {
            return null;
        }
    }

    private static FunctionDefinition.Signature convertSignature(final FunctionSignature functionSignature) {

        if (functionSignature != null) {

            return new Signature(
                    Arrays.stream(functionSignature.args())
                            .filter(Objects::nonNull)
                            .map(FunctionServiceImpl::convertArg)
                            .collect(Collectors.toList()),
                    convertType(functionSignature.returnType()),
                    convertString(functionSignature.returnDescription()),
                    convertString(functionSignature.description()));
        } else {
            return null;
        }
    }

    private static FunctionDefinition.Arg convertArg(final FunctionArg functionArg) {

        if (functionArg != null) {
            return new Arg(
                    convertString(functionArg.name()),
                    convertType(functionArg.argType()),
                    functionArg.isVarargs(),
                    functionArg.minVarargsCount(),
                    convertString(functionArg.description()));
        } else {
            return null;
        }
    }

    private static String convertString(final String str) {
        if (str != null) {
            return str.isEmpty()
                    ? null
                    : str;
        } else {
            return null;
        }
    }

    private static FunctionDefinition.Type convertType(final Class<? extends Val> type) {
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
        } else {
            throw new RuntimeException("Unknown type " + type.getName());
        }
    }
}
