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

import stroom.ai.api.ElasticKeyStreams.Transform;
import stroom.ai.impl.tools.compare.AggregateQuery.Bucket;
import stroom.ai.impl.tools.compare.AggregateQuery.Kind;
import stroom.ai.impl.tools.compare.AggregateQuery.Metric;
import stroom.ai.impl.tools.sequence.EqlSequenceResults;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// Writes an [AggregateQuery] as the text that runs it: the ES|QL commands after `FROM` for an Elastic Index, or a
/// whole StroomQL query for any other index. Every field name and value is quoted, so a value can't change the
/// query: ES|QL names in backquotes and values in `"..."`; StroomQL names as `${...}` where they need it and values
/// in `"..."`.
final class AggregateText {

    /// The prefix of the columns a transformed filter field's values are put in before they are compared.
    static final String TRANSFORMED_PREFIX = "transformed_";
    /// The column a document's `StreamId:EventId` is put in before it is aggregated.
    static final String ONE_EVENT_KEY = "one_event_key";
    private static final Pattern PLAIN_NAME = Pattern.compile("[A-Za-z_@][A-Za-z0-9_@.]*");
    private static final Pattern METRIC_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    /// The hour bucket as StroomQL writes it, in UTC: read back by [AggregateRows].
    static final String STROOMQL_HOUR_PATTERN = "yyyy-MM-dd HH";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private AggregateText() {
        // Static only.
    }

    /// The ES|QL commands after `FROM`.
    ///
    /// @param query         The aggregate.
    /// @param offsetMinutes Null when the cluster applies the person's zone (the request's `time_zone`), so a day
    ///                      bucket is formatted in it; else the zone's offset, in minutes, added to times first, as
    ///                      the cluster doesn't apply zones (a fixed offset, so a day is out by an hour across a
    ///                      daylight saving change, which the result says).
    /// @return The commands, each after `|`.
    static String esql(final AggregateQuery query, final Integer offsetMinutes) {
        final String time = esqlName(query.source().timeField());
        final List<String> commands = new ArrayList<>();
        final List<String> conditions = new ArrayList<>();
        conditions.add(time + " >= TO_DATETIME(" + esqlString(iso(query.from())) + ")");
        conditions.add(time + " < TO_DATETIME(" + esqlString(iso(query.to())) + ")");
        final List<String> transformedEvals = new ArrayList<>();
        final List<String> transformedConditions = new ArrayList<>();
        for (final Map.Entry<String, List<String>> filter : query.filters().entrySet()) {
            final String values = String.join(", ", filter.getValue().stream()
                    .map(AggregateText::esqlString)
                    .toList());
            final List<Transform> transforms = query.transformed().get(filter.getKey());
            if (transforms == null || transforms.isEmpty()) {
                conditions.add(esqlName(filter.getKey()) + " IN (" + values + ")");
            } else {
                // A scripted field: the field's values transformed, compared once the range is narrowed.
                final String column = TRANSFORMED_PREFIX + transformedEvals.size();
                transformedEvals.add(column + " = " + esqlTransformed(esqlName(filter.getKey()), transforms));
                transformedConditions.add(column + " IN (" + values + ")");
            }
        }
        for (final Map.Entry<String, List<String>> exclude : query.excludes().entrySet()) {
            final String name = esqlName(exclude.getKey());
            conditions.add("(" + name + " IS NULL OR " + name + " NOT IN (" + String.join(", ", exclude.getValue()
                    .stream()
                    .map(AggregateText::esqlString)
                    .toList()) + "))");
        }
        for (final Map.Entry<String, String> differing : query.differing().entrySet()) {
            final String name = esqlName(differing.getKey());
            conditions.add(name + " IS NOT NULL");
            if (differing.getValue() != null) {
                final String other = esqlName(differing.getValue());
                conditions.add("(" + other + " IS NULL OR " + other + " != " + name + ")");
            }
        }
        commands.add("WHERE " + String.join(" AND ", conditions));
        if (!transformedEvals.isEmpty()) {
            commands.add("EVAL " + String.join(", ", transformedEvals));
            commands.add("WHERE " + String.join(" AND ", transformedConditions));
        }
        if (query.bucket() == Bucket.HOUR) {
            commands.add("EVAL " + AggregateQuery.BUCKET_COLUMN + " = DATE_TRUNC(1 hour, " + time + ")");
        } else if (query.bucket() == Bucket.DAY) {
            final String shifted = offsetMinutes == null || offsetMinutes == 0
                    ? time
                    : time + " + " + offsetMinutes + " minutes";
            commands.add("EVAL " + AggregateQuery.BUCKET_COLUMN + " = DATE_FORMAT(\"yyyy-MM-dd\", " + shifted + ")");
        }
        if (query.metrics().stream().anyMatch(metric -> metric.kind() == Kind.ONE_EVENT)) {
            commands.add("EVAL " + ONE_EVENT_KEY + " = CONCAT(TO_STRING("
                         + esqlName(EqlSequenceResults.STREAM_ID) + "), \":\", TO_STRING("
                         + esqlName(EqlSequenceResults.EVENT_ID) + "))");
        }
        final List<String> aggregations = new ArrayList<>();
        for (final Metric metric : query.metrics()) {
            aggregations.add(metricName(metric) + " = " + switch (metric.kind()) {
                case COUNT -> "COUNT(*)";
                case SUM -> "SUM(" + esqlName(metric.field()) + ")";
                case DISTINCT -> "COUNT_DISTINCT(" + esqlName(metric.field()) + ")";
                case MIN_TIME -> "MIN(" + time + ")";
                case MAX_TIME -> "MAX(" + time + ")";
                case ONE_EVENT -> "MIN(" + ONE_EVENT_KEY + ")";
                case CENTROID -> "ST_CENTROID_AGG(" + esqlName(metric.field()) + ")";
            });
        }
        final List<String> by = new ArrayList<>();
        if (query.bucket() != Bucket.NONE) {
            by.add(AggregateQuery.BUCKET_COLUMN);
        }
        for (int i = 0; i < query.groups().size(); i++) {
            by.add(AggregateQuery.groupColumn(i) + " = " + esqlName(query.groups().get(i)));
        }
        commands.add("STATS " + String.join(", ", aggregations) + (by.isEmpty()
                ? ""
                : " BY " + String.join(", ", by)));
        commands.add("LIMIT " + query.limit());
        return "| " + String.join("\n| ", commands);
    }

    /// The whole StroomQL query.
    ///
    /// @param query The aggregate.
    /// @param zone  The person's zone, the day bucket's.
    /// @return The query.
    static String stroomQl(final AggregateQuery query, final ZoneId zone) {
        final String time = stroomQlName(query.source().timeField());
        final StringBuilder sb = new StringBuilder("from ").append(stroomQlString(query.source().name()));
        final List<String> conditions = new ArrayList<>();
        conditions.add(time + " >= " + stroomQlString(iso(query.from())));
        conditions.add(time + " < " + stroomQlString(iso(query.to())));
        for (final Map.Entry<String, List<String>> filter : query.filters().entrySet()) {
            final String name = stroomQlName(filter.getKey());
            final List<String> any = filter.getValue().stream()
                    .map(value -> name + " = " + stroomQlString(value))
                    .toList();
            conditions.add(any.size() == 1
                    ? any.getFirst()
                    : "(" + String.join(" or ", any) + ")");
        }
        for (final Map.Entry<String, List<String>> exclude : query.excludes().entrySet()) {
            final String name = stroomQlName(exclude.getKey());
            conditions.add("not (" + String.join(" or ", exclude.getValue().stream()
                    .map(value -> name + " = " + stroomQlString(value))
                    .toList()) + ")");
        }
        if (!query.differing().isEmpty()) {
            throw new IllegalArgumentException("Comparing two fields needs ES|QL: " + query.differing());
        }
        if (!query.transformed().isEmpty()) {
            throw new IllegalArgumentException("Transforming a field's values needs ES|QL: " + query.transformed());
        }
        sb.append("\nwhere ").append(String.join("\n  and ", conditions));
        final List<String> grouped = new ArrayList<>();
        final List<String> selected = new ArrayList<>();
        if (query.bucket() != Bucket.NONE) {
            final String expression = query.bucket() == Bucket.HOUR
                    ? "formatDate(" + time + ", " + stroomQlString(STROOMQL_HOUR_PATTERN) + ", \"UTC\")"
                    : "formatDate(" + time + ", \"yyyy-MM-dd\", " + stroomQlString(zone.getId()) + ")";
            sb.append("\neval ").append(AggregateQuery.BUCKET_COLUMN).append(" = ").append(expression);
            grouped.add(AggregateQuery.BUCKET_COLUMN);
            selected.add(AggregateQuery.BUCKET_COLUMN + " as " + AggregateQuery.BUCKET_COLUMN);
        }
        for (int i = 0; i < query.groups().size(); i++) {
            final String name = stroomQlName(query.groups().get(i));
            grouped.add(name);
            selected.add(name + " as " + AggregateQuery.groupColumn(i));
        }
        for (final Metric metric : query.metrics()) {
            final String aggregation = switch (metric.kind()) {
                case COUNT -> "count()";
                case SUM -> "sum(" + stroomQlName(metric.field()) + ")";
                case DISTINCT -> "countUnique(" + stroomQlName(metric.field()) + ")";
                case MIN_TIME -> "min(" + time + ")";
                case MAX_TIME -> "max(" + time + ")";
                case ONE_EVENT -> "any(concat(" + stroomQlName(EqlSequenceResults.STREAM_ID) + ", \":\", "
                                  + stroomQlName(EqlSequenceResults.EVENT_ID) + "))";
                case CENTROID -> throw new IllegalArgumentException("A centroid needs ES|QL: "
                                                                    + metric.field());
            };
            selected.add(aggregation + " as " + metricName(metric));
        }
        if (!grouped.isEmpty()) {
            sb.append("\ngroup by ").append(String.join(", ", grouped));
        }
        return sb.append("\nlimit ").append(query.limit())
                .append("\nselect ").append(String.join(", ", selected))
                .toString();
    }

    /// @param name       A field's name, as ES|QL names it.
    /// @param transforms What is done to its values, in order.
    /// @return The ES|QL expression of its values transformed, as [Transform#apply(String)] does: lower case;
    /// without a domain (up to the last backslash, from the first `@`); a path's last part.
    static String esqlTransformed(final String name, final List<Transform> transforms) {
        String expression = name;
        for (final Transform transform : transforms) {
            expression = switch (transform) {
                case LOWERCASE -> "TO_LOWER(" + expression + ")";
                case STRIP_DOMAIN -> "REPLACE(REPLACE(" + expression + ", " + esqlString("^.*\\\\") + ", \"\"), "
                                     + esqlString("@.*$") + ", \"\")";
                case FILE_NAME -> "REPLACE(" + expression + ", " + esqlString("^.*[/\\\\]") + ", \"\")";
            };
        }
        return expression;
    }

    /// @param name A field's name.
    /// @return It as ES|QL names it: in backquotes, any backquote doubled.
    static String esqlName(final String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    /// @param value A value.
    /// @return It as an ES|QL string.
    static String esqlString(final String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /// @param name A field's name.
    /// @return It as StroomQL names it: `${...}` unless it is a plain name.
    static String stroomQlName(final String name) {
        return PLAIN_NAME.matcher(name).matches()
                ? name
                : "${" + name + "}";
    }

    /// @param value A value.
    /// @return It as a StroomQL string.
    static String stroomQlString(final String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /// @param instant A time.
    /// @return It as an ISO 8601 instant with milliseconds, as ES|QL's `TO_DATETIME` and StroomQL read one.
    static String iso(final Instant instant) {
        return ISO.format(instant);
    }

    private static String metricName(final Metric metric) {
        if (!METRIC_NAME.matcher(metric.name()).matches()) {
            throw new IllegalArgumentException("A metric's name must be letters, digits and underscores: "
                                               + metric.name());
        }
        return metric.name();
    }
}
