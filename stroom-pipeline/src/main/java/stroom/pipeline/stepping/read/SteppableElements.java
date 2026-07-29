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

package stroom.pipeline.stepping.read;

import stroom.pipeline.factory.ElementRegistry;
import stroom.pipeline.factory.ElementRegistryFactory;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which of a pipeline's elements stepping shows the user - the elements that get input/output recorders, one
 * captured chunk per record each.
 * <p>
 * Derived from the element types' {@code VISABILITY_STEPPING} role, which is the same test
 * {@link PipelineFactory} applies when it builds a stepping pipeline: elements without it are linked through
 * rather than wrapped, so they neither appear as a pane nor produce a chunk. Reading the flag here rather than
 * listing types keeps the two definitions from drifting apart.
 * <p>
 * The alternative - and what the planner used to do - is to take the set from the store: only steppable
 * elements are captured, so what has been captured <em>is</em> the steppable set. That is exact for a capture
 * that ran the whole pipeline, and wrong for one that deliberately did not: a capture truncated at the record
 * boundary has captured nothing below it, and a planner reading the set from the store would conclude there
 * was nothing below it to run. The set has to come from the pipeline for the same reason the record count
 * does not: it is a property of the configuration, not of how far a capture happened to get.
 */
@Singleton
public class SteppableElements {

    private final ElementRegistryFactory elementRegistryFactory;

    @Inject
    public SteppableElements(final ElementRegistryFactory elementRegistryFactory) {
        this.elementRegistryFactory = elementRegistryFactory;
    }

    /**
     * @return the ids of the elements stepping would capture, in the order the pipeline data lists them.
     * An element whose type the registry does not know is left out - it cannot be classified, and a
     * misclassified element would be planned for as though it produced chunks it never will. Callers pair
     * this with what the store actually holds, so an unknown type that <i>does</i> get captured is still
     * planned for.
     */
    public Set<String> in(final PipelineData pipelineData) {
        final ElementRegistry registry = elementRegistryFactory.get();
        final Set<String> ids = new LinkedHashSet<>();
        for (final PipelineElement element : pipelineData.getAddedElements()) {
            final PipelineElementType type = registry.getElementType(element.getType());
            if (type != null && type.hasRole(PipelineElementType.VISABILITY_STEPPING)) {
                ids.add(element.getId());
            }
        }
        return ids;
    }
}
