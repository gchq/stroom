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

package stroom.mcp.impl.tools;

import stroom.index.shared.LuceneIndexDoc;
import stroom.mcp.impl.tools.MergedPipeline.ElementProperty;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexDocs {

    private static final ElementProperty INDEX_NAME = new ElementProperty("elasticIndexingFilter", "indexName");

    @Test
    void testAnElasticIndexIsWrittenToByItsNameOrAPatternOfIt() {
        assertThat(IndexDocs.writesTo(Map.of(INDEX_NAME, "ecs-vpn-v1"), ElasticIndexDoc.TYPE, "e", "ecs-vpn-v1"))
                .isTrue();
        // A name built from values ({_suffix}).
        assertThat(IndexDocs.writesTo(Map.of(INDEX_NAME, "ecs-windows{_suffix}"), ElasticIndexDoc.TYPE, "e",
                "ecs-windows-2026"))
                .isTrue();
        // The doc names a pattern or alias.
        assertThat(IndexDocs.writesTo(Map.of(INDEX_NAME, "ecs-windows-v1"), ElasticIndexDoc.TYPE, "e",
                "ecs-windows*"))
                .isTrue();
        assertThat(IndexDocs.writesTo(Map.of(INDEX_NAME, "ecs-linux-v1"), ElasticIndexDoc.TYPE, "e", "ecs-vpn-v1"))
                .isFalse();
        assertThat(IndexDocs.writesTo(Map.of(INDEX_NAME, "ecs-vpn-v1"), ElasticIndexDoc.TYPE, "e", null)).isFalse();
    }

    @Test
    void testALuceneIndexIsWrittenToByItsDoc() {
        final ElementProperty index = new ElementProperty("indexingFilter", "index");
        assertThat(IndexDocs.writesTo(Map.of(index, Map.of("type", LuceneIndexDoc.TYPE, "uuid", "l", "name", "Acme")),
                LuceneIndexDoc.TYPE, "l", null)).isTrue();
        assertThat(IndexDocs.writesTo(Map.of(index, Map.of("uuid", "other")), LuceneIndexDoc.TYPE, "l", null))
                .isFalse();
    }
}
