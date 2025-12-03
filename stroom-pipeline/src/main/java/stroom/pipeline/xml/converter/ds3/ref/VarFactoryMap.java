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

package stroom.pipeline.xml.converter.ds3.ref;

import stroom.pipeline.xml.converter.ds3.NodeFactory;
import stroom.pipeline.xml.converter.ds3.VarFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VarFactoryMap {
    private Set<String> uniqueIdSet;
    private Map<String, VarFactory> map;

    public VarFactoryMap() {
        uniqueIdSet = new HashSet<>();
        map = new HashMap<>();
    }

    /**
     * Checks node id to ensure uniqueness.
     */
    public void checkUniqueId(final NodeFactory nodeFactory) {
        final String id = nodeFactory.getId();
        if (id != null) {
            if (!uniqueIdSet.add(id)) {
                throw new RuntimeException("Duplicate id: " + id);
            }
        }
    }

    /**
     * Registers a var factory so that it can be linked to group or data
     * factories by id later on.
     */
    public void register(final VarFactory varFactory) {
        final String id = varFactory.getId();
        if (id != null) {
            map.put(id, varFactory);
        }
    }

    /**
     * Used to get a var factory by id that has been registered.
     */
    public VarFactory get(final String id) {
        return map.get(id);
    }
}
