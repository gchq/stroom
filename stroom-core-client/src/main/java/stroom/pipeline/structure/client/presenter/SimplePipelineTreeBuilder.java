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

package stroom.pipeline.structure.client.presenter;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        final Map<PipelineElement, List<PipelineElement>> simpleChildMap =
                createSimpleMap(model);
        return build(simpleChildMap);
    }

    private Map<PipelineElement, List<PipelineElement>> createSimpleMap(
            final PipelineModel model) {
        final Map<PipelineElement, List<PipelineElement>> simpleChildMap = new HashMap<>();

        // Build a simplified version of the pipeline for stepping mode and
        // simple translation assignment.
        addDescendants(model, PipelineModel.SOURCE_ELEMENT, model.getChildMap(), simpleChildMap);

        // Sort all child lists.
        for (final List<PipelineElement> children : simpleChildMap.values()) {
            Collections.sort(children);
        }

        return simpleChildMap;
    }

    private void addDescendants(final PipelineModel model,
                                final PipelineElement parent,
                                final Map<PipelineElement, List<PipelineElement>> childMap,
                                final Map<PipelineElement, List<PipelineElement>> tempChildMap) {
        final List<PipelineElement> descendants = new ArrayList<>();
        getDescendantFilters(model, parent, childMap, descendants);

        if (descendants.size() > 0) {
            tempChildMap.put(parent, descendants);
            for (final PipelineElement descendent : descendants) {
                addDescendants(model, descendent, childMap, tempChildMap);
            }
        }
    }

    private void getDescendantFilters(final PipelineModel model,
                                      final PipelineElement parent,
                                      final Map<PipelineElement, List<PipelineElement>> childMap,
                                      final List<PipelineElement> descendants) {
        final List<PipelineElement> children = childMap.get(parent);
        if (children != null && children.size() > 0) {
            for (final PipelineElement child : children) {
                final PipelineElementType type = model.getElementType(child);
                if (roles == null) {
                    descendants.add(child);
                } else {
                    boolean hasRole = false;
                    for (final String role : roles) {
                        if (type.hasRole(role)) {
                            hasRole = true;
                            break;
                        }
                    }

                    if (hasRole) {
                        descendants.add(child);
                    } else {
                        getDescendantFilters(model, child, childMap, descendants);
                    }
                }
            }
        }
    }
}
