/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.handler;

import stroom.proxy.StroomStatusCode;

/**
 * A destination has said a file group will never be accepted as it is: the feed is not set to
 * receive, the policy rejected it, the type is wrong, there is no feed. Retrying cannot help, so
 * the forward stage gives up on the group at once (forward.md F3, F5).
 */
public class Refused extends Exception {

    private final StroomStatusCode status;

    /**
     * @param status The downstream's status, or null where the refusal did not come with one.
     */
    public Refused(final StroomStatusCode status, final String message, final Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public StroomStatusCode getStatus() {
        return status;
    }
}
