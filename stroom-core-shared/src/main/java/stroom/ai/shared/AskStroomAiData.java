package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DashboardTableData.class, name = "dashboardTable"),
        @JsonSubTypes.Type(value = QueryTableData.class, name = "queryTable"),
        @JsonSubTypes.Type(value = GeneralTableData.class, name = "generalTable")
})
public abstract sealed class AskStroomAiData permits DashboardTableData, QueryTableData, GeneralTableData {

    @JsonProperty
    private final String chatMemoryId;

    public AskStroomAiData(final String chatMemoryId) {
        this.chatMemoryId = chatMemoryId;
    }

    public String getChatMemoryId() {
        return chatMemoryId;
    }
}
