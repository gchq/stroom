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

import stroom.query.shared.QueryHelpFunctionSignature.Arg;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FunctionSignatureUtil {

    private FunctionSignatureUtil() {
    }

    public static String buildSnippetText(final String name,
                                          final List<Arg> args) {
        final String snippetStr;
        if (args.isEmpty()) {
            snippetStr = name + "()";
        } else if (isNonBracketedForm(name, args)) {
            if (args.size() == 2) {
                snippetStr = argToSnippetArg(args.get(0), 1)
                             + " "
                             + name
                             + " "
                             + argToSnippetArg(args.get(1), 2)
                             + "$0";
            } else {
                final String argName = args.get(0).getName();
                snippetStr = argToSnippetArg(argName + "1", args.get(0), 1)
                             + " "
                             + name
                             + " "
                             + argToSnippetArg(argName + "2", args.get(0), 2)
                             + "$0";
            }
        } else {
            final AtomicInteger argPosition = new AtomicInteger(1);

            final String argsStr = args.stream()
                    .flatMap(arg -> {
                        final List<String> snippetArgStrs = new ArrayList<>();
                        final String argName = arg.getName();

                        if (arg.isVarargs()) {
                            for (int i = 1; i <= arg.getMinVarargsCount(); i++) {
                                snippetArgStrs.add(argToSnippetArg(
                                        argName + i,
                                        arg,
                                        argPosition.getAndIncrement()));
                            }
                        } else if (arg.isOptional()) {
                            snippetArgStrs.add(argToSnippetArg(
                                    "[" + argName + "]",
                                    arg,
                                    argPosition.getAndIncrement()));
                        } else {
                            snippetArgStrs.add(argToSnippetArg(argName, arg, argPosition.getAndIncrement()));
                        }
                        return snippetArgStrs.stream();
                    })
                    .collect(Collectors.joining(", "));
            snippetStr = name + "(" + argsStr + ")$0";
        }
        return snippetStr;
    }

    private static String argToSnippetArg(final Arg arg,
                                          final int position) {
        return argToSnippetArg(arg.getName(), arg, position);
    }

    private static String argToSnippetArg(final String argName,
                                          final Arg arg,
                                          final int position) {
        final String snippetDefault = arg.getDefaultValue() != null
                ? arg.getDefaultValue()
                : argName;

        // No need to quote args as when you tab through you can surround with quotes
        // just by hitting the ' or " key. Also, more often than not, the arg value
        // is another func call or a field.

        return "${" +
               position +
               ":" +
               snippetDefault
                       .replace("$", "\\$")
                       .replace("}", "\\}") +
               "}";
    }

    private static boolean hasVarargs(final List<Arg> args) {
        return NullSafe.stream(args)
                .anyMatch(Arg::isVarargs);
    }

    private static int getMinVarargCount(final List<Arg> args) {
        // Should only have one varargs arg
        return NullSafe.stream(args)
                .filter(Arg::isVarargs)
                .findFirst()
                .map(Arg::getMinVarargsCount)
                .orElse(0);
    }

    public static String buildSignatureStr(final String name,
                                           final List<Arg> args) {
        String sigStr;

        if (NullSafe.isEmptyCollection(args)) {
            sigStr = name + " ()";
        } else {
            int effectiveArgCount = args.size();
            effectiveArgCount = hasVarargs(args)
                    ? effectiveArgCount + getMinVarargCount(args) + 2
                    : effectiveArgCount;
            if (effectiveArgCount > 7) {
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
                final Function<Arg, String> nameFunc = effectiveArgCount > 3
                        ? FunctionSignatureUtil::toTruncatedArgName
                        : Arg::getName;

                sigStr = args
                        .stream()
                        .flatMap(arg -> {
                            final List<String> argStrs = new ArrayList<>();

                            if (arg.isVarargs()) {
                                for (int i = 1; i <= arg.getMinVarargsCount() + 1; i++) {
                                    argStrs.add(buildVarargsName(arg, i, nameFunc));
                                }
                            } else if (arg.isOptional() && !foundOptArg.get()) {
                                argStrs.add("[" + nameFunc.apply(arg));
                                foundOptArg.set(true);
                            } else {
                                argStrs.add(nameFunc.apply(arg));
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
        }

        return sigStr;
    }

    private static String toTruncatedArgName(final Arg arg) {
        final String name = arg.getName();
        return name.length() <= 3
                ? name
                : name.substring(0, 3);
    }

    private static boolean isNonBracketedForm(final String name,
                                              final List<Arg> args) {
        return (name.length() == 1 || ">=".equals(name) || "<=".equals(name))
               && (args.size() == 2 || (args.size() == 1 && args.get(0).isVarargs()));
    }

    private static String buildVarargsName(final Arg arg,
                                           final int argNo,
                                           final Function<Arg, String> nameFunc) {

        final String suffix = argNo <= arg.getMinVarargsCount()
                ? String.valueOf(argNo)
                : "N";
        final String prefix = argNo <= arg.getMinVarargsCount()
                ? ""
                : "... , ";
        return prefix + nameFunc.apply(arg) + suffix;
    }
}
