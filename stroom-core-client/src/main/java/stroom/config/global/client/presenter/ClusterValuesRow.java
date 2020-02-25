package stroom.config.global.client.presenter;

import stroom.util.shared.Expander;
import stroom.util.shared.TreeRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class ClusterValuesRow implements TreeRow {

    private String effectiveValue;
    private Integer nodeCount;
    private String nodeName;
    private Expander expander;

    ClusterValuesRow(final String effectiveValue,
                     final Integer nodeCount,
                     final String nodeName,
                     final Expander expander) {
        this.effectiveValue = effectiveValue;
        this.nodeName = nodeName;
        this.nodeCount = nodeCount;
        this.expander = expander;
    }

    ClusterValuesRow(final String effectiveValue,
                     final Integer nodeCount,
                     final String nodeName) {
        this.effectiveValue = effectiveValue;
        this.nodeName = nodeName;
        this.nodeCount = nodeCount;
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

    public Integer getNodeCount() {
        return nodeCount;
    }

    public static List<ClusterValuesRow> buildTree(final Map<String, Set<String>> effectiveValueToNodesMap,
                                                   final ClusterValuesTreeAction treeAction) {

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
                    final Set<String> nodes = entry.getValue();
                    final int nodeCount = nodes != null ? nodes.size() : 0;

                    // If this value has only one node associated to it then just show all the detail
                    // in the master row
                    final boolean isLeaf;
                    final String groupRowNodeName;
                    if (nodeCount == 0) {
                        isLeaf = true;
                        groupRowNodeName = null;
                    } else if (nodeCount == 1) {
                        isLeaf = true;
                        groupRowNodeName = nodes.iterator().next();
                    } else {
                        isLeaf = false;
                        groupRowNodeName = null;
                    }
                    final ClusterValuesRow row = new ClusterValuesRow(
                            effectiveValue,
                            nodeCount,
                            groupRowNodeName);

                    boolean isExpanded = treeAction.isRowExpanded(row)
                        || (!treeAction.isRowExpanded(row) && !treeAction.isRowCollapsed(row));

                    if (row.getExpander() == null) {
                        row.setExpander(new Expander(depth, isExpanded, isLeaf));
                    } else {
                        row.getExpander().setExpanded(isExpanded);
                    }
                    treeAction.setRowExpanded(row, isExpanded);

                    // Add the group row, with blank node name
                    rows.add(row);

                    // Only show the child row if we have more than one child
                    if (nodeCount > 1) {
                        if (treeAction.isRowExpanded(row)) {
                            // Add the detail rows with blank value
                            entry.getValue()
                                .stream()
                                .sorted()
                                .map(nodeName ->
                                    new ClusterValuesRow(
                                        null,
                                        null,
                                        nodeName,
                                        new Expander(depth + 1, false, true)))
                                .forEach(rows::add);
                        }
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
