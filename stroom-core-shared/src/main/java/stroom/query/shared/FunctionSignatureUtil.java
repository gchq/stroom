package stroom.query.shared;

import stroom.query.shared.QueryHelpFunctionSignature.Arg;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class FunctionSignatureUtil {

    private FunctionSignatureUtil() {
    }

    public static String buildSignatureStr(final String name,
                                           final List<Arg> args) {
        String sigStr;
        if (args.isEmpty()) {
            sigStr = name + " ()";
        } else if (args.size() > 3) {
            // Funcs with long arg lists get truncated. Help text explains all the args.
            sigStr = name + " (......)";
        } else if (isNonBracketedForm(name, args)) {
            if (args.size() == 2) {
                sigStr = args.get(0).getName()
                        + " "
                        + name
                        + " "
                        + args.get(1).getName();
            } else {
                final String argName = args.get(0).getName();
                sigStr = argName + "1 " + name + " " + argName + "2";
            }
        } else {
            final AtomicBoolean foundOptArg = new AtomicBoolean(false);
            sigStr = args
                    .stream()
                    .flatMap(arg -> {
                        List<String> argStrs = new ArrayList<>();

                        if (arg.isVarargs()) {
                            for (int i = 1; i <= arg.getMinVarargsCount() + 1; i++) {
                                argStrs.add(buildVarargsName(arg, i));
                            }
                        } else if (arg.isOptional() && !foundOptArg.get()) {
                            argStrs.add("[" + arg.getName());
                            foundOptArg.set(true);
                        } else {
                            argStrs.add(arg.getName());
                        }
                        return argStrs.stream();
                    })
                    .collect(Collectors.joining(", "));

            if (foundOptArg.get()) {
                sigStr += "]";
            }
            // Add a space to make it a bit clearer
            sigStr = name + " (" + sigStr + ")";
        }

        return sigStr;
    }

    private static boolean isNonBracketedForm(final String name,
                                              final List<Arg> args) {
        return (name.length() == 1 || ">=".equals(name) || "<=".equals(name))
                && (args.size() == 2 || (args.size() == 1 && args.get(0).isVarargs()));
    }

    private static String buildVarargsName(final Arg arg,
                                           final int argNo) {

        final String suffix = argNo <= arg.getMinVarargsCount()
                ? String.valueOf(argNo)
                : "N";
        final String prefix = argNo <= arg.getMinVarargsCount()
                ? ""
                : "... , ";
        return prefix + arg.getName() + suffix;
    }
}
