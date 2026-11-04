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

package stroom.core.receive;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.receive.content.shared.ContentTemplate;
import stroom.receive.content.shared.ContentTemplates;
import stroom.receive.content.shared.TemplateType;
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestContentTemplateStoreImpl {

    private static final DocRef PIPELINE = new DocRef("Pipeline", "pipeline-uuid", "Pipeline");
    private static final DocRef NEW_PIPELINE = new DocRef("Pipeline", "new-pipeline-uuid", "New pipeline");
    private static final DocRef DICTIONARY = new DocRef("Dictionary", "dictionary-uuid", "Dictionary");
    private static final DocRef NEW_DICTIONARY = new DocRef("Dictionary", "new-dictionary-uuid", "New dictionary");

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Serialiser2Factory serialiser2Factory;
    @Mock
    private ClusterLockService clusterLockService;

    private ContentTemplateStoreImpl contentTemplateStore;

    @BeforeEach
    void setUp() {
        contentTemplateStore = new ContentTemplateStoreImpl(
                storeFactory,
                securityContext,
                serialiser2Factory,
                clusterLockService);
    }

    @Test
    void testDependencyRemapFunction_recordsPipelineAndExpressionReferences() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();

        contentTemplateStore.getDependencyRemapFunction().remap(createDoc(), dependencyRemapper);

        assertThat(dependencyRemapper.getDependencies())
                .containsExactlyInAnyOrder(PIPELINE, DICTIONARY);
    }

    @Test
    void testDependencyRemapFunction_remapsPipelineAndExpressionReferences() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(Map.of(
                PIPELINE, NEW_PIPELINE,
                DICTIONARY, NEW_DICTIONARY));

        final ContentTemplates remapped = contentTemplateStore.getDependencyRemapFunction()
                .remap(createDoc(), dependencyRemapper);

        final ContentTemplate template = remapped.getContentTemplates().getFirst();
        assertThat(template.getPipeline())
                .isEqualTo(NEW_PIPELINE);
        assertThat(template.getExpression().getChildren())
                .singleElement()
                .isInstanceOfSatisfying(ExpressionTerm.class, term ->
                        assertThat(term.getDocRef()).isEqualTo(NEW_DICTIONARY));
        assertThat(template.getName())
                .isEqualTo("Template");
        assertThat(dependencyRemapper.isChanged())
                .isTrue();
    }

    @Test
    void testDependencyRemapFunction_noTemplates() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();
        final ContentTemplates doc = ContentTemplates.builder()
                .uuid("templates-uuid")
                .name("Templates")
                .contentTemplates(List.of())
                .build();

        final ContentTemplates remapped = contentTemplateStore.getDependencyRemapFunction()
                .remap(doc, dependencyRemapper);

        assertThat(remapped.getContentTemplates())
                .isEmpty();
        assertThat(dependencyRemapper.getDependencies())
                .isEmpty();
    }

    private ContentTemplates createDoc() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field("Feed")
                        .condition(Condition.IN_DICTIONARY)
                        .docRef(DICTIONARY)
                        .build())
                .build();
        final ContentTemplate template = ContentTemplate.builder()
                .withTemplateNumber(1)
                .withTemplateType(TemplateType.INHERIT_PIPELINE)
                .withExpression(expression)
                .withPipeline(PIPELINE)
                .withName("Template")
                .build();
        return ContentTemplates.builder()
                .uuid("templates-uuid")
                .name("Templates")
                .contentTemplates(List.of(template))
                .build();
    }
}
