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

package stroom.docstore.impl.dao;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.docstore.impl.db.DocStoreDbConnProvider;
import stroom.docstore.impl.db.jooq.tables.DocDependency;
import stroom.pipeline.PipelineStore;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.view.api.ViewStore;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.docstore.impl.db.jooq.tables.Doc.DOC;

/// Regression test for gh-5936: dependencies held in a document's own fields were missed by the
/// original doc_dependency migration and only appeared once the document was saved. The startup
/// rebuild must restore them using the real stores.
class TestDocDependencyRebuild extends AbstractCoreIntegrationTest {

    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private ViewStore viewStore;
    @Inject
    private DocDependencyDao docDependencyDao;
    @Inject
    private DocDependencyRebuildStartup docDependencyRebuildStartup;
    @Inject
    private DocStoreDbConnProvider docStoreDbConnProvider;

    @Test
    void testRebuildRestoresDependenciesMissedByMigration() {
        final DocRef parentPipeline = pipelineStore.createDocument("Parent pipeline");
        final DocRef childPipeline = pipelineStore.createDocument("Child pipeline");
        final DocRef dataSource = pipelineStore.createDocument("Data source pipeline");
        final DocRef view = viewStore.createDocument("View");

        pipelineStore.writeDocument(pipelineStore.readDocument(childPipeline)
                .copy()
                .parentPipeline(parentPipeline)
                .build());
        viewStore.writeDocument(viewStore.readDocument(view)
                .copy()
                .pipeline(dataSource)
                .build());

        // Saving records the dependencies
        assertDependencies(childPipeline, parentPipeline);
        assertDependencies(view, dataSource);

        // Put the docs back in the state the faulty migration left them: no edges, never stamped
        clearDependencies(childPipeline);
        clearDependencies(view);
        assertThat(docDependencyDao.getDependenciesOf(childPipeline.getUuid()))
                .isEmpty();

        docDependencyRebuildStartup.run();

        assertDependencies(childPipeline, parentPipeline);
        assertDependencies(view, dataSource);
        assertThat(getDependencyVersion(childPipeline))
                .isEqualTo(DocDependencyDao.CURRENT_DEPENDENCY_VERSION);
        assertThat(getDependencyVersion(view))
                .isEqualTo(DocDependencyDao.CURRENT_DEPENDENCY_VERSION);
    }

    @Test
    void testRebuildLeavesCurrentDocsAlone() {
        final DocRef parentPipeline = pipelineStore.createDocument("Parent pipeline");
        final DocRef childPipeline = pipelineStore.createDocument("Child pipeline");
        pipelineStore.writeDocument(pipelineStore.readDocument(childPipeline)
                .copy()
                .parentPipeline(parentPipeline)
                .build());

        // Saved docs are stamped, so the rebuild does not pick them up
        assertThat(docDependencyDao.findDocsWithStaleDependencies())
                .extracting(docVersion -> docVersion.docRef().getUuid())
                .doesNotContain(childPipeline.getUuid(), parentPipeline.getUuid());

        docDependencyRebuildStartup.run();

        assertDependencies(childPipeline, parentPipeline);
    }

    private void assertDependencies(final DocRef from, final DocRef... expected) {
        assertThat(docDependencyDao.getDependenciesOf(from.getUuid()))
                .extracting(DocRef::getUuid)
                .containsExactlyInAnyOrder(Arrays.stream(expected)
                        .map(DocRef::getUuid)
                        .toArray(String[]::new));
    }

    private void clearDependencies(final DocRef docRef) {
        JooqUtil.transaction(docStoreDbConnProvider, context -> {
            context.deleteFrom(DocDependency.DOC_DEPENDENCY)
                    .where(DocDependency.DOC_DEPENDENCY.FROM_UUID.eq(docRef.getUuid()))
                    .execute();
            context.update(DOC)
                    .set(DOC.DEPENDENCY_VERSION, (Integer) null)
                    .where(DOC.UUID.eq(docRef.getUuid()))
                    .execute();
        });
    }

    private Integer getDependencyVersion(final DocRef docRef) {
        return JooqUtil.contextResult(docStoreDbConnProvider, context -> context
                .select(DOC.DEPENDENCY_VERSION)
                .from(DOC)
                .where(DOC.UUID.eq(docRef.getUuid()))
                .fetchOne(DOC.DEPENDENCY_VERSION));
    }
}
