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

package stroom.pipeline.structure.client.presenter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

public class SimplePipelineTreeBuilder extends DefaultPipelineTreeBuilder {
    private final Set<String> roles;

    public SimplePipelineTreeBuilder() {
        roles = new HashSet<>();
        roles.add(PipelineElementType.VISABILITY_SIMPLE);
    }

    public SimplePipelineTreeBuilder(final String... roles) {
        if (roles == null) {
            this.roles = null;
        } else {
            this.roles = new HashSet<>(Arrays.asList(roles));
        }
    }

    @Override
    public DefaultTreeForTreeLayout<PipelineElement> getTree(final PipelineModel model) {
        if (model.getChildMap() == null) {
            return null;
        }

        final Map<PipelineElement, List<PipelineElement>> simpleChildMap = createSimpleMap(model.getChildMap());
        return build(simpleChildMap);
    }

    private Map<PipelineElement, List<PipelineElement>> createSimpleMap(
            final Map<PipelineElement, List<PipelineElement>> fullMap) {
        final Map<PipelineElement, List<PipelineElement>> simpleChildMap = new HashMap<>();

        // Build a simplified version of the pipeline for stepping mode and
        // simple translation assignment.
        addDescendents(PipelineModel.SOURCE_ELEMENT, fullMap, simpleChildMap);

        // Sort all child lists.
        for (final List<PipelineElement> children : simpleChildMap.values()) {
            Collections.sort(children);
        }

        return simpleChildMap;
    }

    private void addDescendents(final PipelineElement parent,
            final Map<PipelineElement, List<PipelineElement>> childMap,
            final Map<PipelineElement, List<PipelineElement>> tempChildMap) {
        final List<PipelineElement> descendents = new ArrayList<>();
        getDescendentFilters(parent, childMap, descendents);

        if (descendents.size() > 0) {
            tempChildMap.put(parent, descendents);
            for (final PipelineElement descendent : descendents) {
                addDescendents(descendent, childMap, tempChildMap);
            }
        }
    }

    private void getDescendentFilters(final PipelineElement parent,
            final Map<PipelineElement, List<PipelineElement>> childMap, final List<PipelineElement> descendents) {
        final List<PipelineElement> children = childMap.get(parent);
        if (children != null && children.size() > 0) {
            for (final PipelineElement child : children) {
                final PipelineElementType type = child.getElementType();
                if (roles == null) {
                    descendents.add(child);
                } else {
                    boolean hasRole = false;
                    for (final String role : roles) {
                        if (type.hasRole(role)) {
                            hasRole = true;
                            break;
                        }
                    }

                    if (hasRole) {
                        descendents.add(child);
                    } else {
                        getDescendentFilters(child, childMap, descendents);
                    }
                }
            }
        }
    }
}
