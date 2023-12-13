package stroom.security.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.List;

public class FindApiKeysResponse extends ResultPage<ApiKey> {

    public FindApiKeysResponse(final List<ApiKey> values) {
        super(values);
    }

    public FindApiKeysResponse(final List<ApiKey> values, final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
