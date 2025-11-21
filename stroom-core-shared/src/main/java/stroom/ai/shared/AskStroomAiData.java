package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DashboardTableData.class, name = "dashboardTable"),
        @JsonSubTypes.Type(value = QueryTableData.class, name = "queryTable")
})
public sealed interface AskStroomAiData permits DashboardTableData, QueryTableData {

}
