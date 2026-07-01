package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public final class GeneralTableContext extends AskStroomAiContext {

    @JsonProperty
    private final String description;
    @JsonProperty
    private final List<String> columns;
    @JsonProperty
    private final List<List<String>> rows;

    @JsonCreator
    public GeneralTableContext(@JsonProperty("description") final String description,
                               @JsonProperty("columns") final List<String> columns,
                               @JsonProperty("rows") final List<List<String>> rows) {
        this.description = description;
        this.columns = columns;
        this.rows = rows;
    }

    @Override
    public String getDescription() {
        if (description != null) {
            return description;
        }
        return "Table";
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }
}
