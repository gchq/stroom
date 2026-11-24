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
import stroom.ai.impl.tools.compare.ActivityScoring.Spec;
import stroom.ai.impl.tools.compare.AggregateQuery.Source;
import stroom.ai.impl.tools.compare.AggregateRunner.Table;
import stroom.util.shared.NullSafe;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Where a person's sign-ins and sessions came from, for `compare_activity`, from the index's own geo fields (a
/// country code; on an Elastic Index also a geo point, for the speed between places): countries new in the window,
/// impossible travel (two places further apart than an airliner flies in the time between; with countries only, two
/// countries within two hours, said as such), and activity from abroad on the person's leave. From an hourly
/// aggregate by country, never the events.
final class ActivityLocation {

    /// The fastest believable travel, km/h (an airliner's cruise).
    static final double MAX_SPEED_KMH = 900;
    /// The least distance worth calling travel, km (a city's spread, or geolocation's error).
    static final double MIN_DISTANCE_KM = 500;
    /// Two countries within this many hours, with no coordinates, are flagged.
    static final int COUNTRIES_WITHIN_HOURS = 2;
    /// The size of a location finding: notable, as each is an event a baseline doesn't explain.
    static final double SCORE = 10;
    static final String COUNTRY = "country";
    static final String PLACE = "place";
    private static final Pattern POINT = Pattern.compile("POINT\\s*\\(\\s*(-?[\\d.]+)\\s+(-?[\\d.]+)\\s*\\)");
    private static final double EARTH_KM = 6371.0;

    private ActivityLocation() {
        // Static only.
    }

    /// @return The person's events by hour and country, with their centroid where there is a geo point field.
    static AggregateQuery hourly(final Source source,
                                 final ActivityProfiles.Scope scope,
                                 final String countryField,
                                 final String pointField,
                                 final Instant from,
                                 final Instant to) {
        final List<AggregateQuery.Metric> metrics = new ArrayList<>();
        metrics.add(new AggregateQuery.Metric(AggregateQuery.Kind.COUNT, null, ActivityProfiles.EVENTS));
        if (pointField != null) {
            metrics.add(new AggregateQuery.Metric(AggregateQuery.Kind.CENTROID, pointField, PLACE));
        }
        return new AggregateQuery(source, scope.filter(), from, to, AggregateQuery.Bucket.HOUR,
                List.of(countryField), metrics, ActivityProfiles.MAX_ROWS);
    }

    /// Finds the location indicators.
    ///
    /// @param source     The source's name.
    /// @param spec       The periods and the person's leave.
    /// @param hourly     The person's events by hour and country.
    /// @param hasPoints  True if the aggregate has each hour's centroid.
    /// @return The findings, and what the result should say.
    static Located locate(final String source, final Spec spec, final Table hourly, final boolean hasPoints) {
        final List<Seen> seen = new ArrayList<>();
        for (final Map<String, String> row : hourly.rows()) {
            final Instant hour = ActivityProfiles.instant(row.get(AggregateQuery.BUCKET_COLUMN));
            final String country = row.get(AggregateQuery.groupColumn(0));
            if (hour != null && NullSafe.isNonBlankString(country)) {
                seen.add(new Seen(hour, country.strip(), point(row.get(PLACE)),
                        ActivityProfiles.number(row.get(ActivityProfiles.EVENTS))));
            }
        }
        seen.sort(Comparator.comparing(Seen::hour).thenComparing(Seen::country));
        final List<String> notes = new ArrayList<>();
        final List<Finding> findings = new ArrayList<>();
        if (seen.isEmpty()) {
            notes.add(source + ": no country in the person's events: no location indicators.");
            return new Located(findings, notes);
        }
        final Instant windowStart = spec.windowFrom().atStartOfDay(spec.zone()).toInstant();
        final Map<String, Double> baseline = new TreeMap<>();
        seen.stream().filter(s -> s.hour().isBefore(windowStart)).forEach(s -> baseline.merge(s.country(),
                s.events(), Double::sum));
        final Optional<String> home = baseline.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey);
        // Countries new in the window.
        final Map<String, Instant> firstNew = new LinkedHashMap<>();
        for (final Seen s : seen) {
            if (!s.hour().isBefore(windowStart) && !baseline.containsKey(s.country())) {
                firstNew.putIfAbsent(s.country(), s.hour());
            }
        }
        firstNew.forEach((country, first) -> findings.add(new Finding(source, COUNTRY + ":new", country, 0, 1,
                SCORE, "Activity from " + country + ", not seen in the baseline (" + String.join(", ",
                baseline.keySet()) + "), first at " + local(first, spec) + "", "Travel, a VPN or proxy exit in "
                                                                                + "another country, or a "
                                                                                + "geolocation error.",
                Kind.LOCATION)));
        // Impossible travel, in the window.
        final Set<String> pairs = new LinkedHashSet<>();
        for (int i = 1; i < seen.size(); i++) {
            final Seen before = seen.get(i - 1);
            final Seen after = seen.get(i);
            if (after.hour().isBefore(windowStart) || before.country().equals(after.country())) {
                continue;
            }
            final double hours = Math.max(1, Duration.between(before.hour(), after.hour()).toMinutes() / 60.0);
            if (hasPoints && before.point() != null && after.point() != null) {
                final double km = distanceKm(before.point(), after.point());
                if (km >= MIN_DISTANCE_KM && km / hours > MAX_SPEED_KMH
                    && pairs.add(before.country() + ">" + after.country() + "@" + local(after.hour(), spec))) {
                    findings.add(new Finding(source, PLACE + ":travel", local(after.hour(), spec), 0,
                            km / hours, SCORE, "From " + before.country() + " at " + local(before.hour(), spec)
                                               + " to " + after.country() + " at " + local(after.hour(), spec) + ": "
                                               + Math.round(km) + " km in about " + Math.round(hours)
                                               + " hour(s), " + Math.round(km / hours) + " km/h",
                            "A VPN or proxy exit, a shared account used in two places, or a geolocation error.",
                            Kind.LOCATION));
                }
            } else if (!hasPoints && hours <= COUNTRIES_WITHIN_HOURS
                       && pairs.add(before.country() + ">" + after.country() + "@" + local(after.hour(), spec))) {
                findings.add(new Finding(source, COUNTRY + ":travel", local(after.hour(), spec), 0, hours, SCORE,
                        "From " + before.country() + " at " + local(before.hour(), spec) + " to " + after.country()
                        + " at " + local(after.hour(), spec) + ", within " + COUNTRIES_WITHIN_HOURS
                        + " hours (no coordinates, so the distance isn't known)",
                        "Neighbouring countries, a VPN or proxy exit, or a geolocation error.", Kind.LOCATION));
            }
        }
        // Activity from abroad on leave.
        if (home.isPresent()) {
            final Map<LocalDate, Set<String>> abroad = new TreeMap<>();
            for (final Seen s : seen) {
                final LocalDate date = ZonedDateTime.ofInstant(s.hour(), spec.zone()).toLocalDate();
                if (spec.onLeave(date) && !s.country().equals(home.get())) {
                    abroad.computeIfAbsent(date, k -> new LinkedHashSet<>()).add(s.country());
                }
            }
            if (!abroad.isEmpty()) {
                findings.add(new Finding(source, COUNTRY + ":on_leave", abroad.keySet().iterator().next() + " to "
                                                                        + lastKey(abroad), 0, abroad.size(),
                        SCORE, "Activity from outside " + home.get() + " on " + abroad.size() + " day(s) of the "
                               + "person's leave: " + String.join(", ", abroad.entrySet().stream()
                                .map(day -> day.getKey() + " (" + String.join(", ", day.getValue()) + ")")
                                .toList()),
                        "Working while travelling on leave, or another person using the account.", Kind.LOCATION));
            }
        }
        if (!hasPoints) {
            notes.add(source + ": countries only (no geo point field), so impossible travel is two countries within "
                      + COUNTRIES_WITHIN_HOURS + " hours, not a speed.");
        }
        return new Located(findings, notes);
    }

    /// The great-circle distance between two points.
    ///
    /// @param a One point, `{longitude, latitude}`.
    /// @param b The other.
    /// @return The distance in km.
    static double distanceKm(final double[] a, final double[] b) {
        final double lat1 = Math.toRadians(a[1]);
        final double lat2 = Math.toRadians(b[1]);
        final double dLat = lat2 - lat1;
        final double dLon = Math.toRadians(b[0] - a[0]);
        final double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                         + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_KM * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /// @param value A point as ES|QL writes it, `POINT (longitude latitude)`, or empty.
    /// @return It as `{longitude, latitude}`, or null.
    static double[] point(final String value) {
        if (value == null) {
            return null;
        }
        final Matcher matcher = POINT.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return new double[]{Double.parseDouble(matcher.group(1)), Double.parseDouble(matcher.group(2))};
    }

    private static String local(final Instant instant, final Spec spec) {
        final ZonedDateTime local = ZonedDateTime.ofInstant(instant, spec.zone());
        return local.toLocalDate() + " " + String.format("%02d:00", local.getHour());
    }

    private static LocalDate lastKey(final Map<LocalDate, Set<String>> map) {
        LocalDate last = null;
        for (final LocalDate date : map.keySet()) {
            last = date;
        }
        return last;
    }


    // --------------------------------------------------------------------------------


    /// The location findings.
    ///
    /// @param findings The findings.
    /// @param notes    What the result should say.
    record Located(List<Finding> findings, List<String> notes) {

    }


    // --------------------------------------------------------------------------------


    // An hour's events from a country.
    private record Seen(Instant hour, String country, double[] point, double events) {

    }
}
