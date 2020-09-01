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

import javax.sql.DataSource;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class GenericDao<RecordType extends UpdatableRecord<RecordType>, ObjectType, IdType>
        implements HasCrud<ObjectType, IdType> {

    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(GenericDao.class);

    private Table<RecordType> table;
    private TableField<RecordType, IdType> idField;
    private Class<ObjectType> objectTypeClass;
    private DataSource connectionProvider;
    private BiFunction<ObjectType, RecordType, RecordType> objectToRecordMapper = (object, record) -> {
        record.from(object);
        return record;
    };
    private Function<Record, ObjectType> recordToObjectMapper = record ->
            record.into(objectTypeClass);

    // Could use the pattern described here to get the table type:
    // https://stackoverflow.com/questions/3403909/get-generic-type-of-class-at-runtime
    // That places an interface requirement on the object, which I think is best avoided.
    public GenericDao(final Table<RecordType> table,
                      final TableField<RecordType, IdType> idField,
                      final Class<ObjectType> objectTypeClass,
                      final DataSource connectionProvider) {
        this.table = table;
        this.idField = idField;
        this.objectTypeClass = objectTypeClass;
        this.connectionProvider = connectionProvider;
    }

    public ObjectType create(final ObjectType object) {
        return JooqUtil.contextResult(connectionProvider, context ->
                create(context, object));
    }

    public Optional<ObjectType> fetch(final IdType id) {
        return JooqUtil.contextResult(connectionProvider, context ->
                fetch(context, id));
    }

    public ObjectType update(final ObjectType object) {
        return JooqUtil.contextResultWithOptimisticLocking(connectionProvider, context ->
                update(context, object));
    }

    public boolean delete(final IdType id) {
        return JooqUtil.contextResult(connectionProvider, context ->
                delete(context, id));
    }

    public ObjectType create(final DSLContext context, final ObjectType object) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Creating a {}", table.getName()));
        final RecordType record = objectToRecordMapper.apply(object, context.newRecord(table));
        record.store();
        return recordToObjectMapper.apply(record);
    }

    public Optional<ObjectType> fetch(final DSLContext context, final IdType id) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message(
                "Fetching {} with id {}", table.getName(), id));
        return context
                .selectFrom(table)
                .where(idField.eq(id))
                .fetchOptional(record ->
                        recordToObjectMapper.apply(record));
    }

    public ObjectType update(final DSLContext context, final ObjectType object) {
        final RecordType record = objectToRecordMapper.apply(object, context.newRecord(table));
        LAMBDA_LOGGER.debug(() -> LogUtil.message(
                "Updating a {} with id {}", table.getName(), record.get(idField)));
        record.update();
        return recordToObjectMapper.apply(record);
    }

    public boolean delete(final DSLContext context, final IdType id) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message(
                "Deleting a {} with id {}", table.getName(), id));
        return context
                .deleteFrom(table)
                .where(idField.eq(id))
                .execute() > 0;
    }

    public GenericDao<RecordType, ObjectType, IdType> setObjectToRecordMapper(
            final BiFunction<ObjectType, RecordType, RecordType> objectToRecordMapper) {
        this.objectToRecordMapper = objectToRecordMapper;
        return this;
    }

    public GenericDao<RecordType, ObjectType, IdType> setRecordToObjectMapper(
            final Function<Record, ObjectType> recordToObjectMapper) {
        this.recordToObjectMapper = recordToObjectMapper;
        return this;
    }
}
