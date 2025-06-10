/*
 * Copyright 2016 Crown Copyright
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

package stroom.node.api;

import java.util.Objects;

public interface NodeInfo {

    /**
     * @return The name of the local node, i.e. the node this code is running on.
     */
    String getThisNodeName();

    /**
     * @return True if the passed nodeName matches that of the local node, i.e. the
     * node this code is running on.
     */
    default boolean isThisNode(final String nodeName) {
        return Objects.equals(nodeName, getThisNodeName());
    }
}
