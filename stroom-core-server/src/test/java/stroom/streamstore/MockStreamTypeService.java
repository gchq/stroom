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

package stroom.streamstore;

import stroom.entity.MockNamedEntityService;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.streamstore.shared.FindStreamTypeCriteria;
import stroom.streamstore.shared.StreamType;

import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
class MockStreamTypeService extends MockNamedEntityService<StreamType, FindStreamTypeCriteria>
        implements StreamTypeService {
    MockStreamTypeService() {
        for (final StreamType streamType : StreamType.initialValues()) {
            save(streamType);
        }
    }

    /**
     * @return the stream type by it's ID or null
     */
    @SuppressWarnings("unchecked")
    @Override
    public StreamType get(final String name) {
        final List<StreamType> list = map.values()
                .stream()
                .filter(st -> st.getName().equals(name))
                .collect(Collectors.toList());

        if (list.size() == 0) {
            return null;
        }
        return list.get(0);
    }

    @Override
    public StreamType getOrCreate(final String name) {
        StreamType streamType = get(name);
        if (streamType == null) {
            streamType = create(name);
        }
        return streamType;
    }

    @Override
    public long getId(final String name) {
        return getOrCreate(name).getId();
    }

    @Override
    public EntityIdSet<StreamType> convertNameSet(final CriteriaSet<String> streamTypes) {
        if (streamTypes == null) {
            return null;
        }

        final EntityIdSet<StreamType> entityIdSet = new EntityIdSet<>();
        entityIdSet.setMatchAll(streamTypes.getMatchAll());
        entityIdSet.setMatchNull(streamTypes.getMatchNull());
        streamTypes.forEach(streamTypeName -> entityIdSet.add(getId(streamTypeName)));

        return entityIdSet;
    }

    @Override
    public void clear() {
        // Do nothing as we don't want to loose stream types set in constructor.
    }

    @Override
    public String getNamePattern() {
        return null;
    }

    @Override
    public Class<StreamType> getEntityClass() {
        return StreamType.class;
    }
}
