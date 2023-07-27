package stroom.query.client.presenter;

import stroom.dashboard.shared.FunctionSignature;
import stroom.dashboard.shared.FunctionSignature.Arg;
import stroom.dashboard.shared.FunctionSignature.Type;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionSnippet;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class FunctionSignatureUtil {

    private static final int DEFAULT_COMPLETION_SCORE = 300; // Not sure what the range of scores is

    private FunctionSignatureUtil() {
    }

    public static SafeHtml buildInfoHtml(final FunctionSignature signature,
                                         final String helpUrlBase) {
        if (signature != null) {
            final HtmlBuilder htmlBuilder = new HtmlBuilder();
            htmlBuilder.div(hb1 -> {
                hb1.bold(hb2 -> hb2.append(buildSignatureStr(signature)));
                hb1.br();
                hb1.hr();

                if (signature.getDescription() != null && !signature.getDescription().isEmpty()) {
                    hb1.para(hb2 -> hb2.append(signature.getDescription()),
                            Attribute.className("queryHelpDetail-description"));
                }

                final boolean addedArgs = addArgsBlockToInfo(signature, hb1);

                if (addedArgs) {
                    hb1.br();
                }

                final List<String> aliases = signature.getAliases();
                if (!aliases.isEmpty()) {
                    hb1.para(hb2 -> hb2.append("Aliases: " +
                            aliases.stream()
                                    .collect(Collectors.joining(", "))));
                }

                if (helpUrlBase != null) {
                    addHelpLinkToInfo(signature, helpUrlBase, hb1);
                }
            }, Attribute.className("queryHelpDetail"));

            return htmlBuilder.toSafeHtml();
        } else {
            return SafeHtmlUtils.EMPTY_SAFE_HTML;
        }
    }

    public static AceCompletion convertFunctionDefinitionToCompletion(
            final FunctionSignature signature,
            final String helpUrlBase,
            final int score) {

        final String name = buildSignatureStr(signature);

        // TODO the help link doesn't work as ace seems to be hijacking the click
        // event so leave it out for now.
        final String html = buildInfoHtml(signature, null)
                .asString();

        final String meta;
        if ("Value".equals(signature.getPrimaryCategory())) {
            meta = signature.getPrimaryCategory();
        } else if (signature.getArgs().isEmpty()) {
            meta = signature.getPrimaryCategory() + " Value";
        } else {
            meta = "Func (" + signature.getPrimaryCategory() + ")";
        }
        final String snippetText = buildSnippetText(signature);

//        GWT.log("Adding snippet " + name + " | " + meta + " | " + snippetText);

        return new AceCompletionSnippet(
                name,
                snippetText,
                GwtNullSafe.requireNonNullElse(score, DEFAULT_COMPLETION_SCORE),
                meta,
                html);
    }

    public static String buildSnippetText(final FunctionSignature signature) {
//        GWT.log("buildSnippetText " + signature.getName());
        final String name = signature.getName();
        final List<Arg> args = signature.getArgs();
        final String snippetStr;
        if (signature.getArgs().isEmpty()) {
            snippetStr = name + "()";
        } else if (isNonBracketedForm(signature)) {
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
            snippetStr = signature.getName() + "(" + argsStr + ")$0";
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
        final StringBuilder stringBuilder = new StringBuilder()
                .append("${")
                .append(position)
                .append(":")
                .append(snippetDefault
                        .replace("$", "\\$")
                        .replace("}", "\\}"))
                .append("}");

        return stringBuilder.toString();
    }

    private static boolean isNonBracketedForm(final FunctionSignature functionSignature) {
        final String name = functionSignature.getName();
        final List<Arg> args = functionSignature.getArgs();
        return (name.length() == 1 || ">=".equals(name) || "<=".equals(name))
                && (args.size() == 2 || (args.size() == 1 && args.get(0).isVarargs()));
    }

    public static String buildSignatureStr(final FunctionSignature signature) {
        final String name = signature.getName();
        final List<Arg> args = signature.getArgs();
        String sigStr;
        if (args.isEmpty()) {
            sigStr = name + " ()";
        } else if (args.size() > 3) {
            // Funcs with long arg lists get truncated. Help text explains all the args.
            sigStr = name + " (......)";
        } else if (isNonBracketedForm(signature)) {
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
            sigStr = signature.getArgs()
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


    private static boolean addArgsBlockToInfo(final FunctionSignature signature,
                                              final HtmlBuilder htmlBuilder) {
        AtomicBoolean addedContent = new AtomicBoolean(false);
        addedContent.set(!signature.getArgs().isEmpty());

        final TableBuilder tb = new TableBuilder();
        tb.row(
                TableCell.header("Parameter"),
                TableCell.header("Type"),
                TableCell.header("Description"));
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

                    tb.row(argName,
                            convertType(arg.getArgType()),
                            descriptionBuilder.toString());
                });
        if (signature.getReturnType() != null) {
            if (!signature.getArgs().isEmpty()) {
                tb.row();
            }
            tb.row("Return",
                    convertType(signature.getReturnType()),
                    signature.getReturnDescription());
            addedContent.set(true);
        }

        htmlBuilder.div(tb::write, Attribute.className("queryHelpDetail-table"));
        return addedContent.get();
    }

    private static StringBuilder appendSpaceIfNeeded(final StringBuilder stringBuilder) {
        if (stringBuilder.length() > 0) {
            stringBuilder.append(" ");
        }
        return stringBuilder;
    }

    private static void addHelpLinkToInfo(final FunctionSignature signature,
                                          final String helpUrlBase,
                                          final HtmlBuilder htmlBuilder) {
        htmlBuilder.append("For more information see the ");
        htmlBuilder.appendLink(
                helpUrlBase +
                        signature.getPrimaryCategory().toLowerCase().replace(" ", "-") +
                        "#" +
                        functionSignatureToAnchor(signature),
                "Help Documentation");
        htmlBuilder.append(".");
    }

    private static String functionSignatureToAnchor(final FunctionSignature signature) {
        final String helpAnchor = signature.getHelpAnchor();
        if (GwtNullSafe.isBlankString(helpAnchor)) {
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

    private static String convertType(final Type type) {
        final String number = "Number";
        switch (type) {
            case LONG:
            case DOUBLE:
            case INTEGER:
            case NUMBER:
                return number;
            case STRING:
                return "Text";
            default:
                return type.getName();
        }
    }
}
