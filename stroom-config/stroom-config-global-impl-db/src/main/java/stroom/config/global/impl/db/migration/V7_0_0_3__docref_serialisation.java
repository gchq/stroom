package stroom.config.global.impl.db.migration;

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

import static stroom.config.impl.db.stroom.tables.Config.CONFIG;

@SuppressWarnings("unused") // used by FlyWay
public class V7_0_0_3__docref_serialisation implements JdbcMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V7_0_0_3__docref_serialisation.class);

    /**
     * Executes this migration. The execution will automatically take place within a transaction, when the underlying
     * database supports it.
     *
     * @param connection The connection to use to execute statements.
     * @throws Exception when the migration failed.
     */
    @Override
    public void migrate(final Connection connection) throws Exception {

        // Change the serialised form of internal statistic docRef lists in the config table from:
        // docRef(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes),docRef(StroomStatsStore,b0110ab4-ac25-4b73-b4f6-96f2b50b456a,Heap Histogram Bytes)
        // to:
        // |,docRef(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes)|docRef(StroomStatsStore,b0110ab4-ac25-4b73-b4f6-96f2b50b456a,Heap Histogram Bytes)

        try {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);

            create
                    .selectFrom(CONFIG)
                    .where(CONFIG.NAME.like("stroom.statistics.internal.%"))
                    .fetch()
                    .forEach(configRecord -> {
                        final String value = configRecord.getVal();
                        // change the delimiter between docRefs
                        String newValue = value.replace("),(", ")|(");
                        // add the delimiters in use to the front for de-serialisation
                        newValue = "|," + newValue;
                        if (!newValue.equals(value)) {
                            configRecord.setVal(newValue);
                            LOGGER.info("Changing value for property {} from [{}] to [{}]",
                                    configRecord.getName(),
                                    value,
                                    newValue);
                            configRecord.store();
                        } else {
                            LOGGER.info("Property {} requires no change to value [{}]",
                                    configRecord.getName(),
                                    value);
                        }
                    });
        } catch (final RuntimeException e) {
            LOGGER.error("Error changing docRef serialisation", e);
            throw e;
        }
    }
}
