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

package stroom.ai.impl.tools;

import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenType;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.DateExpressionParser.DatePoint;
import stroom.query.language.token.Tokeniser;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// Puts a StroomQL query's date points (`day()`, `week()`, `hour()` and so on) in the condition clauses (`where`,
/// `filter`, `having`) in a time zone, as the time each stands for there, quoted: e.g. `EventTime > day()` in
/// Australia/Canberra becomes `EventTime > "2026-11-20T00:00:00+11:00[Australia/Canberra]"`. Stroom works a
/// condition's date points out in UTC whatever the query's time zone, while its expression functions (`eval`,
/// `select`) and times without an offset take the zone: so a tool asked for a zone puts the conditions' date points
/// in it too, and durations after one (`day() - 1d`) still apply, in the zone (its daylight saving included). `now()`
/// is the same instant in every zone, and is left as it is. The query is read with Stroom's own tokeniser, so text
/// in strings and comments is never changed.
final class StroomQlDatePoints {

    private static final Set<TokenType> CONDITION_CLAUSES = Set.of(TokenType.WHERE, TokenType.FILTER,
            TokenType.HAVING);
    // A date point in a time a tool takes, e.g. day() in 'day() - 7d'.
    private static final Pattern DATE_POINT = Pattern.compile(
            "(?<![A-Za-z])(now|second|minute|hour|day|week|month|year)\\(\\)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, DatePoint> BY_NAME = Map.of(
            "second", DatePoint.SECOND,
            "minute", DatePoint.MINUTE,
            "hour", DatePoint.HOUR,
            "day", DatePoint.DAY,
            "week", DatePoint.WEEK,
            "month", DatePoint.MONTH,
            "year", DatePoint.YEAR);

    private StroomQlDatePoints() {
        // Static methods only.
    }

    /// @param query         A StroomQL query.
    /// @param zone          The time zone of the call.
    /// @param referenceTime What now is, in epoch milliseconds.
    /// @return The query with its conditions' date points as times in the zone; the query as given if the zone is
    /// UTC (as Stroom works them out), it has none, or it can't be read (Stroom then refuses it with its own error).
    static String inZone(final String query, final ToolTimeZone zone, final long referenceTime) {
        if (query == null || zone.isUtc()) {
            return query;
        }
        final StringBuilder sb = new StringBuilder(query);
        // Replaced last first, so replacing one doesn't move those before it.
        final List<Placed> placed = new ArrayList<>(find(query, zone, referenceTime));
        Collections.reverse(placed);
        for (final Placed point : placed) {
            sb.replace(point.start(), point.end(), "\"" + format(point.time()) + "\"");
        }
        return sb.toString();
    }

    /// @param query         A StroomQL query.
    /// @param zone          The time zone of the call.
    /// @param referenceTime What now is, in epoch milliseconds.
    /// @return The time each date point in the query's conditions stood for, once each in the order first written,
    /// e.g. `day() = 2026-11-21T00:00:00+11:00`, so a result can say which hours "today" covered (the Stroom UI
    /// works them out in UTC); empty if there are none or the query can't be read.
    static List<String> used(final String query, final ToolTimeZone zone, final long referenceTime) {
        if (query == null) {
            return List.of();
        }
        final Map<String, String> used = new LinkedHashMap<>();
        for (final Placed point : find(query, zone, referenceTime)) {
            used.putIfAbsent(point.name(), point.name() + "() = "
                                           + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(point.time()));
        }
        return List.copyOf(used.values());
    }

    // The date points in the query's conditions, in the order written, with the time each stands for in the zone.
    private static List<Placed> find(final String query, final ToolTimeZone zone, final long referenceTime) {
        final List<Token> tokens;
        try {
            tokens = Tokeniser.parse(query);
        } catch (final RuntimeException e) {
            return List.of();
        }
        final ZonedDateTime now = Instant.ofEpochMilli(referenceTime).atZone(zone.zone());
        // The clause each bracket level is in: a bracketed query (a join's, or a where subquery's) has its own.
        final Deque<TokenType> clauses = new ArrayDeque<>();
        TokenType clause = null;
        final List<Placed> placed = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            final Token token = tokens.get(i);
            final TokenType type = token.getTokenType();
            if (TokenType.OPEN_BRACKET.equals(type)) {
                clauses.push(clause == null
                        ? TokenType.UNKNOWN
                        : clause);
            } else if (TokenType.CLOSE_BRACKET.equals(type)) {
                clause = clauses.isEmpty()
                        ? clause
                        : clauses.pop();
            } else if (TokenType.ALL_KEYWORDS.contains(type)) {
                clause = type;
            } else if (TokenType.FUNCTION_NAME.equals(type) && CONDITION_CLAUSES.contains(clause)) {
                final String name = token.getText().strip().toLowerCase(Locale.ROOT);
                final DatePoint point = BY_NAME.get(name);
                final int close = i + 2;
                if (point != null && close < tokens.size()
                    && TokenType.OPEN_BRACKET.equals(tokens.get(i + 1).getTokenType())
                    && TokenType.CLOSE_BRACKET.equals(tokens.get(close).getTokenType())) {
                    placed.add(new Placed(name, token.getStart(), tokens.get(close).getEnd() + 1, at(point, now)));
                    i = close;
                }
            }
        }
        return placed;
    }

    /// A time a tool takes (e.g. sequence_query's timeFrom) as a time in the call's zone: a date expression (e.g.
    /// `day() - 7d`, its date points in the zone) or a time without an offset (e.g. `2026-11-21T02:00:00`, in the
    /// zone), as an ISO 8601 time with the zone's offset (e.g. `2026-11-14T00:00:00+11:00`), so whatever reads it
    /// next (Stroom, or Elasticsearch through Stroom, in UTC) reads the same time.
    ///
    /// @param value         The time as given, or null.
    /// @param zone          The call's zone.
    /// @param referenceTime What now is, in epoch milliseconds.
    /// @return The time in the zone, in UTC too, so the result can say the time it stood for (e.g. `day()` as
    /// `2026-11-21T00:00:00Z`); the value as given if it has an offset already (e.g. `2026-11-21T02:00:00Z`) or
    /// can't be read (whatever reads it next then refuses it).
    static String timeInZone(final String value, final ToolTimeZone zone, final long referenceTime) {
        if (value == null || value.isBlank() || hasOffset(value.strip())) {
            return value;
        }
        final ZonedDateTime now = Instant.ofEpochMilli(referenceTime).atZone(zone.zone());
        final Matcher matcher = DATE_POINT.matcher(value);
        final StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            final String name = matcher.group(1).toLowerCase(Locale.ROOT);
            final ZonedDateTime time = "now".equals(name)
                    ? now
                    : at(BY_NAME.get(name), now);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(format(time)));
        }
        matcher.appendTail(sb);
        try {
            return DateExpressionParser.parse(sb.toString().strip(), zone.dateTimeSettings(referenceTime))
                    .map(time -> zone.format(time.toInstant()))
                    .orElse(value);
        } catch (final RuntimeException e) {
            return value;
        }
    }

    /// A condition's value on a date field (e.g. esql_query's or a sequence_query step's `where`) as a time in the
    /// call's zone, as [#timeInZone] does a tool's time: each of a between's or an in's values separated by commas
    /// (a negated in is IN with `negated`).
    ///
    /// @param value         The condition's value, or null.
    /// @param condition     The condition.
    /// @param zone          The call's zone.
    /// @param referenceTime What now is, in epoch milliseconds.
    /// @return The value in the zone; a value that isn't a time as given.
    static String conditionInZone(final String value,
                                  final Condition condition,
                                  final ToolTimeZone zone,
                                  final long referenceTime) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (condition == Condition.BETWEEN || condition == Condition.IN) {
            return Arrays.stream(value.split(",", -1))
                    .map(part -> timeInZone(part.strip(), zone, referenceTime))
                    .collect(Collectors.joining(","));
        }
        return timeInZone(value, zone, referenceTime);
    }

    private static boolean hasOffset(final String value) {
        try {
            ZonedDateTime.parse(value);
            return true;
        } catch (final DateTimeParseException e) {
            return false;
        }
    }

    /// @param point A date point.
    /// @param now   Now, in the zone.
    /// @return The time the date point stands for in now's zone, as Stroom works it out in UTC (a week starts on
    /// Monday).
    static ZonedDateTime at(final DatePoint point, final ZonedDateTime now) {
        return switch (point) {
            case NOW -> now;
            case SECOND -> now.truncatedTo(ChronoUnit.SECONDS);
            case MINUTE -> now.truncatedTo(ChronoUnit.MINUTES);
            case HOUR -> now.truncatedTo(ChronoUnit.HOURS);
            case DAY -> now.truncatedTo(ChronoUnit.DAYS);
            case WEEK -> now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS);
            case MONTH -> now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            case YEAR -> now.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
        };
    }

    // A time with its zone, so durations after it are added in the zone (a region keeps its daylight saving).
    private static String format(final ZonedDateTime time) {
        return time.getZone() instanceof ZoneOffset
                ? DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time)
                : DateTimeFormatter.ISO_ZONED_DATE_TIME.format(time);
    }


    // --------------------------------------------------------------------------------


    // A date point in a query: its name, where it is written, and the time it stands for.
    private record Placed(String name, int start, int end, ZonedDateTime time) {

    }
}
