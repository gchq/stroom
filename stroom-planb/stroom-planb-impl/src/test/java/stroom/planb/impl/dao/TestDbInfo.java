/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.dao;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.dao.state.StateDb;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateSettings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the instance UUID and source meta id held in the {@code info_db} of every Plan B LMDB
 * instance. The instance UUID uniquely identifies a merge source across replays; the source meta id
 * records the stream a part shard was written from. See docs/merge-idempotency-design.md.
 */
class TestDbInfo {

    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final PlanBDoc DOC = PlanBDoc
            .builder()
            .uuid(UUID.randomUUID().toString())
            .name("test")
            .settings(new StateSettings.Builder().build())
            .build();

    @Test
    void instanceUuidIsMintedOnceAndSurvivesReopen(@TempDir final Path tempDir) {
        final String instanceUuid;
        try (final StateDb db = StateDb.create(tempDir, BYTE_BUFFERS, DOC, false)) {
            instanceUuid = db.getInstanceUuid();
            assertThat(instanceUuid).isNotNull();
        }

        // Reopening for write must not mint a new id.
        try (final StateDb db = StateDb.create(tempDir, BYTE_BUFFERS, DOC, false)) {
            assertThat(db.getInstanceUuid()).isEqualTo(instanceUuid);
        }

        // A read only open, as used when merging a source, sees the same id.
        try (final StateDb db = StateDb.create(tempDir, BYTE_BUFFERS, DOC, true)) {
            assertThat(db.getInstanceUuid()).isEqualTo(instanceUuid);
        }
    }

    @Test
    void separateInstancesGetSeparateUuids(@TempDir final Path tempDir1,
                                           @TempDir final Path tempDir2) {
        try (final StateDb db1 = StateDb.create(tempDir1, BYTE_BUFFERS, DOC, false);
                final StateDb db2 = StateDb.create(tempDir2, BYTE_BUFFERS, DOC, false)) {
            assertThat(db1.getInstanceUuid()).isNotEqualTo(db2.getInstanceUuid());
        }
    }

    @Test
    void sourceMetaIdIsRecordedAndSurvivesReopen(@TempDir final Path tempDir) {
        try (final StateDb db = StateDb.create(tempDir, BYTE_BUFFERS, DOC, false)) {
            assertThat(db.getSourceMetaId()).isEmpty();
            db.writeSourceMetaId(123L);
            assertThat(db.getSourceMetaId()).hasValue(123L);
        }

        try (final StateDb db = StateDb.create(tempDir, BYTE_BUFFERS, DOC, true)) {
            assertThat(db.getSourceMetaId()).hasValue(123L);
        }
    }
}
