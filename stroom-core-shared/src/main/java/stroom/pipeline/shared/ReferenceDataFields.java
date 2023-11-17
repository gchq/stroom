package stroom.pipeline.shared;

import stroom.datasource.api.v2.Conditions;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.QueryField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;

import java.util.Arrays;
import java.util.List;

public class ReferenceDataFields {

    public static final DocRef REF_STORE_PSEUDO_DOC_REF = new DocRef(
            "Searchable",
            "Reference Data Store",
            "Reference Data Store (This Node Only)");
    public static final QueryField FEED_NAME_FIELD = new TextField(
            "Feed Name", true, Conditions.REF_DATA_TEXT);
    public static final QueryField KEY_FIELD = new TextField(
            "Key", true, Conditions.REF_DATA_TEXT);
    public static final QueryField VALUE_FIELD = new TextField(
            "Value", true, Conditions.REF_DATA_TEXT);
    public static final QueryField VALUE_REF_COUNT_FIELD = new IntegerField(
            "Value Reference Count", false);
    public static final QueryField MAP_NAME_FIELD = new TextField(
            "Map Name", true, Conditions.REF_DATA_TEXT);
    public static final QueryField CREATE_TIME_FIELD = new DateField(
            "Create Time", true);
    public static final QueryField EFFECTIVE_TIME_FIELD = new DateField(
            "Effective Time", true);
    public static final QueryField LAST_ACCESSED_TIME_FIELD = new DateField(
            "Last Accessed Time", true);
    public static final QueryField PIPELINE_FIELD = new DocRefField(PipelineDoc.DOCUMENT_TYPE,
            "Reference Loader Pipeline", true, Conditions.REF_DATA_DOC_REF);
    public static final QueryField PROCESSING_STATE_FIELD = new TextField(
            "Processing State", false);
    public static final QueryField STREAM_ID_FIELD = new IdField(
            "Stream ID", false);
    public static final QueryField PART_NO_FIELD = new LongField(
            "Part Number", false);
    public static final QueryField PIPELINE_VERSION_FIELD = new TextField(
            "Pipeline Version", false);

    public static final List<QueryField> FIELDS = Arrays.asList(
            FEED_NAME_FIELD,
            KEY_FIELD,
            VALUE_FIELD,
            VALUE_REF_COUNT_FIELD,
            MAP_NAME_FIELD,
            CREATE_TIME_FIELD,
            EFFECTIVE_TIME_FIELD,
            LAST_ACCESSED_TIME_FIELD,
            PIPELINE_FIELD,
            PROCESSING_STATE_FIELD,
            STREAM_ID_FIELD,
            PART_NO_FIELD,
            PIPELINE_VERSION_FIELD);

    public static List<QueryField> getFields() {
        return FIELDS;
    }
}
