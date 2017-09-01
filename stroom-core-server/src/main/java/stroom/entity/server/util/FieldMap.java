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

package stroom.entity.server.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class FieldMap {
    private final Map<String, String> sqlFieldMap;
    private final Map<String, String> hqlFieldMap;

    public FieldMap() {
        sqlFieldMap = Collections.emptyMap();
        hqlFieldMap = Collections.emptyMap();
    }

    private FieldMap(final Map<String, String> sqlFieldMap, final Map<String, String> hqlFieldMap) {
        this.sqlFieldMap = sqlFieldMap;
        this.hqlFieldMap = hqlFieldMap;
    }

    public FieldMap add(final String fieldName, final String sql, final String hql) {
        final Map<String, String> sqlFieldMap = new HashMap<>(this.sqlFieldMap);
        final Map<String, String> hqlFieldMap = new HashMap<>(this.hqlFieldMap);
        sqlFieldMap.put(fieldName, sql);
        hqlFieldMap.put(fieldName, hql);
        return new FieldMap(Collections.unmodifiableMap(sqlFieldMap), Collections.unmodifiableMap(hqlFieldMap));
    }

    public Map<String, String> getSqlFieldMap() {
        return sqlFieldMap;
    }

    public Map<String, String> getHqlFieldMap() {
        return hqlFieldMap;
    }
}