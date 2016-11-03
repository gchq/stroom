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

import java.io.Reader;

import javax.inject.Inject;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.ElementIcons;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.shared.Severity;

@Component
@Scope("prototype")
@ConfigurableElement(type = "BadTextXMLFilterReader", category = Category.READER, roles = {
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.ROLE_READER }, icon = ElementIcons.STREAM)
public class BadTextXMLFilterReaderElement extends AbstractReaderElement {
    private final ErrorReceiver errorReceiver;

    private BadTextXMLFilterReader badTextXMLFilterReader;
    private String[] forceLeafEntities = new String[0];

    @Inject
    public BadTextXMLFilterReaderElement(final ErrorReceiverProxy errorReceiver) {
        this.errorReceiver = errorReceiver;
    }

    @Override
    protected Reader insertFilter(final Reader reader) {
        badTextXMLFilterReader = new BadTextXMLFilterReader(reader, forceLeafEntities);
        return badTextXMLFilterReader;
    }

    @Override
    public void endStream() {
        if (badTextXMLFilterReader.hasModifiedContent()) {
            errorReceiver.log(Severity.WARNING, null, getElementId(), "The content was modified", null);
        }
    }

    @PipelineProperty(description = "Leaf-entities are the identifiers for tags between which non-escaped characters need to be escaped.")
    public void setLeafList(final String leafList) {
        if (leafList != null) {
            forceLeafEntities = leafList.split(" ");
        }
    }
}
