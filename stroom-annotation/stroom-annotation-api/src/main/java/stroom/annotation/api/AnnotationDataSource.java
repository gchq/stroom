package stroom.annotation.api;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.TextField;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface AnnotationDataSource {
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

    AbstractField ID_FIELD = new IdField(ID);
//    AbstractField STREAM_ID_FIELD = new IdField(IndexConstants.STREAM_ID);
//    AbstractField EVENT_ID_FIELD = new IdField(IndexConstants.EVENT_ID);
    AbstractField CREATED_ON_FIELD = new DateField(CREATED_ON);
    AbstractField CREATED_BY_FIELD = new TextField(CREATED_BY);
    AbstractField UPDATED_ON_FIELD = new DateField(UPDATED_ON);
    AbstractField UPDATED_BY_FIELD = new TextField(UPDATED_BY);
    AbstractField TITLE_FIELD = new TextField(TITLE);
    AbstractField SUBJECT_FIELD = new TextField(SUBJECT);
    AbstractField STATUS_FIELD = new TextField(STATUS);
    AbstractField ASSIGNED_TO_FIELD = new TextField(ASSIGNED_TO);
    AbstractField COMMENT_FIELD = new TextField(COMMENT);
    AbstractField HISTORY_FIELD = new TextField(HISTORY);

    List<AbstractField> FIELDS = Arrays.asList(
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
    Map<String, AbstractField> FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(AbstractField::getName, Function.identity()));
}
