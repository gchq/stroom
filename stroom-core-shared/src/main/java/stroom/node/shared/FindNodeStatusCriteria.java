package stroom.node.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FindNodeStatusCriteria extends BaseCriteria {

    public FindNodeStatusCriteria() {
        super();
    }

    @JsonCreator
    public FindNodeStatusCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                  @JsonProperty("sortList") final List<CriteriaFieldSort> sortList) {
        super(pageRequest, sortList);
    }
}
