package stroom.dashboard.client.table;

import stroom.dashboard.shared.FunctionSignature;
import stroom.dashboard.shared.FunctionSignature.Arg;
import stroom.dashboard.shared.FunctionSignature.Type;
import stroom.util.client.SafeHtmlUtil;
import stroom.widget.menu.client.presenter.InfoMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.tooltip.client.presenter.TooltipUtil.Builder;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Command;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionSnippet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FunctionDefinitionUtil {

    private static final int DEFAULT_COMPLETION_SCORE = 300; // Not sure what the range of scores is

    private FunctionDefinitionUtil() {
    }

    public static List<Item> buildMenuItems(final List<FunctionSignature> signatures,
                                            final Consumer<String> insertFunction,
                                            final String helpUrlBase) {
        return buildMenuItems(signatures, insertFunction, helpUrlBase, 0);
    }

    public static List<Item> buildMenuItems(final List<FunctionSignature> signatures,
                                            final Consumer<String> insertFunction,
                                            final String helpUrlBase,
                                            final int depth) {
        // This is roughly what we are aiming for
        // Date //primary category
        //   Rounding // sub-category
        //     ceil(
        //     floor(
        //   parseDate // overload branch
        //     parseDate($) // overload 1
        //     parseDate($, $) // overload 2
        // Aggregate
        //   average(
        //   mean( // alias for average

        // leaves and overload branches come after category branches
        final Comparator<Entry<Optional<String>, List<FunctionSignature>>> entryComparator =
                Comparator.comparing(entry ->
                        entry.getKey().orElse("ZZZZ"));

        final AtomicInteger positionInMenu = new AtomicInteger(0);
        return signatures.stream()
                .collect(Collectors.groupingBy(sig -> sig.getCategory(depth)))
                .entrySet()
                .stream()
                .sorted(entryComparator)
                .flatMap(optCatSigEntry -> {
                    // Either have an empty key with a single sig in the list
                    // or a category key with one/more sigs in the list
                    final Optional<String> optCategory = optCatSigEntry.getKey();
                    final List<FunctionSignature> categorySignatures = optCatSigEntry.getValue()
                            .stream()
                            .sorted(Comparator.comparing(FunctionSignature::getName))
                            .collect(Collectors.toList());

                    if (optCategory.isPresent()) {
                        // We have a category so recurse
                        final List<Item> childItems = buildMenuItems(
                                categorySignatures,
                                insertFunction,
                                helpUrlBase,
                                depth + 1);

                        return Stream.of(new SimpleParentMenuItem(
                                positionInMenu.getAndIncrement(),
                                optCategory.get(),
                                childItems));
                    } else {
                        // No category at this depth so this is a list of leaves
                        // Due to aliases, each leaf may become multiple leaves
                        // or due to overloads each leaf may become a branch
                        final List<Item> leafItems = convertLeaves(
                                categorySignatures,
                                positionInMenu,
                                insertFunction,
                                helpUrlBase);
                        return leafItems.stream();
                    }
                })
                .collect(Collectors.toList());
    }

    private static List<Item> convertLeaves(final List<FunctionSignature> signatures,
                                              final AtomicInteger positionInMenu,
                                              final Consumer<String> insertFunction,
                                              final String helpUrlBase) {

        // Create one for each alias too, except the special one char ones
        // like +, -, /, * etc. as they have a form without brackets
        final List<FunctionSignature> categorySignatures = signatures
                .stream()
                .flatMap(value ->
                        value.asAliases().stream())
//                .peek(functionSignature ->
//                        GWT.log("Func: " +
//                                buildSignatureStr(functionSignature) + " " +
//                                isBracketedForm(functionSignature)))
                .filter(FunctionDefinitionUtil::isBracketedForm)
                .sorted(Comparator.comparing(FunctionSignature::getName))
                .collect(Collectors.toList());

//        final AtomicInteger functionPosition = new AtomicInteger(0);

        // These will be branches if a func has multiple overloads in the category
        // or a leaf if it only has one
        final List<Item> childMenuItems = categorySignatures.stream()
                .collect(Collectors.groupingBy(FunctionSignature::getName))
                .entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .map(nameSigsEntry ->
                        convertFunctionDefinitionToItem(
                                nameSigsEntry.getKey(),
                                nameSigsEntry.getValue(),
                                insertFunction,
                                positionInMenu.getAndIncrement(),
                                helpUrlBase))
                .collect(Collectors.toList());

        return childMenuItems;
    }


    public static List<AceCompletion> buildCompletions(final List<FunctionSignature> signatures,
                                                       final String helpUrlBase) {
        // FlatMap to aliases so we have one func def per alias
        // Filter on name length > 1 to ignore aliases like +, -, * etc which have a different form,
        // e.g. 1+2 vs add(1, 2)
        return signatures.stream()
                .flatMap(signature -> signature.asAliases().stream())
                .filter(FunctionDefinitionUtil::isBracketedForm)
                .map(signature ->
                        convertFunctionDefinitionToCompletion(
                                signature,
                                helpUrlBase))
                .collect(Collectors.toList());
    }

    public static SafeHtml buildInfoHtml(final FunctionSignature signature,
                                         final String helpUrlBase) {
        if (signature != null) {

            // Limit the width of the tooltip
            final Builder builder = TooltipUtil.builder(safeStylesBuilder ->
                            safeStylesBuilder.appendTrustedString("max-width:500px;"))
                    .addHeading(buildSignatureStr(signature))
                    .addSeparator();

            if (signature.getDescription() != null && !signature.getDescription().isEmpty()) {
                builder.addSafeHtml(TooltipUtil.styledParagraph(
                        signature.getDescription(),
                        safeStylesBuilder ->
                                safeStylesBuilder.appendTrustedString("white-space: pre-wrap;")));
            }

            final boolean addedArgs = addArgsBlockToInfo(signature, builder);

            if (addedArgs) {
                builder.addBreak();
            }

            final List<String> aliases = signature.getAliases();
            if (!aliases.isEmpty()) {
                builder.addParagraph("Aliases: " +
                        aliases.stream()
                                .collect(Collectors.joining(", ")));
            }

            if (helpUrlBase != null) {
                addHelpLinkToInfo(signature, helpUrlBase, builder);
            }
            return builder.build();

        } else {
            return SafeHtmlUtil.getSafeHtml("");
        }
    }

    private static AceCompletion convertFunctionDefinitionToCompletion(
            final FunctionSignature signature,
            final String helpUrlBase) {

        final String name = buildSignatureStr(signature);

        // TODO the help link doesn't work as ace seems to be hijacking the click
        // event so leave it out for now.
        final String html = buildInfoHtml(signature, null)
                .asString();
        final String functionTypeStr = signature.getArgs().isEmpty()
                ? "Value"
                : "Function";
        final String meta = signature.getPrimaryCategory() + " " + functionTypeStr;
        final String snippetText = buildSnippetText(signature);

//                    GWT.log("Adding snippet " + name + " | " + meta + " | " + snippetText);

        return new AceCompletionSnippet(
                name,
                snippetText,
                DEFAULT_COMPLETION_SCORE,
                meta,
                html);
    }

    private static String buildSnippetText(final FunctionSignature signature) {
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

        return signature.getName() + "(" + argsStr + ")$0";
    }

    private static String argToSnippetArg(final String argName,
                                          final int position) {
        return "${" + position + ":" + argName + "}";
    }

    private static Item convertFunctionDefinitionToItem(final String name,
                                                        final List<FunctionSignature> signatures,
                                                        final Consumer<String> insertFunction,
                                                        final int functionPosition,
                                                        final String helpUrlBase) {

        // We either return
        //   func1 (sig) -> info
        // or
        //   func1
        //     -> sig1 -> info
        //     -> sig2 -> info

        final Item functionMenuItem;
        if (signatures.size() == 1) {
            functionMenuItem = convertSignatureToItem(
                    signatures.get(0),
                    insertFunction,
                    functionPosition,
                    helpUrlBase);
        } else {
            // Multiple sigs so add a branch in the tree
            final AtomicInteger signaturePosition = new AtomicInteger(0);
            final List<Item> childItems = signatures
                    .stream()
                    .sorted(Comparator.comparing(signature -> signature.getArgs().size()))
                    .map(signature ->
                            convertSignatureToItem(
                                    signature,
                                    insertFunction,
                                    signaturePosition.getAndIncrement(),
                                    helpUrlBase))
                    .collect(Collectors.toList());

            // Wrap all the signatures in a menu item for the function
            functionMenuItem = new SimpleParentMenuItem(
                    functionPosition,
                    name,
                    childItems);
        }
        return functionMenuItem;
    }

    private static Item convertSignatureToItem(final FunctionSignature signature,
                                               final Consumer<String> insertFunction,
                                               final int signaturePosition,
                                               final String helpUrlBase) {
        // Return something like
        // funcX (sigY) -> info

        final String signatureStr = buildInsertText(signature);

        final Command command = () -> insertFunction.accept(signatureStr);
        final InfoMenuItem infoMenuItem = new InfoMenuItem(
                buildInfoHtml(signature, helpUrlBase),
                null,
                false,
                null);

        return new SimpleParentMenuItem(
                signaturePosition,
                signatureStr,
                Collections.singletonList(infoMenuItem),
                command);
    }

    private static String buildSignatureStr(final FunctionSignature signature) {
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

        return signature.getName() + "(" + argsStr + ")";
    }

    private static String buildInsertText(final FunctionSignature signature) {
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
                                snippetArgStrs.add(argName);
                            }
                        } else {
                            snippetArgStrs.add(arg.getName());
                        }
                        return snippetArgStrs.stream();
                    })
                    .collect(Collectors.joining(", "));
        }

        return signature.getName() + "(" + argsStr + ")";
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
                                              final Builder builder) {
        AtomicBoolean addedContent = new AtomicBoolean(false);
        addedContent.set(!signature.getArgs().isEmpty());
        builder.addThreeColTable(tableBuilder -> {
            tableBuilder.addHeaderRow("Parameter", "Type", "Description");

            signature.getArgs()
                    .forEach(arg -> {
                        final String argName = arg.isVarargs()
                                ? arg.getName() + "1...N"
                                : arg.getName();
                        tableBuilder.addRow(
                                argName,
                                convertType(arg.getArgType()),
                                arg.getDescription());
                    });
            if (signature.getReturnType() != null) {
                if (!signature.getArgs().isEmpty()) {
                    tableBuilder.addBlankRow();
                }
                tableBuilder.addRow(
                        "Return",
                        convertType(signature.getReturnType()),
                        signature.getReturnDescription());
                addedContent.set(true);
            }
            return tableBuilder.build();
        });
        return addedContent.get();
    }

    private static void addHelpLinkToInfo(final FunctionSignature signature,
                                          final String helpUrlBase,
                                          final Builder builder) {
        builder
                .appendWithoutBreak("For more information see the ")
                .appendLinkWithoutBreak(
                        helpUrlBase +
                                "/user-guide/dashboards/expressions/" +
                                signature.getPrimaryCategory().toLowerCase().replace(" ", "-") +
                                "#" +
                                functionNameToAnchor(signature.getName()),
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

    private static String convertType(final Type type) {
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

    private static boolean isBracketedForm(final FunctionSignature signature) {

        return signature.getName().length() > 1
                && Character.isLetter(signature.getName().charAt(0));
    }

}
