package stroom.annotation.shared;

import stroom.query.api.Column;
import stroom.query.api.Format;

import java.util.Arrays;
import java.util.List;

public interface AnnotationColumns {

    Column ANNOTATION_ID = Column.builder()
            .id("__annotation_id__")
            .name("Annotation Id")
            .format(Format.NUMBER)
            .build();
    Column ANNOTATION_UUID = Column.builder()
            .id("__annotation_uuid__")
            .name("Annotation UUID")
            .format(Format.TEXT)
            .build();
    Column ANNOTATION_CREATED_ON = Column.builder()
            .id("__annotation_created_on__")
            .name("Annotation Created On")
            .format(Format.DATE_TIME)
            .build();
    Column ANNOTATION_CREATED_BY = Column.builder()
            .id("__annotation_created_by__")
            .name("Annotation Created By")
            .format(Format.TEXT)
            .build();
    Column ANNOTATION_UPDATED_ON = Column.builder()
            .id("__annotation_updated_on__")
            .name("Annotation Updated On")
            .format(Format.DATE_TIME)
            .build();
    Column ANNOTATION_UPDATED_BY = Column.builder()
            .id("__annotation_updated_by__")
            .name("Annotation Updated By")
            .format(Format.TEXT)
            .build();
    Column ANNOTATION_TITLE = Column.builder()
            .id("__annotation_title__")
            .name("Annotation Title")
            .format(Format.TEXT)
            .build();
    Column ANNOTATION_SUBJECT = Column.builder()
            .id("__annotation_subject__")
            .name("Annotation Subject")
            .format(Format.TEXT)
            .build();
    Column ANNOTATION_STATUS = Column.builder()
            .id("__annotation_status__")
            .name("Annotation Status")
            .format(Format.TEXT)
            .build();
    Column ANNOTATION_ASSIGNED_TO = Column.builder()
            .id("__annotation_assigned_to__")
            .name("Annotation Assigned To")
            .format(Format.TEXT)
            .build();
    Column ANNOTATION_LABEL = Column.builder()
            .id("__annotation_label__")
            .name("Annotation Label")
            .format(Format.TEXT)
            .build();
    Column ANNOTATION_COLLECTION = Column.builder()
            .id("__annotation_collection__")
            .name("Annotation Collection")
            .format(Format.TEXT)
            .build();

    List<Column> COLUMNS = Arrays.asList(
            ANNOTATION_ID,
            ANNOTATION_UUID,
            ANNOTATION_CREATED_ON,
            ANNOTATION_CREATED_BY,
            ANNOTATION_UPDATED_ON,
            ANNOTATION_UPDATED_BY,
            ANNOTATION_TITLE,
            ANNOTATION_SUBJECT,
            ANNOTATION_STATUS,
            ANNOTATION_ASSIGNED_TO,
            ANNOTATION_LABEL,
            ANNOTATION_COLLECTION);
}
