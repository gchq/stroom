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
 */

package stroom.pipeline.server.filter;

import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.spring.StroomScope;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;

/**
 * A filter used to sample the output produced by SAX events at any point in the
 * XML pipeline. Many instances of this filter can be used.
 */
@Component
@Scope(StroomScope.TASK)
@Profile(StroomSpringProfiles.TEST)
@ConfigurableElement(type = "TestFilter", roles = {PipelineElementType.ROLE_TARGET,
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE,
        PipelineElementType.VISABILITY_STEPPING}, icon = ElementIcons.STREAM)
public class TestFilter extends AbstractSamplingFilter {
    @Inject
    public TestFilter(final ErrorReceiverProxy errorReceiverProxy, final LocationFactoryProxy locationFactory) {
        super(errorReceiverProxy, locationFactory);
    }
}