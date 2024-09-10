package stroom.security.shared;

import stroom.datasource.api.v2.ConditionSet;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DocumentPermissionFields {

    public static final String DOCUMENT_STORE_TYPE = "DocumentStore";
    public static final DocRef DOCUMENT_STORE_DOC_REF = DocRef.builder()
            .type(DOCUMENT_STORE_TYPE)
            .uuid(DOCUMENT_STORE_TYPE)
            .name(DOCUMENT_STORE_TYPE)
            .build();

    private static final List<QueryField> FIELDS = new ArrayList<>();
    private static final Map<CIKey, QueryField> ALL_FIELD_MAP;

    public static final QueryField DOCUMENT = QueryField
            .builder()
            .fldName(CIKey.ofStaticKey("Document"))
            .fldType(FieldType.DOC_REF)
            .conditionSet(ConditionSet.DOC_DOC_IS)
            .queryable(true)
            .build();
    public static final QueryField CHILDREN = QueryField
            .builder()
            .fldName(CIKey.ofStaticKey("Children"))
            .fldType(FieldType.DOC_REF)
            .conditionSet(ConditionSet.DOC_DOC_OF)
            .queryable(true)
            .build();
    public static final QueryField DESCENDANTS = QueryField
            .builder()
            .fldName(CIKey.ofStaticKey("Descendants"))
            .fldType(FieldType.DOC_REF)
            .conditionSet(ConditionSet.DOC_DOC_OF)
            .queryable(true)
            .build();
    public static final QueryField USER = QueryField
            .builder()
            .fldName(CIKey.ofStaticKey("User"))
            .fldType(FieldType.USER_REF)
            .conditionSet(ConditionSet.DOC_USER_IS)
            .queryable(true)
            .build();
    public static final QueryField DOCUMENT_TYPE = QueryField
            .builder()
            .fldName(CIKey.ofStaticKey("DocumentType"))
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();
    public static final QueryField DOCUMENT_NAME = QueryField
            .builder()
            .fldName(CIKey.ofStaticKey("DocumentName"))
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();
    public static final QueryField DOCUMENT_UUID = QueryField
            .builder()
            .fldName(CIKey.ofStaticKey("DocumentUUID"))
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();
    public static final QueryField DOCUMENT_TAG = QueryField
            .builder()
            .fldName(CIKey.ofStaticKey("DocumentTag"))
            .fldType(FieldType.TEXT)
            .conditionSet(ConditionSet.DEFAULT_TEXT)
            .queryable(true)
            .build();

    static {
        FIELDS.add(DOCUMENT);
        FIELDS.add(CHILDREN);
        FIELDS.add(DESCENDANTS);
        FIELDS.add(DOCUMENT_TYPE);
        FIELDS.add(DOCUMENT_NAME);
        FIELDS.add(DOCUMENT_UUID);
        FIELDS.add(DOCUMENT_TAG);
        FIELDS.add(USER);

        ALL_FIELD_MAP = FIELDS.stream()
                .collect(Collectors.toMap(QueryField::getFldNameAsCIKey, Function.identity()));
    }

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<CIKey, QueryField> getAllFieldMap() {
        return ALL_FIELD_MAP;
    }
}
