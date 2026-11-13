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

package stroom.pipeline.xslt;

import stroom.ai.impl.tools.generation.HelpersXslt;
import stroom.ai.impl.tools.generation.SourceTypeModule;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.pipeline.shared.XsltDoc;

import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XsltCompiler;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

/// An aggregated feed's source type group, as the generator writes it, compiled under Stroom's own resolver of
/// imports ([CustomURIResolver], as it is in Stroom, without a system id of an import's own): the group imports the
/// feed's common module, which imports the helpers, a chain, so Saxon compiles it with no warning of a module
/// imported more than once; a group importing the helpers beside the common module (a diamond) Saxon refuses, as the
/// import checks do before it is saved.
class TestCustomUriResolverWithSourceTypeModules {

    private static final String COMMON_HREF = "uuid='c0000000-0000-0000-0000-000000000001', name='PEGA common'";
    private static final String HELPERS_HREF = "uuid='c0000000-0000-0000-0000-000000000002', name='PEGA helpers'";
    private static final EventSchema SCHEMA = EventSchema.parse(xsd());

    @Test
    void aGroupCommonAndHelpersChainCompilesWithNoModuleImportedTwice() {
        final Compiled compiled = compile(group());

        assertThat(compiled.errors()).isEmpty();
        assertThat(compiled.warnings()).noneSatisfy(warning -> assertThat(warning).contains("more than once"));
    }

    @Test
    void aGroupImportingTheHelpersBesideTheCommonModuleIsRefusedBySaxon() {
        final String diamond = group().replace("<xsl:import href=\"" + COMMON_HREF + "\"/>",
                "<xsl:import href=\"" + COMMON_HREF + "\"/><xsl:import href=\"" + HELPERS_HREF + "\"/>");

        final Compiled compiled = compile(diamond);

        // Saxon takes the helpers imported the second time for a stylesheet importing itself, and refuses it.
        assertThat(compiled.warnings()).anySatisfy(warning -> assertThat(warning).contains("PEGA%20helpers",
                "imported more than once"));
        assertThat(compiled.errors()).anySatisfy(error -> assertThat(error).contains("cannot import itself"));
    }

    // --- the modules, and compiling under Stroom's resolver ---

    // The event-logging schema the tools' tests read, beside their translation world.
    private static String xsd() {
        try (final InputStream in = TestCustomUriResolverWithSourceTypeModules.class.getResourceAsStream(
                "/stroom/mcp/impl/tools/event-logging-v4.1.0.xsd")) {
            return new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Compiled compile(final String xslt) {
        final Map<String, String> docs = Map.of(COMMON_HREF, common(), HELPERS_HREF, HelpersXslt.generate());
        final XsltImports imports = Mockito.mock(XsltImports.class);
        Mockito.when(imports.resolve(anyString())).thenAnswer(inv -> {
            final String href = inv.getArgument(0);
            return docs.containsKey(href)
                    ? XsltImports.Resolution.found(XsltDoc.builder().uuid(href).name(href).data(docs.get(href))
                    .build())
                    : XsltImports.Resolution.notFound();
        });
        final Processor processor = new Processor(false);
        stubs().forEach(processor::registerExtensionFunction);
        final XsltCompiler compiler = processor.newXsltCompiler();
        compiler.setURIResolver(new CustomURIResolver(imports));
        final List<String> warnings = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        compiler.setErrorListener(new ErrorListener() {
            @Override
            public void warning(final TransformerException exception) {
                warnings.add(exception.getMessage());
            }

            @Override
            public void error(final TransformerException exception) {
                errors.add(exception.getMessage());
            }

            @Override
            public void fatalError(final TransformerException exception) {
                errors.add(exception.getMessage());
            }
        });
        try {
            compiler.compile(new StreamSource(new StringReader(xslt)));
        } catch (final SaxonApiException e) {
            errors.add(e.getMessage());
        }
        return new Compiled(warnings, errors);
    }

    // Stroom's functions the modules call, as stand-ins: what is checked is how the imports compile.
    private static List<ExtensionFunction> stubs() {
        final SequenceType string = SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ZERO_OR_ONE);
        final SequenceType any = SequenceType.makeSequenceType(ItemType.ANY_ITEM, OccurrenceIndicator.ZERO_OR_MORE);
        return List.of(stub("log", List.of(string, any)), stub("record-no", List.of()), stub("get", List.of(string)),
                stub("put", List.of(string, any)));
    }

    private static ExtensionFunction stub(final String name, final List<SequenceType> arguments) {
        return new ExtensionFunction() {
            @Override
            public QName getName() {
                return new QName("stroom", name);
            }

            @Override
            public SequenceType getResultType() {
                return SequenceType.makeSequenceType(ItemType.ANY_ITEM, OccurrenceIndicator.ZERO_OR_MORE);
            }

            @Override
            public SequenceType[] getArgumentTypes() {
                return arguments.toArray(new SequenceType[0]);
            }

            @Override
            public XdmValue call(final XdmValue[] values) {
                return XdmEmptySequence.getInstance();
            }
        };
    }

    private static TranslationMapping commonMapping() {
        return TranslationMapping.parse(ToolJson.loads("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventTime/TimeCreated", "field": "timestamp"},
                            {"path": "EventSource/System/Name", "value": "PEGA"},
                            {"path": "EventSource/System/Environment", "value": "Prod"},
                            {"path": "EventSource/Device/HostName", "field": "hostname"}],
                 "events": [{"name": "common"}],
                 "module": {"kind": "common", "name": "PEGA", "mode": "pega_common",
                            "helpers": "%s"}}""".formatted(HELPERS_HREF)));
    }

    private static String common() {
        final XsltGenerator.Generated generated = XsltGenerator.generate(commonMapping(), SCHEMA, "4.1.0");
        assertThat(generated.problems()).isEmpty();
        return generated.xslt();
    }

    private static String group() {
        final TranslationMapping mapping = TranslationMapping.parse(ToolJson.loads("""
                {"input": "json", "json_layout": "lines", "json_fields": ["message"],
                 "common": [{"path": "EventSource/Generator", "value": "Pega"}],
                 "ignored_fields": ["app"],
                 "events": [{"name": "login", "when": [{"field": "message.eventType", "equals": "login"}],
                             "fields": [{"path": "EventDetail/TypeId", "value": "Login"},
                                        {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                                        {"path": "EventDetail/Authenticate/User/Id", "field": "message.operatorId"}]},
                            {"name": "other", "allow_unknown": "for later",
                             "fields": [{"path": "EventDetail/TypeId", "field": "message.eventType"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "operator",
                                         "field": "message.operatorId"}]}]}"""));
        final List<SourceTypeModule.Row> rows = List.of(
                new SourceTypeModule.Row("PegaSecurityEvent", "value", "PegaSecurityEvent"),
                new SourceTypeModule.Row("Mail", "pattern", "^Mail/"));
        final XsltGenerator.Generated generated = XsltGenerator.generate(mapping.withModule(new SourceTypeModule(
                SourceTypeModule.GROUP, "PEGA security", "pega_security", List.of("PegaSecurityEvent"), COMMON_HREF,
                null, SourceTypeModule.commonParts(commonMapping()), null, "sourcetype", rows)), SCHEMA, "4.1.0");
        assertThat(generated.problems()).isEmpty();
        return generated.xslt();
    }


    // --------------------------------------------------------------------------------


    private record Compiled(List<String> warnings, List<String> errors) {

    }
}
