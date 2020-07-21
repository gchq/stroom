package stroom.db.util;

import stroom.util.logging.LogUtil;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Range;
import stroom.util.shared.Selection;
import stroom.util.shared.Sort;
import stroom.util.shared.StringCriteria;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JooqUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JooqUtil.class);

    private static final String DEFAULT_ID_FIELD_NAME = "id";
    private static final Boolean RENDER_SCHEMA = false;

    private JooqUtil() {
        // Utility class.
    }

    public static void disableJooqLogoInLogs() {
        System.getProperties().setProperty("org.jooq.no-logo", "true");
    }

    public static DSLContext createContext(final Connection connection) {
        Settings settings = new Settings();
        // Turn off fully qualified schemata.
        settings = settings.withRenderSchema(RENDER_SCHEMA);
        return DSL.using(connection, SQLDialect.MYSQL, settings);
    }

    public static DSLContext createContextWithOptimisticLocking(final Connection connection) {
        Settings settings = new Settings();
        // Turn off fully qualified schemata.
        settings = settings.withRenderSchema(RENDER_SCHEMA);
        settings = settings.withExecuteWithOptimisticLocking(true);
        return DSL.using(connection, SQLDialect.MYSQL, settings);
    }

    public static void context(final DataSource dataSource, final Consumer<DSLContext> consumer) {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = createContext(connection);
            consumer.accept(context);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static <R extends Record> void truncateTable(final DataSource dataSource,
                                                        final Table<R> table) {

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = createContext(connection);
            context
                    .batch(
                            "SET FOREIGN_KEY_CHECKS=0",
                            "truncate table " + table.getName(),
                            "SET FOREIGN_KEY_CHECKS=1")
                    .execute();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static <R extends Record> int getTableCount(final DataSource dataSource,
                                                       final Table<R> table) {

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = createContext(connection);
            return context
                    .selectCount()
                    .from(table)
                    .fetchOne()
                    .value1();
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static <R> R contextResult(final DataSource dataSource, final Function<DSLContext, R> function) {
        R result;
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = createContext(connection);
            result = function.apply(context);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return result;
    }

//    public static void contextResultWithOptimisticLocking(final DataSource dataSource, final Consumer<DSLContext> consumer) {
//        try (final Connection connection = dataSource.getConnection()) {
//            final DSLContext context = createContextWithOptimisticLocking(connection);
//            consumer.accept(context);
//        } catch (final SQLException e) {
//            LOGGER.error(e.getMessage(), e);
//            throw new RuntimeException(e.getMessage(), e);
//        }
//    }

    public static <R> R contextResultWithOptimisticLocking(final DataSource dataSource,
                                                           final Function<DSLContext, R> function) {
        R result;
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext context = createContextWithOptimisticLocking(connection);
            result = function.apply(context);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return result;
    }

    public static void transaction(final DataSource dataSource, final Consumer<DSLContext> consumer) {
        context(dataSource, context -> context.transaction(nested -> consumer.accept(DSL.using(nested))));
    }

    public static <R> R transactionResult(final DataSource dataSource, final Function<DSLContext, R> function) {
        return contextResult(dataSource, context -> context.transactionResult(nested -> function.apply(DSL.using(nested))));
    }

    /**
     * Delete all rows matching the passed id value
     *
     * @param field The field to match id against
     * @param id    The id value to match on
     * @return The number of deleted records
     */
    public static <R extends Record> int deleteById(final DataSource dataSource,
                                                    final Table<R> table,
                                                    final Field<Integer> field,
                                                    final int id) {

        return JooqUtil.contextResult(dataSource, context ->
                context
                        .deleteFrom(table)
                        .where(field.eq(id))
                        .execute());
    }

    /**
     * Delete all rows matching the passed id value
     *
     * @param id The id value to match on
     * @return The number of deleted records
     */
    public static <R extends Record> int deleteById(final DataSource dataSource,
                                                    final Table<R> table,
                                                    final int id) {

        final Field<Integer> idField = getIdField(table);
        return JooqUtil.contextResult(dataSource, context ->
                context
                        .deleteFrom(table)
                        .where(idField.eq(id))
                        .execute());
    }

    /**
     * Fetch a single row using the passed id value. If the id matches zero rows or more than one row then
     * an exception will be thrown. Assumes the table's id field is named 'id'.
     *
     * @param type The type of record to return
     * @param id   The id to match on
     * @return An optional containing the record if it was found.
     */
    public static <R extends Record, T> Optional<T> fetchById(final DataSource dataSource,
                                                              final Table<R> table,
                                                              final Class<T> type,
                                                              final int id) {

        final Field<Integer> idField = getIdField(table);
        return JooqUtil.contextResult(dataSource, context ->
                context
                        .fetchOptional(table, idField.eq(id))
                        .map(record ->
                                record.into(type)));
    }

    private static Field<Integer> getIdField(Table<?> table) {
        final Field<Integer> idField = table.field(DEFAULT_ID_FIELD_NAME, Integer.class);
        if (idField == null) {
            throw new RuntimeException(LogUtil.message("Field [id] not found on table [{}]", table.getName()));
        }
        return idField;
    }

    public static int getLimit(final PageRequest pageRequest,
                               final boolean oneLarger) {
        return getLimit(pageRequest, oneLarger, Integer.MAX_VALUE);
    }

    public static int getLimit(final PageRequest pageRequest,
                               final boolean oneLarger,
                               final int defaultValue) {
        if (pageRequest != null) {
            if (pageRequest.getLength() != null) {
                if (oneLarger) {
                    return pageRequest.getLength() + 1;
                } else {
                    return pageRequest.getLength();
                }
            }
        }

        return defaultValue;
    }

    public static int getOffset(final PageRequest pageRequest) {
        if (pageRequest != null) {
            if (pageRequest.getOffset() != null) {
                return pageRequest.getOffset().intValue();
            }
        }

        return 0;
    }

    @SafeVarargs
    @SuppressWarnings("varargs") // Creating a stream from an array is safe
    public static Collection<Condition> conditions(final Optional<Condition>... conditions) {
        return Stream.of(conditions)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Used to build JOOQ conditions from our Criteria Range
     *
     * @param field    The jOOQ field being range queried
     * @param criteria The criteria to apply
     * @param <T>      The type of the range
     * @return A condition that applies the given range.
     */
    public static <T extends Number> Optional<Condition> getRangeCondition(
            final Field<T> field,
            final Range<T> criteria) {
        if (criteria == null || !criteria.isConstrained()) {
            return Optional.empty();
        }

        Boolean matchNull = null;
        if (criteria.isMatchNull()) {
            matchNull = Boolean.TRUE;
        }

        final Optional<Condition> fromCondition;
        if (criteria.getFrom() == null) {
            fromCondition = Optional.empty();
        } else {
            fromCondition = Optional.of(field.greaterOrEqual(criteria.getFrom()));
        }
        final Optional<Condition> toCondition;
        if (criteria.getTo() == null) {
            toCondition = Optional.empty();
        } else {
            toCondition = Optional.of(field.lessThan(criteria.getTo()));
        }

        // Combine conditions.
        final Optional<Condition> condition = fromCondition.map(c1 -> toCondition.map(c1::and).orElse(c1)).or(() -> toCondition);
        return convertMatchNull(field, matchNull, condition);
    }

    /**
     * Used to build jOOQ conditions from criteria sets
     *
     * @param field    The jOOQ field being set queried
     * @param criteria The criteria to apply
     * @param <T>      The type of the range
     * @return A condition that applies the given set.
     */
    public static <T> Optional<Condition> getSetCondition(
            final Field<T> field,
            final Selection<T> criteria) {
        if (criteria == null || criteria.isMatchAll()) {
            return Optional.empty();
        }
        return Optional.of(field.in(criteria.getSet()));
    }

    /**
     * Used to build jOOQ conditions from string criteria
     *
     * @param field    The jOOQ field being queried
     * @param criteria The criteria to apply
     * @return A condition that applies the given criteria
     */
    public static Optional<Condition> getStringCondition(
            final Field<String> field,
            final StringCriteria criteria) {
        if (criteria == null || !criteria.isConstrained()) {
            return Optional.empty();
        }

        final Optional<Condition> valueCondition;
        if (criteria.getMatchString() != null) {
            if (criteria.getMatchStyle() == null) {
                if (criteria.isCaseInsensitive()) {
                    valueCondition = Optional.of(field.upper().eq(criteria.getMatchString()));
                } else {
                    valueCondition = Optional.of(field.eq(criteria.getMatchString()));
                }
            } else {
                if (criteria.isCaseInsensitive()) {
                    valueCondition = Optional.of(field.upper().like(criteria.getMatchString()));
                } else {
                    valueCondition = Optional.of(field.like(criteria.getMatchString()));
                }
            }
        } else {
            valueCondition = Optional.empty();
        }

        return convertMatchNull(field, criteria.getMatchNull(), valueCondition);
    }

    private static Optional<Condition> convertMatchNull(final Field<?> field, final Boolean matchNull, final Optional<Condition> condition) {
        if (matchNull == null) {
            return condition;
        }
        if (matchNull) {
            return condition.map(c -> c.or(field.isNull())).or(() -> Optional.of(field.isNull()));
        }
        return condition.or(() -> Optional.of(field.isNotNull()));
    }

    public static Collection<OrderField<?>> getOrderFields(final Map<String, Field<?>> fieldMap, final BaseCriteria criteria) {
        if (criteria.getSortList() == null) {
            return Collections.emptyList();
        }

        return criteria.getSortList()
                .stream()
                .map(s -> getOrderField(fieldMap, s))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private static Optional<OrderField<?>> getOrderField(final Map<String, Field<?>> fieldMap, final Sort sort) {
        final Field<?> field = fieldMap.get(sort.getId());

        if (field != null) {
            if (sort.isDesc()) {
                return Optional.of(field.desc());
            } else {
                return Optional.of(field.asc());
            }
        }

        return Optional.empty();
    }

    /**
     * Converts a time in millis since epoch to a {@link java.sql.Timestamp}
     */
    public static Field<Timestamp> epochMsToTimestamp(Field<? extends Number> field) {
        return DSL.field("from_unixtime({0} / 1000)", SQLDataType.TIMESTAMP, field);
    }

    /**
     * Converts a time in millis since epoch to a {@link java.sql.Date}
     */
    public static Field<Date> epochMsToDate(Field<? extends Number> field) {
        return DSL.field("from_unixtime({0} / 1000)", SQLDataType.DATE, field);
    }

    public static Field<Integer> periodDiff(final Field<? extends Date> date1,
                                            final Field<? extends Date> date2) {
        return DSL.field("period_diff(extract(year_month from {0}), extract(year_month from {1}))",
                SQLDataType.INTEGER, date1, date2);
    }

    public static Field<Integer> periodDiff(final Field<? extends Date> date1,
                                            final Date date2) {
        return DSL.field("period_diff(extract(year_month from {0}), extract(year_month from {1}))",
                SQLDataType.INTEGER, date1, date2);
    }

}
