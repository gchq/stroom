package stroom.receive.common;

import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.util.NullSafe;
import stroom.util.cert.CertificateExtractor;
import stroom.util.shared.UserDesc;

import java.util.Collections;

public class GetFeedStatusRequestAdapter {

    private GetFeedStatusRequestAdapter() {

    }

    public static GetFeedStatusRequestV2 mapLegacyRequest(final GetFeedStatusRequest legacyRequest) {
        if (legacyRequest == null) {
            return null;
        } else {
            final String senderDn = legacyRequest.getSenderDn();
            final String subjectId = NullSafe.get(senderDn, CertificateExtractor::extractCNFromDN);

            return new GetFeedStatusRequestV2(
                    legacyRequest.getFeedName(),
                    UserDesc.forSubjectId(subjectId),
                    Collections.emptyMap());
        }
    }
}
