package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DashboardTableContext.class, name = "dashboardTable"),
        @JsonSubTypes.Type(value = QueryTableContext.class, name = "queryTable"),
        @JsonSubTypes.Type(value = GeneralTableContext.class, name = "generalTable")
})
@Schema(
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "dashboardTable", schema = DashboardTableContext.class),
                @DiscriminatorMapping(value = "queryTable", schema = QueryTableContext.class),
                @DiscriminatorMapping(value = "generalTable", schema = GeneralTableContext.class)})
public abstract sealed class AskStroomAiContext permits DashboardTableContext, QueryTableContext, GeneralTableContext {

    public AskStroomAiContext() {
    }

    /**
     * Returns a human-readable description of this context suitable for display in the UI,
     * chat message headers, and audit event logs.
     */
    public abstract String getDescription();
}
