package stroom.job.impl.db;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.UpdatableRecordImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static stroom.db.util.JooqUtil.contextWithOptimisticLocking;

public class GenericDao<RecordType extends UpdatableRecordImpl, EntityType> {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericDao.class);

    private Table<RecordType> table;
    private Field idField;
    private Class<EntityType> entityTypeClass;
    private ConnectionProvider connectionProvider;

    // Could use the pattern described here to get the table type:
    // https://stackoverflow.com/questions/3403909/get-generic-type-of-class-at-runtime
    // That places an interface requirement on the entity, which I think is best avoided.
    public GenericDao(Table<RecordType> table, Field idField, Class<EntityType> entityTypeClass, ConnectionProvider connectionProvider) {
        this.table = table;
        this.idField = idField;
        this.entityTypeClass = entityTypeClass;
        this.connectionProvider = connectionProvider;
    }

    public EntityType create(EntityType entity) {
        return contextWithOptimisticLocking(connectionProvider, (context) -> {
            LOGGER.debug("Creating a {}", table.getName());
            RecordType record = context.newRecord(table, entity);
            record.store();
            EntityType createdRecord = record.into(entityTypeClass);
            return createdRecord;
        });
    }

    public EntityType update(final EntityType entity) {
        return contextWithOptimisticLocking(connectionProvider, (context) -> {
            RecordType record = context.newRecord(table, entity);
            // This depends on there being a field named 'id' that is what we expect it to be.
            // I'd rather this was implicit/opinionated than forced into place with an interface.
            LOGGER.debug("Updating a {} with id {}", table.getName(), record.getValue("id"));
            record.update();
            return record.into(entityTypeClass);
        });
    }

    public int delete(int id) {
        return contextWithOptimisticLocking(connectionProvider, context -> {
            LOGGER.debug("Deleting a {} with id {}",table.getName(), id);
            return context
                    .deleteFrom(table)
                    .where(idField.eq(id))
                    .execute();
        });
    }

    public Optional<EntityType> fetch(int id) {
        return contextWithOptimisticLocking(connectionProvider, (context) -> {
            LOGGER.debug("Fetching {} with id {}",table.getName(), id);
            EntityType record = context.selectFrom(table).where(idField.eq(id)).fetchOneInto(entityTypeClass);
            return Optional.ofNullable(record);
        });
    }
}
