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


import stroom.editor.client.model.XMLStyler;
import stroom.test.StroomCoreClientTestFileUtil;
import stroom.test.common.ComparisonHelper;
import stroom.util.io.StreamUtil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

class TestXMLStyler {

    private static final String PRE_START_ELEMENT = "<pre class=\"xmlArea-ContentPre\">";
    private static final String PRE_END_ELEMENT = "</pre>";

    @Test
    void testXML() throws IOException {
        test("XML");
    }

    @Test
    void testXSLT() throws IOException {
        test("XSLT");
    }

    @Test
    void testWhitespace() throws IOException {
        test("TestWhitespace");
    }

    @Test
    void testComments() throws IOException {
        test("TestComments");
    }

    @Test
    void testAttributes() throws IOException {
        test("TestAttributes");
    }

    @Test
    void testAttributes2() throws IOException {
        test("TestAttributes2");
    }

    @Test
    void testAttributes3() throws IOException {
        test("TestAttributes3");
    }

    @Test
    void testAttributes4() throws IOException {
        test("TestAttributes4");
    }

    private void test(final String name) {
        // Get the testing directory.
        final Path testDataDir = StroomCoreClientTestFileUtil.getTestResourcesDir();
        final Path testDir = testDataDir.resolve("TestXMLStyler");
        final Path inFile = testDir.resolve(name + ".in");

        final String inXML = StreamUtil.fileToString(inFile);

        // Output styled marked up output.
        final String tmpXML = new XMLStyler().processXML(inXML, true, true, 1, null);
        final Path tmpFile = testDir.resolve(name + ".tmp");
        StreamUtil.stringToFile(tmpXML, tmpFile);

        // Output text.
        final String text = getText(tmpXML);
        final Path textTmpFile = testDir.resolve(name + "_Text.tmp");
        StreamUtil.stringToFile(text, textTmpFile);

        // Compare styled marked up output.
        final Path outFile = testDir.resolve(name + ".out");
        final String outXML = StreamUtil.fileToString(outFile);
        ComparisonHelper.compareStrings(outXML, tmpXML, "The output does not match reference at index: ");

        // Compare text output.
        final Path textOutFile = testDir.resolve(name + "_Text.out");
        final String textOut = StreamUtil.fileToString(textOutFile);
        ComparisonHelper.compareStrings(textOut, text, "The output does not match reference at index: ");
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
