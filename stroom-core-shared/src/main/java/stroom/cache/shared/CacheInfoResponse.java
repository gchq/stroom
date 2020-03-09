package stroom.cache.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class CacheInfoResponse extends ResultPage<CacheInfo> {
    public CacheInfoResponse(final List<CacheInfo> values) {
        super(values);
    }

    @JsonCreator
    public CacheInfoResponse(@JsonProperty("values") final List<CacheInfo> values,
                             @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
