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

package stroom.entity;

import event.logging.BaseObject;
import stroom.util.shared.BaseCriteria;
import stroom.event.logging.api.ObjectInfoProvider;

class BaseCriteriaObjectInfoProvider implements ObjectInfoProvider {
    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        return null;
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        final BaseCriteria criteria = (BaseCriteria) object;

        String name = criteria.getClass().getSimpleName();
        final StringBuilder sb = new StringBuilder();
        final char[] chars = name.toCharArray();
        for (final char c : chars) {
            if (Character.isUpperCase(c)) {
                sb.append(" ");
            }
            sb.append(c);
        }
        name = sb.toString().trim();
        final int start = name.indexOf(" ");
        final int end = name.lastIndexOf(" ");
        if (start != -1 && end != -1) {
            name = name.substring(start + 1, end);
        }

        return name;
    }
}
