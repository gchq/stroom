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

package stroom.docstore.impl.db.migration;

import stroom.docref.DocRef;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DocRefFinder} — pure JSON tree-walking, no DB needed.
 */
class TestDocRefFinder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String OWNER_UUID = "owner-uuid-1234";

    @Test
    void testFindsSimpleDocRef() throws Exception {
        final String json = """
                {
                  "dataSource": {
                    "type": "Index",
                    "uuid": "abc-123",
                    "name": "My Index"
                  }
                }
                """;

        final Set<DocRef> results = findDocRefs(json);

        assertThat(results).hasSize(1);
        assertThat(results).contains(new DocRef("Index", "abc-123", "My Index"));
    }

    @Test
    void testFindsMultipleDocRefs() throws Exception {
        final String json = """
                {
                  "dataSource": {
                    "type": "Index",
                    "uuid": "abc-123",
                    "name": "My Index"
                  },
                  "pipeline": {
                    "type": "Pipeline",
                    "uuid": "def-456",
                    "name": "My Pipeline"
                  }
                }
                """;

        final Set<DocRef> results = findDocRefs(json);

        assertThat(results).hasSize(2);
        assertThat(results).contains(new DocRef("Index", "abc-123", "My Index"));
        assertThat(results).contains(new DocRef("Pipeline", "def-456", "My Pipeline"));
    }

    @Test
    void testFindsNestedDocRefs() throws Exception {
        final String json = """
                {
                  "outer": {
                    "inner": {
                      "deeply": {
                        "nested": {
                          "type": "Dictionary",
                          "uuid": "deep-uuid",
                          "name": "Nested Dict"
                        }
                      }
                    }
                  }
                }
                """;

        final Set<DocRef> results = findDocRefs(json);

        assertThat(results).hasSize(1);
        assertThat(results).contains(new DocRef("Dictionary", "deep-uuid", "Nested Dict"));
    }

    @Test
    void testFindsDocRefsInArrays() throws Exception {
        final String json = """
                {
                  "dependencies": [
                    {"type": "Script", "uuid": "script-1", "name": "Script A"},
                    {"type": "Script", "uuid": "script-2", "name": "Script B"}
                  ]
                }
                """;

        final Set<DocRef> results = findDocRefs(json);

        assertThat(results).hasSize(2);
        assertThat(results).contains(new DocRef("Script", "script-1", "Script A"));
        assertThat(results).contains(new DocRef("Script", "script-2", "Script B"));
    }

    @Test
    void testExcludesSelfReference() throws Exception {
        final String json = """
                {
                  "self": {
                    "type": "View",
                    "uuid": "%s",
                    "name": "Myself"
                  },
                  "other": {
                    "type": "Index",
                    "uuid": "other-uuid",
                    "name": "Other"
                  }
                }
                """.formatted(OWNER_UUID);

        final Set<DocRef> results = findDocRefs(json);

        assertThat(results).hasSize(1);
        assertThat(results).contains(new DocRef("Index", "other-uuid", "Other"));
    }

    @Test
    void testIgnoresObjectsWithoutUuid() throws Exception {
        final String json = """
                {
                  "notADocRef": {
                    "type": "SomeType",
                    "name": "No UUID here"
                  }
                }
                """;

        final Set<DocRef> results = findDocRefs(json);

        assertThat(results).isEmpty();
    }

    @Test
    void testIgnoresObjectsWithoutType() throws Exception {
        final String json = """
                {
                  "notADocRef": {
                    "uuid": "some-uuid",
                    "name": "No type here"
                  }
                }
                """;

        final Set<DocRef> results = findDocRefs(json);

        assertThat(results).isEmpty();
    }

    @Test
    void testIgnoresBlankTypeOrUuid() throws Exception {
        final String json = """
                {
                  "blankType": {
                    "type": "",
                    "uuid": "some-uuid",
                    "name": "Blank type"
                  },
                  "blankUuid": {
                    "type": "SomeType",
                    "uuid": "",
                    "name": "Blank uuid"
                  }
                }
                """;

        final Set<DocRef> results = findDocRefs(json);

        assertThat(results).isEmpty();
    }

    @Test
    void testHandlesMissingName() throws Exception {
        final String json = """
                {
                  "ref": {
                    "type": "Feed",
                    "uuid": "feed-uuid"
                  }
                }
                """;

        final Set<DocRef> results = findDocRefs(json);

        assertThat(results).hasSize(1);
        final DocRef holder = results.iterator().next();
        assertThat(holder.getType()).isEqualTo("Feed");
        assertThat(holder.getUuid()).isEqualTo("feed-uuid");
        assertThat(holder.getName()).isEqualTo("");
    }

    @Test
    void testDeduplicatesByUuid() throws Exception {
        final String json = """
                {
                  "ref1": {
                    "type": "Index",
                    "uuid": "same-uuid",
                    "name": "Name A"
                  },
                  "ref2": {
                    "type": "Index",
                    "uuid": "same-uuid",
                    "name": "Name B"
                  }
                }
                """;

        final Set<DocRef> results = findDocRefs(json);

        // Same uuid = same dependency edge, deduplicated
        assertThat(results).hasSize(1);
        assertThat(results.iterator().next().getUuid()).isEqualTo("same-uuid");
    }

    @Test
    void testExpressionTreeWithDocRefs() throws Exception {
        // Simulates an expression tree like those in ContentTemplates, DataRetentionRules, etc.
        final String json = """
                {
                  "expression": {
                    "type": "operator",
                    "op": "AND",
                    "children": [
                      {
                        "type": "term",
                        "field": "Feed",
                        "condition": "IS_DOC_REF",
                        "docRef": {
                          "type": "Feed",
                          "uuid": "feed-uuid-1",
                          "name": "Test Feed"
                        }
                      },
                      {
                        "type": "term",
                        "field": "Dictionary",
                        "condition": "IN_DICTIONARY",
                        "docRef": {
                          "type": "Dictionary",
                          "uuid": "dict-uuid-1",
                          "name": "Test Dictionary"
                        }
                      }
                    ]
                  }
                }
                """;

        final Set<DocRef> results = findDocRefs(json);

        // The expression tree nodes themselves have "type" but no "uuid", so
        // only the docRef children should be found
        assertThat(results).hasSize(2);
        assertThat(results).contains(new DocRef("Feed", "feed-uuid-1", "Test Feed"));
        assertThat(results).contains(new DocRef("Dictionary", "dict-uuid-1", "Test Dictionary"));
    }

    @Test
    void testNullNodeHandledGracefully() {
        final Set<DocRef> results = new HashSet<>();
        DocRefFinder.findDocRefs(null, OWNER_UUID, results);

        assertThat(results).isEmpty();
    }

    @Test
    void testEmptyObject() throws Exception {
        final Set<DocRef> results = findDocRefs("{}");
        assertThat(results).isEmpty();
    }

    @Test
    void testEmptyArray() throws Exception {
        final Set<DocRef> results = findDocRefs("[]");
        assertThat(results).isEmpty();
    }

    @Test
    void testNonTextTypeAndUuidIgnored() throws Exception {
        final String json = """
                {
                  "ref": {
                    "type": 123,
                    "uuid": true,
                    "name": "Not text fields"
                  }
                }
                """;

        final Set<DocRef> results = findDocRefs(json);

        assertThat(results).isEmpty();
    }

    @Test
    void testSafeStrNullReturnsEmpty() {
        assertThat(DocRefFinder.safeStr(null)).isEqualTo("");
    }

    @Test
    void testSafeStrNonNullPassthrough() {
        assertThat(DocRefFinder.safeStr("hello")).isEqualTo("hello");
    }

    // --- Helper ---

    private Set<DocRef> findDocRefs(final String json) throws Exception {
        final JsonNode root = OBJECT_MAPPER.readTree(json);
        final Set<DocRef> results = new HashSet<>();
        DocRefFinder.findDocRefs(root, OWNER_UUID, results);
        return results;
    }
}
