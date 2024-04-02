package stroom.security.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.List;

public class FindApiKeysResponse extends ResultPage<HashedApiKey> {

    public FindApiKeysResponse(final List<HashedApiKey> values) {
        super(values);
    }

    public FindApiKeysResponse(final List<HashedApiKey> values, final PageResponse pageResponse) {
        super(values, pageResponse);
    }
}
