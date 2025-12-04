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

package stroom.pipeline.xml.bug;

import stroom.test.common.ProjectPathUtil;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

public class DataCreator {

    public void create() throws Exception {
        final Path dir = getDir();
        final Path jsonLocation = dir.resolve("input.json");

        final String json = Files.readString(jsonLocation, StandardCharsets.UTF_8);

        // Write a fragment sample.
        final Path outLocationFragment = dir.resolve("sample-fragment-small.xml");
        try (final Writer writer = Files.newBufferedWriter(outLocationFragment, StandardCharsets.UTF_8)) {
            addRows(writer, json);
        }

        // Write a full XML 1.0 file.
        final Path outLocation10 = dir.resolve("sample-small-10.xml");
        try (final Writer writer = Files.newBufferedWriter(outLocation10, StandardCharsets.UTF_8)) {
            writeFull(writer, json, "1.0");
        }

        // Write a full XML 1.1 file.
        final Path outLocation11 = dir.resolve("sample-small-11.xml");
        try (final Writer writer = Files.newBufferedWriter(outLocation11, StandardCharsets.UTF_8)) {
            writeFull(writer, json, "1.1");
        }

        // Rewrite the XML 1.0 file with the XML parser.
        final Path outLocation10Rewrite = dir.resolve("sample-small-10-rewrite.xml");
        rewrite(outLocation10, outLocation10Rewrite);

        // Because we think parsing XML 1.1 is flawed create an XML 1.1 by doing a version replacement on 1.0.
        final Path outLocation11Rewrite = dir.resolve("sample-small-11-rewrite.xml");
        replace(outLocation10Rewrite, outLocation11Rewrite, "version=\"1.0\"", "version=\"1.1\"");

        // Rewrite a deliberately flawed XML 1.1 file with the XML parser.
        final Path outLocation11BadRewrite = dir.resolve("sample-small-11-bad-rewrite.xml");
        rewrite(outLocation11, outLocation11BadRewrite);
    }

    private void writeFull(final Writer writer,
                           final String json,
                           final String xmlVersion) throws Exception {
        writer.write("<?xml version=\"");
        writer.write(xmlVersion);
        writer.write("\" encoding=\"utf-8\"?>");
        writer.write("\n");
        writer.write("<root>");
        writer.write("\n");
        addRows(writer, json);
        writer.write("\n");
        writer.write("</root>");
        writer.write("\n");
    }

    private void addRows(final Writer writer,
                         final String json) throws Exception {
        for (int i = 0; i < 1000; i++) {
            writer.write("<row>");
            writer.write("\n");
            writer.write(json);
            writer.write("\n");
            writer.write("</row>");
            writer.write("\n");
        }
    }

    private void rewrite(final Path inputPath,
                         final Path outputPath) throws Exception {
        try (final Reader input = Files.newBufferedReader(inputPath)) {
            try (final Writer output = Files.newBufferedWriter(outputPath)) {
                final TransformerFactory factory = TransformerFactory.newInstance();
                final TransformerHandler th = ((SAXTransformerFactory) factory).newTransformerHandler();
                th.setResult(new StreamResult(output));
                SaxUtil.parse(input, th, new NullEntityResolver());
            }
        }
    }

    private void replace(final Path inputPath,
                         final Path outputPath,
                         final String match,
                         final String replacement) throws Exception {
        try (final Reader input = Files.newBufferedReader(inputPath)) {
            try (final Writer output = Files.newBufferedWriter(outputPath)) {
                final char[] buffer = new char[4096];
                int len = 0;
                while ((len = input.read(buffer)) != -1) {
                    final String string = new String(buffer, 0, len);
                    final String replaced = string.replace(match, replacement);
                    output.write(replaced);
                }
            }
        }
    }

    public static Path getDir() {
        return ProjectPathUtil
                .resolveDir("stroom-pipeline")
                .resolve("src")
                .resolve("test")
                .resolve("resources")
                .resolve("TestXml11ParserBug");
    }
}
