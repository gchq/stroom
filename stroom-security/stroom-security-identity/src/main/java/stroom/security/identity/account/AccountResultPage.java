package stroom.security.identity.account;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AccountResultPage extends ResultPage<Account> {

    @JsonProperty
    private final String qualifiedFilterInput;

    @JsonCreator
    public AccountResultPage(@JsonProperty("values") final List<Account> values,
                             @JsonProperty("pageResponse") final PageResponse pageResponse,
                             @JsonProperty("qualifiedFilterInput") final String qualifiedFilterInput) {
        super(values, pageResponse);
        this.qualifiedFilterInput = qualifiedFilterInput;
    }

    public String getQualifiedFilterInput() {
        return qualifiedFilterInput;
    }
}
