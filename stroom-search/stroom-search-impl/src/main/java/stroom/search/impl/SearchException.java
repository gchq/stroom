/*
 * Copyright 2016-2024 Crown Copyright
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

package stroom.search.impl;

import stroom.util.NullSafe;

public class SearchException extends RuntimeException {

    private static final long serialVersionUID = -482925256715483280L;

    public SearchException(final String message) {
        super(message);
    }

    public SearchException(final String message, final Throwable t) {
        super(message, t);
    }

    public static SearchException wrap(final Throwable t) {
        if (t instanceof SearchException) {
            return (SearchException) t;
        }
        String message = t.getMessage();
        if (NullSafe.isEmptyString(message)) {
            message = t.toString();
        }
        return new SearchException(message, t);
    }
}
