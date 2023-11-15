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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QueryHelpRequest extends BaseCriteria {

    @JsonProperty
    private final String query;
    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String parentPath;
    @JsonProperty
    private final StringMatch stringMatch;
    @JsonProperty
    private final boolean showAll;

    @JsonCreator
    public QueryHelpRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                            @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                            @JsonProperty("query") final String query,
                            @JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                            @JsonProperty("parentPath") final String parentPath,
                            @JsonProperty("stringMatch") final StringMatch stringMatch,
                            @JsonProperty("showAll") final boolean showAll) {
        super(pageRequest, sortList);
        this.query = query;
        this.dataSourceRef = dataSourceRef;
        this.parentPath = parentPath;
        this.stringMatch = stringMatch;
        this.showAll = showAll;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getQuery() {
        return query;
    }

    public String getParentPath() {
        return parentPath;
    }

    public StringMatch getStringMatch() {
        return stringMatch;
    }

    public boolean isShowAll() {
        return showAll;
    }
}
