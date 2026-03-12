/*
 * Copyright 2016-2026 Crown Copyright
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


import stroom.receive.common.DataFeedIdentity.IdentityStatus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Delegates for {@link DataFeedKeyService} and {@link CertificateIdentityService}
 */
public class DataFeedIdentityServiceImpl implements DataFeedIdentityService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataFeedIdentityServiceImpl.class);

    private final DataFeedKeyService dataFeedKeyService;
    private final CertificateIdentityService certificateIdentityService;

    @Inject
    public DataFeedIdentityServiceImpl(final DataFeedKeyService dataFeedKeyService,
                                       final CertificateIdentityService certificateIdentityService) {
        this.dataFeedKeyService = dataFeedKeyService;
        this.certificateIdentityService = certificateIdentityService;
    }

    @Override
    public int addDataFeedKeys(final List<DataFeedIdentity> dataFeedIdentities, final Path sourceFile) {
        if (NullSafe.hasItems(dataFeedIdentities) && sourceFile != null) {
            final List<IdentityStatus> statuses = new ArrayList<>();
            dataFeedIdentities.forEach(identity -> {
                final IdentityStatus identityStatus = switch (identity) {
                    case final HashedDataFeedKey hashedDataFeedKey ->
                            dataFeedKeyService.addDataFeedKey(hashedDataFeedKey, sourceFile);
                    case final CertificateIdentity certificateIdentity ->
                            certificateIdentityService.addCertificateIdentity(certificateIdentity, sourceFile);
                };
                statuses.add(identityStatus);
            });
            LOGGER.debug(() -> LogUtil.message("addDataFeedKeys() - dataFeedIdentities.size: {}, statuses summary: {}",
                    dataFeedIdentities.size(), statuses.stream()
                            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))));
            return Math.toIntExact(statuses.stream()
                    .filter(status -> IdentityStatus.ADDED == status)
                    .count());
        } else {
            LOGGER.debug("addDataFeedKeys() - Empty dataFeedIdentities");
            return 0;
        }
    }

    @Override
    public void removeKeysForFile(final Path sourceFile) {
        if (sourceFile != null) {
            try {
                dataFeedKeyService.removeKeysForFile(sourceFile);
            } catch (final Exception e) {
                LOGGER.error("Error adding data feed keys from file " + sourceFile, e);
            }
            try {
                certificateIdentityService.removeKeysForFile(sourceFile);
            } catch (final Exception e) {
                LOGGER.error("Error adding certificate identities from file " + sourceFile, e);
            }
        } else {
            LOGGER.debug("removeKeysForFile() - Null sourceFile");
        }
    }
}
