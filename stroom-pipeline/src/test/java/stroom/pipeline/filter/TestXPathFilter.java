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

import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.shared.XPathFilter.MatchType;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.test.common.TestUtil;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.shared.NullSafe;
import stroom.util.xml.SAXParserFactoryFactory;

import io.vavr.Tuple;
import net.sf.saxon.Configuration;
import net.sf.saxon.om.NodeInfo;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;

class TestXPathFilter extends StroomUnitTest {

    private static final String INPUT = "TestTranslationStepping/1.xml";
    private static final SAXParserFactory PARSER_FACTORY = SAXParserFactoryFactory.newInstance();

    @TestFactory
    Stream<DynamicTest> testXPathFilters()
            throws SAXException, IOException, ParserConfigurationException, XPathExpressionException {

        final Path input = StroomPipelineTestFileUtil.getTestResourcesFile(INPUT);
        final SAXParser parser = PARSER_FACTORY.newSAXParser();
        final XMLReader xmlReader = parser.getXMLReader();
        final SAXEventRecorder steppingFilter = new SAXEventRecorder(null, null);
        steppingFilter.clear(null);
        xmlReader.setContentHandler(steppingFilter);

        xmlReader.parse(new InputSource(Files.newBufferedReader(input)));
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, MatchType.class, String.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final String xPath = testCase.getInput()._1;
                    final MatchType matchType = testCase.getInput()._2;
                    final String value = testCase.getInput()._3;

                    final XPathFilter xPathFilter = new XPathFilter();
                    xPathFilter.setPath(xPath);
                    xPathFilter.setMatchType(matchType);
                    xPathFilter.setValue(value);
                    xPathFilter.setIgnoreCase(true);

                    try {
                        return match(xPathFilter, steppingFilter);
                    } catch (final XPathExpressionException e) {
                        throw new RuntimeException(e);
                    }
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of("/foo", MatchType.EXISTS, null),
                        false)
                .addCase(Tuple.of("/records", MatchType.EXISTS, null),
                        true)
                .addCase(Tuple.of("records", MatchType.EXISTS, null),
                        true)
                .addCase(Tuple.of("records/record", MatchType.EXISTS, null),
                        true)
                .addCase(Tuple.of("records/record/data", MatchType.EXISTS, null),
                        true)
                .addCase(Tuple.of("records/record/data[@name]", MatchType.EXISTS, null),
                        true)
                .addCase(Tuple.of("records/record/data[@name = 'FileNo']", MatchType.EXISTS, null),
                        true)
                .addCase(Tuple.of("records/record/data[@name = 'FileNo' and @value]", MatchType.EXISTS, null),
                        true)
                .addCase(Tuple.of("records/record/data[@name = 'FileNo' and @value = '1']", MatchType.EXISTS, null),
                        true)
                .addCase(Tuple.of("records/record/data[@name = 'FileNo']/@value", MatchType.EQUALS, "1"),
                        true)
                .addCase(Tuple.of(
                                "records/record[10]/data[@name = 'Message']/@value",
                                MatchType.EQUALS,
                                "some message 10"),
                        true)

                .addCase(Tuple.of("records/record[1]/data[@name = 'LineNo']/@value mod 2", MatchType.EQUALS, "1"),
                        true)
                .addCase(Tuple.of("records/record[1]/data[@name = 'LineNo']/@value mod 2", MatchType.NOT_EQUALS, "0"),
                        true)
                .addCase(Tuple.of("records/record[2]/data[@name = 'LineNo']/@value mod 2", MatchType.EQUALS, "0"),
                        true)

                .addCase(Tuple.of("records/record[10]/data[@name = 'Message']/@value", MatchType.CONTAINS, "10"),
                        true)
                .addCase(Tuple.of(
                                "records/record[10]/data[@name = 'Message']/@value",
                                MatchType.CONTAINS,
                                "some message"),
                        true)
                .addCase(
                        Tuple.of("records/record[10]/data[@name = 'Message']/@value", MatchType.CONTAINS, "foo"),
                        false)
                .build();
    }

    @SuppressWarnings("unchecked")
    private boolean match(final XPathFilter xPathFilter, final SAXEventRecorder steppingFilter)
            throws XPathExpressionException {
        final Configuration configuration = steppingFilter.getConfiguration();
        final NodeInfo nodeInfo = steppingFilter.getEvents();
        final NamespaceContext namespaceContext = steppingFilter.getNamespaceContext();

        final SAXEventRecorder.CompiledXPathFilter compiledXPathFilter = new SAXEventRecorder.CompiledXPathFilter(
                xPathFilter, configuration, namespaceContext);
        final Object result = compiledXPathFilter.getXPathExpression().evaluate(nodeInfo, XPathConstants.NODESET);

        final List<Object> objects = (List<Object>) result;
        if (NullSafe.hasItems(objects)) {
            return SAXEventRecorder.isFilterMatch(objects, compiledXPathFilter, 1L, 0);
        }
        return false;
    }
}
