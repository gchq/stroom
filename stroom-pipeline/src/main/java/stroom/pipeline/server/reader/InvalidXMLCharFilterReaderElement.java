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

package stroom.pipeline.server.reader;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.server.reader.InvalidXMLCharFilterReader.XMLmode;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.Reader;

@Component
@Scope("prototype")
@ConfigurableElement(type = "InvalidXMLCharFilterReader", category = Category.READER, roles = {
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.ROLE_READER }, icon = ElementIcons.STREAM)
public class InvalidXMLCharFilterReaderElement extends AbstractReaderElement {
    private final ErrorReceiver errorReceiver;

    private InvalidXMLCharFilterReader invalidXMLCharFilterReader;
    private XMLmode mode = XMLmode.XML_1_1;

    @Inject
    public InvalidXMLCharFilterReaderElement(final ErrorReceiverProxy errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    @Override
    protected Reader insertFilter(final Reader reader) {
        invalidXMLCharFilterReader = new InvalidXMLCharFilterReader(reader, mode);
        return invalidXMLCharFilterReader;
    }

    @Override
    public void endStream() {
        if (invalidXMLCharFilterReader.hasModifiedContent()) {
            errorReceiver.log(Severity.WARNING, null, getElementId(), "The content was modified", null);
        }
    }

    @PipelineProperty(description = "XML version, e.g. 1.0 or 1.1", defaultValue = "1.1")
    public void setXMLVersion(final String xmlMode) {
        if ("1.0".equals(xmlMode)) {
            mode = XMLmode.XML_1_0;
        }
    }
}
