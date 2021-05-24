package stroom.explorer.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class QuickFindCriteria extends BaseCriteria {

    @JsonProperty
    private String quickFilterInput;

    @JsonCreator
    public QuickFindCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                             @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                             @JsonProperty("quickFilterInput") final String quickFilterInput) {
        super(pageRequest, sortList);
        this.quickFilterInput = quickFilterInput;
    }

    public QuickFindCriteria(@JsonProperty("quickFilterInput") final String quickFilterInput) {
        super(new PageRequest(), Collections.emptyList());
        this.quickFilterInput = quickFilterInput;
    }

    public String getQuickFilterInput() {
        return quickFilterInput;
    }

    @Override
    public String toString() {
        return "QuickFindCriteria{" +
                "quickFilterInput='" + quickFilterInput + '\'' +
                '}';
    }
}
