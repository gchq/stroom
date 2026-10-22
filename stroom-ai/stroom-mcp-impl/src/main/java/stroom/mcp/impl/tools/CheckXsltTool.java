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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.ai.impl.tools.validation.XmlTrees;
import stroom.ai.impl.tools.validation.XmlTrees.NotWellFormedException;
import stroom.ai.impl.tools.validation.XsltChecks;
import stroom.ai.impl.tools.validation.XsltCompileCheck;
import stroom.ai.impl.tools.validation.XsltSafety;
import stroom.ai.shared.AiToolCategory;
import stroom.docstore.api.DocFinder;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `check_xslt` - checks an XSLT before it is saved or stepped: well-formed, an xsl:stylesheet with a version,
/// only real stroom: functions, expressions that select nothing for want of the input's namespace, event-logging
/// elements the schema has no place for, schema locations Stroom doesn't hold, imports that aren't XSLT documents,
/// and anything that reads the environment, files or the network. Ported from the Stroom MCP server's
/// `tools/validation.py`, on the checks of [XsltChecks] and [XsltSafety].
///
/// Read as the user: the XML schemas and the imported XSLTs are looked for among those the user may view. It
/// compiles nothing (as the Stroom MCP server's didn't): full compilation happens when the pipeline is stepped.
class CheckXsltTool extends AbstractBuiltInTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CheckXsltTool.class);

    static final String NAME = "check_xslt";
    static final String XSLT_ARG = "xslt";
    static final String SCHEMA_VERSION_ARG = "schema_version";
    static final String INPUT_NAMESPACE_ARG = "input_namespace";

    private final HeldSchemas heldSchemas;
    private final Provider<DocFinder> docFinderProvider;
    private final DocumentAccess documentAccess;
    private final XsltCompileCheck compileCheck;

    /// @param heldSchemas       The XML schemas this Stroom holds.
    /// @param docFinderProvider Finds the XSLTs an XSLT imports, by name.
    /// @param documentAccess    Says which of them the user may view.
    /// @param compileCheck      Compiles an XSLT as Stroom compiles it.
    @Inject
    CheckXsltTool(final HeldSchemas heldSchemas,
                  final Provider<DocFinder> docFinderProvider,
                  final DocumentAccess documentAccess,
                  final XsltCompileCheck compileCheck) {
        this.heldSchemas = heldSchemas;
        this.docFinderProvider = docFinderProvider;
        this.documentAccess = documentAccess;
        this.compileCheck = compileCheck;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Check an XSLT before saving or stepping it: well-formed, an xsl:stylesheet with a "
                             + "version, the stroom namespace declared and only real stroom: functions used (an "
                             + "unknown one comes back with the nearest real name), match and select expressions "
                             + "that would select nothing because the input's namespace is not declared, "
                             + "event-logging elements the schema has no place for (e.g. EventDetail/ServerEvent), "
                             + "an xsi:schemaLocation naming a schema this Stroom doesn't hold (file:///, three "
                             + "slashes, say), and xsl:import / xsl:include targets that exist as XSLT documents in "
                             + "Stroom. Calls that read the environment, files or network (doc(), unparsed-text(), "
                             + "environment-variable() and the like), instructions that read or write a document "
                             + "by URI (xsl:result-document, xsl:evaluate, ...) and imports by URL are refused. When "
                             + "all that passes, it is compiled as Stroom compiles it, with Stroom's own functions "
                             + "and its imports, so an undeclared variable or a wrong call is found before stepping.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(XSLT_ARG, "Full XSLT document text.")
                        .addStringProperty(SCHEMA_VERSION_ARG, "Event-logging version the output must follow, e.g. "
                                                               + "'4.1.0'. Defaults to the Version the XSLT writes "
                                                               + "on Events, else the newest this Stroom holds.")
                        .addStringProperty(INPUT_NAMESPACE_ARG, "The namespace of the XSLT's input when known ('' "
                                                                + "for a source's XML in no namespace, whose own "
                                                                + "records/record elements are then not taken for "
                                                                + "a Data Splitter's records:2).")
                        .required(XSLT_ARG)
                        .build())
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String xslt = rawArg(call, XSLT_ARG);
        if (NullSafe.isBlankString(xslt)) {
            return JsonResults.refused("Give xslt: the full XSLT document text.");
        }
        return JsonResults.of(check(xslt, stringArg(call, SCHEMA_VERSION_ARG), rawArg(call, INPUT_NAMESPACE_ARG)));
    }

    /// @param text           An XSLT's text, as sent.
    /// @param schemaVersion  The event-logging version its output must follow, or null.
    /// @param inputNamespace The namespace of its input when known ("" for none), or null.
    /// @return `ok`, `errors` and `warnings`, and for a well-formed XSLT `imports` and `stroom_functions`, as the
    /// Stroom MCP server gave them.
    Map<String, Object> check(final String text, final String schemaVersion, final String inputNamespace) {
        final XsltChecks.Unescaped unescaped = XsltChecks.unescaped(text);
        final String xslt = unescaped.text();
        final Element root;
        try {
            root = XmlTrees.parse(xslt);
        } catch (final NotWellFormedException e) {
            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", false);
            result.put("errors", List.of("Not well-formed XML (line " + e.getLine() + "): " + e.getMessage()));
            result.put("warnings", unescaped.warnings());
            return result;
        }
        final List<String> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>(unescaped.warnings());
        if (!XmlTrees.isXsl(root, "stylesheet") && !XmlTrees.isXsl(root, "transform")) {
            errors.add("Root element must be xsl:stylesheet");
        }
        if (NullSafe.isEmptyString(XmlTrees.attribute(root, "version"))) {
            errors.add("xsl:stylesheet needs a version attribute (Stroom supports 2.0 and 3.0)");
        }
        final XsltChecks.FunctionProblems functions = XsltChecks.functionProblems(xslt, root);
        errors.addAll(functions.errors());
        XsltSafety.unsafeInXslt(xslt).forEach(unsafe -> errors.add(unsafe.why()));
        errors.addAll(XsltChecks.namespaceProblems(root, inputNamespace));
        if (xslt.contains("schemaLocation")) {
            try {
                errors.addAll(heldSchemas.locationProblems(root));
            } catch (final ToolRefusal e) {
                warnings.add("Schema locations not checked: " + e.getMessage());
            }
        }
        warnings.addAll(XsltChecks.unguardedJson(root));
        eventElementProblems(root, xslt, schemaVersion, errors, warnings);
        final List<String> imports = imports(root);
        final List<String> missing = imports.stream()
                .filter(href -> !isXsltDocument(href))
                .toList();
        if (!missing.isEmpty()) {
            errors.add("xsl:import/include targets not found as XSLT documents: " + String.join(", ", missing));
        }
        // Compiled only once the rest passes, so a fault isn't reported twice (e.g. a missing import), and only
        // with imports the user can view.
        if (errors.isEmpty()) {
            compileCheck.compile(xslt).errors().forEach(error -> errors.add("Does not compile: " + error));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", errors.isEmpty());
        result.put("errors", errors);
        result.put("warnings", warnings);
        result.put("imports", imports);
        result.put("stroom_functions", new ArrayList<>(functions.calls()));
        return result;
    }

    // The event-logging elements the XSLT writes where the schema has no place for them, as errors; a warning when
    // the schema can't be read.
    private void eventElementProblems(final Element root,
                                      final String xslt,
                                      final String schemaVersion,
                                      final List<String> errors,
                                      final List<String> warnings) {
        final List<Element> written = XmlTrees.named(root, XsltChecks.EVT, "*");
        if (written.isEmpty()) {
            return;
        }
        final Optional<Element> events = written.stream()
                .filter(element -> "Events".equals(element.getLocalName()))
                .findFirst();
        final EventSchema schema;
        try {
            final String version = NullSafe.isNonBlankString(schemaVersion)
                    ? schemaVersion
                    : events.map(element -> XmlTrees.attribute(element, "Version"))
                            .filter(NullSafe::isNonEmptyString)
                            .orElseGet(() -> heldSchemas.translationVersion(null, xslt));
            schema = heldSchemas.eventSchema(version);
        } catch (final ToolRefusal e) {
            warnings.add("Event-logging element names not checked: " + e.getMessage());
            return;
        }
        errors.addAll(XsltChecks.eventElementProblems(root, schema));
    }

    // The xsl:import and xsl:include hrefs, in document order.
    private static List<String> imports(final Element root) {
        return XmlTrees.descendantsAndSelf(root).stream()
                .filter(element -> XmlTrees.isXsl(element, "import") || XmlTrees.isXsl(element, "include"))
                .map(element -> XmlTrees.attribute(element, "href"))
                .filter(NullSafe::isNonEmptyString)
                .toList();
    }

    // Whether an XSLT document of exactly that name exists that the user may view: Stroom imports by name.
    private boolean isXsltDocument(final String name) {
        try {
            return NullSafe.list(docFinderProvider.get().findByName(XsltDoc.TYPE, name, false)).stream()
                    .filter(docRef -> name.equals(docRef.getName()))
                    .anyMatch(documentAccess::canView);
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to find the XSLT {}", name, e);
            return false;
        }
    }
}
