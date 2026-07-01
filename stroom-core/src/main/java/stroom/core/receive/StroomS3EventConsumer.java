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

package stroom.core.receive;


import stroom.aws.s3.impl.S3ManagerFactory;
import stroom.cache.api.CacheManager;
import stroom.data.store.api.Store;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.S3CreateEvent;
import stroom.receive.common.S3EventConsumer;
import stroom.receive.common.StroomStreamException;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class StroomS3EventConsumer implements S3EventConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomS3EventConsumer.class);

    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final S3ManagerFactory s3ManagerFactory;
    private final Store store;

    @Inject
    public StroomS3EventConsumer(final AttributeMapFilterFactory attributeMapFilterFactory,
                                 final S3ManagerFactory s3ManagerFactory,
                                 final Store store,
                                 final CacheManager cacheManager) {
        this.attributeMapFilterFactory = attributeMapFilterFactory;
        this.s3ManagerFactory = s3ManagerFactory;
        this.store = store;
    }

    @Override
    public void accept(final S3CreateEvent s3CreateEvent) {
        LOGGER.debug("accept() - s3CreateEvent: {}", s3CreateEvent);
        final String regionName = s3CreateEvent.s3Location().regionName();
        final String bucketName = s3CreateEvent.s3Location().bucketName();
        final String objectKey = s3CreateEvent.s3Location().key();


        final AttributeMapFilter attributeMapFilter = attributeMapFilterFactory.create();

        final boolean canReceive;
        try {
            canReceive = attributeMapFilter.filter(s3CreateEvent.attributeMap());
            LOGGER.debug("handleEvent() - s3CreateEvent: {}, isAllowed: {}", s3CreateEvent, canReceive);
            if (canReceive) {
                // TODO
//                store.addExistingS3Source();
            } else {
                LOGGER.debug("handleEvent() - s3CreateEvent: {}, isAllowed: {}", s3CreateEvent, canReceive);
                // TODO log the drop
            }
        } catch (final StroomStreamException e) {
            // TODO log the rejection
            LOGGER.debug("handleEvent() - s3CreateEvent: {}, stroomStreamException: {}",
                    s3CreateEvent, LogUtil.exceptionMessage(e));
        }
    }


    // --------------------------------------------------------------------------------


}
