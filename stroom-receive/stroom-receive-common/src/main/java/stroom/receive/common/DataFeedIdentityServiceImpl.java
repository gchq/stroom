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


import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DataFeedIdentityServiceImpl implements DataFeedIdentityService {

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
        final Map<Class<?>, List<DataFeedIdentity>> groupedIdentities = NullSafe.stream(dataFeedIdentities)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Object::getClass, Collectors.toList()));

//        groupedIdentities.forEach((clazz, identities) -> {
//            switch (clazz) {
//                case HashedDataFeedKey.class:
//                    dataFeedKeyService.addDataFeedKeys(identities, sourceFile);
//            }
//        });


        // TODO
        return 0;
    }

    @Override
    public void removeKeysForFile(final Path sourceFile) {

    }
}
