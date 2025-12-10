/*
 * Copyright 2016-2025 Crown Copyright
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
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;

public class GenericDao<T_REC_TYPE extends UpdatableRecord<T_REC_TYPE>, T_OBJ_TYPE, T_ID_TYPE>
        implements HasCrud<T_OBJ_TYPE, T_ID_TYPE> {

    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(GenericDao.class);

    private final DataSource connectionProvider;
    final Table<T_REC_TYPE> table;
    final TableField<T_REC_TYPE, T_ID_TYPE> idField;
    final BiFunction<T_OBJ_TYPE, T_REC_TYPE, T_REC_TYPE> objectToRecordMapper;
    final Function<Record, T_OBJ_TYPE> recordToObjectMapper;

    // Could use the pattern described here to get the table type:
    // https://stackoverflow.com/questions/3403909/get-generic-type-of-class-at-runtime
    // That places an interface requirement on the object, which I think is best avoided.
    public GenericDao(final DataSource connectionProvider,
                      final Table<T_REC_TYPE> table,
                      final TableField<T_REC_TYPE, T_ID_TYPE> idField,
                      final BiFunction<T_OBJ_TYPE, T_REC_TYPE, T_REC_TYPE> objectToRecordMapper,
                      final Function<Record, T_OBJ_TYPE> recordToObjectMapper) {
        this.connectionProvider = connectionProvider;
        this.table = table;
        this.idField = idField;
        this.objectToRecordMapper = objectToRecordMapper;
        this.recordToObjectMapper = recordToObjectMapper;
    }

    public GenericDao(final DataSource connectionProvider,
                      final Table<T_REC_TYPE> table,
                      final TableField<T_REC_TYPE, T_ID_TYPE> idField,
                      final Class<T_OBJ_TYPE> objectTypeClass) {
        this(
                connectionProvider,
                table,
                idField,
                (object, record) -> {
                    record.from(object);
                    return record;
                },
                record ->
                        record.into(objectTypeClass));
    }

    /**
     * Will try to insert the record, but will fail silently if it already exists.
     * It will use keyField to retrieve the persisted record if it already exists.
     *
     * @return The persisted record
     */
    public <T_FIELD> T_OBJ_TYPE tryCreate(final T_OBJ_TYPE object,
                                          final TableField<T_REC_TYPE, T_FIELD> keyField) {
        return tryCreate(object, keyField, null, null, null);
    }

    /**
     * Will try to insert the record, but will fail silently if it already exists.
     * It will use keyField to retrieve the persisted record if it already exists.
     *
     * @return The persisted record
     */
    public <T_FIELD> T_OBJ_TYPE tryCreate(final T_OBJ_TYPE object,
                                          final TableField<T_REC_TYPE, T_FIELD> keyField,
                                          final Consumer<T_OBJ_TYPE> onCreateAction) {
        return tryCreate(object, keyField, null, null, onCreateAction);
    }

    /**
     * Will try to insert the record, but will fail silently if it already exists.
     * It will use keyField1 and keyField2 (a compound key) to retrieve the persisted
     * record if it already exists.
     *
     * @return The persisted record
     */
    public <T_FIELD1, T_FIELD2> T_OBJ_TYPE tryCreate(final T_OBJ_TYPE object,
                                                     final TableField<T_REC_TYPE, T_FIELD1> keyField1,
                                                     final TableField<T_REC_TYPE, T_FIELD2> keyField2) {
        return tryCreate(object, keyField1, keyField2, null, null);
    }

    /**
     * Will try to insert the record, but will fail silently if it already exists.
     * It will use keyField1 and keyField2 (a compound key) to retrieve the persisted
     * record if it already exists.
     *
     * @return The persisted record
     */
    public <T_FIELD1, T_FIELD2> T_OBJ_TYPE tryCreate(final T_OBJ_TYPE object,
                                                     final TableField<T_REC_TYPE, T_FIELD1> keyField1,
                                                     final TableField<T_REC_TYPE, T_FIELD2> keyField2,
                                                     final Consumer<T_OBJ_TYPE> onCreateAction) {

        return JooqUtil.contextResult(connectionProvider, context ->
                tryCreate(context, object, keyField1, keyField2, null, onCreateAction));
    }

    /**
     * Will try to insert the record, but will fail silently if it already exists.
     * It will use keyField1 and keyField2 (a compound key) to retrieve the persisted
     * record if it already exists.
     *
     * @return The persisted record
     */
    public <T_FIELD1, T_FIELD2, T_FIELD3> T_OBJ_TYPE tryCreate(final T_OBJ_TYPE object,
                                                               final TableField<T_REC_TYPE, T_FIELD1> keyField1,
                                                               final TableField<T_REC_TYPE, T_FIELD2> keyField2,
                                                               final TableField<T_REC_TYPE, T_FIELD3> keyField3,
                                                               final Consumer<T_OBJ_TYPE> onCreateAction) {

        return JooqUtil.contextResult(connectionProvider, context ->
                tryCreate(context, object, keyField1, keyField2, null, onCreateAction));
    }

    /**
     * Will try to insert the record, but will fail silently if it already exists.
     * It will use keyField1 and keyField2 (a compound key) to retrieve the persisted
     * record if it already exists.
     *
     * @return The persisted record
     */
    public <T_FIELD1, T_FIELD2> T_OBJ_TYPE tryCreate(final DSLContext context,
                                                     final T_OBJ_TYPE object,
                                                     final TableField<T_REC_TYPE, T_FIELD1> keyField1,
                                                     final TableField<T_REC_TYPE, T_FIELD2> keyField2,
                                                     final Consumer<T_OBJ_TYPE> onCreateAction) {
        return tryCreate(context, object, keyField1, keyField2, null, onCreateAction);
    }

    /**
     * Will try to insert the record, but will fail silently if it already exists.
     * It will use keyField1 and keyField2 (a compound key) to retrieve the persisted
     * record if it already exists.
     *
     * @return The persisted record
     */
    public <T_FIELD1, T_FIELD2, T_FIELD3> T_OBJ_TYPE tryCreate(final DSLContext context,
                                                               final T_OBJ_TYPE object,
                                                               final TableField<T_REC_TYPE, T_FIELD1> keyField1,
                                                               final TableField<T_REC_TYPE, T_FIELD2> keyField2,
                                                               final TableField<T_REC_TYPE, T_FIELD3> keyField3,
                                                               final Consumer<T_OBJ_TYPE> onCreateAction) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Creating a {}", table.getName()));
        final T_REC_TYPE record = objectToRecord(object);

        final Consumer<T_REC_TYPE> onCreateRecAction;
        if (onCreateAction != null) {
            onCreateRecAction = rec -> {
                final T_OBJ_TYPE obj = recordToObjectMapper.apply(rec);
                onCreateAction.accept(obj);
            };
        } else {
            onCreateRecAction = null;
        }

        final T_REC_TYPE persistedRecord = JooqUtil.tryCreate(
                context,
                record,
                keyField1,
                keyField2,
                keyField3,
                onCreateRecAction);
        return recordToObjectMapper.apply(persistedRecord);
    }

    @Override
    public T_OBJ_TYPE create(final T_OBJ_TYPE object) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Creating a {}", table.getName()));
        final T_REC_TYPE record = objectToRecord(object);
        final T_REC_TYPE persistedRecord = JooqUtil.contextResult(connectionProvider, context ->
                createRecord(context, record));
        return recordToObjectMapper.apply(persistedRecord);
    }

    public T_OBJ_TYPE create(final DSLContext context, final T_OBJ_TYPE object) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Creating a {}", table.getName()));
        final T_REC_TYPE record = objectToRecord(object);
        final T_REC_TYPE persistedRecord = createRecord(context, record);
        return recordToObjectMapper.apply(persistedRecord);
    }

    @Override
    public Optional<T_OBJ_TYPE> fetch(final T_ID_TYPE id) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Fetching {} with id {}", table.getName(), id));
        final Optional<T_REC_TYPE> optional = JooqUtil.contextResult(connectionProvider, context -> context
                .selectFrom(table)
                .where(idField.eq(id))
                .fetchOptional());
        return optional.map(recordToObjectMapper);
    }

    public <T_KEY_TYPE> Optional<T_OBJ_TYPE> fetchBy(final TableField<T_REC_TYPE, T_KEY_TYPE> keyField,
                                                     final T_KEY_TYPE key) {

        LAMBDA_LOGGER.debug(() -> LogUtil.message("Fetching {} with {} {}",
                table.getName(), keyField.getName(), key));

        final Optional<T_REC_TYPE> optional = JooqUtil.contextResult(connectionProvider, context -> context
                .selectFrom(table)
                .where(keyField.eq(key))
                .fetchOptional());
        return optional.map(recordToObjectMapper);
    }

    public T_OBJ_TYPE update(final DSLContext context, final T_OBJ_TYPE object) {
        final T_REC_TYPE record = objectToRecord(object);
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Updating a {} with id {}",
                table.getName(),
                record.get(idField)));
        final T_REC_TYPE persistedRecord = updateRecord(context, record);
        return recordToObjectMapper.apply(persistedRecord);
    }

    @Override
    public T_OBJ_TYPE update(final T_OBJ_TYPE object) {
        final T_REC_TYPE record = objectToRecord(object);
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Updating a {} with id {}",
                table.getName(),
                record.get(idField)));
        final T_REC_TYPE persistedRecord = JooqUtil.contextResultWithOptimisticLocking(connectionProvider, context ->
                updateRecord(context, record));
        return recordToObjectMapper.apply(persistedRecord);
    }

    /**
     * Performs the update of the object without optimistic locking. Should only be used
     * in cases where it does not matter if another thread/node could have also done
     * an update on the same record.
     */
    public T_OBJ_TYPE updateWithoutOptimisticLocking(final T_OBJ_TYPE object) {
        final T_REC_TYPE record = objectToRecordMapper.apply(object, table.newRecord());
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Updating a {} with id {}",
                table.getName(),
                record.get(idField)));
        final T_REC_TYPE persistedRecord = JooqUtil.contextResult(connectionProvider, context ->
                updateRecord(context, record));
        return recordToObjectMapper.apply(persistedRecord);
    }

    @Override
    public boolean delete(final T_ID_TYPE id) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Deleting a {} with id {}", table.getName(), id));
        return JooqUtil.contextResult(connectionProvider, context ->
                delete(context, id));
    }

    public boolean delete(final DSLContext context, final T_ID_TYPE id) {
        LAMBDA_LOGGER.debug(() -> LogUtil.message("Deleting a {} with id {}", table.getName(), id));
        return context
                .deleteFrom(table)
                .where(idField.eq(id))
                .execute() > 0;
    }

    T_REC_TYPE objectToRecord(final T_OBJ_TYPE object) {
        return objectToRecordMapper.apply(object, table.newRecord());
    }

    private T_REC_TYPE createRecord(final DSLContext context, final T_REC_TYPE record) {
        record.attach(context.configuration());
        record.store();
        return record;
    }

    T_REC_TYPE updateRecord(final DSLContext context, final T_REC_TYPE record) {
        record.attach(context.configuration());
        record.update();
        return record;
    }
}
