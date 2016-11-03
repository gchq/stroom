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

import java.util.List;
import java.util.Map;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

public class DefaultPipelineTreeBuilder implements PipelineTreeBuilder {
    @Override
    public DefaultTreeForTreeLayout<PipelineElement> getTree(final PipelineModel model) {
        if (model.getChildMap() == null) {
            return null;
        }

        return build(model.getChildMap());
    }

    protected DefaultTreeForTreeLayout<PipelineElement> build(
            final Map<PipelineElement, List<PipelineElement>> childMap) {
        final DefaultTreeForTreeLayout<PipelineElement> tree = new DefaultTreeForTreeLayout<>(
                PipelineModel.SOURCE_ELEMENT);
        addChildren(tree, PipelineModel.SOURCE_ELEMENT, childMap);
        return tree;
    }

    private void addChildren(final DefaultTreeForTreeLayout<PipelineElement> tree, final PipelineElement parent,
            final Map<PipelineElement, List<PipelineElement>> childMap) {
        final List<PipelineElement> children = childMap.get(parent);
        if (children != null) {
            for (final PipelineElement child : children) {
                tree.addChild(parent, child);
                addChildren(tree, child, childMap);
            }
        }
    }
}
