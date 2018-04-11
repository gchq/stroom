/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.pipeline;

import stroom.entity.shared.BaseResultList;
import stroom.pipeline.shared.FindXSLTCriteria;
import stroom.pipeline.shared.XSLT;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;

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
    private static final String RESOURCE_NOT_FOUND = "Resource not found: \"";
    private static final String RESOURCE_NOT_FOUND_END = "\"";

    private final XSLTService xsltService;

    @Inject
    CustomURIResolver(final XSLTService xsltService) {
        this.xsltService = xsltService;
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
            // Try and locate a translation with this name
            final FindXSLTCriteria findXSLTCriteria = new FindXSLTCriteria();
            findXSLTCriteria.getName().setString(href);
            final BaseResultList<XSLT> results = xsltService.find(findXSLTCriteria);

            if (results != null && results.size() > 0) {
                final XSLT xslt = results.getFirst();
                return new StreamSource(StreamUtil.stringToStream(xslt.getData()));
            }

            final StringBuilder sb = new StringBuilder();
            sb.append(RESOURCE_NOT_FOUND);
            sb.append(href);
            sb.append(RESOURCE_NOT_FOUND_END);
            throw new IOException(sb.toString());

        } catch (IOException e) {
            throw new TransformerException(e);
        }
    }
}
