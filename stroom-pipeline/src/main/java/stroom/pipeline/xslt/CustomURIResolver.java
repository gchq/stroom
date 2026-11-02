/*
 * Copyright 2016 Crown Copyright
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
import stroom.pipeline.shared.XsltDoc;
import stroom.util.io.StreamUtil;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.util.Optional;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

/**
 * <p>
 * This class is used to resolve resources found by the XSL transformation
 * engine. The resource catalogue is used to try and find the resource.
 * </p>
 * <p>
 * <p>
 * We also allow standard common translation includes
 * </p>
 */
class CustomURIResolver implements URIResolver {

    private final XsltImports xsltImports;

    @Inject
    CustomURIResolver(final XsltImports xsltImports) {
        this.xsltImports = xsltImports;
    }

    /**
     * Called by the processor when it encounters an xsl:include, xsl:import, or
     * document() function.
     *
     * @param href An href attribute, which may be relative or absolute.
     * @param base The base URI against which the first argument will be made
     *             absolute if the absolute URI is required.
     * @return A Source object, or null if the href cannot be resolved, and the
     * processor should try to resolve the URI itself.
     * @throws TransformerException Thrown if the referenced resource cannot be found.
     */
    public Source resolve(final String href, final String base) throws TransformerException {
        final XsltImports.Resolution resolution = xsltImports.resolve(href);
        if (resolution.isAmbiguous()) {
            throw new TransformerException(LogUtil.message(
                    "Found {} XSLTs named \"{}\"", resolution.matchCount(), href));
        }
        if (!resolution.isFound()) {
            throw new TransformerException(LogUtil.message(
                    "No XSLT found with name or UUID \"{}\"", href));
        }
        return new StreamSource(StreamUtil.stringToStream(resolution.xsltDoc().getData()));
    }

    static DocRef parseDocRef(final String href) {
        return new DocRef(
                getPart("type", href, XsltDoc.TYPE),
                getPart("uuid", href, href),
                getPart("name", href, null));
    }

    static String getPart(final String key, final String href, final String defaultValue) {
        return getQuotedPart(key, href, "'")
                .orElseGet(() -> getQuotedPart(key, href, "\"")
                        .orElseGet(() -> getQuotedPart(key, href, "")
                                .orElse(defaultValue)));
    }

    static Optional<String> getQuotedPart(final String key, final String href, final String quotes) {
        final String match = key + "=" + quotes;
        int start = href.indexOf(match);
        if (start != -1) {
            start = start + match.length();
            int end = href.indexOf(quotes + ",", start);
            if (end == -1) {
                end = href.indexOf(quotes + " ", start);
                if (end == -1) {
                    if (quotes.length() > 0) {
                        end = href.indexOf(quotes, start);
                    }
                    if (end == -1) {
                        end = href.length();
                    }
                }
            }
            return Optional.of(href.substring(start, end));
        }
        return Optional.empty();
    }
}
