/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.node.api;

import stroom.node.shared.FindNodeCriteria;
import stroom.node.shared.Node;
import stroom.util.entity.FindService;
import stroom.util.shared.BaseResultList;

import java.util.List;

/**
 * <p>
 * Class to manage nodes.
 * </p>
 */
public interface NodeService  {
    String getClusterUrl(String nodeName);

    boolean isEnabled(String nodeName);

    int getPriority(String nodeName);

    List<String> findNodeNames(FindNodeCriteria criteria);
}
