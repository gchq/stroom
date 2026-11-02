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

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Finds and resolves the XSLTs that an XSLT imports or includes (`xsl:import`, `xsl:include`).
///
/// An href is resolved as the compile resolves it (see [CustomURIResolver]): first as the name of an XSLT, then,
/// if no XSLT has that name, as a UUID or a doc ref string such as `uuid='…', name='…'`.
///
/// This class does not change the current user. Resolve as the processing user to match the compile.
@Singleton
public class XsltImports {

    // An import or include element with any namespace prefix, and its href in single or double quotes.
    // A false match (e.g. in a comment) only means an extra href is resolved.
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "<(?:[\\w.-]+:)?(?:import|include)\\b[^>]*?\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    private final DocFinder docFinder;
    private final XsltStore xsltStore;

    @Inject
    public XsltImports(final DocFinder docFinder,
                       final XsltStore xsltStore) {
        this.docFinder = docFinder;
        this.xsltStore = xsltStore;
    }

    /// @param xslt The XSLT text. May be null.
    /// @return The hrefs of the XSLT's import and include elements, in document order. Empty if there are none.
    public static List<String> findHrefs(final String xslt) {
        final List<String> hrefs = new ArrayList<>();
        if (NullSafe.isNonBlankString(xslt)) {
            final Matcher matcher = IMPORT_PATTERN.matcher(xslt);
            while (matcher.find()) {
                hrefs.add(matcher.group(1) != null
                        ? matcher.group(1)
                        : matcher.group(2));
            }
        }
        return hrefs;
    }

    /// @param xslt The XSLT text. May be null.
    /// @return True if the XSLT has at least one import or include element.
    public static boolean hasImports(final String xslt) {
        return NullSafe.isNonBlankString(xslt) && IMPORT_PATTERN.matcher(xslt).find();
    }

    /// Resolve an import or include href to an XSLT.
    ///
    /// @param href The href as written in the importing XSLT.
    /// @return The XSLT the href refers to, or why there isn't exactly one.
    public Resolution resolve(final String href) {
        final List<DocRef> docRefs = NullSafe.list(docFinder.findByName(XsltDoc.TYPE, href, false));
        if (docRefs.size() > 1) {
            return Resolution.ambiguous(docRefs.size());
        }
        final DocRef docRef = docRefs.isEmpty()
                ? CustomURIResolver.parseDocRef(href)
                : docRefs.getFirst();
        try {
            final XsltDoc xsltDoc = xsltStore.readDocument(docRef);
            return xsltDoc != null
                    ? Resolution.found(xsltDoc)
                    : Resolution.notFound();
        } catch (final DocumentNotFoundException e) {
            return Resolution.notFound();
        }
    }


    // --------------------------------------------------------------------------------


    /// The result of resolving an href.
    ///
    /// @param xsltDoc    The XSLT the href refers to, or null if there isn't exactly one.
    /// @param matchCount The number of XSLTs with the href as their name when there is more than one, else 0
    ///                   or 1.
    public record Resolution(XsltDoc xsltDoc, int matchCount) {

        /// @param xsltDoc The XSLT the href refers to.
        /// @return A resolution to that XSLT.
        public static Resolution found(final XsltDoc xsltDoc) {
            return new Resolution(xsltDoc, 1);
        }

        /// @return A resolution to no XSLT.
        public static Resolution notFound() {
            return new Resolution(null, 0);
        }

        /// @param matchCount The number of XSLTs with the href as their name.
        /// @return A resolution to more than one XSLT.
        public static Resolution ambiguous(final int matchCount) {
            return new Resolution(null, matchCount);
        }

        /// @return True if the href refers to exactly one XSLT.
        public boolean isFound() {
            return xsltDoc != null;
        }

        /// @return True if more than one XSLT has the href as its name.
        public boolean isAmbiguous() {
            return matchCount > 1;
        }
    }
}
