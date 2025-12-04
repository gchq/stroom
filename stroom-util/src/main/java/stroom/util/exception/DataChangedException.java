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

package stroom.util.exception;

/**
 * Indicates that some data that is being modified has been changed by another
 * thread/node.
 */
public class DataChangedException extends RuntimeException {

    public DataChangedException(final String message) {
        super(message);
    }

    public DataChangedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
