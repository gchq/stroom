package stroom.receive.common;

import stroom.proxy.feed.remote.GetFeedStatusRequest;
import stroom.proxy.feed.remote.GetFeedStatusRequestV2;
import stroom.util.cert.CertificateExtractor;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserDesc;

import jakarta.inject.Inject;

import java.util.Collections;

public class GetFeedStatusRequestAdapter {

    private final CertificateExtractor certificateExtractor;

    @Inject
    public GetFeedStatusRequestAdapter(final CertificateExtractor certificateExtractor) {
        this.certificateExtractor = certificateExtractor;
    }

    public GetFeedStatusRequestV2 mapLegacyRequest(final GetFeedStatusRequest legacyRequest) {
        if (legacyRequest == null) {
            return null;
        } else {
            final String senderDn = legacyRequest.getSenderDn();
            final String subjectId = NullSafe.get(senderDn, certificateExtractor::extractCNFromDN)
                    .orElse(null);

            return new GetFeedStatusRequestV2(
                    legacyRequest.getFeedName(),
                    UserDesc.forSubjectId(subjectId),
                    Collections.emptyMap());
        }
    }
}
