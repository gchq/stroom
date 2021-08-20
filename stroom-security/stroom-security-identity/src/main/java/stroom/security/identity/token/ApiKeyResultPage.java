package stroom.security.identity.token;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class ApiKeyResultPage extends ResultPage<ApiKey> {
    @JsonCreator
    public ApiKeyResultPage(@JsonProperty("values") final List<ApiKey> values,
                            @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
