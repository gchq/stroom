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

package stroom.annotation.api;

import stroom.datasource.api.v2.QueryField;
import stroom.util.shared.string.CIKey;
import stroom.util.shared.string.CIKeys;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface AnnotationFields {

    String CURRENT_USER_FUNCTION = "currentUser()";

    String NAMESPACE = "annotation";
    String ANNOTATION_FIELD_PREFIX = NAMESPACE + ":";
    String ID = ANNOTATION_FIELD_PREFIX + "Id";

//    String CREATED_ON = ANNOTATION_FIELD_PREFIX + "CreatedOn";
//    String CREATED_BY = ANNOTATION_FIELD_PREFIX + "CreatedBy";
//    String UPDATED_ON = ANNOTATION_FIELD_PREFIX + "UpdatedOn";
//    String UPDATED_BY = ANNOTATION_FIELD_PREFIX + "UpdatedBy";
//    String TITLE = ANNOTATION_FIELD_PREFIX + "Title";
//    String SUBJECT = ANNOTATION_FIELD_PREFIX + "Subject";
//    String STATUS = ANNOTATION_FIELD_PREFIX + "Status";
//    String ASSIGNED_TO = ANNOTATION_FIELD_PREFIX + "AssignedTo";
//    String COMMENT = ANNOTATION_FIELD_PREFIX + "Comment";
//    String HISTORY = ANNOTATION_FIELD_PREFIX + "History";

    QueryField ID_FIELD = QueryField.createId(CIKeys.ANNO_ID, true);
    //    AbstractField STREAM_ID_FIELD = QueryField.createId(IndexConstants.STREAM_ID);
//    AbstractField EVENT_ID_FIELD = QueryField.createId(IndexConstants.EVENT_ID);
    QueryField CREATED_ON_FIELD = QueryField.createDate(CIKeys.ANNO_CREATED_ON, true);
    QueryField CREATED_BY_FIELD = QueryField.createText(CIKeys.ANNO_CREATED_BY, true);
    QueryField UPDATED_ON_FIELD = QueryField.createDate(CIKeys.ANNO_UPDATED_ON, true);
    QueryField UPDATED_BY_FIELD = QueryField.createText(CIKeys.ANNO_UPDATED_BY, true);
    QueryField TITLE_FIELD = QueryField.createText(CIKeys.ANNO_TITLE, true);
    QueryField SUBJECT_FIELD = QueryField.createText(CIKeys.ANNO_SUBJECT, true);
    QueryField STATUS_FIELD = QueryField.createText(CIKeys.ANNO_STATUS, true);
    QueryField ASSIGNED_TO_FIELD = QueryField.createText(CIKeys.ANNO_ASSIGNED_TO, true);
    QueryField COMMENT_FIELD = QueryField.createText(CIKeys.ANNO_COMMENT, true);
    QueryField HISTORY_FIELD = QueryField.createText(CIKeys.ANNO_HISTORY, true);

    List<QueryField> FIELDS = Arrays.asList(
            ID_FIELD,
//            STREAM_ID_FIELD,
//            EVENT_ID_FIELD,
            CREATED_ON_FIELD,
            CREATED_BY_FIELD,
            UPDATED_ON_FIELD,
            UPDATED_BY_FIELD,
            TITLE_FIELD,
            SUBJECT_FIELD,
            STATUS_FIELD,
            ASSIGNED_TO_FIELD,
            COMMENT_FIELD,
            HISTORY_FIELD);

    Map<CIKey, QueryField> FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(
                    QueryField::getFldNameAsCIKey,
                    Function.identity()));
}
