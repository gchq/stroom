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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Somewhere a file group leaves the proxy: a downstream HTTP endpoint, a directory tree another
 * process collects from, or an object store.
 * <p>
 * A destination is given the path of a complete file group and either accepts it or throws. It
 * never queues, retries or deletes anything: what happens to a group that could not be delivered is
 * the forward stage's decision, made from which of the two exceptions was thrown
 * ({@code designs/stages/forward.md} §3).
 * </p>
 */
public interface Destination {

    String getName();

    /**
     * Where this destination delivers to, for logs and monitoring: a URL, a directory, a bucket.
     */
    String getDescription();

    /**
     * Deliver a file group. Returns only once the destination has accepted it durably: the
     * downstream has answered with a receipt, the directory has been renamed into place, the object
     * store has acknowledged the upload and any notification has been sent.
     * <p>
     * On a filesystem store the path is the committed group itself, which a destination may move;
     * on an object store it is a downloaded copy. Either way the caller deletes what is left.
     * </p>
     *
     * @throws Refused     For a permanent failure: the destination has said this group will never
     *                     be accepted as it is.
     * @throws IOException For a transient failure: everything else.
     */
    void deliver(Path group) throws Refused, IOException;

    /**
     * A check of whether the destination is reachable, where it has one. The forward stage runs it
     * on a schedule and pauses the destination's loop while it fails.
     */
    default Optional<LivenessCheck> livenessCheck() {
        return Optional.empty();
    }
}
