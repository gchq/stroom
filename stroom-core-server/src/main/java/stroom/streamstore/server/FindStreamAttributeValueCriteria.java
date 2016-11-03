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

package stroom.streamstore.server;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.Period;
import stroom.streamstore.shared.Stream;

/**
 * <p>
 * Criteria Object for find streams that is a bit more advanced that find by
 * example.
 * </p>
 */
public class FindStreamAttributeValueCriteria extends BaseCriteria {
    private static final long serialVersionUID = -4777723504698304778L;

    private EntityIdSet<Stream> streamIdSet = null;
    private Period createPeriod = null;

    public FindStreamAttributeValueCriteria() {
        // GWT
    }

    public static FindStreamAttributeValueCriteria create(Stream stream) {
        FindStreamAttributeValueCriteria criteria = new FindStreamAttributeValueCriteria();
        criteria.obtainStreamIdSet().add(stream);
        return criteria;
    }

    public EntityIdSet<Stream> getStreamIdSet() {
        return streamIdSet;
    }

    public EntityIdSet<Stream> obtainStreamIdSet() {
        if (streamIdSet == null) {
            streamIdSet = new EntityIdSet<>();
        }
        return streamIdSet;
    }

    public void setStreamIdSet(EntityIdSet<Stream> streamIdSet) {
        this.streamIdSet = streamIdSet;
    }

    public Period getCreatePeriod() {
        return createPeriod;
    }

    public Period obtainCreatePeriod() {
        if (createPeriod == null) {
            createPeriod = new Period();
        }
        return createPeriod;

    }

    public void setCreatePeriod(final Period createPeriod) {
        this.createPeriod = createPeriod;
    }

}
