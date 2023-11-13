package stroom.datasource.api.v2;

import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.StringCriteria;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class FindFieldInfoCriteria extends BaseCriteria {

    public static final String SORT_BY_NAME = "name";

    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String parentPath;
    @JsonProperty
    private final StringMatch stringMatch;

    @JsonCreator
    public FindFieldInfoCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                 @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                 @JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                                 @JsonProperty("parentPath") final String parentPath,
                                 @JsonProperty("stringMatch") final StringMatch stringMatch) {
        super(pageRequest, sortList);
        this.dataSourceRef = dataSourceRef;
        this.parentPath = parentPath;
        this.stringMatch = stringMatch;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getParentPath() {
        return parentPath;
    }

    public StringMatch getStringMatch() {
        return stringMatch;
    }
}
