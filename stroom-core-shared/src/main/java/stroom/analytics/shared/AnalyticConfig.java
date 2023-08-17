package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StreamingAnalyticConfig.class, name = "streaming"),
        @JsonSubTypes.Type(value = TableBuilderAnalyticConfig.class, name = "table_builder"),
        @JsonSubTypes.Type(value = ScheduledQueryAnalyticConfig.class, name = "scheduled_query"),
})
public abstract class AnalyticConfig {

}
