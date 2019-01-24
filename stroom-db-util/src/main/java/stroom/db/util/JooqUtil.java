package stroom.db.util;

import org.jooq.DSLContext;
import org.jooq.Field;
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
import stroom.entity.shared.PageRequest;
import stroom.util.logging.LambdaLogger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

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

    public static <R> R contextResultWithOptimisticLocking(final DataSource connectionProvider, final Function<DSLContext, R> function) {
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
     * @param field The field to match id against
     * @param id The id value to match on
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
     * an exception will be thrown.
     * @param type The type of record to return
     * @param id The id to match on
     * @return An optional containing the record if it was found.
     */
    public static <R extends Record, T> Optional<T> fetchById(final DataSource connectionProvider,
                                                              final Table<R> table,
                                                              final Class<T> type,
                                                              final int id) {

        final Field<Integer> idField = getIdField(table);
        return Optional.ofNullable(JooqUtil.contextResult(connectionProvider, context ->
                context
                        .fetchOne(table, idField.eq(id))
                        .into(type)));
    }

    private static Field<Integer> getIdField(Table<?> table) {
        final Field<Integer> idField = table.field(DEFAULT_ID_FIELD_NAME, Integer.class);
        if (idField == null) {
            throw new RuntimeException(LambdaLogger.buildMessage("Field [id] not found on table [{}]", table.getName()));
        }
        return idField;
    }
}
