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

import stroom.ai.impl.tools.draft.LocalCheck;
import stroom.ai.impl.tools.draft.SplitterXml;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.pipeline.xml.converter.ds3.ConfigFilter;
import stroom.pipeline.xml.converter.ds3.DS3Parser;
import stroom.pipeline.xml.converter.ds3.RootFactory;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.Serializer;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.SAXParserFactory;

import static org.assertj.core.api.Assertions.assertThat;

/// Found in the audit of 2026-11-26: the tools' verdicts on data_splitter records (the dry run's, as LocalCheck reads
/// them) against the generated XSLT run over the records Stroom's own Data Splitter (DS3Parser) writes from the
/// converter the tools write.
class TestLocalCheckSplitterParity extends XsltRuns {

    private static final SplitterSpec CSV = SplitterSpec.builder("delimited").delimiter(",").header(true)
            .quote("\"").build();

    @Test
    void aDuplicatedHeadingIsTestedAsStroomWritesIt() {
        // Stroom writes a data element for each cell, both named a: data[@name='a']/@value selects both.
        final String sample = "a,a,b\n1,2,3\n";
        assertThat(disagreements(sample, List.of(equals("a", "1"), equals("a", "2"),
                not(equals("a", "2"))))).isEmpty();
    }

    @Test
    void aQuoteInsideAnUnquotedCellIsTestedAsStroomWritesIt() {
        // Stroom's Data Splitter writes 'say "hi' (the closing quote lost); the dry run 'say hi'.
        final String sample = "a,b\nsay \"hi\",2\n";
        assertThat(disagreements(sample, List.of(equals("a", "say \"hi\""), equals("a", "say hi")))).isEmpty();
    }

    private static List<String> disagreements(final String sample, final List<Map<String, Object>> conditions) {
        final TranslationMapping mapping = mapping(conditions);
        final List<Object> records = LocalCheck.sampleRecords(mapping, sample, CSV).records();
        final List<Condition> when = mapping.events().getFirst().when();
        final List<List<Boolean>> xslt = TestLocalCheckJsonParity.xsltVerdicts(mapping, when, stroomXml(sample));
        assertThat(xslt).hasSize(records.size());
        final List<String> out = new ArrayList<>();
        for (int n = 0; n < records.size(); n++) {
            for (int i = 0; i < when.size(); i++) {
                final Boolean tools = LocalCheck.holds(mapping, when.get(i), records.get(n), null);
                if (!xslt.get(n).get(i).equals(tools)) {
                    out.add("record " + n + " " + records.get(n) + " " + ToolJson.dumps(conditions.get(i))
                            + ": tools " + tools + ", processing " + xslt.get(n).get(i));
                }
            }
        }
        return out;
    }

    // The records as Stroom's Data Splitter writes them from the tools' converter.
    private static String stroomXml(final String sample) {
        try {
            final RootFactory factory = new RootFactory();
            final SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(true);
            final XMLReader configReader = parserFactory.newSAXParser().getXMLReader();
            configReader.setContentHandler(new ConfigFilter(factory));
            configReader.parse(new InputSource(new StringReader(SplitterXml.converter(CSV))));
            factory.compile();
            final DS3Parser parser = new DS3Parser(factory.newInstance(new VarMap()), RootFactory.MIN_BUFFER_SIZE,
                    factory.getBufferSize());
            final StringWriter out = new StringWriter();
            final Serializer serializer = new Processor(false).newSerializer(out);
            serializer.setOutputProperty(Serializer.Property.VERSION, "1.1");
            parser.setContentHandler(serializer.getContentHandler());
            parser.parse(new InputSource(new StringReader(sample)));
            return out.toString();
        } catch (final Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static TranslationMapping mapping(final List<Map<String, Object>> conditions) {
        final Map<String, Object> json = new LinkedHashMap<>(Map.of("input", "data_splitter"));
        json.put("unmatched", "skip");
        json.put("log_unmapped_fields", false);
        json.put("common", List.of(
                Map.of("path", "EventTime/TimeCreated", "value", "2026-11-19T10:00:00.000Z"),
                Map.of("path", "EventSource/System/Name", "value", "Audit"),
                Map.of("path", "EventSource/System/Environment", "value", "Test"),
                Map.of("path", "EventSource/Generator", "value", "Audit"),
                Map.of("path", "EventSource/Device/HostName", "value", "host")));
        final List<Map<String, Object>> fields = List.of(Map.of("path", "EventDetail/TypeId", "value", "T"),
                Map.of("path", "EventDetail/Unknown/Data", "data_name", "b", "field", "b"));
        json.put("events", List.of(
                Map.of("name", "all", "when", conditions, "allow_unknown", "a test", "fields", fields),
                Map.of("name", "rest", "allow_unknown", "a test", "fields", fields)));
        return TranslationMapping.fromJson(json);
    }

    private static Map<String, Object> equals(final String field, final String value) {
        return Map.of("field", field, "equals", value);
    }

    private static Map<String, Object> not(final Map<String, Object> condition) {
        final Map<String, Object> out = new LinkedHashMap<>(condition);
        out.put("not", true);
        return out;
    }
}
