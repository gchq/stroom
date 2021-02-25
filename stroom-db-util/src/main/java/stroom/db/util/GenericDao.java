package stroom.db.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.HasCrud;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UpdatableRecord;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.sql.DataSource;

public class GenericDao<T_REC_TYPE extends UpdatableRecord<T_REC_TYPE>, T_OBJ_TYPE, T_ID_TYPE>
        implements HasCrud<T_OBJ_TYPE, T_ID_TYPE> {

    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(GenericDao.class);

    private final Table<T_REC_TYPE> table;
    private final TableField<T_REC_TYPE, T_ID_TYPE> idField;
    private Class<T_OBJ_TYPE> objectTypeClass;
    private final DataSource connectionProvider;
    private BiFunction<T_OBJ_TYPE, T_REC_TYPE, T_REC_TYPE> objectToRecordMapper = (object, record) -> {
        record.from(object);
        return record;
    };
    private Function<Record, T_OBJ_TYPE> recordToObjectMapper = record ->
            record.into(objectTypeClass);

    // Could use the pattern described here to get the table type:
    // https://stackoverflow.com/questions/3403909/get-generic-type-of-class-at-runtime
    // That places an interface requirement on the object, which I think is best avoided.
    public GenericDao(final Table<T_REC_TYPE> table,
                      final TableField<T_REC_TYPE, T_ID_TYPE> idField,
                      final Class<T_OBJ_TYPE> objectTypeClass,
                      final DataSource connectionProvider) {
        this.table = table;
        this.idField = idField;
        this.objectTypeClass = objectTypeClass;
        this.connectionProvider = connectionProvider;
    }

    public T_OBJ_TYPE create(final T_OBJ_TYPE object) {
        return JooqUtil.contextResult(connectionProvider, context ->
                create(context, object));
    }

    public Optional<T_OBJ_TYPE> fetch(final T_ID_TYPE id) {
        return JooqUtil.contextResult(connectionProvider, context ->
                fetch(context, id));
    }

    public T_OBJ_TYPE update(final T_OBJ_TYPE object) {
        return JooqUtil.contextResultWithOptimisticLocking(connectionProvider, context ->
                update(context, object));
    }

    public boolean delete(final T_ID_TYPE id) {
        return JooqUtil.contextResult(connectionProvider, context ->
                delete(context, id));
    }

    public T_OBJ_TYPE create(final DSLContext context, final T_OBJ_TYPE object) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Creating a {}", table.getName()));
        final T_REC_TYPE record = objectToRecordMapper.apply(object, context.newRecord(table));
        record.store();
        return recordToObjectMapper.apply(record);
    }

    public Optional<T_OBJ_TYPE> fetch(final DSLContext context, final T_ID_TYPE id) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message(
                "Fetching {} with id {}", table.getName(), id));
        return context
                .selectFrom(table)
                .where(idField.eq(id))
                .fetchOptional(record ->
                        recordToObjectMapper.apply(record));
    }

    public T_OBJ_TYPE update(final DSLContext context, final T_OBJ_TYPE object) {
        final T_REC_TYPE record = objectToRecordMapper.apply(object, context.newRecord(table));
        LAMBDA_LOGGER.debug(() -> LogUtil.message(
                "Updating a {} with id {}", table.getName(), record.get(idField)));
        record.update();
        return recordToObjectMapper.apply(record);
    }

    public boolean delete(final DSLContext context, final T_ID_TYPE id) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message(
                "Deleting a {} with id {}", table.getName(), id));
        return context
                .deleteFrom(table)
                .where(idField.eq(id))
                .execute() > 0;
    }

    public GenericDao<T_REC_TYPE, T_OBJ_TYPE, T_ID_TYPE> setObjectToRecordMapper(
            final BiFunction<T_OBJ_TYPE, T_REC_TYPE, T_REC_TYPE> objectToRecordMapper) {
        this.objectToRecordMapper = objectToRecordMapper;
        return this;
    }

    public GenericDao<T_REC_TYPE, T_OBJ_TYPE, T_ID_TYPE> setRecordToObjectMapper(
            final Function<Record, T_OBJ_TYPE> recordToObjectMapper) {
        this.recordToObjectMapper = recordToObjectMapper;
        return this;
    }
}
