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

import stroom.query.api.datasource.QueryField;

import java.util.Arrays;
import java.util.List;

public interface AnnotationDecorationFields {

    String NAMESPACE = "annotation";
    String ANNOTATION_FIELD_PREFIX = NAMESPACE + ":";
    String ANNOTATION_ID = ANNOTATION_FIELD_PREFIX + "Id";
    String ANNOTATION_UUID = ANNOTATION_FIELD_PREFIX + "Uuid";
    String ANNOTATION_CREATED_ON = ANNOTATION_FIELD_PREFIX + "CreatedOn";
    String ANNOTATION_CREATED_BY = ANNOTATION_FIELD_PREFIX + "CreatedBy";
    String ANNOTATION_UPDATED_ON = ANNOTATION_FIELD_PREFIX + "UpdatedOn";
    String ANNOTATION_UPDATED_BY = ANNOTATION_FIELD_PREFIX + "UpdatedBy";
    String ANNOTATION_TITLE = ANNOTATION_FIELD_PREFIX + "Title";
    String ANNOTATION_SUBJECT = ANNOTATION_FIELD_PREFIX + "Subject";
    String ANNOTATION_STATUS = ANNOTATION_FIELD_PREFIX + "Status";
    String ANNOTATION_ASSIGNED_TO = ANNOTATION_FIELD_PREFIX + "AssignedTo";
    String ANNOTATION_LABEL = ANNOTATION_FIELD_PREFIX + "Label";
    String ANNOTATION_COLLECTION = ANNOTATION_FIELD_PREFIX + "Collection";
    String ANNOTATION_COMMENT = ANNOTATION_FIELD_PREFIX + "Comment";
    String ANNOTATION_HISTORY = ANNOTATION_FIELD_PREFIX + "History";
    String ANNOTATION_DESCRIPTION = ANNOTATION_FIELD_PREFIX + "Description";

    QueryField ANNOTATION_ID_FIELD = QueryField.createId(ANNOTATION_ID);
    QueryField ANNOTATION_UUID_FIELD = QueryField.createText(ANNOTATION_UUID);
    QueryField ANNOTATION_CREATED_ON_FIELD = QueryField.createDate(ANNOTATION_CREATED_ON);
    QueryField ANNOTATION_CREATED_BY_FIELD = QueryField.createText(ANNOTATION_CREATED_BY);
    QueryField ANNOTATION_UPDATED_ON_FIELD = QueryField.createDate(ANNOTATION_UPDATED_ON);
    QueryField ANNOTATION_UPDATED_BY_FIELD = QueryField.createText(ANNOTATION_UPDATED_BY);
    QueryField ANNOTATION_TITLE_FIELD = QueryField.createText(ANNOTATION_TITLE);
    QueryField ANNOTATION_SUBJECT_FIELD = QueryField.createText(ANNOTATION_SUBJECT);
    QueryField ANNOTATION_STATUS_FIELD = QueryField.createText(ANNOTATION_STATUS);
    QueryField ANNOTATION_ASSIGNED_TO_FIELD = QueryField.createText(ANNOTATION_ASSIGNED_TO);
    QueryField ANNOTATION_LABEL_FIELD = QueryField.createText(ANNOTATION_LABEL);
    QueryField ANNOTATION_COLLECTION_FIELD = QueryField.createText(ANNOTATION_COLLECTION);
    QueryField ANNOTATION_COMMENT_FIELD = QueryField.createText(ANNOTATION_COMMENT);
    QueryField ANNOTATION_HISTORY_FIELD = QueryField.createText(ANNOTATION_HISTORY);
    QueryField ANNOTATION_DESCRIPTION_FIELD = QueryField.createText(ANNOTATION_DESCRIPTION);

    List<QueryField> DECORATION_FIELDS = Arrays.asList(
            ANNOTATION_ID_FIELD,
            ANNOTATION_UUID_FIELD,
            ANNOTATION_CREATED_ON_FIELD,
            ANNOTATION_CREATED_BY_FIELD,
            ANNOTATION_UPDATED_ON_FIELD,
            ANNOTATION_UPDATED_BY_FIELD,
            ANNOTATION_TITLE_FIELD,
            ANNOTATION_SUBJECT_FIELD,
            ANNOTATION_STATUS_FIELD,
            ANNOTATION_ASSIGNED_TO_FIELD,
            ANNOTATION_LABEL_FIELD,
            ANNOTATION_COLLECTION_FIELD,
            ANNOTATION_COMMENT_FIELD,
            ANNOTATION_HISTORY_FIELD,
            ANNOTATION_DESCRIPTION_FIELD);
}
