package stroom.authentication.account;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class SearchAccountRequest extends BaseCriteria {
    @JsonProperty
    private final String quickFilter;

    @JsonCreator
    public SearchAccountRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                @JsonProperty("sortList") final List<Sort> sortList,
                                @JsonProperty("quickFilter") final String quickFilter) {
        super(pageRequest, sortList);
        this.quickFilter = quickFilter;
    }

    public String getQuickFilter() {
        return quickFilter;
    }
}
