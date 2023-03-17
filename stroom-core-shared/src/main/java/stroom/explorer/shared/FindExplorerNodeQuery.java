package stroom.explorer.shared;

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindExplorerNodeQuery extends BaseCriteria {
    private final String pattern;
    private final boolean regex;
    private final boolean matchCase;

    @JsonCreator
    public FindExplorerNodeQuery(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                 @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                 @JsonProperty("pattern") final String pattern,
                                 @JsonProperty("regex")final boolean regex,
                                 @JsonProperty("matchCase") final boolean matchCase) {
        super(pageRequest, sortList);
        this.pattern = pattern;
        this.regex = regex;
        this.matchCase = matchCase;
    }

    public String getPattern() {
        return pattern;
    }

    public boolean isRegex() {
        return regex;
    }

    public boolean isMatchCase() {
        return matchCase;
    }
}
