package stroom.dashboard.client.table;

import stroom.dashboard.shared.FunctionDefinition;
import stroom.util.client.SafeHtmlUtil;
import stroom.widget.menu.client.presenter.InfoMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuItem;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.tooltip.client.presenter.TooltipUtil.Builder;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FunctionDefinitionUtil {

    private FunctionDefinitionUtil() {
    }

    public static List<Item> buildMenuItems(final List<FunctionDefinition> functionDefinitions,
                                            final Consumer<String> insertFunction) {

        final AtomicInteger categoryPosition = new AtomicInteger(0);
        return functionDefinitions.stream()
                .collect(Collectors.groupingBy(FunctionDefinition::getFunctionCategory))
                .entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .map(entry -> {
                    final String category = entry.getKey();
                    final List<FunctionDefinition> categoryFuncDefs = entry.getValue();
                    final AtomicInteger functionPosition = new AtomicInteger(0);

                    final List<Item> functionMenuItems = categoryFuncDefs.stream()
                            .sorted(Comparator.comparing(FunctionDefinition::getName))
                            .map(functionDefinition ->
                                    convertFunctionDefinition(
                                            functionDefinition,
                                            insertFunction,
                                            functionPosition.getAndIncrement()))
                            .collect(Collectors.toList());

                    return new SimpleParentMenuItem(
                            categoryPosition.getAndIncrement(),
                            category,
                            functionMenuItems);
                })
                .collect(Collectors.toList());
    }

    private static Item convertFunctionDefinition(final FunctionDefinition functionDefinition,
                                                  final Consumer<String> insertFunction,
                                                  final int functionPosition) {
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
            functionMenuItem = convertSignature(functionDefinition,
                    functionDefinition.getSignatures().get(0),
                    insertFunction,
                    functionPosition);
        } else {
            AtomicInteger signaturePosition = new AtomicInteger(0);
            List<Item> childItems = functionDefinition.getSignatures()
                    .stream()
                    .map(signature ->
                            convertSignature(
                                    functionDefinition,
                                    signature,
                                    insertFunction,
                                    signaturePosition.getAndIncrement()))
                    .collect(Collectors.toList());

            // Wrap all the signatures in a menu item for the function
            functionMenuItem = new SimpleParentMenuItem(
                    functionPosition,
                    functionDefinition.getName(),
                    childItems);
        }
        return functionMenuItem;
    }

    private static MenuItem convertSignature(final FunctionDefinition functionDefinition,
                                             final FunctionDefinition.Signature signature,
                                             final Consumer<String> insertFunction,
                                             final int signaturePosition) {
        // Return something like
        // funcX (sigY) -> info

        final String signatureStr = getSignatureStr(functionDefinition, signature);

        final Command command = () -> insertFunction.accept(signatureStr);
        final InfoMenuItem infoMenuItem = new InfoMenuItem(
                buildInfoHtml(functionDefinition, signature),
                null,
                true,
                command);

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
                                final String suffix = i <= arg.getMinVarargsCount()
                                        ? String.valueOf(i)
                                        : "...";
                                argStrs.add(arg.getName() + suffix);
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

    private static SafeHtml buildInfoHtml(final FunctionDefinition functionDefinition,
                                          final FunctionDefinition.Signature signature) {
        if (functionDefinition != null) {

            final Builder builder = TooltipUtil.builder()
                    .addHeading(getSignatureStr(functionDefinition, signature))
                    .addSeparator();

            boolean hasContent = false;

            final String description = signature.getDescription() != null
                    ? signature.getDescription()
                    : functionDefinition.getDescription();

            if (description != null && !description.isEmpty()) {
                builder.addLine(description);
                hasContent = true;
            }

            if (hasContent) {
                builder.addBreak();
            }

            builder.addThreeColTable(tableBuilder -> {
                tableBuilder.addHeaderRow("Parameter", "Type", "Description");

                signature.getArgs()
                        .forEach(arg -> {
                            if (arg.isVarargs()) {
                                for (int i = 1; i <= arg.getMinVarargsCount() + 1; i++) {
                                    final String suffix = i <= arg.getMinVarargsCount()
                                            ? String.valueOf(i)
                                            : "...";
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

            if (hasContent) {
                builder.addBreak();
            }

            builder
                    .appendWithoutBreak("For more information see the ")
                    .appendLinkWithoutBreak(
                            "https://gchq.github.io/stroom-docs/user-guide/dashboards/expressions/" +
                                    functionDefinition.getFunctionCategory().toLowerCase() +
                                    "#" +
                                    functionNameToAnchor(functionDefinition.getName()),
                            "Help Documentation")
                    .appendWithoutBreak(".");
            return builder.build();

        } else {
            return SafeHtmlUtil.getSafeHtml("");
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
