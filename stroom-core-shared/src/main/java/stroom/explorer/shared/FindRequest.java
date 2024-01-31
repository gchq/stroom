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
public class FindRequest extends BaseCriteria {

    @JsonProperty
    private final ExplorerTreeFilter filter;

    @JsonCreator
    public FindRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                       @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                       @JsonProperty("filter") final ExplorerTreeFilter filter) {
        super(pageRequest, sortList);
        this.filter = filter;
    }

    public ExplorerTreeFilter getFilter() {
        return filter;
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
        final FindRequest that = (FindRequest) o;
        return Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filter);
    }

    @Override
    public String toString() {
        return "FindCriteria{" +
                "filter=" + filter +
                '}';
    }
}
