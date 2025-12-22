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

package stroom.annotation.shared;

import stroom.docref.DocRef;
import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface AnnotationFields {

    DocRef ANNOTATIONS_PSEUDO_DOC_REF = new DocRef("Annotations", "Annotations", "Annotations");

    String ID = "Id";
    String UUID = "UUID";
    String CREATED_ON = "CreatedOn";
    String CREATED_BY = "CreatedBy";
    String UPDATED_ON = "UpdatedOn";
    String UPDATED_BY = "UpdatedBy";
    String TITLE = "Title";
    String SUBJECT = "Subject";
    String STATUS = "Status";
    String ASSIGNED_TO = "AssignedTo";
    String LABEL = "Label";
    String COLLECTION = "Collection";
    String COMMENT = "Comment";
    String HISTORY = "History";
    String DESCRIPTION = "Description";
    String STREAM_ID = "StreamId";
    String EVENT_ID = "EventId";
    String FEED = "Feed";

    QueryField ID_FIELD = QueryField.createId(ID);
    QueryField UUID_FIELD = QueryField.createId(UUID);
    QueryField CREATED_ON_FIELD = QueryField.createDate(CREATED_ON);
    QueryField CREATED_BY_FIELD = QueryField.createText(CREATED_BY);
    QueryField UPDATED_ON_FIELD = QueryField.createDate(UPDATED_ON);
    QueryField UPDATED_BY_FIELD = QueryField.createText(UPDATED_BY);
    QueryField TITLE_FIELD = QueryField.createText(TITLE);
    QueryField SUBJECT_FIELD = QueryField.createText(SUBJECT);
    QueryField STATUS_FIELD = QueryField.createText(STATUS);
    QueryField ASSIGNED_TO_FIELD = QueryField.createText(ASSIGNED_TO);
    QueryField LABEL_FIELD = QueryField.createText(LABEL);
    QueryField COLLECTION_FIELD = QueryField.createText(COLLECTION);
    QueryField COMMENT_FIELD = QueryField.createText(COMMENT);
    QueryField HISTORY_FIELD = QueryField.createText(HISTORY);
    QueryField DESCRIPTION_FIELD = QueryField.createText(DESCRIPTION);
    QueryField STREAM_ID_FIELD = QueryField.createText(STREAM_ID);
    QueryField EVENT_ID_FIELD = QueryField.createText(EVENT_ID);
    QueryField FEED_FIELD = QueryField.createText(FEED);

    List<QueryField> FIELDS = Arrays.asList(
            ID_FIELD,
            UUID_FIELD,
            CREATED_ON_FIELD,
            CREATED_BY_FIELD,
            UPDATED_ON_FIELD,
            UPDATED_BY_FIELD,
            TITLE_FIELD,
            SUBJECT_FIELD,
            STATUS_FIELD,
            ASSIGNED_TO_FIELD,
            LABEL_FIELD,
            COLLECTION_FIELD,
            COMMENT_FIELD,
            HISTORY_FIELD,
            DESCRIPTION_FIELD,
            STREAM_ID_FIELD,
            EVENT_ID_FIELD,
            FEED_FIELD);
    Map<String, QueryField> FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
}
