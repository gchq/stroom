package stroom.query.client.presenter;

import stroom.query.shared.FunctionSignatureUtil;
import stroom.query.shared.QueryHelpFunctionSignature;
import stroom.query.shared.QueryHelpFunctionSignature.Arg;
import stroom.query.shared.QueryHelpFunctionSignature.Type;
import stroom.query.shared.QueryHelpRow;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class FunctionDetailProvider implements DetailProvider {

    private final HelpUrlProvider helpUrlProvider;

    @Inject
    public FunctionDetailProvider(final HelpUrlProvider helpUrlProvider) {
        this.helpUrlProvider = helpUrlProvider;
    }

    @Override
    public void getDetail(final QueryHelpRow row, final Consumer<Detail> consumer) {
        helpUrlProvider.fetchHelpUrl(helpUrl -> {
            final QueryHelpFunctionSignature sig = (QueryHelpFunctionSignature) row.getData();
            final String insertText = buildSnippetText(sig.getName(), sig.getArgs());
            final InsertType insertType = GwtNullSafe.isBlankString(insertText)
                    ? InsertType.BLANK
                    : InsertType.SNIPPET;
            final SafeHtml documentation = buildInfoHtml(sig, helpUrl);
            final Detail detail = new Detail(insertType, insertText, documentation);
            consumer.accept(detail);
        });
    }

    private String buildSnippetText(final String name,
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

    private boolean isNonBracketedForm(final String name,
                                       final List<Arg> args) {
        return (name.length() == 1 || ">=".equals(name) || "<=".equals(name))
                && (args.size() == 2 || (args.size() == 1 && args.get(0).isVarargs()));
    }


    private String argToSnippetArg(final Arg arg,
                                   final int position) {
        return argToSnippetArg(arg.getName(), arg, position);
    }

    private String argToSnippetArg(final String argName,
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

    private SafeHtml buildInfoHtml(final QueryHelpFunctionSignature signature,
                                   final String helpUrlBase) {
        if (signature != null) {
            final HtmlBuilder htmlBuilder = new HtmlBuilder();
            htmlBuilder.div(hb1 -> {
                hb1.bold(hb2 -> hb2.append(FunctionSignatureUtil.buildSignatureStr(signature.getName(),
                        signature.getArgs())));
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

    private static boolean addArgsBlockToInfo(final QueryHelpFunctionSignature signature,
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

    private static void addHelpLinkToInfo(final QueryHelpFunctionSignature signature,
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

    private static String functionSignatureToAnchor(final QueryHelpFunctionSignature signature) {
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
