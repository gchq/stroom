package stroom.index.shared;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.TextField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IndexVolumeFields {
    public static final String INDEX_VOLUME_TYPE = "IndexVolume";
    public static final String FIELD_ID = "Id";
    public static final String FIELD_GROUP_ID = "Group Id";
    public static final String FIELD_NODE_NAME = "Node Name";

    private static final List<AbstractField> FIELDS = new ArrayList<>();
    private static final Map<String, AbstractField> FIELD_MAP;

    public static final IdField GROUP_ID = new IdField(FIELD_GROUP_ID);
    public static final TextField NODE_NAME = new TextField(FIELD_NODE_NAME);

    // Id's
    public static final IdField ID = new IdField(FIELD_ID);

    static {
        // Non grouped fields
        FIELDS.add(GROUP_ID);
        FIELDS.add(NODE_NAME);

        // Id's
        FIELDS.add(ID);

        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(AbstractField::getName, Function.identity()));
    }

    public static List<AbstractField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, AbstractField> getFieldMap() {
        return FIELD_MAP;
    }
}
