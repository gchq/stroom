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

package stroom.processor.shared;

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

public class ProcessorTaskFields {

    public static final DocRef PROCESSOR_TASK_PSEUDO_DOC_REF = new DocRef(
            "Searchable",
            "Processor Tasks",
            "Processor Tasks");

    private static final List<QueryField> FIELDS = new ArrayList<>();
    private static final Map<CIKey, QueryField> FIELD_MAP;

    public static final String FIELD_ID = "Id";
    public static final String FIELD_CREATE_TIME = "Created";
    public static final String FIELD_START_TIME = "Start Time";
    public static final String FIELD_END_TIME_DATE = "End Time";
    public static final String FIELD_FEED = "Feed";
    public static final String FIELD_PRIORITY = "Priority";
    public static final String FIELD_PIPELINE = "Pipeline";
    public static final String FIELD_PIPELINE_NAME = "Pipeline Name";
    public static final String FIELD_STATUS = "Status";
    public static final String FIELD_COUNT = "Count";
    public static final String FIELD_NODE = "Node";
    public static final String FIELD_POLL_AGE = "Poll Age";

    public static final QueryField CREATE_TIME = QueryField.createDate(
            CIKey.ofStaticKey("Create Time"), true);
    public static final QueryField CREATE_TIME_MS = QueryField.createLong(
            CIKey.ofStaticKey("Create Time Ms"), true);
    public static final QueryField START_TIME = QueryField.createDate(CIKey.ofStaticKey("Start Time"), true);
    public static final QueryField START_TIME_MS = QueryField.createLong(
            CIKey.ofStaticKey("Start Time Ms"), true);
    public static final QueryField END_TIME_MS = QueryField.createLong(
            CIKey.ofStaticKey("End Time Ms"), true);
    public static final QueryField END_TIME = QueryField.createDate(CIKey.ofStaticKey("End Time"), true);
    public static final QueryField STATUS_TIME_MS = QueryField.createLong(
            CIKey.ofStaticKey("Status Time Ms"), true);
    public static final QueryField STATUS_TIME = QueryField.createDate(
            CIKey.ofStaticKey("Status Time"), true);
    public static final QueryField META_ID = QueryField.createId(CIKey.ofStaticKey("Meta Id"), true);
    public static final QueryField NODE_NAME = QueryField.createText(CIKey.ofStaticKey(FIELD_NODE), true);
    public static final QueryField PIPELINE = QueryField.createDocRefByUuid(
            PipelineDoc.DOCUMENT_TYPE, CIKey.ofStaticKey(FIELD_PIPELINE));
    public static final QueryField PIPELINE_NAME = QueryField.createDocRefByNonUniqueName(
            PipelineDoc.DOCUMENT_TYPE, CIKey.ofStaticKey(FIELD_PIPELINE_NAME));
    public static final QueryField PROCESSOR_FILTER_ID = QueryField.createId(CIKey.ofStaticKey("Processor Filter Id"),
            true);
    public static final QueryField PROCESSOR_FILTER_PRIORITY = QueryField.createLong(CIKey.ofStaticKey(
            "Processor Filter Priority"), true);
    public static final QueryField PROCESSOR_ID = QueryField.createId(
            CIKey.ofStaticKey("Processor Id"), true);
    public static final QueryField FEED = QueryField.createDocRefByUniqueName(FeedDoc.DOCUMENT_TYPE,
            CIKey.ofStaticKey(FIELD_FEED));
    public static final QueryField STATUS = QueryField.createText(CIKey.ofStaticKey(FIELD_STATUS), true);
    public static final QueryField TASK_ID = QueryField.createId(CIKey.ofStaticKey("Task Id"), true);

    static {
        FIELDS.add(CREATE_TIME);
        FIELDS.add(CREATE_TIME_MS);
        FIELDS.add(START_TIME);
        FIELDS.add(START_TIME_MS);
        FIELDS.add(END_TIME);
        FIELDS.add(END_TIME_MS);
        FIELDS.add(STATUS_TIME);
        FIELDS.add(STATUS_TIME_MS);
        FIELDS.add(META_ID);
        FIELDS.add(NODE_NAME);
        FIELDS.add(PIPELINE);
        FIELDS.add(PIPELINE_NAME);
        FIELDS.add(PROCESSOR_FILTER_ID);
        FIELDS.add(PROCESSOR_FILTER_PRIORITY);
        FIELDS.add(PROCESSOR_ID);
        FIELDS.add(FEED);
        FIELDS.add(STATUS);
        FIELDS.add(TASK_ID);

        FIELD_MAP = FIELDS.stream()
                .collect(Collectors.toMap(
                        QueryField::getFldNameAsCIKey,
                        Function.identity()));
    }

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<CIKey, QueryField> getFieldMap() {
        return FIELD_MAP;
    }
}
