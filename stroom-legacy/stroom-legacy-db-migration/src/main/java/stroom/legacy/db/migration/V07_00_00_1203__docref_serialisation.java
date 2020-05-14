package stroom.legacy.db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import stroom.config.impl.db.jooq.tables.Config;
import stroom.db.util.JooqUtil;

import java.util.Map;

@SuppressWarnings("unused") // used by FlyWay
public class V07_00_00_1203__docref_serialisation extends BaseJavaMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V07_00_00_1203__docref_serialisation.class);

    /**
     * Executes this migration. The execution will automatically take place within a transaction, when the underlying
     * database supports it.
     *
     * @param flywayContext The flyway context to use to execute statements.
     * @throws Exception when the migration failed.
     */
    @Override
    public void migrate(final Context flywayContext) {

        // Change the serialised form of internal statistic docRef lists in the config table from:
        // docRef(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes),docRef(StroomStatsStore,b0110ab4-ac25-4b73-b4f6-96f2b50b456a,Heap Histogram Bytes)
        // to:
        // |,docRef(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes)|,docRef(StroomStatsStore,b0110ab4-ac25-4b73-b4f6-96f2b50b456a,Heap Histogram Bytes)

        try {
            final DSLContext context = JooqUtil.createContext(flywayContext.getConnection());

            // This line should only be un-commented for manual testing in development
//            loadTestDataForManualTesting(create);

            context
                    .selectFrom(Config.CONFIG)
                    .where(Config.CONFIG.NAME.like("stroom.statistics.internal.%"))
                    .fetch()
                    .forEach(configRecord -> {
                        final String value = configRecord.getVal();
                        // change the delimiter between docRefs and prefix the docRef with its internal delimiter
                        String newValue = value.replace("),docRef", ")|,docRef");
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

    private void loadTestDataForManualTesting(final DSLContext create) {

        LOGGER.warn("Loading test data - Not for use in prod");

        final Map<String, String> testDataMap = Map.of(
                "stroom.statistics.internal.cpu", "docRef(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes),docRef(StroomStatsStore,b0110ab4-ac25-4b73-b4f6-96f2b50b456a,Heap Histogram Bytes)",
                "stroom.statistics.internal.memory", "docRef(StatisticStore,934a1600-b456-49bf-9aea-f1e84025febd,Heap Histogram Bytes)"
        );

        testDataMap.forEach((key, value) ->
                create
                        .insertInto(Config.CONFIG)
                        .columns(Config.CONFIG.NAME, Config.CONFIG.VAL)
                        .values(key, value)
                        .execute());
    }
}
