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

package stroom.dataconnection.impl;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.Param;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.DateExpressionParser;
import stroom.util.shared.NullSafe;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

/// Works out the value of each placeholder in a query's SQL, and binds the values to a statement.
///
/// `:from` and `:to` take the ends of the query's time range. Without a format they are bound as
/// timestamps in UTC. A format in braces binds them as text instead, formatted in UTC with a
/// `DateTimeFormatter` pattern, as partition columns often need (`:from{yyyy-MM-dd}`). The formats
/// `epochMillis` and `epochSeconds` bind them as numbers. Any other placeholder takes the query
/// parameter of the same name, bound as text.
public final class SqlParameterBinder {

    /// The format that binds a time as milliseconds since the epoch.
    public static final String EPOCH_MILLIS = "epochMillis";
    /// The format that binds a time as seconds since the epoch.
    public static final String EPOCH_SECONDS = "epochSeconds";

    private SqlParameterBinder() {
    }

    /// Works out the value of each placeholder.
    ///
    /// @param parsedSql        The query's SQL.
    /// @param timeRange        The query's time range, or `null`.
    /// @param params           The query's parameters, or `null`.
    /// @param dateTimeSettings Settings for reading relative times such as `now() - 1d`.
    /// @return A value for each placeholder, in order: an [Instant], a [Long] or a [String].
    /// @throws IllegalArgumentException If a placeholder has no value, or has a format it cannot use.
    public static List<Object> resolveValues(final ParsedSql parsedSql,
                                             final TimeRange timeRange,
                                             final List<Param> params,
                                             final DateTimeSettings dateTimeSettings) {
        final Map<String, String> paramValues = new HashMap<>();
        NullSafe.forEach(params, param -> paramValues.putIfAbsent(param.getKey(), param.getValue()));

        final List<Object> values = new ArrayList<>(parsedSql.placeholders().size());
        for (final SqlPlaceholder placeholder : parsedSql.placeholders()) {
            if (placeholder.isTimeRange()) {
                values.add(timeValue(placeholder, timeRange, dateTimeSettings));
            } else {
                if (placeholder.format() != null) {
                    throw new IllegalArgumentException("Placeholder " + placeholder + " has a format, but only " +
                                                       ":from and :to can take one.");
                }
                final String value = paramValues.get(placeholder.name());
                if (value == null) {
                    throw new IllegalArgumentException("Placeholder :" + placeholder.name() + " has no value. " +
                                                       "Give the query a parameter named '" + placeholder.name()
                                                       + "'.");
                }
                values.add(value);
            }
        }
        return values;
    }

    /// Binds the values from [#resolveValues] to a prepared statement.
    ///
    /// @param statement A statement prepared from the SQL's `jdbcSql`.
    /// @param values    The values, in placeholder order.
    /// @throws SQLException If the driver will not take a value.
    public static void bind(final PreparedStatement statement, final List<Object> values) throws SQLException {
        final Calendar utc = Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
        for (int i = 0; i < values.size(); i++) {
            final int parameterIndex = i + 1;
            final Object value = values.get(i);
            switch (value) {
                case final Instant instant -> statement.setTimestamp(parameterIndex, Timestamp.from(instant), utc);
                case final Long number -> statement.setLong(parameterIndex, number);
                case final String text -> statement.setString(parameterIndex, text);
                default -> throw new IllegalStateException("Unexpected parameter value type " + value.getClass());
            }
        }
    }

    private static Object timeValue(final SqlPlaceholder placeholder,
                                    final TimeRange timeRange,
                                    final DateTimeSettings dateTimeSettings) {
        final String expression = NullSafe.get(
                timeRange,
                range -> SqlPlaceholder.FROM.equals(placeholder.name())
                        ? range.getFrom()
                        : range.getTo());
        if (NullSafe.isBlankString(expression)) {
            throw new IllegalArgumentException("Placeholder :" + placeholder.name() + " has no value. " +
                                               "Set the " + placeholder.name() + " time of the query's time range.");
        }
        final Instant instant = Instant.ofEpochMilli(DateExpressionParser.getMs(
                placeholder.name(),
                expression,
                Objects.requireNonNullElseGet(dateTimeSettings, () -> DateTimeSettings.builder().build())));

        final String format = placeholder.format();
        if (format == null) {
            return instant;
        } else if (EPOCH_MILLIS.equals(format)) {
            return instant.toEpochMilli();
        } else if (EPOCH_SECONDS.equals(format)) {
            return instant.getEpochSecond();
        } else {
            return formatter(placeholder).format(instant);
        }
    }

    private static DateTimeFormatter formatter(final SqlPlaceholder placeholder) {
        try {
            return DateTimeFormatter.ofPattern(placeholder.format()).withZone(ZoneOffset.UTC);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("Placeholder " + placeholder + " has a format that is not a " +
                                               "date pattern, epochMillis or epochSeconds: " + e.getMessage(), e);
        }
    }
}
