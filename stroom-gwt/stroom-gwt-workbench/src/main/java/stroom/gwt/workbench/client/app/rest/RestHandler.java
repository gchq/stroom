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

package stroom.gwt.workbench.client.app.rest;

/// Creates the fake reply to a REST request from the request, e.g. one that echoes its body.
@FunctionalInterface
public interface RestHandler {

    /// @param request The request, e.g. to reply depending on its body.
    /// @return The reply, not null.
    RestReply reply(RecordedRequest request);
}
