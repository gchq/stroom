package stroom.pipeline.shared;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionTerm.Condition;

import java.util.Arrays;
import java.util.List;

public class ReferenceDataFields {

    public static final DocRef REF_STORE_PSEUDO_DOC_REF = new DocRef(
            "Searchable",
            "Reference Data Store",
            "Reference Data Store (This Node Only)");

    public static final List<Condition> SUPPORTED_STRING_CONDITIONS = Arrays.asList(
            Condition.EQUALS,
            Condition.IN,
            Condition.IN_DICTIONARY);
    public static final List<Condition> SUPPORTED_DOC_REF_CONDITIONS = Arrays.asList(
            Condition.IS_DOC_REF,
            Condition.EQUALS);

    public static final AbstractField KEY_FIELD = new TextField(
            "Key", true, SUPPORTED_STRING_CONDITIONS);
    public static final AbstractField VALUE_FIELD = new TextField(
            "Value", true, SUPPORTED_STRING_CONDITIONS);
    public static final AbstractField VALUE_REF_COUNT_FIELD = new IntegerField(
            "Value Reference Count", false);
    public static final AbstractField MAP_NAME_FIELD = new TextField(
            "Map Name", true, SUPPORTED_STRING_CONDITIONS);
    public static final AbstractField CREATE_TIME_FIELD = new DateField(
            "Create Time", true);
    public static final AbstractField EFFECTIVE_TIME_FIELD = new DateField(
            "Effective Time", true);
    public static final AbstractField LAST_ACCESSED_TIME_FIELD = new DateField(
            "Last Accessed Time", true);
    public static final AbstractField PIPELINE_FIELD = new DocRefField(PipelineDoc.DOCUMENT_TYPE,
            "Reference Loader Pipeline", true, SUPPORTED_DOC_REF_CONDITIONS);
    public static final AbstractField PROCESSING_STATE_FIELD = new TextField(
            "Processing State", false);
    public static final AbstractField STREAM_ID_FIELD = new IdField(
            "Stream ID", false);
    public static final AbstractField PART_NO_FIELD = new LongField(
            "Part Number", false);
    public static final AbstractField PIPELINE_VERSION_FIELD = new TextField(
            "Pipeline Version", false);

    public static final List<AbstractField> FIELDS = Arrays.asList(
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

    public static List<AbstractField> getFields() {
        return FIELDS;
    }
}
