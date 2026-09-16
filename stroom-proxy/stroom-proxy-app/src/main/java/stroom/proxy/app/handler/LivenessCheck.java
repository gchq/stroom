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

/**
 * Whether a destination is reachable: an HTTP {@code GET} of its status URL, a read or touch of a
 * path.
 */
@FunctionalInterface
public interface LivenessCheck {

    /**
     * Returns normally when the destination is live and throws, with the reason as the message,
     * when it is not.
     */
    void check() throws Exception;
}
