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

package stroom.pipeline.xml;

import stroom.docref.DocRef;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.PipelineTestUtil;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.factory.Pipeline;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.task.api.TaskContextFactory;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

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
    private final TaskContextFactory taskContextFactory;

    @Inject
    XMLValidator(final Provider<PipelineFactory> pipelineFactoryProvider,
                 final PipelineStore pipelineStore,
                 final Provider<ErrorReceiverProxy> errorReceiverProvider,
                 final PipelineScopeRunnable pipelineScopeRunnable,
                 final TaskContextFactory taskContextFactory) {
        this.pipelineFactoryProvider = pipelineFactoryProvider;
        this.pipelineStore = pipelineStore;
        this.errorReceiverProvider = errorReceiverProvider;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.taskContextFactory = taskContextFactory;
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
        return taskContextFactory.contextResult("XMLValidator", taskContext1 ->
                pipelineScopeRunnable.scopeResult(() -> {
                    // Only validate if something is provided
                    if (resourceName != null && !resourceName.isEmpty()) {
                        try {
                            // Buffer the stream.
                            final InputStream inputStream = new BufferedInputStream(
                                    StroomPipelineTestFileUtil.getInputStream(resourceName));

                            // Setup the error receiver.
                            errorReceiverProvider.get().setErrorReceiver(new LoggingErrorReceiver());

                            // Create the pipeline.
                            final DocRef pipelineRef =
                                    PipelineTestUtil.createTestPipeline(pipelineStore,
                                    StroomPipelineTestFileUtil.getString("F2XTestUtil/validation.Pipeline.json"));
                            final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineRef);
                            PipelineData pipelineData = pipelineDoc.getPipelineData();
                            final PipelineDataBuilder builder = new PipelineDataBuilder(pipelineData);

                            // final ElementType schemaFilterElementType = new ElementType(
                            // "SchemaFilter");
                            // final PropertyType schemaValidationPropertyType = new
                            // PropertyType(
                            // schemaFilterElementType, "schemaValidation", "Boolean",
                            // false);
                            builder.addProperty(PipelineDataUtil.createProperty("schemaFilter",
                                    "schemaValidation",
                                    false));
                            // final PropertyType schemaGroupPropertyType = new
                            // PropertyType(
                            // schemaFilterElementType, "schemaGroup", "String", false);
                            builder
                                    .addProperty(PipelineDataUtil.createProperty("schemaFilter",
                                            "schemaGroup",
                                            "DATA_SPLITTER"));
                            pipelineData = builder.build();
                            pipelineDoc.setPipelineData(pipelineData);
                            pipelineStore.writeDocument(pipelineDoc);

                            final Pipeline pipeline = pipelineFactoryProvider.get().create(pipelineData, taskContext1);
                            pipeline.process(inputStream);

                            return errorReceiverProvider.get().toString();

                        } catch (final RuntimeException e) {
                            return e.getMessage();
                        }
                    }

                    return NO_RESOURCE_PROVIDED;
                })).get();
    }
}
