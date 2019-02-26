package stroom.db.util;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.SelectForUpdateStep;
import org.jooq.SelectLimitAfterOffsetStep;
import org.jooq.SelectLimitStep;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaSet;
import stroom.util.shared.PageRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.shared.Range;
import stroom.util.shared.Sort;
import stroom.util.shared.StringCriteria;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JooqUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JooqUtil.class);

    private static final String DEFAULT_ID_FIELD_NAME = "id";

    private JooqUtil() {
        // Utility class.
    }

    public static void context(final DataSource connectionProvider, final Consumer<DSLContext> consumer) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            consumer.accept(context);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static <R> R contextResult(final DataSource connectionProvider, final Function<DSLContext, R> function) {
        R result;
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL);
            result = function.apply(context);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return result;
    }

    public static void contextWithOptimisticLocking(final DataSource connectionProvider, final Consumer<DSLContext> consumer) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL, new Settings().withExecuteWithOptimisticLocking(true));
            consumer.accept(context);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static <R> R contextWithOptimisticLocking(final DataSource connectionProvider, final Function<DSLContext, R> function) {
        R result;
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext context = DSL.using(connection, SQLDialect.MYSQL, new Settings().withExecuteWithOptimisticLocking(true));
            result = function.apply(context);
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return result;
    }

    public static <R extends Record> SelectForUpdateStep<R> applyLimits(final SelectLimitStep<R> select, final PageRequest pageRequest) {
        SelectForUpdateStep<R> result = select;

        if (pageRequest != null) {
            if (pageRequest.getOffset() != null) {
                final SelectLimitAfterOffsetStep<R> selectLimitAfterOffsetStep = select.offset(pageRequest.getOffset().intValue());
                result = selectLimitAfterOffsetStep;

                if (pageRequest.getLength() != null) {
                    result = selectLimitAfterOffsetStep.limit(pageRequest.getLength());
                }
            } else if (pageRequest.getLength() != null) {
                result = select.limit(pageRequest.getLength());
            }
        }

        return result;
    }

    /**
     * Delete all rows matching the passed id value
     *
     * @param field The field to match id against
     * @param id    The id value to match on
     * @return The number of deleted records
     */
    public static <R extends Record> int deleteById(final DataSource connectionProvider,
                                                    final Table<R> table,
                                                    final TableField<R, Integer> field,
                                                    final int id) {

        return JooqUtil.contextResult(connectionProvider, context ->
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
    public static <R extends Record> int deleteById(final DataSource connectionProvider,
                                                    final Table<R> table,
                                                    final int id) {

        final Field<Integer> idField = getIdField(table);
        return JooqUtil.contextResult(connectionProvider, context ->
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
    public static <R extends Record, T> Optional<T> fetchById(final DataSource connectionProvider,
                                                              final Table<R> table,
                                                              final Class<T> type,
                                                              final int id) {

        final Field<Integer> idField = getIdField(table);
        return JooqUtil.contextResult(connectionProvider, context ->
                context
                        .fetchOptional(table, idField.eq(id))
                        .map(record ->
                                record.into(type)));
    }

    private static Field<Integer> getIdField(Table<?> table) {
        final Field<Integer> idField = table.field(DEFAULT_ID_FIELD_NAME, Integer.class);
        if (idField == null) {
            throw new RuntimeException(LambdaLogger.buildMessage("Field [id] not found on table [{}]", table.getName()));
        }
        return idField;
    }

    /**
     * Used to build JOOQ conditions from our Criteria Range
     *
     * @param field The jOOQ field being range queried
     * @param criteria The criteria to apply
     * @param <R> The type of the record
     * @param <T> The type of the range
     * @return A condition that applies the given range.
     */
    public static <R extends Record, T extends Number>
    Optional<Condition> applyRange(final TableField<R, T> field,
<<<<<<< HEAD
                         final Range<T> criteria) {
=======
                                   final Range<T> criteria) {
>>>>>>> e175a306bcf551bb66937a9b2e21045107e90bf0
        if (criteria.isConstrained()) {
            Optional<Condition> nullCondition = Optional.empty();
            if (criteria.isMatchNull()) {
                nullCondition = Optional.of(field.isNull());
            }
            Optional<Condition> fromCondition = Optional.empty();
            if (criteria.getFrom() != null) {
                fromCondition = Optional.of(field.greaterOrEqual(criteria.getFrom()));
            }
            Optional<Condition> toCondition = Optional.empty();
            if (criteria.getTo() != null) {
                toCondition = Optional.of(field.lessThan(criteria.getTo()));
            }

            return Optional.of(
                    DSL.or(
                            Stream.of(nullCondition, fromCondition, toCondition)
                                    .filter(Optional::isPresent)
                                    .map(Optional::get)
                                    .collect(Collectors.toSet()
                                    )
                    ));
        }

        return Optional.empty();
    }

    /**
     * Used to build jOOQ conditions from criteria sets
     *
     * @param field The jOOQ field being set queried
     * @param criteria The criteria to apply
     * @param <R> The type of the record
     * @param <T> The type of the range
     * @return A condition that applies the given set.
     */
    public static <R extends Record, T>
    Optional<Condition> applySet(final TableField<R, T> field,
<<<<<<< HEAD
                       final CriteriaSet<T> criteria) {
=======
                                 final CriteriaSet<T> criteria) {
>>>>>>> e175a306bcf551bb66937a9b2e21045107e90bf0
        if (criteria.isConstrained()) {
            Optional<Condition> nullCondition = Optional.empty();

            if (criteria.getMatchNull() != null) {
                nullCondition = Optional.of(criteria.getMatchNull() ? field.isNull() : field.isNotNull());
            }
            Optional<Condition> valueCondition = Optional.empty();
            if (criteria.size() > 0) {
                valueCondition = Optional.of(field.in(criteria.getSet()));
            }

            return Optional.of(
                    DSL.or(
                            Stream.of(nullCondition, valueCondition)
                                    .filter(Optional::isPresent)
                                    .map(Optional::get)
                                    .collect(Collectors.toSet()
                                    )
                    ));
        }

        return Optional.empty();

    }

    /**
     * Used to build jOOQ conditions from string criteria
     * @param field The jOOQ field being queried
     * @param criteria The criteria to apply
     * @param <R> The type of the record
     * @return A condition that applies the given criteria
     */
    public static <R extends Record>
    Optional<Condition> applyString(final TableField<R, String> field,
<<<<<<< HEAD
                          final StringCriteria criteria) {
=======
                                    final StringCriteria criteria) {
>>>>>>> e175a306bcf551bb66937a9b2e21045107e90bf0
        if (criteria.isConstrained()) {
            Optional<Condition> nullCondition = Optional.empty();

            if (criteria.getMatchNull() != null) {
                nullCondition = Optional.of(criteria.getMatchNull() ? field.isNull() : field.isNotNull());
            }

            final Optional<Condition> valueCondition;
            if (criteria.getMatchStyle() == null) {
                valueCondition = Optional.of(field.eq(criteria.getString()));
            } else {
                if (criteria.isCaseInsensitive()) {
                    valueCondition = Optional.of(field.upper().eq(criteria.getMatchString()));
                } else {
                    valueCondition = Optional.of(field.eq(criteria.getMatchString()));
                }
            }

            return Optional.of(
                    DSL.or(
                            Stream.of(nullCondition, valueCondition)
                                    .filter(Optional::isPresent)
                                    .map(Optional::get)
                                    .collect(Collectors.toSet()
                                    )
                    ));
        }

        return Optional.empty();
    }

    public static OrderField[] getOrderFields(final Map<String, TableField> tableFieldMap, final BaseCriteria criteria) {
        if (criteria.getSortList() != null) {
            return criteria.getSortList().stream()
                    .map(s -> getOrderField(tableFieldMap, s))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toArray(OrderField[]::new);
        }

        return new OrderField[0];
    }

    private static Optional<OrderField> getOrderField(final Map<String, TableField> tableFieldMap, final Sort sort) {
        final TableField field = tableFieldMap.get(sort.getField());

        if (null != field) {
            switch (sort.getDirection()) {
                case ASCENDING:
                    return Optional.of(field.asc());
                case DESCENDING:
                    return Optional.of(field.desc());
            }
        }

        return Optional.empty();
    }
}
