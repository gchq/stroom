package stroom.config.global.client.presenter;

import stroom.util.shared.Expander;
import stroom.util.shared.TreeAction;
import stroom.util.shared.TreeRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    ClusterValuesRow(final String effectiveValue,
                     final String nodeName) {
        this.effectiveValue = effectiveValue;
        this.nodeName = nodeName;
        this.expander = null;
    }

    @Override
    public Expander getExpander() {
        return expander;
    }

    void setExpander(final Expander expander) {
        this.expander = expander;
    }

    public String getEffectiveValue() {
        return effectiveValue;
    }

    public String getNodeName() {
        return nodeName;
    }

    public static List<ClusterValuesRow> buildTree(final Map<String, Set<String>> effectiveValueToNodesMap,
                                                   final TreeAction<ClusterValuesRow> treeAction) {

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
                    final String effectiveValue = entry.getKey();
                    final ClusterValuesRow row = new ClusterValuesRow(
                            effectiveValue,
                            null);

                    boolean isExpanded = treeAction.isRowExpanded(row);
                    if (row.getExpander() == null) {
                        row.setExpander(new Expander(depth, isExpanded, false));
                    } else {
                        row.getExpander().setExpanded(isExpanded);
                    }

                    // Add the group row, with blank node name
                    rows.add(row);

                    if (treeAction.isRowExpanded(row)) {
                        // Add the detail rows with blank value
                        entry.getValue()
                            .stream()
                            .sorted()
                            .map(nodeName ->
                                new ClusterValuesRow(
                                    "",
                                    nodeName,
                                    new Expander(depth + 1, false, true)))
                            .forEach(rows::add);
                    }
                });
        return rows;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ClusterValuesRow that = (ClusterValuesRow) o;
        return Objects.equals(effectiveValue, that.effectiveValue) &&
            Objects.equals(nodeName, that.nodeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(effectiveValue, nodeName);
    }

    @Override
    public String toString() {
        return "ClusterValuesRow{" +
            "effectiveValue='" + effectiveValue + '\'' +
            ", nodeName='" + nodeName + '\'' +
            ", expander=" + expander +
            '}';
    }
}
