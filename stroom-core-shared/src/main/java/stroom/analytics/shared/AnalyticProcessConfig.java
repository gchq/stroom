package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StreamingAnalyticProcessConfig.class, name = "streaming"),
        @JsonSubTypes.Type(value = TableBuilderAnalyticProcessConfig.class, name = "table_builder"),
        @JsonSubTypes.Type(value = ScheduledQueryAnalyticProcessConfig.class, name = "scheduled_query"),
})
public abstract sealed class AnalyticProcessConfig permits
        StreamingAnalyticProcessConfig,
        TableBuilderAnalyticProcessConfig,
        ScheduledQueryAnalyticProcessConfig {

}
