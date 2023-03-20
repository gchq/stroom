package stroom.explorer.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class FindExplorerNodeQuery extends BaseCriteria {

    private final String pattern;
    private final boolean matchCase;
    private final boolean regex;

    @JsonCreator
    public FindExplorerNodeQuery(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                 @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                 @JsonProperty("pattern") final String pattern,
                                 @JsonProperty("matchCase") final boolean matchCase,
                                 @JsonProperty("regex") final boolean regex) {
        super(pageRequest, sortList);
        this.pattern = pattern;
        this.regex = regex;
        this.matchCase = matchCase;
    }

    public String getPattern() {
        return pattern;
    }

    public boolean isMatchCase() {
        return matchCase;
    }

    public boolean isRegex() {
        return regex;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FindExplorerNodeQuery that = (FindExplorerNodeQuery) o;
        return regex == that.regex && matchCase == that.matchCase && Objects.equals(pattern, that.pattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), pattern, regex, matchCase);
    }
}
