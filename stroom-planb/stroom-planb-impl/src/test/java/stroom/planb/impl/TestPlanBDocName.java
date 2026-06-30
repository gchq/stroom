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

package stroom.planb.impl;

import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.planb.shared.TraceSettings;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class TestPlanBDocName {

    @Test
    void testUniqueName() {
        assertThat(PlanBDocStoreImpl.createUniqueName("test",
                Set.of("test"))).isEqualTo("test2");
        assertThat(PlanBDocStoreImpl.createUniqueName("test2",
                Set.of("test"))).isEqualTo("test3");
        assertThat(PlanBDocStoreImpl.createUniqueName("test2",
                Set.of("test", "test2", "test3"))).isEqualTo("test4");
    }

    @Test
    void testShardingSettingsRules() {
        // Assert that shard count and shared path are preserved exactly as configured without coercion.
        final PlanBDoc doc1 = PlanBDoc.builder()
                .uuid("test-uuid-1")
                .name("test-name-1")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(0, "/shared/path"))
                        .build())
                .build();
        assertThat(doc1.getSharedPath()).isEqualTo("/shared/path");
        assertThat(doc1.getShardCount()).isEqualTo(0);

        final PlanBDoc doc2 = PlanBDoc.builder()
                .uuid("test-uuid-2")
                .name("test-name-2")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(-5, "/shared/path"))
                        .build())
                .build();
        assertThat(doc2.getShardCount()).isEqualTo(-5);

        final PlanBDoc doc3 = PlanBDoc.builder()
                .uuid("test-uuid-3")
                .name("test-name-3")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(10, "/shared/path"))
                        .build())
                .build();
        assertThat(doc3.getShardCount()).isEqualTo(10);

        final PlanBDoc doc4 = PlanBDoc.builder()
                .uuid("test-uuid-4")
                .name("test-name-4")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(5, null))
                        .build())
                .build();
        assertThat(doc4.getShardCount()).isEqualTo(5);

        final PlanBDoc doc5 = PlanBDoc.builder()
                .uuid("test-uuid-5")
                .name("test-name-5")
                .settings(new TraceSettings.Builder()
                        .sharedFileStore(new SharedFileStoreSettings(1, "  "))
                        .build())
                .build();
        assertThat(doc5.getShardCount()).isEqualTo(1);
    }
}
