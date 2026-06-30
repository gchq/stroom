/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.processor.impl.db;

import stroom.processor.shared.QueryData;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Singleton;

@Singleton
public class QueryDataSerialiser {

    public String serialise(final QueryData queryData) {
        if (queryData == null) {
            return null;
        }
        return JsonUtil.writeValueAsString(queryData);
    }

    public QueryData deserialise(final String json) {
        if (NullSafe.isBlankString(json)) {
            return null;
        }
        return JsonUtil.readValue(json, QueryData.class);
    }
}
