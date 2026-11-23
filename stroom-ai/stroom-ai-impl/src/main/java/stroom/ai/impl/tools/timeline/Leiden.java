/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools.timeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/// A network's clusters by the Leiden method (Traag, Waltman and van Eck, 2019): the same algorithm, step for step,
/// as the chart analytics' network clusters (`leiden` in the generic visualisations, `d3-generic.js` and
/// `echarts-generic.js`, Node Colours cluster), so a timeline's activities are found as a network chart's clusters
/// are. It improves on Louvain by never leaving a cluster badly connected, or in pieces: each node is moved into the
/// neighbouring cluster that most raises the modularity (how much more the clusters are linked within than chance
/// would give); each cluster is then refined into well-connected parts, merged node by node (the best merge each
/// time, rather than Leiden's random one, so the same network always gives the same clusters); and each part
/// becomes one node of a smaller network, starting in its cluster, until no node moves. Links are taken either way
/// round, weighted by their value.
final class Leiden {

    /// The most levels the network is folded.
    static final int MAX_LEVELS = 30;
    private static final double EPSILON = 1e-12;

    private Leiden() {
        // Static only.
    }

    /// @param names The nodes, in a fixed order.
    /// @param links The links between them, by name.
    /// @return Each node's cluster (by its place in names), numbered from 0 in order of first appearance.
    static int[] clusters(final List<String> names, final List<Link> links) {
        final Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            index.put(names.get(i), i);
        }
        Graph graph = new Graph(names.size());
        for (final Link link : links) {
            final Integer a = index.get(link.source());
            final Integer b = index.get(link.target());
            final double weight = link.value() > 0
                    ? link.value()
                    : 0;
            if (a == null || b == null || a.equals(b) || weight == 0) {
                continue;
            }
            graph.adjacent.get(a).merge(b, weight, Double::sum);
            graph.adjacent.get(b).merge(a, weight, Double::sum);
        }
        // Each original node's node at this level, and each node's cluster.
        int[] membership = identity(names.size());
        int[] community = identity(names.size());
        for (int level = 0; level < MAX_LEVELS; level++) {
            final double[] degree = new double[graph.size()];
            double twiceTotal = 0;
            for (int i = 0; i < graph.size(); i++) {
                double sum = 0;
                for (final double weight : graph.adjacent.get(i).values()) {
                    sum += weight;
                }
                degree[i] = sum + 2 * graph.within[i];
                twiceTotal += degree[i];
            }
            if (twiceTotal == 0) {
                break;
            }
            community = moveNodes(graph, degree, twiceTotal, community);
            if (distinct(community) == community.length) {
                break;
            }
            final int[] parts = refineClusters(graph, degree, twiceTotal, community);
            // Each part becomes a node, starting in the cluster its nodes are in.
            final Map<Integer, Integer> number = new HashMap<>();
            for (final int part : parts) {
                number.putIfAbsent(part, number.size());
            }
            // Nothing merged: the next level would be this one again.
            if (number.size() == parts.length) {
                break;
            }
            final Graph next = new Graph(number.size());
            final int[] nextCommunity = new int[number.size()];
            for (int a = 0; a < parts.length; a++) {
                final int from = number.get(parts[a]);
                nextCommunity[from] = community[a];
                next.within[from] += graph.within[a];
                for (final Map.Entry<Integer, Double> other : graph.adjacent.get(a).entrySet()) {
                    final int to = number.get(parts[other.getKey()]);
                    if (to == from) {
                        // Seen from both its ends.
                        next.within[from] += other.getValue() / 2;
                    } else {
                        next.adjacent.get(from).merge(to, other.getValue(), Double::sum);
                    }
                }
            }
            final int[] nextMembership = new int[membership.length];
            for (int i = 0; i < membership.length; i++) {
                nextMembership[i] = number.get(parts[membership[i]]);
            }
            membership = nextMembership;
            graph = next;
            community = nextCommunity;
        }
        final Map<Integer, Integer> numbered = new HashMap<>();
        final int[] result = new int[membership.length];
        for (int i = 0; i < membership.length; i++) {
            result[i] = numbered.computeIfAbsent(community[membership[i]], c -> numbered.size());
        }
        return result;
    }

    /// The modularity of clusters: the share of the links' weight within clusters, less the share chance would
    /// give.
    ///
    /// @param names    The nodes.
    /// @param links    The links.
    /// @param clusters Each node's cluster, as [#clusters] gives them.
    /// @return The modularity, from -0.5 to 1; 0 with no links.
    static double modularity(final List<String> names, final List<Link> links, final int[] clusters) {
        final Map<String, Integer> of = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            of.put(names.get(i), clusters[i]);
        }
        final Map<Integer, Double> inside = new HashMap<>();
        final Map<Integer, Double> ends = new HashMap<>();
        double total = 0;
        for (final Link link : links) {
            final Integer a = of.get(link.source());
            final Integer b = of.get(link.target());
            if (a == null || b == null || link.source().equals(link.target()) || !(link.value() > 0)) {
                continue;
            }
            total += link.value();
            ends.merge(a, link.value(), Double::sum);
            ends.merge(b, link.value(), Double::sum);
            if (a.equals(b)) {
                inside.merge(a, link.value(), Double::sum);
            }
        }
        if (total == 0) {
            return 0;
        }
        double modularity = 0;
        for (final Map.Entry<Integer, Double> cluster : ends.entrySet()) {
            final double share = cluster.getValue() / (2 * total);
            modularity += inside.getOrDefault(cluster.getKey(), 0.0) / total - share * share;
        }
        return modularity;
    }

    // Leiden's fast local moving: each node in turn (from a queue) moves into the neighbouring cluster that most
    // raises the modularity, and its neighbours outside that cluster are visited again, until none moves.
    private static int[] moveNodes(final Graph graph, final double[] degree, final double twiceTotal,
                                   final int[] start) {
        final int[] community = start.clone();
        final Map<Integer, Double> totals = new HashMap<>();
        for (int i = 0; i < community.length; i++) {
            totals.merge(community[i], degree[i], Double::sum);
        }
        final List<Integer> queue = new ArrayList<>();
        final boolean[] queued = new boolean[community.length];
        for (int i = 0; i < community.length; i++) {
            queue.add(i);
            queued[i] = true;
        }
        // Read from its head, as removing from a long queue's front is slow.
        for (int head = 0; head < queue.size(); head++) {
            final int i = queue.get(head);
            queued[i] = false;
            final int own = community[i];
            final Map<Integer, Double> toCommunity = new TreeMap<>();
            for (final Map.Entry<Integer, Double> other : graph.adjacent.get(i).entrySet()) {
                toCommunity.merge(community[other.getKey()], other.getValue(), Double::sum);
            }
            totals.merge(own, -degree[i], Double::sum);
            int best = own;
            double bestGain = toCommunity.getOrDefault(own, 0.0) - totals.getOrDefault(own, 0.0) * degree[i]
                                                                   / twiceTotal;
            for (final Map.Entry<Integer, Double> candidate : toCommunity.entrySet()) {
                final double gain = candidate.getValue() - totals.getOrDefault(candidate.getKey(), 0.0) * degree[i]
                                                           / twiceTotal;
                if (gain > bestGain + EPSILON) {
                    best = candidate.getKey();
                    bestGain = gain;
                }
            }
            totals.merge(best, degree[i], Double::sum);
            if (best != own) {
                community[i] = best;
                for (final int other : new TreeMap<>(graph.adjacent.get(i)).keySet()) {
                    if (community[other] != best && !queued[other]) {
                        queued[other] = true;
                        queue.add(other);
                    }
                }
            }
        }
        return community;
    }

    // Leiden's refinement: within each cluster, every node starts as a part of its own; each node still alone, and
    // well connected to the rest of its cluster, merges into the well-connected part of its cluster it raises the
    // modularity most by joining (if any), so each part is connected. Returns each node's part.
    private static int[] refineClusters(final Graph graph,
                                        final double[] degree,
                                        final double twiceTotal,
                                        final int[] community) {
        final int[] part = identity(community.length);
        final boolean[] alone = new boolean[community.length];
        Arrays.fill(alone, true);
        final double[] partTotal = degree.clone();
        final Map<Integer, Double> clusterTotal = new HashMap<>();
        final Map<Integer, List<Integer>> members = new TreeMap<>();
        for (int i = 0; i < community.length; i++) {
            clusterTotal.merge(community[i], degree[i], Double::sum);
            members.computeIfAbsent(community[i], c -> new ArrayList<>()).add(i);
        }
        // Each part's links to the rest of its cluster.
        final double[] outside = new double[community.length];
        for (int i = 0; i < community.length; i++) {
            for (final Map.Entry<Integer, Double> other : graph.adjacent.get(i).entrySet()) {
                if (community[other.getKey()] == community[i]) {
                    outside[i] += other.getValue();
                }
            }
        }
        for (final Map.Entry<Integer, List<Integer>> cluster : members.entrySet()) {
            final double total = clusterTotal.get(cluster.getKey());
            for (final int v : cluster.getValue()) {
                if (!alone[v] || outside[v] < degree[v] * (total - degree[v]) / twiceTotal) {
                    continue;
                }
                final Map<Integer, Double> toPart = new TreeMap<>();
                for (final Map.Entry<Integer, Double> other : graph.adjacent.get(v).entrySet()) {
                    if (community[other.getKey()] == cluster.getKey()) {
                        toPart.merge(part[other.getKey()], other.getValue(), Double::sum);
                    }
                }
                int best = -1;
                double bestGain = 0;
                for (final Map.Entry<Integer, Double> candidate : toPart.entrySet()) {
                    final int p = candidate.getKey();
                    if (p == part[v] || outside[p] < partTotal[p] * (total - partTotal[p]) / twiceTotal) {
                        continue;
                    }
                    final double gain = candidate.getValue() - partTotal[p] * degree[v] / twiceTotal;
                    if (gain > bestGain + EPSILON || (best < 0 && gain >= 0)) {
                        best = p;
                        bestGain = gain;
                    }
                }
                if (best < 0) {
                    continue;
                }
                final int own = part[v];
                // The merged part's links to the rest of its cluster: both parts' less those between them.
                outside[best] = outside[best] + outside[own] - 2 * toPart.get(best);
                partTotal[best] += partTotal[own];
                part[v] = best;
                alone[v] = false;
                alone[best] = false;
            }
        }
        return part;
    }

    private static int[] identity(final int size) {
        final int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = i;
        }
        return values;
    }

    private static int distinct(final int[] values) {
        return (int) Arrays.stream(values).distinct().count();
    }


    // --------------------------------------------------------------------------------


    /// A link between two nodes.
    ///
    /// @param source One node.
    /// @param target The other.
    /// @param value  Its weight; a link of no weight is left out.
    record Link(String source, String target, double value) {

    }


    // --------------------------------------------------------------------------------


    // The network at one level: each node's links to others (by index) and the weight within it.
    private static final class Graph {

        private final List<Map<Integer, Double>> adjacent = new ArrayList<>();
        private final double[] within;

        Graph(final int size) {
            for (int i = 0; i < size; i++) {
                adjacent.add(new HashMap<>());
            }
            within = new double[size];
        }

        int size() {
            return within.length;
        }
    }
}
