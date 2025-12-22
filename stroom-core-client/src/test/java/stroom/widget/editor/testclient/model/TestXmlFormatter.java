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

package stroom.widget.editor.testclient.model;


import stroom.editor.client.model.XmlFormatter;
import stroom.test.StroomCoreClientTestFileUtil;
import stroom.util.io.DiffUtil;
import stroom.util.io.StreamUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

class TestXmlFormatter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestXmlFormatter.class);

    private static final String PRE_START_ELEMENT = "<pre class=\"xmlArea-ContentPre\">";
    private static final String PRE_END_ELEMENT = "</pre>";

    @Test
    void testXML() {
        testFile("XML");
    }

    @Test
    void testXSLT() {
        testFile("XSLT");
    }

    @Test
    void testWhitespace() {
        testFile("TestWhitespace");
    }

    @Test
    void testComments() {
        // Has multiline attrs
        testFile("TestComments", false);
    }

    @Test
    void testAttributes() {
        // Has multiline attrs
        testFile("TestAttributes", false);
    }

    @Test
    void testAttributes2() {
        testFile("TestAttributes2");
    }

    @Test
    void testAttributes3() {
        testFile("TestAttributes3");
    }

    @Test
    void testAttributes4() {
        testFile("TestAttributes4");
    }

    @Test
    void testNonXml() {
        testString("""
                1,2,3
                4,5,6
                7,8,9""", false);
    }

    @Test
    void testNonXmlWithXmlLikeContent1() {
        testString("""
                1,<a>,3
                4,<b>,6
                7,<c>,9""", false);
    }

    @Test
    void testNonXmlWithXmlLikeContent2() {
        testString("""
                1,3,2
                4,6,<OOPS>
                7,9,8""", false);
    }

    @Test
    void testNonXmlWithXmlLikeContent3() {
        testString("""
                1,3,<
                4,6,5
                7,9,8""", false);
    }

    @Test
    void testNonXmlWithXmlLikeContent4() {
        testString("""
                <a b="1">""", false);
    }

    @Test
    void testXml1() {
        testString("""
                <data val="123" />
                <data val="123" />
                <data val="123" />
                <data val="123" />""", true);
    }

    @Test
    void testXml2() {
        testString("""
                <data />
                <data />
                <data />
                <data />""", true);
    }

    @Test
    void testXml3() {
        testString("""
                <data val="123"></data>
                <data val="123"></data>
                <data val="123"></data>
                <data val="123"></data>""", true);
    }

    @Test
    void testXml4() {
        testString("""
                <?xml version="1.0" encoding="UTF-8"?>

                <!-- comment -->""", true);
    }

    @Test
    void testXml5() {
        testString("""
                <evt:root>
                  <a></a>
                  <b />
                </evt:root>""", true);
    }

    @Test
    void testXml6() {
        testString("""
                <a />""", true);
    }

    @Test
    void testXml7() {
        testString("""
                <a attr="x">xxx</a>""", true);
    }

    @Test
    void testXml8() {
        testString("""
                <a attr="x"></a>""", true);
    }

    @Test
    void testXml9() {
        testString("""
                <z:a attr="x" />""", true);
    }

    private void testFile(final String name) {
        testFile(name, true);
    }

    private void testFile(final String name, final boolean removeLineBreaks) {
        // Get the testing directory.
        final Path testDataDir = StroomCoreClientTestFileUtil.getTestResourcesDir();
        final Path testDir = testDataDir.resolve("TestXmlFormatter");
        final Path inFile = testDir.resolve(name + ".in");

        LOGGER.info("Testing file {}", inFile.toAbsolutePath().normalize());

        final String inXML = StreamUtil.fileToString(inFile);

        LOGGER.info("""
                Input:
                -------------------------------------------
                {}
                -------------------------------------------""", inXML);

        Assertions.assertThat(XmlFormatter.looksLikeXml(inXML))
                .isTrue();

        final String formatterInput = removeLineBreaks
                ? inXML.replace("\n", "")
                : inXML;

        // Output styled marked up output.
        // remove all the line breaks to make the formatter re-format it all
        final String tmpXML = new XmlFormatter()
                .format(formatterInput);

        final Path tmpFile = testDir.resolve(name + ".tmp");
        StreamUtil.stringToFile(tmpXML, tmpFile);

        // Compare styled marked up output.
        final Path outFile = testDir.resolve(name + ".out");
//        final String outXML = StreamUtil.fileToString(outFile);
        if (DiffUtil.unifiedDiff(outFile, tmpFile, true, 3)) {
            throw new RuntimeException("Files do not match");
        }
    }

    private void testString(final String input, final boolean isXml) {
        LOGGER.info("""
                Input:
                -------------------------------------------
                {}
                -------------------------------------------""", input);

        Assertions.assertThat(XmlFormatter.looksLikeXml(input))
                .isEqualTo(isXml);

        final String output;

        if (isXml) {
            // remove all the line breaks to make the formatter re-format it all
            output = new XmlFormatter()
                    .format(input.replace("\n", ""));
        } else {
            output = new XmlFormatter()
                    .format(input);
        }

        LOGGER.info("""
                Output:
                -------------------------------------------
                {}
                -------------------------------------------""", output);

        if (DiffUtil.unifiedDiff(input, output, true, 3)) {
            throw new RuntimeException("Input and output do not match");
        }
    }

    public String getText(String html) {
        // Get rid of line numbers etc...
        int index = html.indexOf(PRE_START_ELEMENT);
        if (index != -1) {
            html = html.substring(index + PRE_START_ELEMENT.length());
        }
        index = html.indexOf(PRE_END_ELEMENT);
        if (index != -1) {
            html = html.substring(0, index);
        }

        // Replace <br> with \n.
        html = html.replaceAll("<span[^>]*> </span><br>", "\n");
        html = html.replaceAll("<br>", "\n");

        // Remove all elements.
        html = html.replaceAll("<[^>]*>", "");
        // Now replace entity references to form proper XML.
        html = html.replaceAll("&lt;", "<");
        html = html.replaceAll("&gt;", ">");
        html = html.replaceAll("&nbsp;", " ");
        html = html.replaceAll("&amp;", "&");

        return html;
    }
}
