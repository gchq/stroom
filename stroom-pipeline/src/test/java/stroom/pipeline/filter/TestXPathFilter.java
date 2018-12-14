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

package stroom.pipeline.filter;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.NodeInfo;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.shared.XPathFilter.MatchType;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.test.StroomUnitTest;
import stroom.util.xml.SAXParserFactoryFactory;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestXPathFilter extends StroomUnitTest {
    private static final String INPUT = "TestTranslationStepping/1.xml";
    private static final SAXParserFactory PARSER_FACTORY;

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
    }

    @Test
    void test() throws SAXException, IOException, ParserConfigurationException, XPathExpressionException {
        final Path input = StroomPipelineTestFileUtil.getTestResourcesFile(INPUT);
        final SAXParser parser = PARSER_FACTORY.newSAXParser();
        final XMLReader xmlReader = parser.getXMLReader();
        final SAXEventRecorder steppingFilter = new SAXEventRecorder(null, null);
        steppingFilter.clear(null);
        xmlReader.setContentHandler(steppingFilter);

        xmlReader.parse(new InputSource(Files.newBufferedReader(input)));

        testPathExists("/records", steppingFilter);
        testPathExists("records", steppingFilter);
        testPathExists("records/record", steppingFilter);
        testPathExists("records/record/data", steppingFilter);
        testPathExists("records/record/data[@name]", steppingFilter);
        testPathExists("records/record/data[@name = 'FileNo']", steppingFilter);
        testPathExists("records/record/data[@name = 'FileNo' and @value]", steppingFilter);
        testPathExists("records/record/data[@name = 'FileNo' and @value = '1']", steppingFilter);

        testPathExists("records/record", steppingFilter);
        testPathExists("records/record", steppingFilter);

        testPathEquals("records/record/data[@name = 'FileNo']/@value", "1", steppingFilter);
    }

    private void testPathExists(final String xPath, final SAXEventRecorder steppingFilter) throws XPathExpressionException {
        final XPathFilter xPathFilter = new XPathFilter();
        xPathFilter.setXPath(xPath);
        xPathFilter.setMatchType(MatchType.EXISTS);
        assertThat(match(xPathFilter, steppingFilter)).isTrue();
    }

    private void testPathEquals(final String xPath, final String value, final SAXEventRecorder steppingFilter) throws XPathExpressionException {
        final XPathFilter xPathFilter = new XPathFilter();
        xPathFilter.setXPath(xPath);
        xPathFilter.setMatchType(MatchType.EQUALS);
        xPathFilter.setValue(value);
        assertThat(match(xPathFilter, steppingFilter)).isTrue();
    }

    @SuppressWarnings("unchecked")
    private boolean match(final XPathFilter xPathFilter, final SAXEventRecorder steppingFilter) throws XPathExpressionException {
        final Configuration configuration = steppingFilter.getConfiguration();
        final NodeInfo nodeInfo = steppingFilter.getEvents();
        final NamespaceContext namespaceContext = steppingFilter.getNamespaceContext();

        final SAXEventRecorder.CompiledXPathFilter compiledXPathFilter = new SAXEventRecorder.CompiledXPathFilter(
                xPathFilter, configuration, namespaceContext);
        final Object result = compiledXPathFilter.getXPathExpression().evaluate(nodeInfo, XPathConstants.NODESET);

        final List<NodeInfo> nodes = (List<NodeInfo>) result;
        if (nodes.size() > 0) {
            switch (xPathFilter.getMatchType()) {
                case EXISTS:
                    return true;

                case CONTAINS:
                    for (final NodeInfo node : nodes) {
                        if (contains(node.getStringValue(), xPathFilter.getValue(), xPathFilter.isIgnoreCase())) {
                            return true;
                        }
                    }
                    break;

                case EQUALS:
                    for (final NodeInfo node : nodes) {
                        if (equals(node.getStringValue(), xPathFilter.getValue(), xPathFilter.isIgnoreCase())) {
                            return true;
                        }
                    }
                    break;
                case UNIQUE:
                    return true;
            }
        }

        return false;
    }

    private boolean contains(final String value, final String text, final Boolean ignoreCase) {
        if (value != null && text != null) {
            String val = value.trim();
            String txt = text.trim();

            if (ignoreCase != null && ignoreCase) {
                val = val.toLowerCase();
                txt = text.toLowerCase();
            }

            return val.contains(txt);
        }

        return false;
    }

    private boolean equals(final String value, final String text, final Boolean ignoreCase) {
        if (value != null && text != null) {
            String val = value.trim();
            String txt = text.trim();

            if (ignoreCase != null && ignoreCase) {
                val = val.toLowerCase();
                txt = text.toLowerCase();
            }

            return val.equals(txt);
        }

        return false;
    }
}
