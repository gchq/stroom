package stroom.security.identity.shared;

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindAccountRequest extends ExpressionCriteria {


//    @JsonProperty
//    private final String quickFilter;

    @JsonCreator
    public FindAccountRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                              @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                              @JsonProperty("expression") final ExpressionOperator expression) {
        super(pageRequest, sortList, expression);
//        this.quickFilter = quickFilter;
    }

//    public String getQuickFilter() {
//        return quickFilter;
//    }


    // --------------------------------------------------------------------------------


    public static class Builder extends AbstractBuilder<FindAccountRequest, Builder> {

        @Override
        protected Builder self() {
            return null;
        }

        @Override
        public FindAccountRequest build() {
            return new FindAccountRequest(pageRequest, sortList, expression);
        }
    }
}
