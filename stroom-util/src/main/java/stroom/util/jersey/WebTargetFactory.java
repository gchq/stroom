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

package stroom.util.jersey;

import jakarta.ws.rs.client.WebTarget;

/**
 * A factory for creating {@link WebTarget} instances that add the user token to the Authorization
 * header. So only use this for inter-node communication or communication between proxy and stroom.
 */
public interface WebTargetFactory {

    WebTarget create(String url);
}
