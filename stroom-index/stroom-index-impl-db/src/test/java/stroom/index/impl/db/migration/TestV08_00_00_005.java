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

/// Tests `V08_00_00_005__index_shard_task.sql`, which adds the tables that record what tasks have written to
/// index shards and what must be purged from them, and clears the leases and open statuses that
/// v7.14-beta.19 and v8.0-beta.1 may have left.
class TestV08_00_00_005 extends AbstractSingleFlywayMigrationTest<IndexDbConfig, IndexDbConnProvider> {

    private static final int CLOSED = 0;
    private static final int OPEN = 1;
    private static final int CLOSING = 10;
    private static final int OPENING = 20;
    private static final int DELETED = 99;

    @Override
    protected String getTestDataScript() {
        return scriptBuilder()
                // The shards' volume plays no part.
                .append("SET FOREIGN_KEY_CHECKS = 0;")
                // Left open, with a lease, by a node that has gone.
                .append(insertShard(1, OPEN, "'process'", "10", "NULL"))
                .append(insertShard(2, OPENING, "NULL", "NULL", "NULL"))
                .append(insertShard(3, CLOSING, "NULL", "NULL", "NULL"))
                // Closed, but with a lease, which nothing would release if the lapsed lease job is disabled.
                .append(insertShard(4, CLOSED, "'process'", "10", "NULL"))
                // Closed cleanly, with replaced commits to prune later.
                .append(insertShard(5, CLOSED, "NULL", "NULL", "12345"))
                .append(insertShard(6, DELETED, "'process'", "10", "NULL"))
                .append("SET FOREIGN_KEY_CHECKS = 1;")
                .build();
    }

    @Test
    void test() {
        assertThat(getTableCount("index_shard_task")).isZero();
        assertThat(getTableCount("index_shard_purge")).isZero();

        final List<Shard> shards = getRows(
                "select id, status, lease_instance_id, lease_expiry_ms, prune_commits_after_ms, writer_id "
                + "from index_shard order by id",
                rec -> new Shard(
                        rec.get("id", Long.class),
                        rec.get("status", Integer.class),
                        rec.get("lease_instance_id", String.class),
                        rec.get("lease_expiry_ms", Long.class),
                        rec.get("prune_commits_after_ms", Long.class),
                        rec.get("writer_id", String.class)));

        assertThat(shards).allSatisfy(shard -> {
            assertThat(shard.leaseInstanceId()).isNull();
            assertThat(shard.leaseExpiryMs()).isNull();
            assertThat(shard.writerId()).isNull();
        });
        assertThat(shards)
                .extracting(Shard::status)
                .containsExactly(CLOSED, CLOSED, CLOSED, CLOSED, CLOSED, DELETED);

        // Shards that were left open keep any commits they replaced for a while yet.
        final long nowMs = System.currentTimeMillis();
        assertThat(shards.subList(0, 3)).allSatisfy(shard ->
                assertThat(shard.pruneCommitsAfterMs()).isGreaterThan(nowMs));
        assertThat(shards.get(3).pruneCommitsAfterMs()).isNull();
        assertThat(shards.get(4).pruneCommitsAfterMs()).isEqualTo(12345L);
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

    private static String insertShard(final long id,
                                      final int status,
                                      final String leaseInstanceId,
                                      final String leaseExpiryMs,
                                      final String pruneCommitsAfterMs) {
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
                    prune_commits_after_ms)
                VALUES (%s, 'node1', 1, 'index', 5, %s, 'all', %s, %s, %s);
                """.formatted(id, status, leaseInstanceId, leaseExpiryMs, pruneCommitsAfterMs);
    }


    // --------------------------------------------------------------------------------


    private record Shard(long id,
                         int status,
                         String leaseInstanceId,
                         Long leaseExpiryMs,
                         Long pruneCommitsAfterMs,
                         String writerId) {

    }
}
