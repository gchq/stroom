package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public final class GeneralTableData extends AskStroomAiData {

    @JsonProperty
    private final List<String> columns;
    @JsonProperty
    private final List<List<String>> rows;

    @JsonCreator
    public GeneralTableData(@JsonProperty("chatMemoryId") final String chatMemoryId,
                            @JsonProperty("columns") final List<String> columns,
                            @JsonProperty("rows") final List<List<String>> rows) {
        super(chatMemoryId);
        this.columns = columns;
        this.rows = rows;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<List<String>> getRows() {
        return rows;
    }
}
