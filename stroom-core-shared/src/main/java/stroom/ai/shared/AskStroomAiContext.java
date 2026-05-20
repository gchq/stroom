package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DashboardTableContext.class, name = "dashboardTable"),
        @JsonSubTypes.Type(value = QueryTableContext.class, name = "queryTable"),
        @JsonSubTypes.Type(value = GeneralTableContext.class, name = "generalTable")
})
public abstract sealed class AskStroomAiContext permits DashboardTableContext, QueryTableContext, GeneralTableContext {

    public AskStroomAiContext() {
    }
}
