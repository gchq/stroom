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

package stroom.streamstore;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.Period;
import stroom.streamstore.shared.StreamEntity;

/**
 * <p>
 * Criteria Object for find streams that is a bit more advanced that find by
 * example.
 * </p>
 */
public class FindStreamAttributeValueCriteria extends BaseCriteria {
    private static final long serialVersionUID = -4777723504698304778L;

    private EntityIdSet<StreamEntity> streamIdSet = null;
    private Period createPeriod = null;

    public FindStreamAttributeValueCriteria() {
        // GWT
    }

    public static FindStreamAttributeValueCriteria create(StreamEntity stream) {
        FindStreamAttributeValueCriteria criteria = new FindStreamAttributeValueCriteria();
        criteria.obtainStreamIdSet().add(stream);
        return criteria;
    }

    public EntityIdSet<StreamEntity> getStreamIdSet() {
        return streamIdSet;
    }

    public void setStreamIdSet(EntityIdSet<StreamEntity> streamIdSet) {
        this.streamIdSet = streamIdSet;
    }

    public EntityIdSet<StreamEntity> obtainStreamIdSet() {
        if (streamIdSet == null) {
            streamIdSet = new EntityIdSet<>();
        }
        return streamIdSet;
    }

    public Period getCreatePeriod() {
        return createPeriod;
    }

    public void setCreatePeriod(final Period createPeriod) {
        this.createPeriod = createPeriod;
    }

    public Period obtainCreatePeriod() {
        if (createPeriod == null) {
            createPeriod = new Period();
        }
        return createPeriod;

    }

}
