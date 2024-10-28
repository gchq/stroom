package stroom.index.shared;

import stroom.datasource.api.v2.QueryField;
import stroom.util.shared.string.CIKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IndexVolumeFields {

    public static final String INDEX_VOLUME_TYPE = "IndexVolume";
    public static final String FIELD_ID = "Id";
    public static final String FIELD_GROUP_ID = "Group Id";
    public static final String FIELD_NODE_NAME = "Node";
    public static final String FIELD_PATH = "Path";

    private static final List<QueryField> FIELDS = new ArrayList<>();
    private static final Map<CIKey, QueryField> FIELD_MAP;

    public static final QueryField GROUP_ID = QueryField.createId(CIKey.ofStaticKey(FIELD_GROUP_ID), true);
    public static final QueryField NODE_NAME = QueryField.createText(
            CIKey.ofStaticKey(FIELD_NODE_NAME), true);
    public static final QueryField PATH = QueryField.createText(CIKey.ofStaticKey(FIELD_PATH), true);

    // Id's
    public static final QueryField ID = QueryField.createId(FIELD_ID);

    static {
        // Non grouped fields
        FIELDS.add(GROUP_ID);
        FIELDS.add(NODE_NAME);
        FIELDS.add(PATH);

        // Id's
        FIELDS.add(ID);

        FIELD_MAP = FIELDS.stream()
                .collect(Collectors.toMap(
                        QueryField::getFldNameAsCIKey,
                        Function.identity()));
    }

    public static List<QueryField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<CIKey, QueryField> getFieldMap() {
        return FIELD_MAP;
    }
}
