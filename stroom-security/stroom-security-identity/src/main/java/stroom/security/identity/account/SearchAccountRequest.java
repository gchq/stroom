package stroom.security.identity.account;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class SearchAccountRequest extends BaseCriteria {

    @JsonProperty
    private final String quickFilter;

    @JsonCreator
    public SearchAccountRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                @JsonProperty("quickFilter") final String quickFilter) {
        super(pageRequest, sortList);
        this.quickFilter = quickFilter;
    }

    public String getQuickFilter() {
        return quickFilter;
    }
}