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

import stroom.docref.DocRef;
import stroom.query.api.datasource.QueryField;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IndexShardFields {

    public static final DocRef INDEX_SHARDS_PSEUDO_DOC_REF = new DocRef(
            "IndexShards", "IndexShards", "Index Shards");

    public static final String FIELD_NAME_NODE = "Node";
    public static final String FIELD_NAME_INDEX = "Index";
    public static final String FIELD_NAME_INDEX_NAME = "Index Name";
    public static final String FIELD_NAME_VOLUME_PATH = "Volume Path";
    public static final String FIELD_NAME_VOLUME_GROUP = "Volume Group";
    public static final String FIELD_NAME_PARTITION = "Partition";
    public static final String FIELD_NAME_DOC_COUNT = "Doc Count";
    public static final String FIELD_NAME_FILE_SIZE = "File Size";
    public static final String FIELD_NAME_STATUS = "Status";
    public static final String FIELD_NAME_LAST_COMMIT = "Last Commit";
    public static final String FIELD_NAME_SHARD_ID = "Shard Id";
    public static final String FIELD_NAME_INDEX_VERSION = "Index Version";

    public static final QueryField FIELD_NODE = QueryField.createText(FIELD_NAME_NODE);
    public static final QueryField FIELD_INDEX = QueryField
            .createDocRefByUuid(LuceneIndexDoc.TYPE, FIELD_NAME_INDEX);
    public static final QueryField FIELD_INDEX_NAME = QueryField.createDocRefByNonUniqueName(
            LuceneIndexDoc.TYPE, FIELD_NAME_INDEX_NAME);
    public static final QueryField FIELD_VOLUME_PATH = QueryField.createText(FIELD_NAME_VOLUME_PATH);
    public static final QueryField FIELD_VOLUME_GROUP = QueryField.createText(FIELD_NAME_VOLUME_GROUP);
    public static final QueryField FIELD_PARTITION = QueryField.createText(FIELD_NAME_PARTITION);
    public static final QueryField FIELD_DOC_COUNT = QueryField.createInteger(FIELD_NAME_DOC_COUNT);
    public static final QueryField FIELD_FILE_SIZE = QueryField.createLong(FIELD_NAME_FILE_SIZE);
    public static final QueryField FIELD_STATUS = QueryField.createText(FIELD_NAME_STATUS);
    public static final QueryField FIELD_LAST_COMMIT = QueryField.createDate(FIELD_NAME_LAST_COMMIT);
    public static final QueryField FIELD_SHARD_ID = QueryField.createLong(FIELD_NAME_SHARD_ID);
    public static final QueryField FIELD_INDEX_VERSION = QueryField.createText(FIELD_NAME_INDEX_VERSION);

    // GWT so no List.of
    private static final List<QueryField> FIELDS = Arrays.asList(
            FIELD_SHARD_ID,
            FIELD_NODE,
            FIELD_INDEX,
            FIELD_INDEX_NAME,
            FIELD_INDEX_VERSION,
            FIELD_VOLUME_PATH,
            FIELD_VOLUME_GROUP,
            FIELD_PARTITION,
            FIELD_DOC_COUNT,
            FIELD_FILE_SIZE,
            FIELD_STATUS,
            FIELD_LAST_COMMIT);

    private static final Map<String, QueryField> FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, QueryField> getFieldMap() {
        return FIELD_MAP;
    }
}
