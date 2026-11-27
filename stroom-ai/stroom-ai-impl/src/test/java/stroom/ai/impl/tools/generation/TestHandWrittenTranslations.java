/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.GoldenFiles;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Translations written by hand, never by the generator, read back by the reader ([HandLayouts], [XsltReader]): what
/// a build starting from an existing pipeline inducts (copy_pipeline, then rebuild_mapping). Each fixture under
/// `hand-written/` is a style of hand-written XSLT (a big xsl:choose on an event id, a template per event, named
/// templates with parameters, an imported helper library, a TypeId built from fields, variables and xsl:if chains,
/// priorities and an empty template), with records: the XSLT is run over them, read back, regenerated from the
/// mapping read, and the regenerated XSLT run over each record on its own beside the original, as rebuild_mapping's
/// proof steps them. What was read (the rules, the entries, those kept as xpath, those not read) and every output
/// path that differs are pinned in the fixture's `read.json` (`-DtestUpdateGoldenFiles=true` to accept a change), so a
/// reader change that reads less fails here.
class TestHandWrittenTranslations extends XsltRuns {

    private static final String FIXTURES = "/stroom/ai/impl/tools/generation/hand-written";

    @TestFactory
    Stream<DynamicTest> eachHandWrittenStyleIsReadBackAndProvenAgainstTheOriginal() {
        return fixtures().stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
            final String code = read(dir.resolve("translation.xsl"));
            final String records = read(dir.resolve("records.xml"));
            final Map<String, String> imports = imports(dir);
            // The fixture itself writes valid Events.
            assertThat(invalid(transform(code, records, imports), XSD)).isEmpty();
            final RebuiltMapping rebuilt = XsltReader.rebuild(code, null, null, imports, SCHEMA);
            assertThat(rebuilt.problems()).as("%s", rebuilt.summary()).isEmpty();
            assertThat(rebuilt.mapping()).isNotNull();
            final XsltGenerator.Generated generated = XsltGenerator.generate(rebuilt.mapping(), SCHEMA, "4.1.0");
            assertThat(generated.ok()).as("%s", generated.problems()).isTrue();
            assertThat(invalid(transform(generated.xslt(), records, imports), XSD)).isEmpty();
            final Map<String, Object> summary = rebuilt.summary();
            final Map<String, Object> read = new LinkedHashMap<>();
            read.put("rules", rebuilt.mapping().events().stream()
                    .map(rule -> rule.name() + (rule.drop()
                            ? " (drop)"
                            : ""))
                    .toList());
            final Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("read", listOf(summary.get("entries_new")).size() - listOf(summary.get("kept_as_xpath")).size()
                               - listOf(summary.get("calls_imported_functions")).size());
            counts.put("kept_as_xpath", listOf(summary.get("kept_as_xpath")).size());
            counts.put("calls_imported_functions", listOf(summary.get("calls_imported_functions")).size());
            counts.put("not_read", listOf(summary.get("not_read")).size());
            read.put("counts", counts);
            read.put("kept_as_xpath", listOf(summary.get("kept_as_xpath")));
            read.put("calls_imported_functions", listOf(summary.get("calls_imported_functions")));
            read.put("written_by_hand", listOf(summary.get("written_by_hand")));
            read.put("not_read", listOf(summary.get("not_read")));
            read.put("differences", differences(code, generated.xslt(), records, imports));
            GoldenFiles.assertResourceMatches(FIXTURES + "/" + dir.getFileName() + "/read.json",
                    ToolJson.dumps(read, 1));
        }));
    }

    @TestFactory
    Stream<DynamicTest> anXsltTheGeneratorWroteIsLeftAsItIs() {
        // Its start template applies a mode: nothing is laid out again.
        return ReadingFixtures.golden(".xslt").stream().limit(20).map(file -> DynamicTest.dynamicTest(
                file.getFileName().toString(), () -> {
                    final XmlElement root = XmlParser.parse(read(file), true, false);
                    final String before = XmlSerialiser.toXml(root);
                    assertThat(HandLayouts.layOut(root)).isEmpty();
                    assertThat(XmlSerialiser.toXml(root)).isEqualTo(before);
                }));
    }

    @Test
    void aTemplateAppliedInsideAnEventIsAHelperNotARule() {
        // The records' template applies one of its own to a record's user: that one writes part of an event.
        final String code = "<xsl:stylesheet xmlns=\"event-logging:3\" xmlns:xsl=\"" + XSL + "\" version=\"2.0\" "
                            + "xpath-default-namespace=\"records:2\"><xsl:template match=\"records\"><Events>"
                            + "<xsl:apply-templates/></Events></xsl:template><xsl:template match=\"record\"><Event>"
                            + "<EventSource><xsl:apply-templates select=\"data[@name='user']\"/></EventSource>"
                            + "<EventDetail><TypeId>seen</TypeId></EventDetail></Event></xsl:template>"
                            + "<xsl:template match=\"data[@name='user']\"><User><Id><xsl:value-of select=\"@value\"/>"
                            + "</Id></User></xsl:template></xsl:stylesheet>";
        final XmlElement root = XmlParser.parse(code, true, false);
        assertThat(HandLayouts.layOut(root)).containsExactly(HandLayouts.HAND + "the records' template (applied "
                                                             + "with no mode) read as the record template");
        assertThat(XsltReader.read(code).rules()).extracting(RuleReading::name).containsExactly("seen");
    }

    @TestFactory
    Stream<DynamicTest> aTestOfMoreThanTheValueBeingThereIsAConditionalGuard() {
        return Stream.of(
                        new Object[]{"data[@name='ip']/@value != ''", "data[@name='ip']/@value", false},
                        new Object[]{"normalize-space(*[@key='reason'])", "*[@key='reason']", false},
                        new Object[]{"string-length(@user) > 0", "@user", false},
                        new Object[]{"exists(@user)", "upper-case(@user)", false},
                        new Object[]{"data[@name='user']/@value != '-'", "data[@name='user']/@value", true},
                        new Object[]{"@type = 'login'", "@user", true},
                        new Object[]{null, "@user", false})
                .map(c -> DynamicTest.dynamicTest(c[0] + " around " + c[1], () ->
                        assertThat(HandLayouts.conditionalGuard((String) c[0], (String) c[1])).isEqualTo(c[2])));
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static List<?> listOf(final Object value) {
        return value instanceof final List<?> list
                ? list
                : List.of();
    }

    private static List<Path> fixtures() {
        try {
            final URL url = TestHandWrittenTranslations.class.getResource(FIXTURES);
            try (final Stream<Path> listed = Files.list(Path.of(url.toURI()))) {
                return listed.filter(Files::isDirectory).sorted().toList();
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> imports(final Path dir) {
        final Path folder = dir.resolve("imports");
        final Map<String, String> out = new LinkedHashMap<>();
        if (!Files.isDirectory(folder)) {
            return out;
        }
        try (final Stream<Path> listed = Files.list(folder)) {
            for (final Path file : listed.sorted().toList()) {
                final String name = file.getFileName().toString();
                out.put(name.substring(0, name.lastIndexOf('.')), read(file));
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Each record run on its own through both XSLTs, and every path whose values differ: `record n: path: original
    // -> rebuilt`.
    private static List<String> differences(final String original,
                                            final String rebuilt,
                                            final String records,
                                            final Map<String, String> imports) {
        final List<String> out = new ArrayList<>();
        final XmlElement all = XmlParser.parse(records, true, true);
        final List<XmlElement> each = all.childElements();
        for (int n = 0; n < each.size(); n++) {
            final XmlElement one = XmlElement.create(all.namespace(), all.localName());
            final XmlElement record = each.get(n);
            final XmlElement copy = one.addElement(record.namespace(), record.localName());
            copyElement(record, copy);
            final String text = XmlSerialiser.toXml(one);
            final Map<String, List<String>> before = values(transform(original, text, imports));
            final Map<String, List<String>> after = values(transform(rebuilt, text, imports));
            final List<String> paths = Stream.concat(before.keySet().stream(), after.keySet().stream())
                    .distinct()
                    .sorted()
                    .toList();
            for (final String path : paths) {
                if (!Objects.equals(before.get(path), after.get(path))) {
                    out.add("record " + (n + 1) + ": " + path + ": " + before.get(path) + " -> " + after.get(path));
                }
            }
        }
        return out;
    }

    private static void copyElement(final XmlElement from, final XmlElement to) {
        from.attributes().forEach(a -> to.set(a.namespace(), a.name(), a.value()));
        to.setText(from.text());
        for (final XmlElement child : from.childElements()) {
            copyElement(child, to.addElement(child.namespace(), child.localName()));
        }
    }

    // Every value of the Events by a readable path, Data by its Name, as rebuild_mapping compares them.
    private static Map<String, List<String>> values(final XmlElement events) {
        final Map<String, List<String>> out = new TreeMap<>();
        for (final XmlElement event : events.childElements()) {
            collect(event, event.localName(), out);
        }
        return out;
    }

    private static void collect(final XmlElement element, final String path, final Map<String, List<String>> out) {
        for (final XmlElement.Attribute attribute : element.attributes()) {
            if (!"Name".equals(attribute.name())) {
                out.computeIfAbsent(path + "/@" + attribute.name(), k -> new ArrayList<>()).add(attribute.value());
            }
        }
        final String text = element.text() == null
                ? ""
                : element.text().strip();
        if (!text.isEmpty() || (element.childElements().isEmpty() && element.attributes().isEmpty())) {
            out.computeIfAbsent(path, k -> new ArrayList<>()).add(text);
        }
        for (final XmlElement child : element.childElements()) {
            final String name = child.get("Name") != null
                    ? child.localName() + "[" + child.get("Name") + "]"
                    : child.localName();
            collect(child, path + "/" + name, out);
        }
    }
}
