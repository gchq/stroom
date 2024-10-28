package stroom.query.client.presenter;

import stroom.datasource.api.v2.QueryField;
import stroom.util.shared.string.CIKey;

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

    QueryField ID_FIELD = QueryField.createId(CIKey.ofStaticKey(ID), true);
    //    AbstractField STREAM_ID_FIELD = QueryField.createId(IndexConstants.STREAM_ID);
//    AbstractField EVENT_ID_FIELD = QueryField.createId(IndexConstants.EVENT_ID);
    QueryField CREATED_ON_FIELD = QueryField.createDate(CIKey.ofStaticKey(CREATED_ON), true);
    QueryField CREATED_BY_FIELD = QueryField.createText(CIKey.ofStaticKey(CREATED_BY), true);
    QueryField UPDATED_ON_FIELD = QueryField.createDate(CIKey.ofStaticKey(UPDATED_ON), true);
    QueryField UPDATED_BY_FIELD = QueryField.createText(CIKey.ofStaticKey(UPDATED_BY), true);
    QueryField TITLE_FIELD = QueryField.createText(CIKey.ofStaticKey(TITLE), true);
    QueryField SUBJECT_FIELD = QueryField.createText(CIKey.ofStaticKey(SUBJECT), true);
    QueryField STATUS_FIELD = QueryField.createText(CIKey.ofStaticKey(STATUS), true);
    QueryField ASSIGNED_TO_FIELD = QueryField.createText(CIKey.ofStaticKey(ASSIGNED_TO), true);
    QueryField COMMENT_FIELD = QueryField.createText(CIKey.ofStaticKey(COMMENT), true);
    QueryField HISTORY_FIELD = QueryField.createText(CIKey.ofStaticKey(HISTORY), true);

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
            .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
}
