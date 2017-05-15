/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamtask.server;

import org.springframework.stereotype.Component;
import stroom.entity.server.EntityMarshaller;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamtask.shared.StreamProcessorFilter;

@Component
public class StreamProcessorFilterMarshaller extends EntityMarshaller<StreamProcessorFilter, FindStreamCriteria> {
    @Override
    public FindStreamCriteria getObject(final StreamProcessorFilter entity) {
        return entity.getFindStreamCriteria();
    }

    @Override
    public void setObject(final StreamProcessorFilter entity, final FindStreamCriteria object) {
        entity.setFindStreamCriteria(object);
    }

    @Override
    protected String getData(final StreamProcessorFilter entity) {
        return entity.getData();
    }

    @Override
    protected void setData(final StreamProcessorFilter entity, final String data) {
        entity.setData(data);
    }

    @Override
    protected Class<FindStreamCriteria> getObjectType() {
        return FindStreamCriteria.class;
    }

    @Override
    public String getEntityType() {
        return StreamProcessorFilter.ENTITY_TYPE;
    }
}
