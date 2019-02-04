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
 *
 */

package stroom.pipeline;

import event.logging.BaseObject;
import event.logging.Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.event.logging.api.ObjectInfoProvider;
import stroom.pipeline.shared.PipelineDoc;

class PipelineDocObjectInfoProvider implements ObjectInfoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineDocObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        final PipelineDoc pipelineDoc = (PipelineDoc) obj;

        String description = null;

        // Add name.
        try {
            description = pipelineDoc.getDescription();
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to get pipeline description!", e);
        }

        final Object object = new Object();
        object.setType(pipelineDoc.getType());
        object.setId(pipelineDoc.getUuid());
        object.setName(pipelineDoc.getName());
        object.setDescription(description);

        return object;
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        return object.getClass().getSimpleName();
    }
}
