package stroom.security.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.QuickFilterResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class ApiKeyResultPage extends QuickFilterResultPage<HashedApiKey> {

    @JsonCreator
    public ApiKeyResultPage(@JsonProperty("values") final List<HashedApiKey> values,
                            @JsonProperty("pageResponse") final PageResponse pageResponse,
                            @JsonProperty("qualifiedFilterInput") final String qualifiedFilterInput) {
        super(values, pageResponse, qualifiedFilterInput);
    }
}
