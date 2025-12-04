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

package stroom.util.shared;

import java.util.Set;

/**
 * A servlet that is registered on the admin port.
 * All admin servlets are un-authenticated on the basis that access to the admin port
 * will be carefully restricted.
 * Any servlets requiring authentication should instead implement {@link IsServlet}.
 */
public interface IsAdminServlet {

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    Set<String> getPathSpecs();
}
