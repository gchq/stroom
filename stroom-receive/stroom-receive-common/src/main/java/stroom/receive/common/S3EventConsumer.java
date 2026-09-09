/*
 * Copyright 2016 Crown Copyright
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


/**
 * Consumes S3 put events, i.e. as captured by {@link S3EventService}.
 * <p>
 * It is the responsibility of the implementation to perform attribute map filtering.
 * </p>
 */
@FunctionalInterface
public interface S3EventConsumer {

    /// Implementations should perform attribute map filtering and handle the event.
    /// Both RECEIVE and DROP outcomes should return without exception.
    /// A REJECT outcome should throw a {@link StroomStreamException}
    ///
    /// @param s3CreateEvent The S3 file to process
    void accept(S3CreateEvent s3CreateEvent) throws StroomStreamException;
}
