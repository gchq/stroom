package stroom.processor.shared;

import stroom.datasource.api.v2.ConditionSet;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProcessorFilterFields {

    public static final String PROCESSOR_FILTERS_TYPE = "ProcessorFilters";
    public static final DocRef PROCESSOR_FILTERS_DOC_REF = DocRef.builder()
            .type(PROCESSOR_FILTERS_TYPE)
            .uuid(PROCESSOR_FILTERS_TYPE)
            .name(PROCESSOR_FILTERS_TYPE)
            .build();

    public static final String FIELD_ID = "Id";

    private static final List<QueryField> FIELDS = new ArrayList<>();
    private static final Map<CIKey, QueryField> FIELD_MAP;

    public static final QueryField ID = QueryField.createId(CIKey.ofStaticKey("Processor Filter Id"), true);
    //    public static final QueryField CREATE_USER = QueryField.createText("Processor Filter Create User");
    public static final QueryField OWNER_UUID = QueryField.createText(
            CIKey.ofStaticKey("Processor Filter Owner User UUID"), true);
    public static final QueryField LAST_POLL_MS = QueryField.createLong(
            CIKey.ofStaticKey("Processor Filter Last Poll Ms"), true);
    public static final QueryField PRIORITY = QueryField.createInteger(
            CIKey.ofStaticKey("Processor Filter Priority"), true);
    public static final QueryField ENABLED = QueryField.createBoolean(
            CIKey.ofStaticKey("Processor Filter Enabled"), true);
    public static final QueryField DELETED = QueryField.createBoolean(
            CIKey.ofStaticKey("Processor Filter Deleted"), true);
    public static final QueryField PROCESSOR_ID = QueryField.createId(
            CIKey.ofStaticKey("Processor Id"), true);
    public static final QueryField UUID = QueryField.createText(
            CIKey.ofStaticKey("Processor Filter UUID"), true);
    public static final QueryField RUN_AS_USER = QueryField
            .builder()
            .fldName("Run As User")
            .conditionSet(ConditionSet.RUN_AS_USER)
            .queryable(true)
            .build();

    static {
        FIELDS.add(ID);
        FIELDS.add(OWNER_UUID);
        FIELDS.add(LAST_POLL_MS);
        FIELDS.add(PRIORITY);
        FIELDS.add(ENABLED);
        FIELDS.add(DELETED);
        FIELDS.add(PROCESSOR_ID);
        FIELDS.add(UUID);
        FIELDS.add(RUN_AS_USER);

        FIELD_MAP = FIELDS.stream()
                .collect(Collectors.toMap(QueryField::getFldNameAsCIKey, Function.identity()));
    }

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<CIKey, QueryField> getFieldMap() {
        return FIELD_MAP;
    }
}
