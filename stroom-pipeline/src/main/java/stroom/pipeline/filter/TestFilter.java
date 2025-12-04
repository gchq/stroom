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

package stroom.pipeline.filter;

import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.svg.shared.SvgImage;

import jakarta.inject.Inject;
import org.xml.sax.SAXException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A filter used to sample the output produced by SAX events at any point in the
 * XML pipeline. Many instances of this filter can be used.
 * <p>
 * This filter accumulates all the complete documents so they can be asserted against at the end of parsing.
 */
@ConfigurableElement(
        type = "TestFilter",
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE,
                PipelineElementType.VISABILITY_STEPPING},
        icon = SvgImage.PIPELINE_STREAM)
public class TestFilter extends AbstractSamplingFilter {

    private final List<String> outputs;

    @Inject
    public TestFilter(final ErrorReceiverProxy errorReceiverProxy,
                      final LocationFactoryProxy locationFactory) {
        super(errorReceiverProxy, locationFactory);
        outputs = new ArrayList<>();
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        outputs.add(getOutput());
    }

    public List<String> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }
}
