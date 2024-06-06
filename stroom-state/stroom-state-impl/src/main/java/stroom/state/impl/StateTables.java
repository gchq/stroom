package stroom.state.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.internal.querybuilder.schema.compaction.DefaultTimeWindowCompactionStrategy;

import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.createTable;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.dropTable;

public class StateTables {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateTables.class);

    public static void create(final CqlSession session) {
        LOGGER.info("Creating tables...");
        LOGGER.logDurationIfInfoEnabled(() -> {
            session.execute(createTable("state", "state")
                    .ifNotExists()
                    .withPartitionKey("map", DataTypes.TEXT)
                    .withPartitionKey("key", DataTypes.TEXT)
                    .withClusteringColumn("effective_time", DataTypes.TIMESTAMP)
                    .withColumn("type_Id", DataTypes.TINYINT)
                    .withColumn("value", DataTypes.BLOB)
                    .withClusteringOrder("effective_time", ClusteringOrder.DESC)
                    .withCompaction(new DefaultTimeWindowCompactionStrategy())
                    .build());
        }, "createTables()");
    }

    public static void drop(final CqlSession session) {
        session.execute(dropTable("state", "state")
                .ifExists()
                .build());
    }
}
