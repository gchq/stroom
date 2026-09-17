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

package stroom.datagen.shared;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.feed.shared.FeedDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generic {@code TestJsonSerialisation} sweep in stroom-app already round-trips every
 * {@code .shared.} class reachable from a REST resource, so this covers only what that sweep
 * does not: the DataGen-specific semantics of {@link DataGenDoc}.
 */
class TestDataGenDoc {

    private static final DocRef FEED_DOC_REF = DocRef.builder()
            .type(FeedDoc.TYPE)
            .uuid("feed-uuid-1")
            .name("TEST-FEED")
            .build();

    @Test
    void roundTrip_fullyPopulated_isEqual() {
        final DataGenDoc doc = fullyPopulatedDoc();

        final String json = JsonUtil.writeValueAsString(doc);
        final DataGenDoc deserialised = JsonUtil.readValue(json, DataGenDoc.class);

        assertThat(deserialised)
                .describedAs("The sweep compares JSON strings; this compares the objects")
                .isEqualTo(doc);
        assertThat(deserialised.getTemplate())
                .isEqualTo(doc.getTemplate());
        assertThat(deserialised.getFeed())
                .isEqualTo(FEED_DOC_REF);
    }

    @Test
    void roundTrip_nullTemplateAndFeed_isEqual() {
        final DataGenDoc doc = DataGenDoc.builder()
                .uuid("doc-uuid-1")
                .name("My Generator")
                .build();

        final String json = JsonUtil.writeValueAsString(doc);
        final DataGenDoc deserialised = JsonUtil.readValue(json, DataGenDoc.class);

        assertThat(deserialised)
                .isEqualTo(doc);
        assertThat(deserialised.getTemplate())
                .isNull();
        assertThat(deserialised.getFeed())
                .isNull();
    }

    /**
     * {@link DataGenDoc} is annotated {@code @JsonInclude(NON_NULL)}, so an unconfigured doc
     * must not persist empty keys. This is what makes a null feed/template reach
     * {@code ScheduledDataGenExecutable} at runtime.
     */
    @Test
    void serialise_unsetFieldsAreOmitted() {
        final DataGenDoc doc = DataGenDoc.builder()
                .uuid("doc-uuid-1")
                .name("My Generator")
                .build();

        final String json = JsonUtil.writeValueAsString(doc);

        assertThat(json)
                .doesNotContain("\"template\"")
                .doesNotContain("\"feed\"")
                .doesNotContain("\"description\"")
                .doesNotContain("null");
        assertThat(json)
                .contains("\"uuid\"")
                .contains("\"type\"");
    }

    @Test
    void copy_withNoChanges_isEqual() {
        final DataGenDoc doc = fullyPopulatedDoc();

        assertThat(doc.copy().build())
                .isEqualTo(doc);
    }

    @Test
    void copy_changingTemplate_leavesEverythingElseIntact() {
        final DataGenDoc doc = fullyPopulatedDoc();

        final DataGenDoc copy = doc.copy()
                .template("different data")
                .build();

        assertThat(copy.getTemplate())
                .isEqualTo("different data");
        assertThat(copy)
                .isNotEqualTo(doc);
        assertThat(copy.getFeed())
                .isEqualTo(doc.getFeed());
        assertThat(copy.getDescription())
                .isEqualTo(doc.getDescription());
        assertThat(copy.getUuid())
                .isEqualTo(doc.getUuid());
        assertThat(copy.getName())
                .isEqualTo(doc.getName());
    }

    /**
     * Regression pin for the copy-from-analytics origin of this feature: DataGen must have its
     * own document type, not the analytic rule one, or the explorer icon and type filtering
     * would follow AnalyticRule.
     */
    @Test
    void documentType_isTheDataGeneratorTypeNotTheAnalyticRuleOne() {
        assertThat(DataGenDoc.TYPE)
                .isEqualTo("DataGen");
        assertThat(DataGenDoc.DOCUMENT_TYPE)
                .isSameAs(DocumentTypeRegistry.DATA_GENERATOR_DOCUMENT_TYPE)
                .isNotEqualTo(DocumentTypeRegistry.ANALYTIC_RULE_DOCUMENT_TYPE);
        assertThat(DataGenDoc.DOCUMENT_TYPE.getType())
                .isEqualTo(DataGenDoc.TYPE);
    }

    @Test
    void buildDocRef_usesTheDataGenType() {
        assertThat(DataGenDoc.buildDocRef().uuid("doc-uuid-1").build())
                .isEqualTo(DocRef.builder()
                        .type(DataGenDoc.TYPE)
                        .uuid("doc-uuid-1")
                        .build());
    }

    @Test
    void asDocRef_usesTheDataGenType() {
        final DocRef docRef = fullyPopulatedDoc().asDocRef();

        assertThat(docRef.getType())
                .isEqualTo(DataGenDoc.TYPE);
        assertThat(docRef.getUuid())
                .isEqualTo("doc-uuid-1");
        assertThat(docRef.getName())
                .isEqualTo("My Generator");
    }

    private static DataGenDoc fullyPopulatedDoc() {
        return DataGenDoc.builder()
                .uuid("doc-uuid-1")
                .name("My Generator")
                .version("version-1")
                .createTimeMs(1_000L)
                .updateTimeMs(2_000L)
                .createUser("createUser")
                .updateUser("updateUser")
                .description("Some description")
                .template("some generated data")
                .feed(FEED_DOC_REF)
                .build();
    }
}
