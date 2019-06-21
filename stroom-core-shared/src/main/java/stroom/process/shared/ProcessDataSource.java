package stroom.process.shared;

import stroom.datasource.api.v2.DataSourceField;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.streamstore.shared.DataSourceUtil;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProcessDataSource {
    private static final List<DataSourceField> FIELDS = new ArrayList<>();
    private static final Map<String, DataSourceField> FIELD_MAP;

    public static final String PIPELINE_UUID = "Pipeline";

    static {
        FIELDS.add(DataSourceUtil.createDocRefField(PIPELINE_UUID, PipelineEntity.ENTITY_TYPE));
        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(DataSourceField::getName, Function.identity()));
    }

    public static List<DataSourceField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, DataSourceField> getFieldMap() {
        return FIELD_MAP;
    }
}