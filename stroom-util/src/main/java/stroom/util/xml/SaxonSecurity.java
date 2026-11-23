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

package stroom.util.xml;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import net.sf.saxon.Configuration;
import net.sf.saxon.TransformerFactoryImpl;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.functions.URIQueryParameters;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.jaxp.SaxonTransformerFactory;
import net.sf.saxon.lib.CollationURIResolver;
import net.sf.saxon.lib.CollectionFinder;
import net.sf.saxon.lib.EnvironmentVariableResolver;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.OutputURIResolver;
import net.sf.saxon.lib.ResourceCollection;
import net.sf.saxon.lib.StandardURIResolver;
import net.sf.saxon.lib.StandardUnparsedTextResolver;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.lib.UnparsedTextURIResolver;
import net.sf.saxon.resource.DirectoryCollection;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.DynamicLoader;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.URIResolver;

/// Creates Saxon processors and configurations that XSLT and XPath written by users cannot use to reach the
/// file system, the network, environment variables or Java system properties.
///
/// All Stroom code must create Saxon objects through this class. A guard test checks for any that don't.
///
/// A secured configuration:
///
/// - refuses `doc()`, `document()`, `unparsed-text()`, `collection()`, `uri-collection()` and
///   `xsl:source-document` for anything the [SaxonPolicy] does not allow, and reads allowed files through
///   their resolved paths;
/// - has no `transform()`, `load-xquery-module()` or `saxon:doc()` functions in XPath or XSLT;
/// - only creates Saxon's own classes from a class name;
/// - refuses `xsl:result-document` (when compiling) and limits `system-property()` to the `xsl:` properties,
///   by turning off Saxon's external functions. Extension functions registered in Java, such as Stroom's own
///   `stroom:` functions, still work;
/// - hides all environment variables;
/// - stops XML that Saxon parses itself, e.g. with `parse-xml()` or `doc()`, from declaring a DOCTYPE or
///   loading external entities or DTDs. A reader given to Saxon by Stroom code keeps its own settings;
/// - stops XSLT, and the XSLT it imports or includes, from loading external entities or DTDs. XSLT may still
///   declare a DOCTYPE with internal entities, e.g. `&nbsp;`.
///
/// Calls to Java methods (`Q{java:...}`) are already refused by Saxon-HE.
public final class SaxonSecurity {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SaxonSecurity.class);

    private static final Pattern QUERY_OPTION_SEPARATOR = Pattern.compile("[;&]");

    // Built-in functions removed from secured configurations, by namespace.
    private static final Map<String, Set<String>> REMOVED_FUNCTIONS = Map.of(
            NamespaceConstant.FN, Set.of("transform", "load-xquery-module"),
            NamespaceConstant.SAXON, Set.of("doc"));

    private SaxonSecurity() {
        // Utility class.
    }

    /// Creates a new Saxon-HE processor secured with the given policy.
    ///
    /// @param policy What XSLT and XPath run by the processor may read.
    /// @return The new processor.
    public static Processor newProcessor(final SaxonPolicy policy) {
        return new Processor(newConfiguration(policy));
    }

    /// Creates a new Saxon-HE processor with none of the restrictions of [#newProcessor(SaxonPolicy)].
    ///
    /// XSLT and XPath run by it can read any file or URL the Stroom process can, write files and read
    /// environment variables. Use it only where an administrator has chosen to turn security off.
    ///
    /// @return The new processor.
    public static Processor newUnsecuredProcessor() {
        LOGGER.debug("Creating an unsecured Saxon processor");
        return new Processor(false);
    }

    /// Creates a new Saxon-HE configuration secured with the given policy.
    ///
    /// @param policy What XSLT and XPath run with the configuration may read.
    /// @return The new configuration.
    public static Configuration newConfiguration(final SaxonPolicy policy) {
        final Configuration configuration = new SecuredConfiguration();
        apply(configuration, policy);
        return configuration;
    }

    /// Creates a new JAXP transformer factory backed by a Saxon-HE configuration secured with the given policy.
    ///
    /// @param policy What XSLT and XPath run by the factory's transformers may read.
    /// @return The new factory.
    public static SaxonTransformerFactory newTransformerFactory(final SaxonPolicy policy) {
        return new TransformerFactoryImpl(newConfiguration(policy));
    }

    @SuppressWarnings("deprecation") // setOutputURIResolver, as there is no replacement in Saxon 9.9.
    private static void apply(final Configuration configuration, final SaxonPolicy policy) {
        Objects.requireNonNull(policy, "policy");

        configuration.setConfigurationProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
        configuration.setConfigurationProperty(Feature.ENVIRONMENT_VARIABLE_RESOLVER, new NoEnvironmentVariables());

        configuration.setURIResolver(new PolicyURIResolver(configuration, policy));
        configuration.setUnparsedTextURIResolver(new PolicyUnparsedTextURIResolver(policy));
        configuration.setCollectionFinder(new PolicyCollectionFinder(policy));
        configuration.setOutputURIResolver(new RefusingOutputURIResolver());
        configuration.setCollationURIResolver(new PolicyCollationURIResolver(configuration.getCollationURIResolver()));
        configuration.setDynamicLoader(new SaxonOnlyDynamicLoader());

    }

    // Restricts each built-in function set in the libraries that has a removed function, looking inside nested
    // library lists too.
    private static void restrictFunctionLibraries(final List<FunctionLibrary> libraries) {
        for (int i = 0; i < libraries.size(); i++) {
            final FunctionLibrary library = libraries.get(i);
            if (library instanceof final FunctionLibraryList libraryList) {
                restrictFunctionLibraries(libraryList.getLibraryList());
            } else if (library instanceof final BuiltInFunctionSet functionSet
                       && !(functionSet instanceof RestrictedFunctionSet)
                       && REMOVED_FUNCTIONS.containsKey(functionSet.getNamespace())) {
                libraries.set(i, new RestrictedFunctionSet(functionSet));
            }
        }
    }

    // Gets the names of the options in the query of a URI, i.e. what is after '?', separated by ';' or '&'.
    // Names are trimmed and lower case.
    private static List<String> getQueryOptionNames(final String uri) {
        final int index = uri.indexOf('?');
        if (index < 0) {
            return List.of();
        }
        return QUERY_OPTION_SEPARATOR.splitAsStream(uri.substring(index + 1))
                .filter(option -> !option.isEmpty())
                .map(option -> option.contains("=")
                        ? option.substring(0, option.indexOf('='))
                        : option)
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    private static String refusedMessage(final String uri) {
        return "Reading '" + uri + "' is not allowed. XSLT and XPath can only read local files below the " +
               "allowed URI prefixes set in the XSLT configuration.";
    }

    // --------------------------------------------------------------------------------


    private static class NoEnvironmentVariables implements EnvironmentVariableResolver {

        @Override
        public Set<String> getAvailableEnvironmentVariables() {
            return Set.of();
        }

        @Override
        public String getEnvironmentVariable(final String name) {
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    // Used for doc(), document(), transform() and xsl:source-document.
    private static class PolicyURIResolver implements URIResolver {

        private final SaxonPolicy policy;
        private final URIResolver delegate;

        PolicyURIResolver(final Configuration configuration, final SaxonPolicy policy) {
            this.policy = policy;
            this.delegate = new StandardURIResolver(configuration);
        }

        @Override
        public Source resolve(final String href, final String base) throws TransformerException {
            final String absoluteUri;
            try {
                absoluteUri = ResolveURI.makeAbsolute(href, base).toString();
            } catch (final URISyntaxException e) {
                throw new TransformerException(refusedMessage(href));
            }
            final Path realPath = policy.resolveAllowed(absoluteUri);
            if (realPath == null) {
                LOGGER.debug("Refused to resolve '{}'", absoluteUri);
                throw new TransformerException(refusedMessage(absoluteUri));
            }
            // Read through the path that was checked.
            return delegate.resolve(realPath.toUri().toString(), null);
        }
    }


    // --------------------------------------------------------------------------------


    // Used for unparsed-text(), unparsed-text-lines() and unparsed-text-available().
    private static class PolicyUnparsedTextURIResolver implements UnparsedTextURIResolver {

        private final SaxonPolicy policy;
        private final UnparsedTextURIResolver delegate = new StandardUnparsedTextResolver();

        PolicyUnparsedTextURIResolver(final SaxonPolicy policy) {
            this.policy = policy;
        }

        @Override
        public Reader resolve(final URI absoluteURI,
                              final String encoding,
                              final Configuration config) throws XPathException {
            final String uri = String.valueOf(absoluteURI);
            final Path realPath = absoluteURI == null
                    ? null
                    : policy.resolveAllowed(uri);
            if (realPath == null) {
                LOGGER.debug("Refused unparsed text '{}'", uri);
                throw new XPathException(refusedMessage(uri));
            }
            // Read through the path that was checked.
            return delegate.resolve(realPath.toUri(), encoding, config);
        }
    }


    // --------------------------------------------------------------------------------


    // Used for collection() and uri-collection().
    private static class PolicyCollectionFinder implements CollectionFinder {

        private static final Set<String> ALLOWED_COLLECTION_OPTIONS = Set.of("select", "recurse", "on-error");

        private final SaxonPolicy policy;

        PolicyCollectionFinder(final SaxonPolicy policy) {
            this.policy = policy;
        }

        @Override
        public ResourceCollection findCollection(final XPathContext context,
                                                 final String collectionURI) throws XPathException {
            // Only directories are supported.
            final Path realDirectory = policy.resolveAllowedDirectory(collectionURI);
            if (realDirectory == null) {
                LOGGER.debug("Refused collection '{}'", collectionURI);
                throw new XPathException(refusedMessage(collectionURI)
                                         + " A collection must be an allowed directory.");
            }
            // Only the supported options are accepted.
            final String query = getQuery(collectionURI);
            for (final String name : getQueryOptionNames(collectionURI)) {
                if (!ALLOWED_COLLECTION_OPTIONS.contains(name)) {
                    LOGGER.debug("Refused collection '{}' with option '{}'", collectionURI, name);
                    throw new XPathException("The collection option '" + name + "' is not allowed. Only "
                                             + String.join(", ", ALLOWED_COLLECTION_OPTIONS.stream().sorted()
                                                     .toList()) + " can be used.");
                }
            }
            // Read through the directory that was checked.
            final String realUri = realDirectory.toUri().toString();
            return new PolicyDirectoryCollection(context.getConfiguration(),
                    realUri,
                    realDirectory.toFile(),
                    new URIQueryParameters(query, context.getConfiguration()),
                    policy);
        }

        private static String getQuery(final String uri) {
            final int index = uri.indexOf('?');
            return index < 0
                    ? ""
                    : uri.substring(index + 1);
        }
    }


    // --------------------------------------------------------------------------------


    // Leaves out any file in a directory collection that the policy does not allow, e.g. one reached through a
    // symbolic link to somewhere else, and gives the real path of each file it keeps. Saxon opens each file from
    // these URIs, so files left out are never opened and the files kept are read through the paths checked.
    private static class PolicyDirectoryCollection extends DirectoryCollection {

        private final SaxonPolicy policy;

        PolicyDirectoryCollection(final Configuration configuration,
                                  final String collectionURI,
                                  final File directory,
                                  final URIQueryParameters parameters,
                                  final SaxonPolicy policy) throws XPathException {
            super(configuration, collectionURI, directory, parameters);
            this.policy = policy;
        }

        @Override
        public Iterator<String> getResourceURIs(final XPathContext context) {
            final Iterator<String> uris = super.getResourceURIs(context);
            final Iterable<String> iterable = () -> uris;
            return StreamSupport.stream(iterable.spliterator(), false)
                    .map(uri -> {
                        final Path realPath = policy.resolveAllowed(uri);
                        if (realPath == null) {
                            LOGGER.debug("Left '{}' out of a collection", uri);
                            return null;
                        }
                        return realPath.toUri().toString();
                    })
                    .filter(Objects::nonNull)
                    .iterator();
        }
    }


    // --------------------------------------------------------------------------------


    // Refuses collation URIs with a class option.
    private static class PolicyCollationURIResolver implements CollationURIResolver {

        private final CollationURIResolver delegate;

        PolicyCollationURIResolver(final CollationURIResolver delegate) {
            // Don't wrap a policy resolver in another, e.g. if apply() is called twice.
            this.delegate = delegate instanceof final PolicyCollationURIResolver policyCollationURIResolver
                    ? policyCollationURIResolver.delegate
                    : delegate;
        }

        @Override
        public StringCollator resolve(final String collationURI,
                                      final Configuration config) throws XPathException {
            if (collationURI != null && getQueryOptionNames(collationURI).contains("class")) {
                LOGGER.debug("Refused collation '{}'", collationURI);
                throw new XPathException("The collation '" + collationURI + "' is not allowed. A collation URI "
                                         + "cannot name a Java class.");
            }
            return delegate.resolve(collationURI, config);
        }
    }


    // --------------------------------------------------------------------------------


    // Saxon creates every class it is given by name through this. Only Saxon's own classes are allowed.
    private static class SaxonOnlyDynamicLoader extends DynamicLoader {

        private static final String SAXON_PACKAGE = "net.sf.saxon.";

        @Override
        public Class getClass(final String className,
                              final Logger traceOut,
                              final ClassLoader classLoader) throws XPathException {
            if (className == null || !className.startsWith(SAXON_PACKAGE)) {
                LOGGER.debug("Refused to load class '{}'", className);
                throw new XPathException("Loading the class '" + className + "' is not allowed.");
            }
            return super.getClass(className, traceOut, classLoader);
        }

        @Override
        public Object getInstance(final String className, final ClassLoader classLoader) throws XPathException {
            return getInstance(className, null, classLoader);
        }

        @Override
        public Object getInstance(final String className,
                                  final Logger traceOut,
                                  final ClassLoader classLoader) throws XPathException {
            // Checked here too in case Saxon ever creates the instance without calling getClass().
            if (className == null || !className.startsWith(SAXON_PACKAGE)) {
                LOGGER.debug("Refused to create class '{}'", className);
                throw new XPathException("Creating the class '" + className + "' is not allowed.");
            }
            return super.getInstance(className, traceOut, classLoader);
        }
    }


    // --------------------------------------------------------------------------------


    // A configuration without the removed functions. Saxon looks every built-in function up through these function
    // sets, so a function left out of them can't be called, referred to (e.g. transform#1) or found with
    // function-lookup().
    private static class SecuredConfiguration extends Configuration {

        private static final int MAX_POOLED_PARSERS = 16;

        // Made when first asked for, as Configuration's constructor may ask before these fields are set.
        private volatile BuiltInFunctionSet xpath31FunctionSet;
        private volatile BuiltInFunctionSet xslt30FunctionSet;
        private volatile BuiltInFunctionSet useWhenFunctionSet;
        private volatile boolean extensionLibrariesRestricted;
        private final Queue<XMLReader> styleParsers = new ConcurrentLinkedQueue<>();
        private final Queue<XMLReader> sourceParsers = new ConcurrentLinkedQueue<>();

        @Override
        public BuiltInFunctionSet getXPath31FunctionSet() {
            if (xpath31FunctionSet == null) {
                xpath31FunctionSet = new RestrictedFunctionSet(super.getXPath31FunctionSet());
            }
            return xpath31FunctionSet;
        }

        @Override
        public BuiltInFunctionSet getXSLT30FunctionSet() {
            if (xslt30FunctionSet == null) {
                xslt30FunctionSet = new RestrictedFunctionSet(super.getXSLT30FunctionSet());
            }
            return xslt30FunctionSet;
        }

        @Override
        public BuiltInFunctionSet getUseWhenFunctionSet() {
            if (useWhenFunctionSet == null) {
                useWhenFunctionSet = new RestrictedFunctionSet(super.getUseWhenFunctionSet());
            }
            return useWhenFunctionSet;
        }

        // Holds Saxon's own extension functions, e.g. saxon:doc(), for both XPath and XSLT. Saxon keeps one list,
        // so restrict it once.
        @Override
        public FunctionLibraryList getBuiltInExtensionLibraryList() {
            final FunctionLibraryList libraryList = super.getBuiltInExtensionLibraryList();
            if (!extensionLibrariesRestricted) {
                synchronized (this) {
                    if (!extensionLibrariesRestricted) {
                        restrictFunctionLibraries(libraryList.getLibraryList());
                        extensionLibrariesRestricted = true;
                    }
                }
            }
            return libraryList;
        }

        // Saxon-HE gives XSLT the built-in functions of a new default configuration rather than these, so the
        // package's function library is restricted too.
        @Override
        public StylesheetPackage makeStylesheetPackage() {
            return new SecuredStylesheetPackage(this);
        }

        // Saxon parses XSLT with its own parse options, not the configuration's, so the parser itself is
        // hardened. XSLT may declare a DOCTYPE for internal entities, but not load anything.
        @Override
        public XMLReader getStyleParser() {
            return takeParser(styleParsers, true);
        }

        @Override
        public void reuseStyleParser(final XMLReader parser) {
            returnParser(styleParsers, parser);
        }

        // Used for everything Saxon parses itself, e.g. with parse-xml() or doc().
        @Override
        public XMLReader getSourceParser() {
            return takeParser(sourceParsers, false);
        }

        @Override
        public void reuseSourceParser(final XMLReader parser) {
            returnParser(sourceParsers, parser);
        }

        @Override
        public XMLReader createXMLParser() {
            return harden(newParser(), false);
        }

        private static XMLReader takeParser(final Queue<XMLReader> pool, final boolean allowDoctype) {
            // The pool is null if Saxon asks for a parser while the configuration is still being constructed.
            final XMLReader pooled = pool == null
                    ? null
                    : pool.poll();
            // Harden a pooled parser again in case anything changed its features while it was in use.
            return harden(Objects.requireNonNullElseGet(pooled, SecuredConfiguration::newParser), allowDoctype);
        }

        private static void returnParser(final Queue<XMLReader> pool, final XMLReader parser) {
            if (pool == null || parser == null || pool.size() >= MAX_POOLED_PARSERS) {
                return;
            }
            try {
                // As Saxon does, so a pooled parser holds nothing from its last use.
                parser.setContentHandler(null);
                parser.setEntityResolver(null);
                parser.setDTDHandler(null);
                parser.setErrorHandler(null);
                parser.setProperty("http://xml.org/sax/properties/lexical-handler", null);
                pool.offer(parser);
            } catch (final SAXException e) {
                LOGGER.debug(() -> LogUtil.message("Not pooling a parser that could not be reset: {}",
                        e.getMessage()));
            }
        }

        private static XMLReader newParser() {
            try {
                return SAXParserFactoryFactory.newInstance().newSAXParser().getXMLReader();
            } catch (final ParserConfigurationException | SAXException e) {
                throw new TransformerFactoryConfigurationError(e, "Unable to create an XML parser");
            }
        }

        // Sets the features whatever ParserConfig says, as that only applies to Stroom's own parsing.
        private static XMLReader harden(final XMLReader parser, final boolean allowDoctype) {
            try {
                parser.setFeature(SAXParserFactoryFactory.FEATURE_DISALLOW_DOCTYPE, !allowDoctype);
                parser.setFeature(SAXParserFactoryFactory.FEATURE_EXTERNAL_GENERAL_ENTITIES, false);
                parser.setFeature(SAXParserFactoryFactory.FEATURE_EXTERNAL_PARAMETER_ENTITIES, false);
                parser.setFeature(SAXParserFactoryFactory.FEATURE_LOAD_EXTERNAL_DTD, false);
                return parser;
            } catch (final SAXException e) {
                throw new TransformerFactoryConfigurationError(e, "Unable to harden an XML parser");
            }
        }
    }


    // --------------------------------------------------------------------------------


    private static class SecuredStylesheetPackage extends StylesheetPackage {

        SecuredStylesheetPackage(final Configuration configuration) {
            super(configuration);
        }

        @Override
        public void createFunctionLibrary() {
            super.createFunctionLibrary();
            restrictFunctionLibraries(getFunctionLibrary().getLibraryList());
        }
    }


    // --------------------------------------------------------------------------------


    private static class RestrictedFunctionSet extends BuiltInFunctionSet {

        private final BuiltInFunctionSet functionSet;
        private final Set<String> removedFunctions;

        RestrictedFunctionSet(final BuiltInFunctionSet functionSet) {
            this.functionSet = functionSet;
            this.removedFunctions = REMOVED_FUNCTIONS.getOrDefault(functionSet.getNamespace(), Set.of());
            importFunctionSet(functionSet);
        }

        @Override
        public Entry getFunctionDetails(final String name, final int arity) {
            if (removedFunctions.contains(name)) {
                LOGGER.debug("Refused function '{}'", name);
                return null;
            }
            return super.getFunctionDetails(name, arity);
        }

        @Override
        public String getNamespace() {
            return functionSet.getNamespace();
        }

        @Override
        public String getConventionalPrefix() {
            return functionSet.getConventionalPrefix();
        }
    }


    // --------------------------------------------------------------------------------


    // xsl:result-document is refused when compiling, as external functions are off. This also refuses it at
    // run time in case that ever changes.
    private static class RefusingOutputURIResolver implements OutputURIResolver {

        @Override
        public OutputURIResolver newInstance() {
            return this;
        }

        @Override
        public Result resolve(final String href, final String base) throws TransformerException {
            throw new TransformerException("Writing '" + href + "' is not allowed.");
        }

        @Override
        public void close(final Result result) {
            // Nothing is ever opened.
        }
    }
}
