/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.query.common.v2.CoprocessorSettingsMap.CoprocessorKey;

import java.util.Map;

public interface ResultHandler {
    void handle(Map<CoprocessorKey, Payload> payloadMap);

    Data getResultStore(String componentId);

    /**
     * Will block until all pending work that the {@link ResultHandler} has is complete.
     */
    void waitForPendingWork() throws InterruptedException;
}
