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

package stroom.pipeline.server.parser;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.util.spring.StroomScope;
import stroom.util.xml.SAXParserFactoryFactory;

import javax.inject.Inject;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;

@Component
@Scope(value = StroomScope.TASK)
@ConfigurableElement(type = "XMLParser", category = Category.PARSER, roles = {PipelineElementType.ROLE_PARSER,
        PipelineElementType.ROLE_HAS_TARGETS, PipelineElementType.VISABILITY_SIMPLE,
        PipelineElementType.VISABILITY_STEPPING, PipelineElementType.ROLE_MUTATOR}, icon = ElementIcons.XML)
public class XMLParser extends AbstractParser {
    private static final SAXParserFactory PARSER_FACTORY;

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
        PARSER_FACTORY.setNamespaceAware(true);
    }

    @Inject
    public XMLParser(final ErrorReceiverProxy errorReceiverProxy, final LocationFactoryProxy locationFactory) {
        super(errorReceiverProxy, locationFactory);
    }

    @Override
    protected XMLReader createReader() throws SAXException {
        SAXParser parser = null;
        try {
            parser = PARSER_FACTORY.newSAXParser();
        } catch (final ParserConfigurationException e) {
            throw ProcessException.wrap(e);
        }
        return parser.getXMLReader();
    }

    @Override
    protected InputSource getInputSource(final InputSource inputSource) throws IOException {
        return inputSource;
    }
}
