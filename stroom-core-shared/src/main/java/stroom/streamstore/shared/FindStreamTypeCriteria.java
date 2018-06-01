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

package stroom.streamstore.shared;

import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.streamstore.shared.StreamTypeEntity.Purpose;

/**
 * <p>
 * Criteria Object for find streams that is a bit more advanced that find by
 * example.
 * </p>
 */
public class FindStreamTypeCriteria extends FindNamedEntityCriteria {
    private static final long serialVersionUID = -4777723504698304778L;

    private CriteriaSet<Purpose> purpose;

    public FindStreamTypeCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindStreamTypeCriteria(final String name) {
        super(name);
    }

    public static FindStreamTypeCriteria createNonNested() {
        FindStreamTypeCriteria criteria = new FindStreamTypeCriteria();
        criteria.obtainPurpose().add(Purpose.PROCESSED);
        return criteria;
    }

    public CriteriaSet<Purpose> getPurpose() {
        return purpose;
    }

    public void setPurpose(final CriteriaSet<Purpose> purpose) {
        this.purpose = purpose;
    }

    public CriteriaSet<Purpose> obtainPurpose() {
        if (purpose == null) {
            purpose = new CriteriaSet<>();
        }
        return purpose;
    }
}
