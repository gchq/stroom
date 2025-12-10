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

package stroom.pipeline;

import stroom.docref.DocRef;
import stroom.event.logging.api.ObjectInfoProvider;
import stroom.pipeline.shared.PipelineDoc;

import event.logging.BaseObject;
import event.logging.Data;
import event.logging.OtherObject;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PipelineDocObjectInfoProvider implements ObjectInfoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineDocObjectInfoProvider.class);

    private final Provider<PipelineService> pipelineServiceProvider;

    @Inject
    PipelineDocObjectInfoProvider(final Provider<PipelineService> pipelineServiceProvider) {
        this.pipelineServiceProvider = pipelineServiceProvider;
    }

    @Override
    public BaseObject createBaseObject(final Object obj) {
        final PipelineDoc pipelineDoc = (PipelineDoc) obj;

        String description = null;

        // Add name.
        try {
            description = pipelineDoc.getDescription();
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to get pipeline description!", e);
        }

        final OtherObject.Builder builder = OtherObject.builder()
                .withType(pipelineDoc.getType())
                .withName(pipelineDoc.getName())
                .withId(pipelineDoc.getUuid())
                .withDescription(description);


        if (pipelineDoc.getParentPipeline() != null) {
            builder.addData(Data.builder().withName("Parent")
                    .withValue(pipelineDoc.getParentPipeline().getDisplayValue()).build());
            builder.addData(Data.builder().withName("ParentUuid")
                    .withValue(pipelineDoc.getParentPipeline().getUuid()).build());
        } else {
            builder.addData(Data.builder().withName("Parent")
                    .withValue("<None>").build());
            builder.addData(Data.builder().withName("ParentUuid")
                    .withValue("").build());
        }


        try {
            final String xml = pipelineServiceProvider.get().fetchPipelineJson(
                    new DocRef(PipelineDoc.TYPE, pipelineDoc.getUuid()));
            if (xml != null) {
                builder.addData(Data.builder().withName("Structure").withValue(xml).build());
            }
        } catch (final Exception ex) {
            //Ignore this error
        }

        return builder.build();
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
