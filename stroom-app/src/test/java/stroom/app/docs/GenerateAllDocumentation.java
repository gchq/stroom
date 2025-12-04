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

package stroom.app.docs;

import stroom.test.common.docs.StroomDocsUtil;
import stroom.test.common.docs.StroomDocsUtil.GeneratesDocumentation;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;

public class GenerateAllDocumentation {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GenerateAllDocumentation.class);

    /**
     * Run this to generate all stroom-docs content and update it in the relevant
     * stroom-docs files. Assumes stroom-docs is in a dir named 'stroom-docs' that is also
     * a sibling of this stroom repo.
     * If not set system prop
     * {@link stroom.test.common.docs.StroomDocsUtil#STROOM_DOCS_REPO_DIR_PROP_KEY}
     */
    @GeneratesDocumentation
    public static void main(final String[] args) {
        LOGGER.info("Generating documentation for pipeline elements");
        final List<DocumentationGenerator> documentationGenerators = List.of(
                new GenerateDocumentReferenceDoc(),
                new GeneratePipelineElementsDoc(),
                new GenerateSnippetsDoc());

        StroomDocsUtil.doWithClassScanResult(scanResult -> {
            documentationGenerators.forEach(documentationGenerator -> {
                LOGGER.info("Generating documentation for " + documentationGenerator.getClass().getSimpleName());
                documentationGenerator.generateAll(scanResult);
            });
        });

        LOGGER.info("Done");
    }
}
