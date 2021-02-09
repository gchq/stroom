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

    public static SafeHtml createTooltip(final String header) {
        return createTooltip(header, Collections.emptyList());
    }
    public static SafeHtml createTooltip(final String header,
                                         final List<FilterFieldDefinition> fieldDefinitions) {

        return createTooltip(header, null, fieldDefinitions);
    }

    public static SafeHtml createTooltip(final String header,
                                         final Consumer<Builder> preambleBuilder,
                                         final List<FilterFieldDefinition> fieldDefinitions) {

        final String defaultFieldNames = getDefaultFieldNamesInfo(fieldDefinitions);

        final String description = "By default matches on " +
                (defaultFieldNames != null ?  defaultFieldNames  + " " : "") +
        "with the characters input appearing anywhere (in order) in the matches.";
        // All this help content needs to match what happens in QuickFilterPredicateFactory
        final Builder builder = TooltipUtil.builder()
                .addHeading(header)
                .addBreak()
                .addLine(description);

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
                                    "Characters anywhere (in order) matching (matches 'xxaxxbxxcxx'). (default)")
                            .addRow(TooltipUtil.fixedWidthText("/abc"),
                                    "Regular expression matching (matches 'xxabcxx').")
                            .addRow(TooltipUtil.fixedWidthText("?ABC"),
                                    "Word boundary matching (matches 'AlphaBravoCharlie').")
                            .addRow(TooltipUtil.fixedWidthText("^abc$"),
                                    "Exact match (matches 'abc'.")
                            .addRow(TooltipUtil.fixedWidthText("abc$"), "Suffix match (matches 'xxxabc')")
                            .addRow(TooltipUtil.fixedWidthText("^abc"),
                                    "Prefix match (matches abcxxx').")
                            .addRow(TooltipUtil.fixedWidthText("!abc"),
                                    "Negated match (matches 'xxx').");

                    if (fieldDefinitions != null && !fieldDefinitions.isEmpty()) {
                        tableBuilder
                                .addBlankRow()
                                .addRow(
                                        TooltipUtil.fixedWidthText("myfield:abc"),
                                        "Named field matching (using above match types)");
                    }

                    return tableBuilder.build();
                });

        addFieldExamples(fieldDefinitions, builder);

        builder
                .addBreak()
                .appendWithoutBreak("For more information see the ")
                .appendLinkWithoutBreak(
                        "https://gchq.github.io/stroom-docs/user-guide/finding-things/finding-things.html",
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
        if (fieldDefinitions != null && !fieldDefinitions.isEmpty()) {
            builder
                    .addBreak()
                    .addHeading("Examples:")
                    .addTwoColTable(tableBuilder -> tableBuilder
                            .addRow(
                                    TooltipUtil.fixedWidthText("/abc type:^err"),
                                    "Matches default field(s) with regex 'abc' and Type field with prefix 'err'")
                            .addRow(
                                    TooltipUtil.fixedWidthText("name:abc type:/(error|warn)"),
                                    "Matches Name field with 'abc' chars anywhere and Type field with " +
                                            "regex '(error|warn)'")
                            .addRow(
                                    TooltipUtil.fixedWidthText("name:?ABC type:!/error"),
                                    "Matches Name field with word first letters A, B, C and Type field that " +
                                            "doesn't match regex 'error'")
                            .build());
        }
    }
}
