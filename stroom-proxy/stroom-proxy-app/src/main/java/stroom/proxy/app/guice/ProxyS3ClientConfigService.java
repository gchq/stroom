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

package stroom.proxy.app.guice;


import stroom.aws.s3.shared.S3ClientConfig;
import stroom.aws.s3.shared.S3ClientConfigService;
import stroom.proxy.app.ProxyConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Optional;

public class ProxyS3ClientConfigService implements S3ClientConfigService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyS3ClientConfigService.class);

    private final Provider<ProxyConfig> proxyConfigProvider;

    @Inject
    public ProxyS3ClientConfigService(final Provider<ProxyConfig> proxyConfigProvider) {
        this.proxyConfigProvider = proxyConfigProvider;
    }

    @Override
    public Optional<S3ClientConfig> getS3ClientConfig(final String regionName, final String bucketName) {
        // We need to add s3ClientConfig into either ReceiveDataConfig or somewhere else so
        // proxy can connect to it.
        throw new UnsupportedOperationException("Not supported in proxy yet.");
//        final Optional<S3ClientConfig> opS3ClientConfig = NullSafe.stream(
//                        proxyConfigProvider.get().getForwardS3Destinations())
//                .map(ForwardS3Config::getClientConfig)
//                .filter(Objects::nonNull)
//                .filter(s3ClientConfig ->
//                        Objects.equals(regionName, s3ClientConfig.getRegion())
//                        && Objects.equals(bucketName, s3ClientConfig.getBucketName()))
//                .findFirst();
//        LOGGER.debug("getS3ClientConfig, regionName: {}, bucketName: {}, optS3ClientConfig: {}",
//                regionName, bucketName, opS3ClientConfig);
    }
}
