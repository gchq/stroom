package stroom.cache.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class CacheNamesResponse extends ResultPage<CacheIdentity> {

    public CacheNamesResponse(final List<CacheIdentity> values) {
        super(values);
    }

    @JsonCreator
    public CacheNamesResponse(@JsonProperty("values") final List<CacheIdentity> values,
                              @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
    }

    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }
}
