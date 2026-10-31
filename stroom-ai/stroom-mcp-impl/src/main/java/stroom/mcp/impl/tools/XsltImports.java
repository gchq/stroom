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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The XSLT an `xsl:import` or `xsl:include` href names, found as Stroom's URI resolver finds it: the one XSLT
/// document of that name, else the one whose UUID the href holds; read as the user.
final class XsltImports {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(XsltImports.class);
    private static final Pattern UUID_IN_HREF = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private XsltImports() {
        // Static only.
    }

    /// @param docFinder      Finds XSLT documents by name.
    /// @param documentAccess Reads one as the user.
    /// @param href           An import's href.
    /// @return The XSLT it names, or empty when none (or more than one) is found, or the user may not read it.
    static Optional<XsltDoc> resolve(final DocFinder docFinder,
                                     final DocumentAccess documentAccess,
                                     final String href) {
        try {
            List<DocRef> found = NullSafe.list(docFinder.findByName(XsltDoc.TYPE, href, false));
            if (found.isEmpty()) {
                final Matcher matcher = UUID_IN_HREF.matcher(href);
                found = matcher.find()
                        ? List.of(new DocRef(XsltDoc.TYPE, matcher.group()))
                        : List.of();
            }
            // Stroom refuses a name more than one XSLT has: so does this.
            return found.size() == 1
                    ? documentAccess.read(found.getFirst(), XsltDoc.class)
                    : Optional.empty();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to resolve the XSLT {}: {}", href, e.getMessage()), e);
            return Optional.empty();
        }
    }
}
