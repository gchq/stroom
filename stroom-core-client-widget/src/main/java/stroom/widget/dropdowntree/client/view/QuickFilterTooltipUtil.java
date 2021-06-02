package stroom.widget.dropdowntree.client.view;

import stroom.util.shared.filter.FilterFieldDefinition;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.tooltip.client.presenter.TooltipUtil.Builder;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class QuickFilterTooltipUtil {

    protected static final String FINDING_THINGS_HELP_PAGE = "/user-guide/finding-things/finding-things.html";

    public static SafeHtml createTooltip(final String header,
                                         final String helpUrlBase) {
        return createTooltip(header, Collections.emptyList(), helpUrlBase);
    }

    public static SafeHtml createTooltip(final String header,
                                         final List<FilterFieldDefinition> fieldDefinitions,
                                         final String helpUrlBase) {

        return createTooltip(header, null, fieldDefinitions, helpUrlBase);
    }

    public static SafeHtml createTooltip(final String header,
                                         final Consumer<Builder> preambleBuilder,
                                         final List<FilterFieldDefinition> fieldDefinitions,
                                         final String helpUrlBase) {

        final String defaultFieldNames = getDefaultFieldNamesInfo(fieldDefinitions);

        final String description = "By default matches on " +
                (defaultFieldNames != null
                        ? defaultFieldNames + " "
                        : "") +
                "where the values contain the text input.";
        // All this help content needs to match what happens in QuickFilterPredicateFactory
        final Builder builder = TooltipUtil.builder()
                .addHeading(header)
                .addBreak()
                .addLine(description)
                .addLine("Space is used to separate multiple terms, unless quoted, see examples.");

        // Add the supplied pre-amble
        if (preambleBuilder != null) {
            preambleBuilder.accept(builder);
        }

        // Adds a break at the end if there are fields
        addFieldInfo(fieldDefinitions, builder);

        builder
                .addTwoColTable(tableBuilder -> {
                    tableBuilder
                            .addHeaderRow("Example input", "Match type")
                            .addRow(TooltipUtil.fixedWidthText("abc"),
                                    "Default contains matching. (matches 'xxabcxx').")
                            .addRow(TooltipUtil.fixedWidthText("~abc"),
                                    "Characters anywhere (in order) matching (matches 'xxaxxbxxcxx').")
                            .addRow(TooltipUtil.fixedWidthText("/ab.c"),
                                    "Regular expression matching (matches 'xxabxcxx').")
                            .addRow(TooltipUtil.fixedWidthText("?ABC"),
                                    "Word boundary matching (matches 'AlphaBravoCharlie').")
                            .addRow(TooltipUtil.fixedWidthText("=abc"),
                                    "Exact match (matches 'abc').")
                            .addRow(TooltipUtil.fixedWidthText("$abc"),
                                    "Suffix match (matches 'xxxabc')")
                            .addRow(TooltipUtil.fixedWidthText("^abc"),
                                    "Prefix match (matches abcxxx').")
                            .addRow(TooltipUtil.fixedWidthText("!abc"),
                                    "Negated match (does not match values containing 'abc'). " +
                                            "Can be used with other match types, e.g. '!=abc'.");

                    if (fieldDefinitions != null && !fieldDefinitions.isEmpty()) {
                        final List<String> qualifiedFields = fieldDefinitions.stream()
                                .filter(filterFieldDefinition -> !filterFieldDefinition.isDefaultField())
                                .map(FilterFieldDefinition::getFilterQualifier)
                                .sorted()
                                .collect(Collectors.toList());

                        if (!qualifiedFields.isEmpty()) {
                            tableBuilder
                                    .addBlankRow()
                                    .addRow(
                                            TooltipUtil.fixedWidthText(""
                                                    + qualifiedFields.get(0)
                                                    + ":abc"),
                                            "Named field matching (using above match types on field '"
                                                    + qualifiedFields.get(0) + "').");
                        }
                    }

                    return tableBuilder.build();
                });

        addFieldExamples(fieldDefinitions, builder);

        builder
                .addBreak()
                .appendWithoutBreak("For more information see the ")
                .appendLinkWithoutBreak(
                        helpUrlBase + FINDING_THINGS_HELP_PAGE,
                        "Help Documentation")
                .appendWithoutBreak(".");

        return builder.build();
    }

    private static String getDefaultFieldNamesInfo(final List<FilterFieldDefinition> fieldDefinitions) {
        if (fieldDefinitions != null) {
            final List<String> fieldNames = fieldDefinitions.stream()
                    .filter(FilterFieldDefinition::isDefaultField)
                    .map(FilterFieldDefinition::getDisplayName)
                    .map(str -> "\"" + str + "\"")
                    .collect(Collectors.toList());
            if (fieldNames.isEmpty()) {
                return null;
            } else if (fieldNames.size() == 1) {
                return "field " + fieldNames.get(0);
            } else if (fieldNames.size() == 2) {
                return "fields " + fieldNames.get(0) + " or " + fieldNames.get(1);
            } else {
                return "fields "
                        + String.join(", ", fieldNames.subList(0, fieldNames.size() - 1))
                        + " or "
                        + fieldNames.get(fieldNames.size() - 1);
            }
        } else {
            return null;
        }
    }

    private static void addFieldInfo(final List<FilterFieldDefinition> fieldDefinitions,
                                     final Builder builder) {
        if (fieldDefinitions != null && !fieldDefinitions.isEmpty()) {
            builder
                    .addBreak()
                    .addTwoColTable(tableBuilder -> {
                        tableBuilder.addHeaderRow("Filterable fields", "Field qualifier");
                        fieldDefinitions.forEach(fieldDefinition -> {
                            String suffix = fieldDefinition.isDefaultField()
                                    ? " (Default)"
                                    : "";
                            final SafeHtml value = new SafeHtmlBuilder()
                                    .append(TooltipUtil.fixedWidthText(fieldDefinition.getFilterQualifier()))
                                    .append(TooltipUtil.italicText(suffix))
                                    .toSafeHtml();
                            tableBuilder.addRow((fieldDefinition.getDisplayName()), value);
                        });
                        return tableBuilder.build();
                    })
                    .addBreak();
        }
    }

    private static void addFieldExamples(final List<FilterFieldDefinition> fieldDefinitions, final Builder builder) {

        builder
                .addBreak()
                .addHeading("Examples:")
                .addTwoColTable(tableBuilder -> {
                    tableBuilder
                            .addRow(
                                    TooltipUtil.fixedWidthText("abc \"d ef\""),
                                    "Matches default field(s) with values containing 'abc' AND 'd ef'.");


                    if (fieldDefinitions != null && !fieldDefinitions.isEmpty()) {

                        final List<String> qualifiers = fieldDefinitions.stream()
                                .filter(filterFieldDefinition -> !filterFieldDefinition.isDefaultField())
                                .map(FilterFieldDefinition::getFilterQualifier)
                                .sorted()
                                .collect(Collectors.toList());

                        if (qualifiers.size() == 1) {

                            tableBuilder
                                    .addRow(
                                            TooltipUtil.fixedWidthText("/abc " + qualifiers.get(0) + ":^def"),
                                            "Matches default field(s) with regex 'abc' " +
                                                    "and " + qualifiers.get(0) + " field values that start with 'def'");
                        } else if (qualifiers.size() > 1) {
                            tableBuilder
                                    .addRow(
                                            TooltipUtil.fixedWidthText("/abc " + qualifiers.get(0) + ":^def"),
                                            "Matches default field(s) with regex 'abc' " +
                                                    "and " + qualifiers.get(0) + " field values that start with 'def'")
                                    .addRow(
                                            TooltipUtil.fixedWidthText(
                                                    "\"" + qualifiers.get(0) + ":ab c\" "
                                                            + qualifiers.get(1) + ":/(def|xyz)"),
                                            "Matches " + qualifiers.get(0) + " field values which contain 'ab c' and "
                                                    + qualifiers.get(1) + " field values which matches " +
                                                    "regex '(def|xyz)'")
                                    .addRow(
                                            TooltipUtil.fixedWidthText("" + qualifiers.get(0) + ":?ABC "
                                                    + qualifiers.get(1) + ":!/def"),
                                            "Matches " + qualifiers.get(0)
                                                    + " field values with word first letters A, B, C " +
                                                    "and " + qualifiers.get(1) + " field values that " +
                                                    "don't match regex 'def'");
                        }
                    }
                    return tableBuilder.build();
                });
    }
}
