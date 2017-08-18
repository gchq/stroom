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

import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.pipeline.server.PipelineService;
import stroom.pipeline.server.PipelineTestUtil;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.server.factory.Pipeline;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineService;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineDataUtil;
import stroom.test.StroomProcessTestFileUtil;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import java.io.BufferedInputStream;
import java.io.InputStream;

/**
 * Helper class to validate XML based resources are valid.
 */
@Component
@Scope(StroomScope.TASK)
public class XMLValidator {
    private static final String NO_RESOURCE_PROVIDED = "No resource provided";
    @Resource
    private PipelineFactory pipelineFactory;
    @Resource
    private PipelineService pipelineService;
    @Resource
    private ErrorReceiverProxy errorReceiver;

    /**
     * Pull back the error message regarding this XML based resource.
     *
     * @param resourceName to check
     * @param useSchema    Set to true if you wish to validate against a schema. False
     *                     will just validate XML is well formed.
     * @return message
     */
    public String getInvalidXmlResourceMessage(final String resourceName, final boolean useSchema) {
        // Only validate if something is provided
        if (StringUtils.isNotBlank(resourceName)) {
            try {
                // Buffer the stream.
                final InputStream inputStream = new BufferedInputStream(
                        StroomProcessTestFileUtil.getInputStream(resourceName));

                // Setup the error receiver.
                errorReceiver.setErrorReceiver(new LoggingErrorReceiver());

                // Create the pipeline.
                PipelineEntity pipelineEntity = PipelineTestUtil.createTestPipeline(pipelineService,
                        StroomProcessTestFileUtil.getString("F2XTestUtil/validation.Pipeline.data.xml"));
                final PipelineData pipelineData = pipelineEntity.getPipelineData();

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
                pipelineEntity = pipelineService.save(pipelineEntity);

                final Pipeline pipeline = pipelineFactory.create(pipelineData);
                pipeline.process(inputStream);

                return errorReceiver.toString();

            } catch (final Exception ex) {
                return ex.getMessage();
            }
        }

        return NO_RESOURCE_PROVIDED;
    }
}
