/*
 * Copyright 2024 Crown Copyright
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

package stroom.meta.shared;

import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MetaFields {

    public static final String STREAM_STORE_TYPE = "StreamStore";
    public static final DocRef STREAM_STORE_DOC_REF = DocRef.builder()
            .type(STREAM_STORE_TYPE)
            .uuid("0")
            .name(STREAM_STORE_TYPE)
            .build();

    public static final String FIELD_ID = "Id";
    public static final String FIELD_FEED = "Feed";
    public static final String FIELD_TYPE = "Type";
    public static final String FIELD_PARENT_FEED = "Parent Feed";

    private static final List<QueryField> FIELDS = new ArrayList<>();
    private static final Map<CIKey, QueryField> FIELD_MAP;
    private static final List<QueryField> EXTENDED_FIELDS = new ArrayList<>();
    private static final List<QueryField> ALL_FIELDS = new ArrayList<>();
    private static final Map<CIKey, QueryField> ALL_FIELD_MAP;
    private static final Map<String, CIKey> ALL_FIELD_NAME_TO_KEY_MAP;

    // Non grouped fields
    // Maps to the docref name (which is unique)
    public static final QueryField FEED = QueryField.createDocRefByUniqueName(
            FeedDoc.DOCUMENT_TYPE, CIKey.ofStaticKey("Feed"));

    // Maps to the docref uuid
    public static final QueryField PIPELINE = QueryField.createDocRefByUuid(
            PipelineDoc.DOCUMENT_TYPE,
            "Pipeline");

    // Maps to the docref name (which is not unique)
    public static final QueryField PIPELINE_NAME = QueryField.createDocRefByNonUniqueName(
            PipelineDoc.DOCUMENT_TYPE,
            "Pipeline Name");

    public static final QueryField STATUS = QueryField.createText("Status");
    public static final QueryField TYPE = QueryField.createText("Type");

    // Id's
    public static final QueryField ID = QueryField.createId("Id");
    public static final QueryField META_INTERNAL_PROCESSOR_ID = QueryField.createId("Processor Id");
    public static final QueryField META_PROCESSOR_FILTER_ID = QueryField.createId("Processor Filter Id");
    public static final QueryField META_PROCESSOR_TASK_ID = QueryField.createId("Processor Task Id");

    // Times
    public static final QueryField CREATE_TIME = QueryField.createDate("Create Time");
    public static final QueryField EFFECTIVE_TIME = QueryField.createDate("Effective Time");
    public static final QueryField STATUS_TIME = QueryField.createDate("Status Time");

    // Extended fields.
//    public static final String NODE = "Node";
    public static final QueryField REC_READ = QueryField.createLong("Read Count");
    public static final QueryField REC_WRITE = QueryField.createLong("Write Count");
    public static final QueryField REC_INFO = QueryField.createLong("Info Count");
    public static final QueryField REC_WARN = QueryField.createLong("Warning Count");
    public static final QueryField REC_ERROR = QueryField.createLong("Error Count");
    public static final QueryField REC_FATAL = QueryField.createLong("Fatal Error Count");
    public static final QueryField DURATION = QueryField.createLong("Duration");
    public static final QueryField FILE_SIZE = QueryField.createLong("File Size");
    public static final QueryField RAW_SIZE = QueryField.createLong("Raw Size");

    // Parent fields.
    public static final QueryField PARENT_ID = QueryField.createId("Parent Id");
    public static final QueryField PARENT_STATUS = QueryField.createText("Parent Status");
    public static final QueryField PARENT_CREATE_TIME = QueryField.createDate("Parent Create Time");
    public static final QueryField PARENT_FEED = QueryField.createDocRefByUniqueName("Feed", FIELD_PARENT_FEED);

    static {
        // Non grouped fields
        FIELDS.add(FEED);
        FIELDS.add(PIPELINE);
        FIELDS.add(PIPELINE_NAME);
        FIELDS.add(STATUS);
        FIELDS.add(TYPE);

        // Id's
        FIELDS.add(ID);
        FIELDS.add(PARENT_ID);
        FIELDS.add(META_INTERNAL_PROCESSOR_ID);
        FIELDS.add(META_PROCESSOR_FILTER_ID);
        FIELDS.add(META_PROCESSOR_TASK_ID);

        // Times
        FIELDS.add(CREATE_TIME);
        FIELDS.add(EFFECTIVE_TIME);
        FIELDS.add(STATUS_TIME);

        FIELD_MAP = FIELDS.stream()
                .collect(Collectors.toMap(
                        QueryField::getFldNameAsCIKey,
                        Function.identity()));

        // Single Items
        EXTENDED_FIELDS.add(DURATION);

        // Counts
        EXTENDED_FIELDS.add(REC_READ);
        EXTENDED_FIELDS.add(REC_WRITE);
        EXTENDED_FIELDS.add(REC_INFO);
        EXTENDED_FIELDS.add(REC_WARN);
        EXTENDED_FIELDS.add(REC_ERROR);
        EXTENDED_FIELDS.add(REC_FATAL);

        // Sizes
        EXTENDED_FIELDS.add(FILE_SIZE);
        EXTENDED_FIELDS.add(RAW_SIZE);

        ALL_FIELDS.addAll(FIELDS);
        ALL_FIELDS.addAll(EXTENDED_FIELDS);

        ALL_FIELD_MAP = ALL_FIELDS.stream()
                .collect(Collectors.toMap(
                        (QueryField queryField) -> CIKey.of(queryField.getFldName()),
                        Function.identity()));

        ALL_FIELD_NAME_TO_KEY_MAP = ALL_FIELD_MAP.keySet()
                .stream()
                .collect(Collectors.toMap(
                        CIKey::get,
                        Function.identity()));
    }

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<CIKey, QueryField> getFieldMap() {
        return FIELD_MAP;
    }

    public static List<QueryField> getAllFields() {
        return ALL_FIELDS;
    }

    public static Map<CIKey, QueryField> getAllFieldMap() {
        return ALL_FIELD_MAP;
    }

    public static List<QueryField> getExtendedFields() {
        return EXTENDED_FIELDS;
    }

    public static CIKey createCIKey(final String fieldName) {
        return CIKey.of(fieldName, ALL_FIELD_NAME_TO_KEY_MAP);
    }
}
