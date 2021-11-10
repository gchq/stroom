package stroom.security.identity.token;

import stroom.util.shared.PageResponse;
import stroom.util.shared.QuickFilterResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class TokenResultPage extends QuickFilterResultPage<Token> {

    @JsonCreator
    public TokenResultPage(@JsonProperty("values") final List<Token> values,
                           @JsonProperty("pageResponse") final PageResponse pageResponse,
                           @JsonProperty("qualifiedFilterInput") final String qualifiedFilterInput) {
        super(values, pageResponse, qualifiedFilterInput);
    }
}
