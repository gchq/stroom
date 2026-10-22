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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.validation.XmlTrees.NotWellFormedException;
import stroom.pipeline.xsltfunctions.StroomXsltFunctionLibrary;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.trans.XPathException;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

/// Compiles an XSLT as Stroom compiles it before running it (as its XSLT pool does for an XSLT filter): with a
/// Saxon processor of its own, Stroom's XSLT function library (stroom:lookup(), stroom:format-date() and the
/// rest) registered, and xsl:import and xsl:include resolved by name against Stroom's XSLT documents. So an XSLT is
/// checked with the real functions and the real imports, where the Stroom MCP server compiled it with stubs for
/// them. Seen: an XSLT saved with "Variable kv has not been declared", which only stepping found.
///
/// As the imports are read, nothing is stood in for: an import that can't be read (one that doesn't exist, say)
/// is the error, as it would be when the pipeline runs.
///
/// It only compiles, but compiling runs an XSLT's static expressions (`use-when`, static parameters), so an XSLT
/// with a function or instruction that reads the environment or a resource ([XsltSafety#unsafeInXslt(String)]) is
/// refused here, before it is compiled, whatever its caller has checked.
public class XsltCompileCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XsltCompileCheck.class);
    // The exception an import that can't be read is wrapped in, as Saxon gives its message.
    private static final String IO_EXCEPTION = "java.io.IOException: ";

    private final Supplier<URIResolver> uriResolver;
    private final Consumer<Configuration> functions;

    /// @param uriResolverProvider     Stroom's resolver of XSLT imports by name.
    /// @param functionLibraryProvider Stroom's XSLT function library.
    @Inject
    public XsltCompileCheck(final Provider<URIResolver> uriResolverProvider,
                            final Provider<StroomXsltFunctionLibrary> functionLibraryProvider) {
        this(uriResolverProvider::get, config -> functionLibraryProvider.get().init(config));
    }

    /// For tests: the resolver and the functions given.
    ///
    /// @param uriResolver The resolver of imports.
    /// @param functions   Registers the extension functions on a Saxon configuration.
    XsltCompileCheck(final Supplier<URIResolver> uriResolver, final Consumer<Configuration> functions) {
        this.uriResolver = uriResolver;
        this.functions = functions;
    }

    /// What Saxon reports compiling an XSLT, as Stroom compiles it.
    ///
    /// @param xslt An XSLT's text.
    /// @return The errors, each its code and message (e.g. `XPST0008 Variable kv has not been declared`).
    public Compiled compile(final String xslt) {
        try {
            XmlTrees.parse(xslt);
        } catch (final NotWellFormedException e) {
            return new Compiled(List.of("not well-formed XML: " + e.getMessage()));
        }
        final List<XsltSafety.Unsafe> unsafe = XsltSafety.unsafeInXslt(xslt);
        if (!unsafe.isEmpty()) {
            return new Compiled(unsafe.stream()
                    .map(found -> "not compiled: " + found.what() + ": " + found.why())
                    .toList());
        }
        final Collected collected = new Collected();
        try {
            final Processor processor = new Processor(false);
            functions.accept(processor.getUnderlyingConfiguration());
            final XsltCompiler compiler = processor.newXsltCompiler();
            compiler.setErrorListener(collected);
            compiler.setURIResolver(uriResolver.get());
            compiler.compile(new StreamSource(new StringReader(xslt)));
        } catch (final SaxonApiException | RuntimeException e) {
            LOGGER.debug(() -> "XSLT did not compile: " + e.getMessage(), e);
            if (collected.errors.isEmpty()) {
                collected.errors.add(e.getMessage() == null
                        ? "it does not compile"
                        : e.getMessage().strip().lines().findFirst().orElse("it does not compile"));
            }
        }
        return new Compiled(List.copyOf(collected.errors));
    }


    // --------------------------------------------------------------------------------


    /// What compiling an XSLT found.
    ///
    /// @param errors Its errors, each its code and message, e.g. `XPST0017 Cannot find a 1-argument function named
    ///               Q{http://www.w3.org/2005/xpath-functions}nosuch()`, or an import that can't be read, e.g.
    ///               `Resource not found: "Missing-V1"`.
    public record Compiled(List<String> errors) {

        /// @return True if it compiled without errors.
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    // Collects Saxon's static errors, each its code and message.
    private static final class Collected implements ErrorListener {

        private final List<String> errors = new ArrayList<>();

        @Override
        public void warning(final TransformerException exception) {
            // Warnings are not errors.
        }

        @Override
        public void error(final TransformerException exception) {
            add(exception);
        }

        @Override
        public void fatalError(final TransformerException exception) {
            add(exception);
        }

        private void add(final TransformerException exception) {
            final String code = exception instanceof final XPathException xPathException
                                && xPathException.getErrorCodeLocalPart() != null
                    ? xPathException.getErrorCodeLocalPart() + " "
                    : "";
            final String said = String.valueOf(exception.getMessage());
            final String message = (code + (said.startsWith(IO_EXCEPTION)
                    ? said.substring(IO_EXCEPTION.length())
                    : said)).strip();
            if (!errors.contains(message)) {
                errors.add(message);
            }
        }
    }
}
