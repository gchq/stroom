package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.List;

@JsonPropertyOrder({"values", "pageResponse"})
@JsonInclude(Include.NON_NULL)
public class UserResultPage extends ResultPage<User> {
    public UserResultPage(final List<User> values) {
        super(values);
    }

    @JsonCreator
    public UserResultPage(@JsonProperty("values") final List<User> values,
                          @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
