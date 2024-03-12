package stroom.annotation.api;

import stroom.datasource.api.v2.QueryField;

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
    String CREATED_ON = ANNOTATION_FIELD_PREFIX + "CreatedOn";
    String CREATED_BY = ANNOTATION_FIELD_PREFIX + "CreatedBy";
    String UPDATED_ON = ANNOTATION_FIELD_PREFIX + "UpdatedOn";
    String UPDATED_BY = ANNOTATION_FIELD_PREFIX + "UpdatedBy";
    String TITLE = ANNOTATION_FIELD_PREFIX + "Title";
    String SUBJECT = ANNOTATION_FIELD_PREFIX + "Subject";
    String STATUS = ANNOTATION_FIELD_PREFIX + "Status";
    String ASSIGNED_TO = ANNOTATION_FIELD_PREFIX + "AssignedTo";
    String COMMENT = ANNOTATION_FIELD_PREFIX + "Comment";
    String HISTORY = ANNOTATION_FIELD_PREFIX + "History";

    QueryField ID_FIELD = QueryField.createId(ID);
    //    AbstractField STREAM_ID_FIELD = QueryField.createId(IndexConstants.STREAM_ID);
//    AbstractField EVENT_ID_FIELD = QueryField.createId(IndexConstants.EVENT_ID);
    QueryField CREATED_ON_FIELD = QueryField.createDate(CREATED_ON);
    QueryField CREATED_BY_FIELD = QueryField.createText(CREATED_BY);
    QueryField UPDATED_ON_FIELD = QueryField.createDate(UPDATED_ON);
    QueryField UPDATED_BY_FIELD = QueryField.createText(UPDATED_BY);
    QueryField TITLE_FIELD = QueryField.createText(TITLE);
    QueryField SUBJECT_FIELD = QueryField.createText(SUBJECT);
    QueryField STATUS_FIELD = QueryField.createText(STATUS);
    QueryField ASSIGNED_TO_FIELD = QueryField.createText(ASSIGNED_TO);
    QueryField COMMENT_FIELD = QueryField.createText(COMMENT);
    QueryField HISTORY_FIELD = QueryField.createText(HISTORY);

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
    Map<String, QueryField> FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(QueryField::getName, Function.identity()));
}
