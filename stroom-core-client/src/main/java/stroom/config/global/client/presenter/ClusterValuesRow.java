/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    private final String effectiveValue;
    private final Integer nodeCount;
    private final String nodeName;
    private final String source;
    private Expander expander;

    ClusterValuesRow(final String effectiveValue,
                     final Integer nodeCount,
                     final String source,
                     final String nodeName,
                     final Expander expander) {
        this.effectiveValue = effectiveValue;
        this.source = source;
        this.nodeName = nodeName;
        this.nodeCount = nodeCount;
        this.expander = expander;
    }

    ClusterValuesRow(final String effectiveValue,
                     final Integer nodeCount,
                     final String source,
                     final String nodeName) {
        this.effectiveValue = effectiveValue;
        this.source = source;
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

    public String getSource() {
        return source;
    }

    public Integer getNodeCount() {
        return nodeCount;
    }

    public static List<ClusterValuesRow> buildTree(final Map<String, Set<NodeSource>> effectiveValueToNodesMap,
                                                   final ClusterValuesTreeAction treeAction) {

        final List<ClusterValuesRow> rows = new ArrayList<>();
        final int depth = 0;

        effectiveValueToNodesMap.entrySet()
                .stream()
                .sorted(Comparator.comparing(entry ->
                        entry.getValue()
                                .stream()
                                .map(NodeSource::getNodeName)
                                .sorted()
                                .findFirst()
                                .orElse("")
                ))
                .forEach(entry -> {
                    final String effectiveValue = entry.getKey();
                    final Set<NodeSource> nodes = entry.getValue();
                    final int nodeCount = nodes != null
                            ? nodes.size()
                            : 0;

                    // If this value has only one node associated to it then just show all the detail
                    // in the master row
                    final boolean isLeaf;
                    final String groupRowNodeName;
                    final String groupRowSource;
                    if (nodeCount == 0) {
                        isLeaf = true;
                        groupRowNodeName = null;
                        groupRowSource = null;
                    } else if (nodeCount == 1) {
                        isLeaf = true;
                        final NodeSource nodeSource = nodes.iterator().next();
                        groupRowNodeName = nodeSource.getNodeName();
                        groupRowSource = nodeSource.getSource();
                    } else {
                        isLeaf = false;
                        groupRowNodeName = null;
                        groupRowSource = null;
                    }
                    final ClusterValuesRow row = new ClusterValuesRow(
                            effectiveValue,
                            nodeCount,
                            groupRowSource,
                            groupRowNodeName);

                    final boolean isExpanded = treeAction.isRowExpanded(row)
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
                                    .sorted(Comparator.comparing(NodeSource::getNodeName))
                                    .map(nodeEffectiveValue ->
                                            new ClusterValuesRow(
                                                    null,
                                                    null,
                                                    nodeEffectiveValue.getSource(),
                                                    nodeEffectiveValue.getNodeName(),
                                                    new Expander(depth + 1, false, true)))
                                    .forEach(rows::add);
                        }
                    }
                });
        return rows;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
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
