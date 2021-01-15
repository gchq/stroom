package stroom.dashboard.client.table;

import stroom.dashboard.shared.FunctionDefinition;
import stroom.dashboard.shared.FunctionDefinition.Arg;
import stroom.util.client.SafeHtmlUtil;
import stroom.widget.menu.client.presenter.InfoMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.tooltip.client.presenter.TooltipUtil.Builder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Command;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionSnippet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FunctionDefinitionUtil {

    private static final int DEFAULT_COMPLETION_SCORE = 300; // Not sure what the range of scores is

    private FunctionDefinitionUtil() {
    }

    public static List<Item> buildMenuItems(final List<FunctionDefinition> functionDefinitions,
                                            final Consumer<String> insertFunction,
                                            final String helpUrlBase) {

        final AtomicInteger categoryPosition = new AtomicInteger(0);
        return functionDefinitions.stream()
                .collect(Collectors.groupingBy(FunctionDefinition::getFunctionCategory))
                .entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .map(entry -> {
                    final String category = entry.getKey();
                    // Create one for each alias too
                    final List<FunctionDefinition> categoryFuncDefs = entry.getValue()
                            .stream()
                            .flatMap(functionDefinition -> functionDefinition.asAliases().stream())
                            .filter(functionDefinition -> functionDefinition.getName().length() > 1)
                            .collect(Collectors.toList());
                    final AtomicInteger functionPosition = new AtomicInteger(0);

                    final List<Item> functionMenuItems = categoryFuncDefs.stream()
                            .sorted(Comparator.comparing(FunctionDefinition::getName))
                            .map(functionDefinition ->
                                    convertFunctionDefinitionToItem(
                                            functionDefinition,
                                            insertFunction,
                                            functionPosition.getAndIncrement(),
                                            helpUrlBase))
                            .collect(Collectors.toList());

                    return new SimpleParentMenuItem(
                            categoryPosition.getAndIncrement(),
                            category,
                            functionMenuItems);
                })
                .collect(Collectors.toList());
    }

    public static List<AceCompletion> buildCompletions(final List<FunctionDefinition> functionDefinitions,
                                                       final String helpUrlBase) {
        // FlatMap to aliases so we have one func def per alias
        // Filter on name length > 1 to ignore aliases like +, -, * etc which have a different form,
        // e.g. 1+2 vs add(1, 2)
        return functionDefinitions.stream()
                .flatMap(functionDefinition -> functionDefinition.asAliases().stream())
                .filter(functionDefinition -> functionDefinition.getName().length() > 1)
                .flatMap(functionDefinition ->
                        convertFunctionDefinitionToCompletion(
                                functionDefinition,
                                helpUrlBase))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public static SafeHtml buildInfoHtml(final FunctionDefinition functionDefinition,
                                         final FunctionDefinition.Signature signature,
                                         final String helpUrlBase) {
        if (functionDefinition != null) {

            final Builder builder = TooltipUtil.builder()
                    .addHeading(getSignatureStr(functionDefinition, signature))
                    .addSeparator();

            boolean hasContent = false;

            if (signature.getDescription() != null && !signature.getDescription().isEmpty()) {
                builder.addLine(signature.getDescription());
                hasContent = true;
            }

            if (hasContent) {
                builder.addBreak();
            }

            addArgsBlockToInfo(signature, builder);

            if (hasContent) {
                builder.addBreak();
            }

            if (helpUrlBase != null) {
                addHelpLinkToInfo(functionDefinition, helpUrlBase, builder);
            }
            return builder.build();

        } else {
            return SafeHtmlUtil.getSafeHtml("");
        }
    }

    private static Stream<AceCompletion> convertFunctionDefinitionToCompletion(
            final FunctionDefinition functionDefinition,
            final String helpUrlBase) {

        return functionDefinition.getSignatures()
                .stream()
                .map(signature -> {
                    final String name = getSignatureStr(functionDefinition, signature);
                    // TODO the help link doesn't work as ace seems to be hijacking the click
                    // event so leave it out for now.
                    final String html = buildInfoHtml(functionDefinition, signature, null)
                            .asString();
                    final String functionTypeStr = signature.getArgs().isEmpty()
                            ? "Value"
                            : "Function";
                    final String meta = functionDefinition.getFunctionCategory() + " " + functionTypeStr;
                    final String snippetText = buildSnippetText(functionDefinition, signature);

                    GWT.log("Adding snippet " + name + " | " + meta + " | " + snippetText);

                    return new AceCompletionSnippet(
                            name,
                            snippetText,
                            DEFAULT_COMPLETION_SCORE,
                            meta,
                            html);
                });
    }

    private static String buildSnippetText(final FunctionDefinition functionDefinition,
                                           final FunctionDefinition.Signature signature) {
        final String argsStr;
        if (signature.getArgs().isEmpty()) {
            argsStr = "";
        } else {
            final AtomicInteger argPosition = new AtomicInteger(1);
            argsStr = signature.getArgs()
                    .stream()
                    .flatMap(arg -> {
                        final List<String> snippetArgStrs = new ArrayList<>();

                        if (arg.isVarargs()) {
                            for (int i = 1; i <= arg.getMinVarargsCount(); i++) {
                                final String argName = arg.getName() + i;
                                snippetArgStrs.add(argToSnippetArg(argName, argPosition.getAndIncrement()));
                            }
                        } else {
                            snippetArgStrs.add(argToSnippetArg(arg.getName(), argPosition.getAndIncrement()));
                        }
                        return snippetArgStrs.stream();
                    })
                    .collect(Collectors.joining(", "));
        }

        return functionDefinition.getName() + "(" + argsStr + ")$0";
    }

    private static String argToSnippetArg(final String argName,
                                          final int position) {
        return "${" + position + ":" + argName + "}";
    }

    private static Item convertFunctionDefinitionToItem(final FunctionDefinition functionDefinition,
                                                        final Consumer<String> insertFunction,
                                                        final int functionPosition,
                                                        final String helpUrlBase) {
        Objects.requireNonNull(functionDefinition);
        Objects.requireNonNull(insertFunction);

        // We either return
        //   func1 (sig) -> info
        // or
        //   func1
        //     -> sig1 -> info
        //     -> sig2 -> info

        final Item functionMenuItem;
        if (functionDefinition.getSignatures().size() == 1) {
            functionMenuItem = convertSignatureToItem(functionDefinition,
                    functionDefinition.getSignatures().get(0),
                    insertFunction,
                    functionPosition,
                    helpUrlBase);
        } else {
            AtomicInteger signaturePosition = new AtomicInteger(0);
            List<Item> childItems = functionDefinition.getSignatures()
                    .stream()
                    .map(signature ->
                            convertSignatureToItem(
                                    functionDefinition,
                                    signature,
                                    insertFunction,
                                    signaturePosition.getAndIncrement(),
                                    helpUrlBase))
                    .collect(Collectors.toList());

            // Wrap all the signatures in a menu item for the function
            functionMenuItem = new SimpleParentMenuItem(
                    functionPosition,
                    functionDefinition.getName(),
                    childItems);
        }
        return functionMenuItem;
    }

    private static Item convertSignatureToItem(final FunctionDefinition functionDefinition,
                                               final FunctionDefinition.Signature signature,
                                               final Consumer<String> insertFunction,
                                               final int signaturePosition,
                                               final String helpUrlBase) {
        // Return something like
        // funcX (sigY) -> info

        final String signatureStr = getSignatureStr(functionDefinition, signature);

        final Command command = () -> insertFunction.accept(signatureStr);
        final InfoMenuItem infoMenuItem = new InfoMenuItem(
                buildInfoHtml(functionDefinition, signature, helpUrlBase),
                null,
                false,
                null);

        return new SimpleParentMenuItem(
                signaturePosition,
                signatureStr,
                Collections.singletonList(infoMenuItem),
                command);
    }

    private static String getSignatureStr(final FunctionDefinition functionDefinition,
                                          final FunctionDefinition.Signature signature) {
        final String argsStr;
        if (signature.getArgs().isEmpty()) {
            argsStr = "";
        } else {

            argsStr = signature.getArgs()
                    .stream()
                    .flatMap(arg -> {
                        List<String> argStrs = new ArrayList<>();

                        if (arg.isVarargs()) {
                            for (int i = 1; i <= arg.getMinVarargsCount() + 1; i++) {
//                                final String suffix = i <= arg.getMinVarargsCount()
//                                        ? String.valueOf(i)
//                                        : "N";
//                                final String prefix = i <= arg.getMinVarargsCount()
//                                        ? ""
//                                        : "... , ";
//                                argStrs.add(prefix + arg.getName() + suffix);
                                argStrs.add(buildVarargsName(arg, i));
                            }
                        } else {
                            argStrs.add(arg.getName());
                        }
                        return argStrs.stream();
                    })
                    .collect(Collectors.joining(", "));
        }

        return functionDefinition.getName() + "(" + argsStr + ")";
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


    private static void addArgsBlockToInfo(final FunctionDefinition.Signature signature,
                                           final Builder builder) {
        builder.addThreeColTable(tableBuilder -> {
            tableBuilder.addHeaderRow("Parameter", "Type", "Description");

            signature.getArgs()
                    .forEach(arg -> {
                        if (arg.isVarargs()) {
                            for (int i = 1; i <= arg.getMinVarargsCount() + 1; i++) {
                                final String suffix = i <= arg.getMinVarargsCount()
                                        ? String.valueOf(i)
                                        : "N";

                                tableBuilder.addRow(
                                        arg.getName() + suffix,
                                        convertType(arg.getArgType()),
                                        arg.getDescription());
                            }
                        } else {
                            tableBuilder.addRow(
                                    arg.getName(),
                                    convertType(arg.getArgType()),
                                    arg.getDescription());
                        }
                    });
            if (signature.getReturnType() != null) {
                if (!signature.getArgs().isEmpty()) {
                    tableBuilder.addBlankRow();
                }
                tableBuilder.addRow(
                        "Return",
                        convertType(signature.getReturnType()),
                        signature.getReturnDescription());
            }
            return tableBuilder.build();
        });
    }

    private static void addHelpLinkToInfo(final FunctionDefinition functionDefinition,
                                          final String helpUrlBase,
                                          final Builder builder) {
        builder
                .appendWithoutBreak("For more information see the ")
                .appendLinkWithoutBreak(
                        helpUrlBase +
                                "/user-guide/dashboards/expressions/" +
                                functionDefinition.getFunctionCategory().toLowerCase() +
                                "#" +
                                functionNameToAnchor(functionDefinition.getName()),
                        "Help Documentation")
                .appendWithoutBreak(".");
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

    private static String convertType(final FunctionDefinition.Type type) {
        final String number = "Number";
        switch (type) {
            case LONG:
            case DOUBLE:
            case INTEGER:
                return number;
            case STRING:
                return "Text";
            default:
                return type.getName();
        }
    }
}
