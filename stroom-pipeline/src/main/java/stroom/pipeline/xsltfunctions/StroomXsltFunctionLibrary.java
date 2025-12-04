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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.shared.data.PipelineReference;

import jakarta.inject.Inject;
import net.sf.saxon.Configuration;

import java.util.List;
import java.util.Set;

public class StroomXsltFunctionLibrary {

    private final Set<StroomExtensionFunctionDefinition> functionDefinitions;

    @Inject
    StroomXsltFunctionLibrary(final Set<StroomExtensionFunctionDefinition> functionDefinitions) {
        this.functionDefinitions = functionDefinitions;
    }

    public void init(final Configuration config) {
        functionDefinitions.forEach(config::registerExtensionFunction);
    }

    public void configure(final ErrorReceiver errorReceiver,
                          final LocationFactory locationFactory,
                          final List<PipelineReference> pipelineReferences) {
        functionDefinitions.forEach(def -> def.configure(errorReceiver, locationFactory, pipelineReferences));
    }

    public void reset() {
        functionDefinitions.forEach(StroomExtensionFunctionDefinition::reset);
    }
}
