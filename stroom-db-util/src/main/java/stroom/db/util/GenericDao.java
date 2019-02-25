package stroom.db.util;

import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UpdatableRecord;
import stroom.util.shared.HasCrud;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.annotation.Nonnull;
import javax.sql.DataSource;
import java.util.Optional;

public class GenericDao<RecordType extends UpdatableRecord, EntityType, IdType>
        implements HasCrud<EntityType, IdType> {

    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(GenericDao.class);

    private Table<RecordType> table;
    private TableField<RecordType, IdType> idField;
    private Class<EntityType> entityTypeClass;
    private DataSource connectionProvider;

    // Could use the pattern described here to get the table type:
    // https://stackoverflow.com/questions/3403909/get-generic-type-of-class-at-runtime
    // That places an interface requirement on the entity, which I think is best avoided.
    public GenericDao(@Nonnull final Table<RecordType> table,
                      @Nonnull final TableField<RecordType, IdType> idField,
                      @Nonnull final Class<EntityType> entityTypeClass,
                      @Nonnull final DataSource connectionProvider) {
        this.table = table;
        this.idField = idField;
        this.entityTypeClass = entityTypeClass;
        this.connectionProvider = connectionProvider;
    }

    public EntityType create(@Nonnull final EntityType entity) {
        return JooqUtil.contextResult(connectionProvider, (context) -> {
            LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage("Creating a {}", table.getName()));
            RecordType record = context.newRecord(table, entity);
            record.store();
            return record.into(entityTypeClass);
        });
    }

    public EntityType update(@Nonnull final EntityType entity) {
        return JooqUtil.contextWithOptimisticLocking(connectionProvider, (context) -> {
            RecordType record = context.newRecord(table, entity);
            LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage(
                    "Updating a {} with id {}", table.getName(), record.get(idField)));
            record.update();
            return record.into(entityTypeClass);
        });
    }

    public boolean delete(@Nonnull final IdType id) {
        return JooqUtil.contextResult(connectionProvider, context -> {
            LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage(
                    "Deleting a {} with id {}", table.getName(), id));
            return context
                    .deleteFrom(table)
                    .where(idField.eq(id))
                    .execute() > 0;
        });
    }

    public Optional<EntityType> fetch(@Nonnull final IdType id) {
        return JooqUtil.contextWithOptimisticLocking(connectionProvider, (context) -> {
            LAMBDA_LOGGER.debug(() -> LambdaLogger.buildMessage(
                    "Fetching {} with id {}", table.getName(), id));
            return context
                    .selectFrom(table)
                    .where(idField.eq(id))
                    .fetchOptionalInto(entityTypeClass);
        });
    }
}
