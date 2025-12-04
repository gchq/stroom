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

package stroom.index.shared;

import stroom.query.api.datasource.QueryField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IndexVolumeFields {

    public static final String INDEX_VOLUME_TYPE = "IndexVolume";
    public static final String FIELD_ID = "Id";
    public static final String FIELD_GROUP_ID = "Group Id";
    public static final String FIELD_NODE_NAME = "Node";
    public static final String FIELD_PATH = "Path";

    private static final List<QueryField> FIELDS = new ArrayList<>();
    private static final Map<String, QueryField> FIELD_MAP;

    public static final QueryField GROUP_ID = QueryField.createId(FIELD_GROUP_ID);
    public static final QueryField NODE_NAME = QueryField.createText(FIELD_NODE_NAME);
    public static final QueryField PATH = QueryField.createText(FIELD_PATH);

    // Id's
    public static final QueryField ID = QueryField.createId(FIELD_ID);

    static {
        // Non grouped fields
        FIELDS.add(GROUP_ID);
        FIELDS.add(NODE_NAME);
        FIELDS.add(PATH);

        // Id's
        FIELDS.add(ID);

        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
    }

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, QueryField> getFieldMap() {
        return FIELD_MAP;
    }
}
