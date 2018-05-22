/*
 * Copyright 2017 Crown Copyright
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

package stroom.xml;

import stroom.guice.PipelineScopeRunnable;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.docref.DocRef;
import stroom.test.StroomPipelineTestFileUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * Helper class to validate XML based resources are valid.
 */
public class XMLValidator {
    private static final String NO_RESOURCE_PROVIDED = "No resource provided";

    private final Provider<PipelineFactory> pipelineFactoryProvider;
    private final PipelineStore pipelineStore;
    private final Provider<ErrorReceiverProxy> errorReceiverProvider;
    private final PipelineScopeRunnable pipelineScopeRunnable;

    @Inject
    XMLValidator(final Provider<PipelineFactory> pipelineFactoryProvider,
                 final PipelineStore pipelineStore,
                 final Provider<ErrorReceiverProxy> errorReceiverProvider,
                 final PipelineScopeRunnable pipelineScopeRunnable) {
        this.pipelineFactoryProvider = pipelineFactoryProvider;
        this.pipelineStore = pipelineStore;
        this.errorReceiverProvider = errorReceiverProvider;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
    }

    /**
     * Pull back the error message regarding this XML based resource.
     *
     * @param resourceName to check
     * @param useSchema    Set to true if you wish to validate against a schema. False
     *                     will just validate XML is well formed.
     * @return message
     */
    public String getInvalidXmlResourceMessage(final String resourceName, final boolean useSchema) {
        return pipelineScopeRunnable.scopeResult(() -> {
            // Only validate if something is provided
            if (resourceName != null && !resourceName.isEmpty()) {
                try {
                    // Buffer the stream.
                    final InputStream inputStream = new BufferedInputStream(
                            StroomPipelineTestFileUtil.getInputStream(resourceName));

                    // Setup the error receiver.
                    errorReceiverProvider.get().setErrorReceiver(new LoggingErrorReceiver());

                    // Create the pipeline.
                    final DocRef pipelineRef = PipelineTestUtil.createTestPipeline(pipelineStore,
                            StroomPipelineTestFileUtil.getString("F2XTestUtil/validation.Pipeline.data.xml"));
                    final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
                    final PipelineData pipelineData = pipelineDoc.getPipelineData();

                    // final ElementType schemaFilterElementType = new ElementType(
                    // "SchemaFilter");
                    // final PropertyType schemaValidationPropertyType = new
                    // PropertyType(
                    // schemaFilterElementType, "schemaValidation", "Boolean",
                    // false);
                    pipelineData.addProperty(PipelineDataUtil.createProperty("schemaFilter", "schemaValidation", false));
                    // final PropertyType schemaGroupPropertyType = new
                    // PropertyType(
                    // schemaFilterElementType, "schemaGroup", "String", false);
                    pipelineData
                            .addProperty(PipelineDataUtil.createProperty("schemaFilter", "schemaGroup", "DATA_SPLITTER"));
                    pipelineStore.writeDocument(pipelineDoc);

                    final Pipeline pipeline = pipelineFactoryProvider.get().create(pipelineData);
                    pipeline.process(inputStream);

                    return errorReceiverProvider.get().toString();

                } catch (final RuntimeException e) {
                    return e.getMessage();
                }
            }

            return NO_RESOURCE_PROVIDED;
        });
    }
}
