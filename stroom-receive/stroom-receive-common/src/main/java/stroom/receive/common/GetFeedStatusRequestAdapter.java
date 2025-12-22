/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
