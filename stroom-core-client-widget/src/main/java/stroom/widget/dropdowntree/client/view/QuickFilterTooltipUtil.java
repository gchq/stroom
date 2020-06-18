package stroom.widget.dropdowntree.client.view;

import stroom.util.shared.filter.FilterFieldDefinition;
import stroom.widget.tooltip.client.presenter.TooltipUtil;
import stroom.widget.tooltip.client.presenter.TooltipUtil.Builder;

import java.util.Collections;
import java.util.List;

public class QuickFilterTooltipUtil {

    public static String createTooltip(final String header) {
        return createTooltip(header, Collections.emptyList());
    }

    public static String createTooltip(final String header, final List<FilterFieldDefinition> fieldDefinitions) {

        // All this help content needs to match what happens in QuickFilterPredicateFactory
        final Builder builder = TooltipUtil.builder()
                .addHeading(header)
                .addBreak()
                .addHeading("Match types:")
                .addTable(tableBuilder -> tableBuilder
                        .addRow("#87af", "UUID partial matching")
                        .addRow("/xxx", "Regular expression matching")
                        .addRow("?ABC", "Word boundary matching")
                        .addRow("^abc$", "Exact match")
                        .addRow("abc$", "Suffix match")
                        .addRow("^abc", "Prefix matching")
                        .addRow("abc", "Characters anywhere (in order) matching (default)")
                        .build());

        addFieldDefinitions(fieldDefinitions, builder);

        builder
                .addBreak()
                .appendWithoutBreak("For more information see the ")
                .appendLinkWithoutBreak(
                        "https://gchq.github.io/stroom-docs/user-guide/finding-things/finding-things.html",
                        "Help Documentation");

        return builder.build();
    }

    private static void addFieldDefinitions(final List<FilterFieldDefinition> fieldDefinitions,
                                            final Builder builder) {
        if (fieldDefinitions != null && !fieldDefinitions.isEmpty()) {
            builder
                    .addBreak()
                    .addRowData("myfield:abc", "Named field matching (using above match types)")
                    .addBreak()
                    .addHeading("Supported fields (with qualifier name):")
                    .addTable(tableBuilder -> {
                        fieldDefinitions.forEach(fieldDefinition -> {
                            String suffix = fieldDefinition.isDefaultField()
                                    ? " (default field)"
                                    : "";
                            tableBuilder.addRow(
                                    fieldDefinition.getDisplayName(),
                                    fieldDefinition.getFilterQualifier() + suffix);
                        });
                        return tableBuilder.build();
                    })
                    .addBreak()
                    .addHeading("Examples:")
                    .addTable(tableBuilder -> tableBuilder
                            .addRow(
                                    "'/abc type:^err'",
                                    "Matches default field(s) with regex 'abc' and Type field with prefix 'err'")
                            .addRow(
                                    "'name:abc type:/(error|warn)'",
                                    "Matches Name field with 'abc' chars anywhere and Type field with regex '(error|warn)'")
                            .build());
        }

    }
}
