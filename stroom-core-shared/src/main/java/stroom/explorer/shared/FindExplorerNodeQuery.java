package stroom.explorer.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.docref.StringMatch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class FindExplorerNodeQuery extends BaseCriteria {

    @JsonProperty
    private final StringMatch filter;

    @JsonCreator
    public FindExplorerNodeQuery(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                 @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                 @JsonProperty("filter") final StringMatch filter) {
        super(pageRequest, sortList);
        this.filter = filter;
    }

    public StringMatch getFilter() {
        return filter;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FindExplorerNodeQuery)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FindExplorerNodeQuery findExplorerNodeQuery = (FindExplorerNodeQuery) o;
        return Objects.equals(filter, findExplorerNodeQuery.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filter);
    }
}
