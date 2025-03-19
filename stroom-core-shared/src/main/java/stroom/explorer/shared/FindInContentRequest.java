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
public class FindInContentRequest extends BaseCriteria {

    @JsonProperty
    private final StringMatch filter;

    @JsonCreator
    public FindInContentRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
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
        if (!(o instanceof FindInContentRequest)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FindInContentRequest that = (FindInContentRequest) o;
        return Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filter);
    }
}
