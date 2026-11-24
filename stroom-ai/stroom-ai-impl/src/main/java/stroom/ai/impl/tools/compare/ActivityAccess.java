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

package stroom.ai.impl.tools.compare;

import stroom.ai.impl.tools.compare.ActivityScoring.Finding;
import stroom.ai.impl.tools.compare.ActivityScoring.Kind;
import stroom.ai.impl.tools.compare.AggregateQuery.Source;
import stroom.ai.impl.tools.compare.AggregateRunner.Table;
import stroom.ai.impl.tools.timeline.Leiden;
import stroom.ai.impl.tools.timeline.Leiden.Link;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// The access graph of `compare_activity`: people and the resources they reach (shares, repositories, sites, record
/// types: a field's values) in the baseline, from one aggregate (people by resource, capped), clustered by the
/// Leiden algorithm the timelines use. Then the person's resources in the window: those outside their cluster, and
/// those no one in their cluster reached before. Also the peers learned from access: the people of their cluster.
final class ActivityAccess {

    /// The feature's name.
    static final String FEATURE = "access";
    /// The most resources named in a finding.
    static final int NAMED = 10;
    /// The least share of the window's events outside the cluster that is a finding.
    static final double MIN_SHARE = 0.1;
    /// The fewest events outside the cluster that is a finding.
    static final int MIN_EVENTS = 5;

    private ActivityAccess() {
        // Static only.
    }

    /// @return Everyone's events by person and resource in the baseline.
    static AggregateQuery graph(final Source source,
                                final String personField,
                                final String resourceField,
                                final Instant from,
                                final Instant to) {
        return new AggregateQuery(source, Map.of(), from, to, AggregateQuery.Bucket.NONE,
                List.of(personField, resourceField), List.of(new AggregateQuery.Metric(AggregateQuery.Kind.COUNT,
                null, ActivityProfiles.EVENTS)), ActivityProfiles.MAX_ROWS);
    }

    /// @return The person's events by resource in the window.
    static AggregateQuery window(final Source source,
                                 final ActivityProfiles.Scope scope,
                                 final String resourceField,
                                 final Instant from,
                                 final Instant to) {
        return new AggregateQuery(source, scope.filter(), from, to, AggregateQuery.Bucket.NONE,
                List.of(resourceField), List.of(new AggregateQuery.Metric(AggregateQuery.Kind.COUNT, null,
                ActivityProfiles.EVENTS)), ActivityProfiles.MAX_ROWS);
    }

    /// Compares the person's access in the window with the baseline's clusters.
    ///
    /// @param source        The source's name.
    /// @param resourceField The resource field.
    /// @param person        The person's values of the person field.
    /// @param graph         Everyone's events by person and resource in the baseline.
    /// @param window        The person's events by resource in the window.
    /// @param windowLabel   The window, as the finding names it.
    /// @return The finding, if any, and what the result should say.
    static Compared compare(final String source,
                            final String resourceField,
                            final List<String> person,
                            final Table graph,
                            final Table window,
                            final String windowLabel) {
        final List<String> notes = new ArrayList<>();
        if (graph.cutShort()) {
            notes.add(source + ": the access graph was cut short at " + ActivityProfiles.MAX_ROWS + " people and "
                      + resourceField + " values: its clusters are approximate.");
        }
        final Map<String, Map<String, Double>> byPerson = new LinkedHashMap<>();
        for (final Map<String, String> row : graph.rows()) {
            final String who = row.get(AggregateQuery.groupColumn(0));
            final String resource = row.get(AggregateQuery.groupColumn(1));
            if (NullSafe.isNonBlankString(who) && NullSafe.isNonBlankString(resource)) {
                byPerson.computeIfAbsent(who, k -> new LinkedHashMap<>()).merge(resource,
                        ActivityProfiles.number(row.get(ActivityProfiles.EVENTS)), Double::sum);
            }
        }
        final Optional<String> own = person.stream()
                .filter(byPerson::containsKey)
                .max((a, b) -> Double.compare(total(byPerson.get(a)), total(byPerson.get(b))));
        if (own.isEmpty()) {
            notes.add(source + ": the person reached no " + resourceField + " in the baseline: no access graph.");
            return new Compared(Optional.empty(), notes, List.of());
        }
        final List<String> names = new ArrayList<>();
        final List<Link> links = new ArrayList<>();
        final Set<String> resources = new LinkedHashSet<>();
        byPerson.forEach((who, reached) -> {
            names.add(personNode(who));
            reached.forEach((resource, events) -> {
                resources.add(resource);
                links.add(new Link(personNode(who), resourceNode(resource), Math.log1p(events)));
            });
        });
        resources.forEach(resource -> names.add(resourceNode(resource)));
        final int[] clusters = Leiden.clusters(names, links);
        final Map<String, Integer> clusterOf = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            clusterOf.put(names.get(i), clusters[i]);
        }
        final int cluster = clusterOf.get(personNode(own.get()));
        // The resources anyone in the person's cluster reached in the baseline.
        final Set<String> clusterReached = new LinkedHashSet<>();
        final List<String> peers = new ArrayList<>();
        byPerson.forEach((who, reached) -> {
            if (clusterOf.get(personNode(who)) == cluster) {
                clusterReached.addAll(reached.keySet());
                if (!person.contains(who)) {
                    peers.add(who);
                }
            }
        });
        // The person's share of events outside their cluster, in the baseline and the window.
        final Map<String, Double> baseline = byPerson.get(own.get());
        final double baseOutside = baseline.entrySet().stream()
                .filter(entry -> clusterOf.get(resourceNode(entry.getKey())) != cluster)
                .mapToDouble(Map.Entry::getValue)
                .sum();
        final double baseShare = baseOutside / Math.max(1, total(baseline));
        double windowTotal = 0;
        double windowOutside = 0;
        final List<String> outside = new ArrayList<>();
        final List<String> newToCluster = new ArrayList<>();
        for (final Map<String, String> row : window.rows()) {
            final String resource = row.get(AggregateQuery.groupColumn(0));
            if (NullSafe.isBlankString(resource)) {
                continue;
            }
            final double events = ActivityProfiles.number(row.get(ActivityProfiles.EVENTS));
            windowTotal += events;
            final Integer resourceCluster = clusterOf.get(resourceNode(resource));
            if (resourceCluster == null || resourceCluster != cluster) {
                windowOutside += events;
                outside.add(resource);
            }
            if (!clusterReached.contains(resource)) {
                newToCluster.add(resource);
            }
        }
        if (!peers.isEmpty()) {
            notes.add(source + ": peers learned from access to " + resourceField + ": " + peers.size()
                      + " people share the person's cluster (" + String.join(", ", peers.stream().limit(NAMED)
                              .toList()) + (peers.size() > NAMED
                              ? ", ..."
                              : "") + ").");
        }
        final double windowShare = windowOutside / Math.max(1, windowTotal);
        if (windowOutside < MIN_EVENTS || windowShare < MIN_SHARE || windowShare < 2 * baseShare) {
            return new Compared(Optional.empty(), notes, peers);
        }
        final double score = Math.min(ActivityScoring.MAX_Z, ActivityScoring.Z_THRESHOLD * windowShare
                                                             / Math.max(baseShare, 0.05));
        final String evidence = Math.round(100 * windowShare) + "% of the window's " + resourceField + " events "
                                + "were on " + outside.size() + " value(s) outside the person's access cluster ("
                                + String.join(", ", outside.stream().limit(NAMED).toList())
                                + (outside.size() > NAMED
                                        ? ", ..."
                                        : "") + ") against " + Math.round(100 * baseShare) + "% in the baseline; "
                                + newToCluster.size() + " reached by no one in their cluster before";
        return new Compared(Optional.of(new Finding(source, FEATURE + ":" + resourceField, windowLabel, baseShare,
                windowShare, score, evidence, "A new project or role, cover for a colleague, or reorganised "
                                              + "resources.", Kind.ACCESS)), notes, peers);
    }

    private static double total(final Map<String, Double> reached) {
        return reached.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    private static String personNode(final String who) {
        return "p|" + who;
    }

    private static String resourceNode(final String resource) {
        return "r|" + resource;
    }


    // --------------------------------------------------------------------------------


    /// The person's access compared.
    ///
    /// @param finding The finding, if the window's access parts from the baseline's clusters.
    /// @param notes   What the result should say.
    /// @param peers   The people sharing the person's cluster.
    record Compared(Optional<Finding> finding, List<String> notes, List<String> peers) {

    }
}
