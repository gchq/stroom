package stroom.streamtask.shared;

import stroom.datasource.api.v2.DataSourceField;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.streamstore.shared.DataSourceUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProcessTaskDataSource {
    private static final List<DataSourceField> FIELDS = new ArrayList<>();
    private static final Map<String, DataSourceField> FIELD_MAP;

    public static final String PIPELINE_UUID = "Pipeline";
    public static final String FEED_UUID = "Feed";
    public static final String TASK_STATUS = "Task Status";

    static {
        FIELDS.add(DataSourceUtil.createDocRefField(PIPELINE_UUID, PipelineEntity.ENTITY_TYPE));
        FIELDS.add(DataSourceUtil.createDocRefField(FEED_UUID, Feed.ENTITY_TYPE));
        FIELDS.add(DataSourceUtil.createStringField(TASK_STATUS));
        FIELD_MAP = FIELDS.stream().collect(Collectors.toMap(DataSourceField::getName, Function.identity()));
    }

    public static List<DataSourceField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static Map<String, DataSourceField> getFieldMap() {
        return FIELD_MAP;
    }
}