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

package stroom.pipeline.stepping;

/**
 * Thrown when the persistent stepping IO store cannot satisfy a request, e.g. an IO error or a
 * configured size cap being exceeded.
 */
public class StepDataStoreException extends RuntimeException {

    public StepDataStoreException(final String message) {
        super(message);
    }

    public StepDataStoreException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
