package stroom.security.impl.session;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.Collections;
import java.util.List;

public class SessionListResponse extends ResultPage<SessionDetails> {

    @JsonCreator
    public SessionListResponse(@JsonProperty("values") final List<SessionDetails> values,
                               @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }

    public SessionListResponse(final List<SessionDetails> values) {
        super(values, SessionListResponse.createPageResponse(values));
    }

    public static final SessionListResponse empty() {
        return new SessionListResponse(
                Collections.emptyList(),
                SessionListResponse.createPageResponse(Collections.emptyList()));
    }


    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
