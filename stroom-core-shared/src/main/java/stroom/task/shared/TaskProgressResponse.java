package stroom.task.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskProgressResponse extends ResultPage<TaskProgress> {

    @JsonPropertyDescription("A list of errors that occurred in running the query")
    @JsonProperty
    private final List<String> errors;

    public TaskProgressResponse(final List<TaskProgress> values) {
        super(values);
        this.errors = null;
    }

    @JsonCreator
    public TaskProgressResponse(@JsonProperty("values") final List<TaskProgress> values,
                                @JsonProperty("errors") final List<String> errors,
                                @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
