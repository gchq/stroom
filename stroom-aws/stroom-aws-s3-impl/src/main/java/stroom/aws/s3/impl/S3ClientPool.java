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

import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;

public interface S3ClientPool {

    /**
     * Get a pooled {@link S3Client} corresponding to the supplied {@link S3ClientConfig}.
     * <p>
     * The {@link PooledClient} object must be used within a try-with-resources block or closed after
     * use. Do not call close() on the contained client object though.
     * </p>
     */
    PooledClient<S3Client> getPooledS3Client(final S3ClientConfig config);

    /**
     * Get a pooled {@link S3Client} corresponding to the supplied {@link S3ClientConfig}.
     * <p>
     * The {@link PooledClient} object must be used within a try-with-resources block or closed after
     * use. Do not call close() on the contained client object though.
     * </p>
     */
    PooledClient<S3AsyncClient> getPooledS3AsyncClient(final S3ClientConfig config);


    // --------------------------------------------------------------------------------


    interface PooledClient<T> extends AutoCloseable {

        /**
         * @return The actual client object. Do not call close on the returned client.
         */
        T getClient();

        @Override
        void close();
    }
}
