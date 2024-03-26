package stroom.analytics.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "pageRequest",
        "sortList",
        "executionSchedule"
})
@JsonInclude(Include.NON_NULL)
public class ExecutionHistoryRequest extends BaseCriteria {

    @JsonProperty
    private final ExecutionSchedule executionSchedule;

    @JsonCreator
    public ExecutionHistoryRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                   @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                   @JsonProperty("executionSchedule") final ExecutionSchedule executionSchedule) {
        super(pageRequest, sortList);
        this.executionSchedule = executionSchedule;
    }

    public ExecutionSchedule getExecutionSchedule() {
        return executionSchedule;
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
        final ExecutionHistoryRequest that = (ExecutionHistoryRequest) o;
        return Objects.equals(executionSchedule, that.executionSchedule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), executionSchedule);
    }

    @Override
    public String toString() {
        return "ExecutionHistoryRequest{" +
                "executionSchedule=" + executionSchedule +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private PageRequest pageRequest;
        private List<CriteriaFieldSort> sortList;
        private ExecutionSchedule executionSchedule;

        private Builder() {
        }

        private Builder(final ExecutionHistoryRequest request) {
            this.pageRequest = request.getPageRequest();
            this.sortList = request.getSortList();
            this.executionSchedule = request.executionSchedule;
        }


        public Builder pageRequest(final PageRequest pageRequest) {
            this.pageRequest = pageRequest;
            return this;
        }

        public Builder sortList(final List<CriteriaFieldSort> sortList) {
            this.sortList = sortList;
            return this;
        }

        public Builder executionSchedule(final ExecutionSchedule executionSchedule) {
            this.executionSchedule = executionSchedule;
            return this;
        }

        public ExecutionHistoryRequest build() {
            return new ExecutionHistoryRequest(
                    pageRequest,
                    sortList,
                    executionSchedule);
        }
    }
}
