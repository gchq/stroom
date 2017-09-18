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

package stroom.pipeline.server.filter;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.NodeInfo;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.shared.XPathFilter.MatchType;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;
import stroom.util.xml.SAXParserFactoryFactory;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.xpath.XPathConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestXPathFilter extends StroomUnitTest {
    private static final String INPUT = "TestTranslationStepping/1.xml";
    private static final SAXParserFactory PARSER_FACTORY;

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
        PARSER_FACTORY.setNamespaceAware(true);
    }

    @Test
    public void test() throws Exception {
        final Path input = StroomPipelineTestFileUtil.getTestResourcesFile(INPUT);
        final SAXParser parser = PARSER_FACTORY.newSAXParser();
        final XMLReader xmlReader = parser.getXMLReader();
        final SAXEventRecorder steppingFilter = new SAXEventRecorder();
        steppingFilter.clear();
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

    private void testPathExists(final String xPath, final SAXEventRecorder steppingFilter) throws Exception {
        final XPathFilter xPathFilter = new XPathFilter();
        xPathFilter.setXPath(xPath);
        xPathFilter.setMatchType(MatchType.EXISTS);
        Assert.assertTrue(match(xPathFilter, steppingFilter));
    }

    private void testPathEquals(final String xPath, final String value, final SAXEventRecorder steppingFilter)
            throws Exception {
        final XPathFilter xPathFilter = new XPathFilter();
        xPathFilter.setXPath(xPath);
        xPathFilter.setMatchType(MatchType.EQUALS);
        xPathFilter.setValue(value);
        Assert.assertTrue(match(xPathFilter, steppingFilter));
    }

    @SuppressWarnings("unchecked")
    private boolean match(final XPathFilter xPathFilter, final SAXEventRecorder steppingFilter) throws Exception {
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
                    for (int i = 0; i < nodes.size(); i++) {
                        final NodeInfo node = nodes.get(i);
                        if (contains(node.getStringValue(), xPathFilter.getValue(), xPathFilter.isIgnoreCase())) {
                            return true;
                        }
                    }
                    break;

                case EQUALS:
                    for (int i = 0; i < nodes.size(); i++) {
                        final NodeInfo node = nodes.get(i);
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

            if (val.contains(txt)) {
                return true;
            }
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

            if (val.equals(txt)) {
                return true;
            }
        }

        return false;
    }
}
