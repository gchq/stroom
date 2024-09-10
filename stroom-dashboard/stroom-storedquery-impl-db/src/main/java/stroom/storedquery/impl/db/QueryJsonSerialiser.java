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

package stroom.storedquery.impl.db;

import stroom.dashboard.shared.StoredQuery;
import stroom.query.api.v2.Query;
import stroom.util.json.AbstractJsonSerialiser;
import stroom.util.json.JsonUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryJsonSerialiser extends AbstractJsonSerialiser<Query> {

    @Override
    protected Class getSerialisableClass() {
        return Query.class;
    }
}
