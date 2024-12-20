package stroom.index.shared;

import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IndexShardFields {

    public static final DocRef INDEX_SHARDS_PSEUDO_DOC_REF = new DocRef(
            "Searchable", "Index Shards", "Index Shards");

    public static final String FIELD_NAME_NODE = "Node";
    public static final String FIELD_NAME_INDEX = "Index";
    public static final String FIELD_NAME_INDEX_NAME = "Index Name";
    public static final String FIELD_NAME_VOLUME_PATH = "Volume Path";
    public static final String FIELD_NAME_VOLUME_GROUP = "Volume Group";
    public static final String FIELD_NAME_PARTITION = "Partition";
    public static final String FIELD_NAME_DOC_COUNT = "Doc Count";
    public static final String FIELD_NAME_FILE_SIZE = "File Size";
    public static final String FIELD_NAME_STATUS = "Status";
    public static final String FIELD_NAME_LAST_COMMIT = "Last Commit";

    public static final QueryField FIELD_NODE = QueryField.createText(FIELD_NAME_NODE);
    public static final QueryField FIELD_INDEX = QueryField
            .createDocRefByUuid(LuceneIndexDoc.TYPE, FIELD_NAME_INDEX);
    public static final QueryField FIELD_INDEX_NAME = QueryField.createDocRefByNonUniqueName(
            LuceneIndexDoc.TYPE, FIELD_NAME_INDEX_NAME);
    public static final QueryField FIELD_VOLUME_PATH = QueryField.createText(FIELD_NAME_VOLUME_PATH);
    public static final QueryField FIELD_VOLUME_GROUP = QueryField.createText(FIELD_NAME_VOLUME_GROUP);
    public static final QueryField FIELD_PARTITION = QueryField.createText(FIELD_NAME_PARTITION);
    public static final QueryField FIELD_DOC_COUNT = QueryField.createInteger(FIELD_NAME_DOC_COUNT);
    public static final QueryField FIELD_FILE_SIZE = QueryField.createLong(FIELD_NAME_FILE_SIZE);
    public static final QueryField FIELD_STATUS = QueryField.createText(FIELD_NAME_STATUS);
    public static final QueryField FIELD_LAST_COMMIT = QueryField.createDate(FIELD_NAME_LAST_COMMIT);

    // GWT so no List.of
    private static final List<QueryField> FIELDS = Arrays.asList(
            FIELD_NODE,
            FIELD_INDEX,
            FIELD_INDEX_NAME,
            FIELD_VOLUME_PATH,
            FIELD_VOLUME_GROUP,
            FIELD_PARTITION,
            FIELD_DOC_COUNT,
            FIELD_FILE_SIZE,
            FIELD_STATUS,
            FIELD_LAST_COMMIT);

    private static final Map<String, QueryField> FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, QueryField> getFieldMap() {
        return FIELD_MAP;
    }
}
