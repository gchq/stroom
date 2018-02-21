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

package stroom.pipeline;

import stroom.entity.EntityMarshaller;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineData;

class PipelineMarshaller extends EntityMarshaller<PipelineEntity, PipelineData> {
    PipelineMarshaller() {
    }

    @Override
    public PipelineData getObject(final PipelineEntity entity) {
        return entity.getPipelineData();
    }

    @Override
    public void setObject(final PipelineEntity entity, final PipelineData object) {
        if (object == null) {
            entity.setPipelineData(new PipelineData());
        } else {
            entity.setPipelineData(object);
        }
    }

    @Override
    protected String getData(final PipelineEntity entity) {
        return entity.getData();
    }

    @Override
    protected void setData(final PipelineEntity entity, final String data) {
        entity.setData(data);
    }

    @Override
    protected Class<PipelineData> getObjectType() {
        return PipelineData.class;
    }

    @Override
    public String getEntityType() {
        return PipelineEntity.ENTITY_TYPE;
    }
}
