/*
 * Copyright 2026 Crown Copyright
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

package stroom.index.impl.db.migration;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.index.impl.db.IndexDbConfig;
import stroom.index.impl.db.IndexDbConnProvider;
import stroom.index.impl.db.IndexDbModule;
import stroom.test.common.util.db.AbstractSingleFlywayMigrationTest;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests `V07_14_00_015__index_shard_pending_stream_clear.sql`, which removes the records v7.14-beta.19 kept
/// for recovering streams and leaves everything else about the shards alone.
class TestV07_14_00_015 extends AbstractSingleFlywayMigrationTest<IndexDbConfig, IndexDbConnProvider> {

    @Override
    protected String getTestDataScript() {
        return scriptBuilder()
                // The shards' volume plays no part.
                .append("SET FOREIGN_KEY_CHECKS = 0;")
                .append(insertShard(1, 0))
                .append(insertShard(2, 1))
                .append("SET FOREIGN_KEY_CHECKS = 1;")
                .append(insertPendingStream(2, 100))
                // An attempt row, which belongs to no shard.
                .append(insertPendingStream(0, 100))
                .build();
    }

    @Test
    void test() {
        assertThat(getTableCount("index_shard_pending_stream")).isZero();

        final List<Shard> shards = getRows(
                "select id, status, document_count, lease_instance_id, lease_expiry_ms, recover_pending_streams "
                + "from index_shard order by id",
                rec -> new Shard(
                        rec.get("id", Long.class),
                        rec.get("status", Integer.class),
                        rec.get("document_count", Integer.class),
                        rec.get("lease_instance_id", String.class),
                        rec.get("lease_expiry_ms", Long.class),
                        rec.get("recover_pending_streams", Integer.class)));
        assertThat(shards).containsExactly(
                new Shard(1, 2, 5, "process", 10L, 0),
                new Shard(2, 2, 5, "process", 10L, 0));
    }

    @Override
    protected AbstractFlyWayDbModule<IndexDbConfig, IndexDbConnProvider> getDatasourceModule() {
        return new IndexDbModule() {
            @Override
            protected List<String> getFlyWayLocations() {
                return mergeLocations(super.getFlyWayLocations(), getTestDataMigrationLocation());
            }

            // Override this, so we target a specific version and don't run all migrations
            @Override
            protected Optional<MigrationVersion> getMigrationTarget() {
                return Optional.ofNullable(getTargetVersion());
            }
        };
    }

    @Override
    protected Class<IndexDbConnProvider> getConnectionProviderType() {
        return IndexDbConnProvider.class;
    }

    private static String insertShard(final long id, final int recoverPendingStreams) {
        return """
                INSERT INTO index_shard (
                    id,
                    node_name,
                    fk_volume_id,
                    index_uuid,
                    document_count,
                    status,
                    partition_name,
                    lease_instance_id,
                    lease_expiry_ms,
                    recover_pending_streams)
                VALUES (%s, 'node1', 1, 'index', 5, 2, 'all', 'process', 10, %s);
                """.formatted(id, recoverPendingStreams);
    }

    private static String insertPendingStream(final long indexShardId, final long streamId) {
        return """
                INSERT INTO index_shard_pending_stream (
                    index_shard_id,
                    stream_id,
                    task_id,
                    state,
                    update_time_ms)
                VALUES (%s, %s, 1, 0, 0);
                """.formatted(indexShardId, streamId);
    }


    // --------------------------------------------------------------------------------


    private record Shard(long id,
                         int status,
                         int documentCount,
                         String leaseInstanceId,
                         Long leaseExpiryMs,
                         int recoverPendingStreams) {

    }
}
