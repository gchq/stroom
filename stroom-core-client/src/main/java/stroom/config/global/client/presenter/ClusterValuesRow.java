package stroom.config.global.client.presenter;

import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.util.shared.Expander;
import stroom.util.shared.TreeRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

class ClusterValuesRow implements TreeRow {

    private String effectiveValue;
    private String nodeName;
    private Expander expander;

    ClusterValuesRow(final String effectiveValue,
                     final String nodeName,
                     final Expander expander) {
        this.effectiveValue = effectiveValue;
        this.nodeName = nodeName;
        this.expander = expander;
    }

    @Override
    public Expander getExpander() {
        return null;
    }

    public String getEffectiveValue() {
        return effectiveValue;
    }

    public String getNodeName() {
        return nodeName;
    }

    public static List<ClusterValuesRow> buildTree(final Map<String, Set<String>> effectiveValueToNodesMap) {

        final List<ClusterValuesRow> rows = new ArrayList<>();
        final int depth = 0;

        effectiveValueToNodesMap.entrySet()
                .stream()
                .sorted(Comparator.comparing(entry ->
                    entry.getValue()
                            .stream()
                            .sorted()
                            .findFirst()
                            .orElse("")
                ))
                .forEach(entry -> {
                    String effectiveValue = entry.getKey();
                    ClusterValuesRow row = new ClusterValuesRow(
                            effectiveValue,
                            null,
                            new Expander(depth, true, false));
                    rows.add(row);

                    entry.getValue()
                            .stream()
                            .sorted()
                            .map(nodeName ->
                                    new ClusterValuesRow(
                                            effectiveValue,
                                            nodeName,
                                            new Expander(depth + 1, true, true)))
                            .forEach(rows::add);
                });
        return rows;
    }
}
