/*
 * Copyright 2018 Crown Copyright
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

package stroom.activity.shared;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.StringCriteria;
import stroom.entity.shared.StringCriteria.MatchStyle;

public class FindActivityCriteria extends BaseCriteria {
    private static final long serialVersionUID = 1451984883275629717L;

    private String userId;
    private StringCriteria name;

    public FindActivityCriteria() {
        name = new StringCriteria();
    }

    public static FindActivityCriteria create(final String name) {
        FindActivityCriteria criteria = new FindActivityCriteria();
        criteria.setName(new StringCriteria(name, MatchStyle.WildStandAndEnd));
        return criteria;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public StringCriteria getName() {
        return name;
    }

    public void setName(final StringCriteria name) {
        this.name = name;
    }
}
