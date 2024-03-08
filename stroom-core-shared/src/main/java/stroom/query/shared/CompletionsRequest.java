package stroom.query.shared;

import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class CompletionsRequest extends BaseCriteria {

    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String text;
    @JsonProperty
    private final int row;
    @JsonProperty
    private final int column;
    @JsonProperty
    private final StringMatch stringMatch;
    @JsonProperty
    private final boolean showAll;

    @JsonCreator
    public CompletionsRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                              @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                              @JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                              @JsonProperty("text") final String text,
                              @JsonProperty("row") final int row,
                              @JsonProperty("column") final int column,
                              @JsonProperty("stringMatch") final StringMatch stringMatch,
                              @JsonProperty("showAll") final boolean showAll) {
        super(pageRequest, sortList);
        this.dataSourceRef = dataSourceRef;
        this.text = text;
        this.row = row;
        this.column = column;
        this.stringMatch = stringMatch;
        this.showAll = showAll;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getText() {
        return text;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public StringMatch getStringMatch() {
        return stringMatch;
    }

    public boolean isShowAll() {
        return showAll;
    }
}
