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

package stroom.pipeline.parser;

import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.xml.converter.json.JSONParserFactory;

import javax.inject.Inject;

@ConfigurableElement(type = "JSONParser", category = Category.PARSER, roles = {PipelineElementType.ROLE_PARSER,
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE,
        PipelineElementType.VISABILITY_STEPPING, PipelineElementType.ROLE_MUTATOR}, icon = ElementIcons.JSON)
public class JSONParser extends AbstractParser {
    @Inject
    public JSONParser(final ErrorReceiverProxy errorReceiverProxy,
                      final LocationFactoryProxy locationFactory) {
        super(errorReceiverProxy, locationFactory);
    }

    @Override
    protected XMLReader createReader() throws SAXException {
        return new JSONParserFactory().getParser();
    }
}
