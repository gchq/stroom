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

package stroom.aws.s3.impl;

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.cache.api.TemplateCache;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

public class S3ManagerFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3ManagerFactory.class);

    private final TemplateCache templateCache;
    private final S3MetaFieldsMapper s3MetaFieldsMapper;
    private final S3ClientPool s3ClientPool;
    private final S3ClientConfigCache s3ClientConfigCache;

    @Inject
    public S3ManagerFactory(final TemplateCache templateCache,
                            final S3MetaFieldsMapper s3MetaFieldsMapper,
                            final S3ClientPool s3ClientPool,
                            final S3ClientConfigCache s3ClientConfigCache) {
        this.templateCache = templateCache;
        this.s3MetaFieldsMapper = s3MetaFieldsMapper;
        this.s3ClientPool = s3ClientPool;
        this.s3ClientConfigCache = s3ClientConfigCache;
    }

    public S3Manager createS3Manager(final S3ClientConfig s3ClientConfig) {
        LOGGER.debug("createS3Manager() - s3ClientConfig: {}", s3ClientConfig);
        return new S3Manager(templateCache, s3ClientConfig, s3MetaFieldsMapper, s3ClientPool);
    }

    public S3Manager createS3Manager(final DocRef s3ConfigRef) {
        LOGGER.debug("createS3Manager() - s3ConfigRef: {}", s3ConfigRef);
        final S3ClientConfig s3ClientConfig = s3ClientConfigCache.get(s3ConfigRef)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No S£ Client Config found with docRef " + s3ConfigRef));
        return createS3Manager(s3ClientConfig);
    }
}
