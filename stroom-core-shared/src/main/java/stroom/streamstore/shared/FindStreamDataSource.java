package stroom.streamstore.shared;

import stroom.datasource.api.v2.DataSourceField;
import stroom.feed.shared.Feed;
import stroom.query.api.v2.ExpressionTerm;

import java.util.ArrayList;
import java.util.List;

import static stroom.streamstore.shared.StreamAttributeConstants.SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP;

public class FindStreamDataSource {
    private static final List<DataSourceField> FIELDS = new ArrayList<>();

    public static List<DataSourceField> getFields() {
        return new ArrayList<>(FIELDS);
    }

    public static final String FEED = "Feed";
    public static final String STREAM_TYPE = "Stream Type";
    public static final String STREAM_ID = "Stream Id";
    public static final String PARENT_STREAM_ID = "Parent Stream Id";
    public static final String CREATED = "Creation time";
    public static final String EFFECTIVE = "Effective time";
    public static final String SYSTEM_ATTR_PREFIX = "Stream.";

    static {
        FIELDS.add(new DataSourceField.Builder<>()
                .name(FEED)
                .addConditions(ExpressionTerm.Condition.EQUALS)
                .addConditions(ExpressionTerm.Condition.IN)
                .type(DataSourceField.DataSourceFieldType.FIELD)
                .build());
        FIELDS.add(new DataSourceField.Builder<>()
                .name(STREAM_TYPE)
                .addConditions(ExpressionTerm.Condition.EQUALS)
                .addConditions(ExpressionTerm.Condition.IN)
                .type(DataSourceField.DataSourceFieldType.FIELD)
                .build());
        FIELDS.add(new DataSourceField.Builder<>()
                .name(STREAM_ID)
                .addConditions(ExpressionTerm.Condition.EQUALS)
                .addConditions(ExpressionTerm.Condition.IN)
                .type(DataSourceField.DataSourceFieldType.ID)
                .build());
        FIELDS.add(new DataSourceField.Builder<>()
                .name(PARENT_STREAM_ID)
                .addConditions(ExpressionTerm.Condition.EQUALS)
                .addConditions(ExpressionTerm.Condition.IN)
                .type(DataSourceField.DataSourceFieldType.ID)
                .build());
        FIELDS.add(new DataSourceField.Builder<>()
                .name(CREATED)
                .addConditions(ExpressionTerm.Condition.BETWEEN)
                .type(DataSourceField.DataSourceFieldType.DATE_FIELD)
                .build());
        FIELDS.add(new DataSourceField.Builder<>()
                .name(EFFECTIVE)
                .addConditions(ExpressionTerm.Condition.BETWEEN)
                .type(DataSourceField.DataSourceFieldType.DATE_FIELD)
                .build());
        SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP.keySet().forEach(streamAttributeKey -> {
            FIELDS.add(new DataSourceField.Builder<>()
                    .name(SYSTEM_ATTR_PREFIX + streamAttributeKey)
                    .addConditions(ExpressionTerm.Condition.EQUALS)
                    .addConditions(ExpressionTerm.Condition.GREATER_THAN_OR_EQUAL_TO)
                    .addConditions(ExpressionTerm.Condition.GREATER_THAN)
                    .addConditions(ExpressionTerm.Condition.LESS_THAN)
                    .addConditions(ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO)
                    .addConditions(ExpressionTerm.Condition.BETWEEN)
                    .type(DataSourceField.DataSourceFieldType.FIELD)
                    .build());
        });
    }
}
