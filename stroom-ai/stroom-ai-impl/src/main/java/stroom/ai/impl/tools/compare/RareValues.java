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
import stroom.ai.impl.tools.compare.AggregateQuery.Bucket;
import stroom.ai.impl.tools.compare.AggregateQuery.Kind;
import stroom.ai.impl.tools.compare.AggregateQuery.Metric;
import stroom.ai.impl.tools.compare.AggregateQuery.Source;
import stroom.ai.impl.tools.compare.AggregateRunner.Table;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

/// A population screen's rare values (`rare`): the values of one keyword field held by very few of the people
/// screened (at most [#MIN_PEOPLE] people or [#SHARE] of them, whichever is more), from one aggregate of each value's
/// distinct people, then the holders of the rare ones (a second aggregate, filtered to them), ranked by how few hold
/// each and how new it is for them. A field of full hostnames is checked for subdomains of one service, rare by name
/// only (a CDN's hosts), which are left out and said, with the field that groups them where the index has one.
final class RareValues {

    /// The fewest people a value may be held by and still be rare, whatever the population.
    static final int MIN_PEOPLE = 3;
    /// The share of the population a value may be held by and still be rare, where that is more than
    /// [#MIN_PEOPLE].
    static final double SHARE = 0.01;
    /// The most rare values whose holders are read.
    static final int MAX_VALUES = 200;
    /// The most rare values the result lists.
    static final int LISTED = 10;
    /// The fewest distinct values a field must have to be offered for `rare`.
    static final int MANY_VALUES = 20;
    /// The most fields whose distinct values are counted to offer.
    static final int MAX_FIELDS = 30;
    /// The fewest rare subdomains of one parent domain that make them a service's hosts, rare by name only.
    static final int SUBDOMAINS = 5;
    static final String PEOPLE = "people";
    static final String FEATURE = "rare:";
    /// The end of a field of registered domains, as ECS names them (`url.registered_domain`).
    static final String REGISTERED_DOMAIN = "registered_domain";
    private static final Pattern HOSTNAME = Pattern.compile("(?i)(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+"
                                                            + "[a-z][a-z0-9-]*[a-z0-9]");

    private RareValues() {
        // Static only.
    }

    /// @param population The people screened.
    /// @return The most people a rare value may be held by.
    static int threshold(final int population) {
        return Math.max(MIN_PEOPLE, (int) Math.ceil(SHARE * population));
    }

    /// Each value of the field with its distinct people, events and first and last times.
    ///
    /// @param source The index.
    /// @param where  Each field's values an event must hold; empty for all.
    /// @param person The person field.
    /// @param field  The rare field.
    /// @param from   The period's start.
    /// @param to     The period's end.
    /// @return The aggregate.
    static AggregateQuery byValue(final Source source,
                                  final Map<String, List<String>> where,
                                  final String person,
                                  final String field,
                                  final Instant from,
                                  final Instant to) {
        return new AggregateQuery(source, where, from, to, Bucket.NONE, List.of(field), List.of(
                new Metric(Kind.DISTINCT, person, PEOPLE),
                new Metric(Kind.COUNT, null, ActivityProfiles.EVENTS),
                new Metric(Kind.MIN_TIME, null, ActivityProfiles.FIRST),
                new Metric(Kind.MAX_TIME, null, ActivityProfiles.LAST)), ActivityProfiles.MAX_ROWS);
    }

    /// The holders of some values: each value and person with their first and last times and events.
    ///
    /// @param source The index.
    /// @param where  Each field's values an event must hold; empty for all.
    /// @param person The person field.
    /// @param field  The rare field.
    /// @param values The rare values.
    /// @param from   The period's start.
    /// @param to     The period's end.
    /// @return The aggregate.
    static AggregateQuery holders(final Source source,
                                  final Map<String, List<String>> where,
                                  final String person,
                                  final String field,
                                  final List<String> values,
                                  final Instant from,
                                  final Instant to) {
        final Map<String, List<String>> filters = new LinkedHashMap<>(where);
        filters.put(field, List.copyOf(values));
        return new AggregateQuery(source, filters, from, to, Bucket.NONE, List.of(field, person), List.of(
                new Metric(Kind.MIN_TIME, null, ActivityProfiles.FIRST),
                new Metric(Kind.MAX_TIME, null, ActivityProfiles.LAST),
                new Metric(Kind.COUNT, null, ActivityProfiles.EVENTS)), ActivityProfiles.MAX_ROWS);
    }

    /// The distinct values of each of some fields over the period, one row.
    ///
    /// @param source The index.
    /// @param where  Each field's values an event must hold; empty for all.
    /// @param fields The fields (at most [#MAX_FIELDS] are counted).
    /// @param from   The period's start.
    /// @param to     The period's end.
    /// @return The aggregate.
    static AggregateQuery cardinality(final Source source,
                                      final Map<String, List<String>> where,
                                      final List<String> fields,
                                      final Instant from,
                                      final Instant to) {
        final List<Metric> metrics = new ArrayList<>();
        final List<String> counted = fields.stream().limit(MAX_FIELDS).toList();
        for (int i = 0; i < counted.size(); i++) {
            metrics.add(new Metric(Kind.DISTINCT, counted.get(i), ActivityProfiles.DISTINCT_PREFIX + i));
        }
        // One row: a limit of two, so it is never taken for cut short.
        return new AggregateQuery(source, where, from, to, Bucket.NONE, List.of(), metrics, 2);
    }

    /// The fields to offer for `rare`: those with many distinct values ([#MANY_VALUES]), most first.
    ///
    /// @param fields The fields counted, as [#cardinality] was given them.
    /// @param table  Its table.
    /// @return Each field with many values and how many.
    static List<FieldValues> usable(final List<String> fields, final Table table) {
        final List<FieldValues> usable = new ArrayList<>();
        if (table.rows().isEmpty()) {
            return usable;
        }
        final Map<String, String> row = table.rows().getFirst();
        final List<String> counted = fields.stream().limit(MAX_FIELDS).toList();
        for (int i = 0; i < counted.size(); i++) {
            final long values = (long) ActivityProfiles.number(row.get(ActivityProfiles.DISTINCT_PREFIX + i));
            if (values >= MANY_VALUES) {
                usable.add(new FieldValues(counted.get(i), values));
            }
        }
        usable.sort(Comparator.comparingLong(FieldValues::values).reversed().thenComparing(FieldValues::field));
        return usable;
    }

    /// Reads the field's values and picks the rare ones: held by at least one and at most the threshold of people,
    /// fewest first, then the newest; the subdomains of a parent domain with [#SUBDOMAINS] or more rare subdomains
    /// are left out as a service's hosts (counted in the result's noise).
    ///
    /// @param field      The field.
    /// @param population The people screened.
    /// @param table      The [#byValue] table.
    /// @return The rare values, at most [#MAX_VALUES], and those left out as noise.
    static Picked pick(final String field, final int population, final Table table) {
        final int threshold = threshold(population);
        final List<Value> all = new ArrayList<>();
        for (final Map<String, String> row : table.rows()) {
            final String value = NullSafe.trim(row.get(AggregateQuery.groupColumn(0)));
            final Instant first = ActivityProfiles.instant(row.get(ActivityProfiles.FIRST));
            final Instant last = ActivityProfiles.instant(row.get(ActivityProfiles.LAST));
            final int people = (int) ActivityProfiles.number(row.get(PEOPLE));
            if (!value.isEmpty() && first != null && last != null && people > 0) {
                all.add(new Value(value, people, (long) ActivityProfiles.number(row.get(ActivityProfiles.EVENTS)),
                        first, last));
            }
        }
        final List<Value> rare = all.stream().filter(value -> value.people() <= threshold).toList();
        // Hostnames: most values look like one, and a good share have a subdomain (so not file names, x.exe).
        final boolean hostnames = !all.isEmpty()
                                  && all.stream().filter(value -> isHostname(value.value())).count() * 2 >= all.size()
                                  && all.stream().filter(value -> hasSubdomain(value.value())).count() * 4
                                     >= all.size();
        final Map<String, Integer> subdomains = new TreeMap<>();
        if (hostnames) {
            rare.stream()
                    .filter(value -> hasSubdomain(value.value()))
                    .forEach(value -> subdomains.merge(parent(value.value()), 1, Integer::sum));
        }
        final Map<String, Integer> noisy = new TreeMap<>();
        subdomains.forEach((parent, count) -> {
            if (count >= SUBDOMAINS) {
                noisy.put(parent, count);
            }
        });
        final List<Value> kept = rare.stream()
                .filter(value -> !(hasSubdomain(value.value()) && noisy.containsKey(parent(value.value()))))
                .sorted(Comparator.comparingInt(Value::people)
                        .thenComparing(Value::first, Comparator.reverseOrder())
                        .thenComparing(Value::value))
                .limit(MAX_VALUES)
                .toList();
        return new Picked(field, threshold, population, all.size(), kept, noisy, hostnames, table.cutShort());
    }

    /// The note for a field of full hostnames: rare by name isn't rare by service where subdomains of one parent
    /// come and go (a CDN's hosts), naming the field that groups them where the index has one (a registered domain,
    /// else its category field).
    ///
    /// @param picked        The rare values picked.
    /// @param fields        The index's fields.
    /// @param categoryField The index's category field, or null.
    /// @return The note, if the field holds hostnames.
    static Optional<String> hostnameNote(final Picked picked,
                                         final Collection<String> fields,
                                         final String categoryField) {
        if (!picked.hostnames()) {
            return Optional.empty();
        }
        final Optional<String> registered = fields.stream()
                .filter(name -> name.endsWith(REGISTERED_DOMAIN) && !name.equals(picked.field()))
                .sorted()
                .findFirst();
        final String better = registered
                .map(name -> " " + name + " holds each host's registered domain: rare=" + name + " screens by "
                             + "service.")
                .orElse(categoryField != null && !categoryField.equals(picked.field())
                        ? " " + categoryField + " holds each host's category: rare=" + categoryField + " screens by "
                          + "kind of site."
                        : "");
        final StringBuilder sb = new StringBuilder(picked.field()).append(" holds full hostnames, so a service's ")
                .append("hosts (a CDN's, by subdomain) can look rare by name alone (CDN noise).");
        if (!picked.noise().isEmpty()) {
            sb.append(" Left out as such: the rare subdomains of ").append(String.join(", ", picked.noise()
                    .entrySet().stream()
                    .limit(LISTED)
                    .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                    .toList())).append('.');
        }
        return Optional.of(sb.append(better).toString());
    }

    /// Finds each rare value's holders and whether it is new for each: first held in the window by someone active
    /// in the source before it, not on a day their leave or an approved exception explains.
    ///
    /// @param picked      The rare values picked.
    /// @param holders     The [#holders] table.
    /// @param windowFrom  The window's start.
    /// @param firstActive Each person's first activity in the source.
    /// @param explained   True for a person's time their leave or an approved exception explains.
    /// @return The rare values with their holders, fewest people first, then those new to someone, then the newest.
    static List<Held> held(final Picked picked,
                           final Table holders,
                           final Instant windowFrom,
                           final Map<String, Instant> firstActive,
                           final BiPredicate<String, Instant> explained) {
        final Map<String, List<Holder>> byValue = new LinkedHashMap<>();
        for (final Map<String, String> row : holders.rows()) {
            final String value = NullSafe.trim(row.get(AggregateQuery.groupColumn(0)));
            final String who = NullSafe.trim(row.get(AggregateQuery.groupColumn(1)));
            final Instant first = ActivityProfiles.instant(row.get(ActivityProfiles.FIRST));
            if (value.isEmpty() || who.isEmpty() || first == null) {
                continue;
            }
            final Instant active = firstActive.get(who);
            final boolean isNew = !first.isBefore(windowFrom) && active != null && active.isBefore(windowFrom)
                                  && !explained.test(who, first);
            byValue.computeIfAbsent(value, k -> new ArrayList<>()).add(new Holder(who, first,
                    (long) ActivityProfiles.number(row.get(ActivityProfiles.EVENTS)), isNew));
        }
        final List<Held> held = new ArrayList<>();
        for (final Value value : picked.values()) {
            final List<Holder> own = byValue.getOrDefault(value.value(), List.of()).stream()
                    .sorted(Comparator.comparing(Holder::first).thenComparing(Holder::person))
                    .toList();
            if (!own.isEmpty()) {
                held.add(new Held(value, own));
            }
        }
        held.sort(Comparator.comparingInt((Held h) -> h.value().people())
                .thenComparing(h -> !h.isNew())
                .thenComparing(h -> h.value().first(), Comparator.reverseOrder())
                .thenComparing(h -> h.value().value()));
        return held;
    }

    /// Each person's finding of the rare values new for them in the window: an indicator just past the threshold,
    /// a little more for each further value, so a new rare value lifts a person without outweighing a strong
    /// change.
    ///
    /// @param source The source's name.
    /// @param picked The rare values picked.
    /// @param held   The rare values with their holders.
    /// @param window The window, as a finding names it.
    /// @param zone   The zone the days are in.
    /// @return Each person's finding.
    static Map<String, Finding> findings(final String source,
                                         final Picked picked,
                                         final List<Held> held,
                                         final String window,
                                         final ZoneId zone) {
        final Map<String, List<String>> newFor = new LinkedHashMap<>();
        for (final Held one : held) {
            for (final Holder holder : one.holders()) {
                if (holder.isNew()) {
                    newFor.computeIfAbsent(holder.person(), k -> new ArrayList<>()).add(one.value().value() + " ("
                            + holder.first().atZone(zone).toLocalDate() + ", held by " + one.value().people()
                            + ")");
                }
            }
        }
        final Map<String, Finding> findings = new LinkedHashMap<>();
        newFor.forEach((who, values) -> {
            final double score = Math.min(2 * ActivityScoring.Z_THRESHOLD, ActivityScoring.Z_THRESHOLD
                                                                           + values.size() - 1);
            findings.put(who, new Finding(source, FEATURE + picked.field(), window, 0, values.size(), score,
                    values.size() + " value(s) of " + picked.field() + " held by at most " + picked.threshold()
                    + " of the " + picked.population() + " people screened, first used in the window: "
                    + String.join(", ", values.stream().limit(LISTED).toList()) + (values.size() > LISTED
                            ? ", ..."
                            : ""), "A new task, tool or site their work needed that few others' does (a new "
                                   + "project, a specialist role).", ActivityScoring.Kind.RARE));
        });
        return findings;
    }

    /// @param value A value.
    /// @return True if it looks like a hostname of two or more labels.
    static boolean isHostname(final String value) {
        return value != null && value.length() <= 253 && HOSTNAME.matcher(value).matches();
    }

    /// @param value A value.
    /// @return True if it looks like a hostname with a subdomain: three or more labels.
    static boolean hasSubdomain(final String value) {
        return isHostname(value) && value.split("\\.").length >= 3;
    }

    /// The parent domain of a hostname: its last two labels, or three where the second last is a short label under
    /// a two-letter country code (`example.co.uk`).
    ///
    /// @param host A hostname.
    /// @return Its parent domain.
    static String parent(final String host) {
        final String[] labels = host.toLowerCase(Locale.ROOT).split("\\.");
        if (labels.length <= 2) {
            return String.join(".", labels);
        }
        final int keep = labels[labels.length - 1].length() == 2 && labels[labels.length - 2].length() <= 3
                ? 3
                : 2;
        return String.join(".", List.of(labels).subList(labels.length - keep, labels.length));
    }


    // --------------------------------------------------------------------------------


    /// A field offered for `rare`.
    ///
    /// @param field  The field.
    /// @param values Its distinct values in the period.
    record FieldValues(String field, long values) {

    }


    // --------------------------------------------------------------------------------


    /// One value of the rare field.
    ///
    /// @param value  The value.
    /// @param people The distinct people holding it.
    /// @param events The events holding it.
    /// @param first  Its first time.
    /// @param last   Its last time.
    record Value(String value, int people, long events, Instant first, Instant last) {

    }


    // --------------------------------------------------------------------------------


    /// The rare values picked from one field.
    ///
    /// @param field      The field.
    /// @param threshold  The most people a rare value is held by.
    /// @param population The people screened.
    /// @param distinct   The field's distinct values in the period.
    /// @param values     The rare values, fewest people first.
    /// @param noise      The parent domains whose many rare subdomains were left out, with how many.
    /// @param hostnames  True if the field holds hostnames.
    /// @param cutShort   True if its values were cut short by the row limit.
    record Picked(String field,
                  int threshold,
                  int population,
                  int distinct,
                  List<Value> values,
                  Map<String, Integer> noise,
                  boolean hostnames,
                  boolean cutShort) {

        /// Copies the collections.
        Picked {
            values = List.copyOf(values);
            noise = new TreeMap<>(noise);
        }
    }


    // --------------------------------------------------------------------------------


    /// One holder of a rare value.
    ///
    /// @param person The person.
    /// @param first  Their first time with it.
    /// @param events Their events with it.
    /// @param isNew  True if it is new for them: first held in the window, after activity before it.
    record Holder(String person, Instant first, long events, boolean isNew) {

    }


    // --------------------------------------------------------------------------------


    /// A rare value with its holders.
    ///
    /// @param value   The value.
    /// @param holders Its holders, first first.
    record Held(Value value, List<Holder> holders) {

        /// Copies the holders.
        Held {
            holders = List.copyOf(holders);
        }

        /// @return True if it is new for any holder.
        boolean isNew() {
            return holders.stream().anyMatch(Holder::isNew);
        }
    }
}
