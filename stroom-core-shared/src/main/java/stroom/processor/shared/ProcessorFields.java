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

package stroom.processor.shared;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.datasource.QueryField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProcessorFields {

    public static final String PROCESSORS_TYPE = "Processors";
    public static final DocRef PROCESSORS_DOC_REF = DocRef.builder()
            .type(PROCESSORS_TYPE)
            .uuid(PROCESSORS_TYPE)
            .name(PROCESSORS_TYPE)
            .build();

    private static final List<QueryField> FIELDS = new ArrayList<>();
    private static final Map<String, QueryField> ALL_FIELD_MAP;


    public static final QueryField ID = QueryField.createId("Processor Id");
    public static final QueryField PROCESSOR_TYPE = QueryField.createText("Processor Type");
    public static final QueryField PIPELINE = QueryField.createDocRefByUuid(
            PipelineDoc.TYPE, "Processor Pipeline");
    public static final QueryField ENABLED = QueryField.createBoolean("Processor Enabled");
    public static final QueryField DELETED = QueryField.createBoolean("Processor Deleted");
    public static final QueryField UUID = QueryField.createText("Processor UUID");
    public static final QueryField ANALYTIC_RULE = QueryField.createDocRefByUuid(
            AnalyticRuleDoc.TYPE, "Analytic Rule");

    static {
        FIELDS.add(ID);
        FIELDS.add(PROCESSOR_TYPE);
        FIELDS.add(PIPELINE);
        FIELDS.add(ENABLED);
        FIELDS.add(DELETED);
        FIELDS.add(UUID);
        FIELDS.add(ANALYTIC_RULE);

        ALL_FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
    }

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, QueryField> getAllFieldMap() {
        return ALL_FIELD_MAP;
    }
}
