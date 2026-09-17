/*
 * Copyright 2024 Crown Copyright
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
import stroom.util.shared.UserDesc;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.Optional;

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
            // No senderDn, or one we can't extract a CN from, means there is no user to attribute
            // the request to, so userDesc is left null, as it is for a request with no
            // authenticated uploader.
            final UserDesc userDesc = Optional.ofNullable(legacyRequest.getSenderDn())
                    .flatMap(certificateExtractor::extractCNFromDN)
                    .map(UserDesc::forSubjectId)
                    .orElse(null);

            return new GetFeedStatusRequestV2(
                    legacyRequest.getFeedName(),
                    userDesc,
                    Collections.emptyMap());
        }
    }
}
