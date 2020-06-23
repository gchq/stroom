package stroom.widget.dropdowntree.client.view;

import stroom.util.shared.filter.FilterFieldDefinition;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.tooltip.client.presenter.TooltipUtil.Builder;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

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

        // All this help content needs to match what happens in QuickFilterPredicateFactory
        final Builder builder = TooltipUtil.builder()
                .addHeading(header)
                .addBreak();

        // Add the supplied pre-amble
        if (preambleBuilder != null) {
            preambleBuilder.accept(builder);
        }

        // Adds a break at the end if there are fields
        addFieldInfo(fieldDefinitions, builder);

        builder
                .addTable(tableBuilder -> {
                    tableBuilder
                            .addHeaderRow("Example input", "Match type")
                            .addRow(TooltipUtil.fixedWidthText("#87af"), "UUID partial matching")
                            .addRow(TooltipUtil.fixedWidthText("/xxx"), "Regular expression matching")
                            .addRow(TooltipUtil.fixedWidthText("?ABC"), "Word boundary matching")
                            .addRow(TooltipUtil.fixedWidthText("^abc$"), "Exact match")
                            .addRow(TooltipUtil.fixedWidthText("abc$"), "Suffix match")
                            .addRow(TooltipUtil.fixedWidthText("^abc"), "Prefix matching")
                            .addRow(TooltipUtil.fixedWidthText("abc"), "Characters anywhere (in order) matching (default)");

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

    private static void addFieldInfo(final List<FilterFieldDefinition> fieldDefinitions,
                                     final Builder builder) {
        if (fieldDefinitions != null && !fieldDefinitions.isEmpty()) {
            builder
                    .addTable(tableBuilder -> {
                        tableBuilder.addHeaderRow("Filterable fields", "Field qualifier");
                        fieldDefinitions.forEach(fieldDefinition -> {
                            String suffix = fieldDefinition.isDefaultField()
                                    ? " *"
                                    : "";
                            final SafeHtml value = new SafeHtmlBuilder()
                                    .append(TooltipUtil.fixedWidthText(fieldDefinition.getFilterQualifier()))
                                    .append(TooltipUtil.italicText(suffix))
                                    .toSafeHtml();
                            tableBuilder.addRow((fieldDefinition.getDisplayName()), value);
                        });
                        return tableBuilder.build();
                    })
                    .addBreak()
                    .addLine("* indicates the field will be included in default matching.")
                    .addBreak();
        }
    }

    private static void addFieldExamples(final List<FilterFieldDefinition> fieldDefinitions, final Builder builder) {
        if (fieldDefinitions != null && !fieldDefinitions.isEmpty()) {
            builder
                    .addBreak()
                    .addHeading("Examples:")
                    .addTable(tableBuilder -> tableBuilder
                            .addRow(
                                    TooltipUtil.fixedWidthText("/abc type:^err"),
                                    "Matches default field(s) with regex 'abc' and Type field with prefix 'err'")
                            .addRow(
                                    TooltipUtil.fixedWidthText("name:abc type:/(error|warn)"),
                                    "Matches Name field with 'abc' chars anywhere and Type field with regex '(error|warn)'")
                            .build());
        }
    }
}
