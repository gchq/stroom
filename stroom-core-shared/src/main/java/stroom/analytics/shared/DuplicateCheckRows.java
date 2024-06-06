package stroom.analytics.shared;

import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DuplicateCheckRows {

    @JsonProperty
    private final List<String> columnNames;
    @JsonProperty
    private final ResultPage<DuplicateCheckRow> resultPage;

    @JsonCreator
    public DuplicateCheckRows(@JsonProperty("columnNames") final List<String> columnNames,
                              @JsonProperty("resultPage") final ResultPage<DuplicateCheckRow> resultPage) {
        this.columnNames = columnNames;
        this.resultPage = resultPage;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public ResultPage<DuplicateCheckRow> getResultPage() {
        return resultPage;
    }
}
