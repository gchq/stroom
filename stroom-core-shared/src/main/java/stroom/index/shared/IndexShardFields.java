package stroom.index.shared;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
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

    public static final TextField FIELD_NODE = new TextField(FIELD_NAME_NODE);
    public static final DocRefField FIELD_INDEX = DocRefField.byUuid(IndexDoc.DOCUMENT_TYPE, FIELD_NAME_INDEX);
    public static final DocRefField FIELD_INDEX_NAME = DocRefField.byNonUniqueName(
            IndexDoc.DOCUMENT_TYPE, FIELD_NAME_INDEX_NAME);
    public static final TextField FIELD_VOLUME_PATH = new TextField(FIELD_NAME_VOLUME_PATH);
    public static final TextField FIELD_VOLUME_GROUP = new TextField(FIELD_NAME_VOLUME_GROUP);
    public static final TextField FIELD_PARTITION = new TextField(FIELD_NAME_PARTITION);
    public static final IntegerField FIELD_DOC_COUNT = new IntegerField(FIELD_NAME_DOC_COUNT);
    public static final LongField FIELD_FILE_SIZE = new LongField(FIELD_NAME_FILE_SIZE);
    public static final TextField FIELD_STATUS = new TextField(FIELD_NAME_STATUS);
    public static final DateField FIELD_LAST_COMMIT = new DateField(FIELD_NAME_LAST_COMMIT);

    // GWT so no List.of
    private static final List<AbstractField> FIELDS = Arrays.asList(
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

    private static final Map<String, AbstractField> FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(AbstractField::getName, Function.identity()));

    public static List<AbstractField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, AbstractField> getFieldMap() {
        return FIELD_MAP;
    }
}
