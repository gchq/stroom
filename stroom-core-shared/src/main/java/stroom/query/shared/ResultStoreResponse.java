package stroom.query.shared;

import stroom.query.api.v2.ResultStoreInfo;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ResultStoreResponse extends ResultPage<ResultStoreInfo> {

    @JsonPropertyDescription("A list of errors that occurred in running the query")
    @JsonProperty
    private final List<String> errors;

    public ResultStoreResponse(final List<ResultStoreInfo> values) {
        super(values);
        this.errors = null;
    }

    @JsonCreator
    public ResultStoreResponse(@JsonProperty("values") final List<ResultStoreInfo> values,
                               @JsonProperty("errors") final List<String> errors,
                               @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
