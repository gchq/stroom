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

package stroom.pipeline.xslt;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.io.StreamUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.List;
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

    private final XsltStore xsltStore;

    @Inject
    CustomURIResolver(final XsltStore xsltStore) {
        this.xsltStore = xsltStore;
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
        try {
            // Try and locate a translation by name
            final List<DocRef> docRefs = xsltStore.findByName(href);

            if (docRefs == null || docRefs.size() == 0) {
                // Try loading by UUID or doc ref string, e.g, `uuid='test-uuid', name='test-name'`.
                final DocRef docRef = parseDocRef(href);
                final XsltDoc document = xsltStore.readDocument(docRef);
                if (document == null) {
                    throw new IOException("Resource not found: \"" + href + "\"");
                }
                return new StreamSource(StreamUtil.stringToStream(document.getData()));
            }

            if (docRefs.size() > 1) {
                throw new IOException("Found " + docRefs.size() + " resources for: \"" + href + "\"");
            }

            final XsltDoc document = xsltStore.readDocument(docRefs.get(0));
            if (document == null) {
                throw new IOException("Error reading: \"" + href + "\"");
            }

            return new StreamSource(StreamUtil.stringToStream(document.getData()));

        } catch (final IOException e) {
            throw new TransformerException(e);
        }
    }

    static DocRef parseDocRef(final String href) {
        return new DocRef(
                getPart("type", href, PipelineDoc.TYPE),
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
