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

package stroom.pipeline.filter;

import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.writer.HTTPAppender;
import stroom.svg.shared.SvgImage;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

@Deprecated // This is pretty limited in what it can do. HttpAppender is much better and more configurable
@ConfigurableElement(type = "HttpPostFilter",
        displayValue = "HTTP Post Filter",
        category = PipelineElementType.Category.FILTER,
        description = """
                This element is deprecated, you should instead use the much more flexible \
                {{< pipe-elm "HTTPAppender" >}}.
                This element will simply POST the output of the XML events to the configured URL.
                """,
        roles = {
                PipelineElementType.ROLE_TARGET,
                PipelineElementType.ROLE_HAS_TARGETS,
                PipelineElementType.VISABILITY_SIMPLE},
        icon = SvgImage.PIPELINE_STREAM)
public class HttpPostFilter extends AbstractSamplingFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpPostFilter.class);

    private final ErrorReceiverProxy errorReceiverProxy;
    private final JerseyClientFactory jerseyClientFactory;

    private String receivingApiUrl;

    @Inject
    public HttpPostFilter(final ErrorReceiverProxy errorReceiverProxy,
                          final LocationFactoryProxy locationFactory,
                          final JerseyClientFactory jerseyClientFactory) {
        super(errorReceiverProxy, locationFactory);
        this.errorReceiverProxy = errorReceiverProxy;
        this.jerseyClientFactory = jerseyClientFactory;
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        final String xml = getOutput();

        final String deprecatedMsg = LogUtil.message("{} is deprecated. Use {} instead.",
                HttpPostFilter.class.getSimpleName(),
                HTTPAppender.class.getSimpleName());
        LOGGER.warn(deprecatedMsg);
        errorReceiverProxy.log(Severity.WARNING, null, null, deprecatedMsg, null);

        if (NullSafe.isBlankString(receivingApiUrl)) {
            final String msg = "Property 'receivingApiUrl' is not set. Unable to POST.";
            errorReceiverProxy.log(Severity.ERROR, null, null, msg, null);
        } else {
            try {
                final Client client = jerseyClientFactory.getNamedClient(JerseyClientName.HTTP_POST_FILTER);
                try (final Response response = client
                        .target(receivingApiUrl)
                        .request()
                        .accept(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON)
                        .post(Entity.xml(xml))) {
                    if (response.getStatus() != Response.Status.ACCEPTED.ordinal()) {
                        final String errorMessage = String.format("POST was not accepted by the API: %s", response);
                        errorReceiverProxy.log(Severity.ERROR, null, null, errorMessage, null);
                        LOGGER.error(errorMessage);
                    } else {
                        LOGGER.info("POSTed document to API {}", receivingApiUrl);
                    }
                }
            } catch (final RuntimeException e) {
                final String errorMessage = String.format("Unable to POST document to API: %s", e);
                errorReceiverProxy.log(Severity.ERROR, null, null, errorMessage, e);
                LOGGER.error(errorMessage);
            }
        }
    }

    @SuppressWarnings("unused") // Called if the prop is set in the UI
    @PipelineProperty(description = "The URL of the receiving API.", displayPriority = 1)
    public void setReceivingApiUrl(final String receivingApiUrl) {
        this.receivingApiUrl = receivingApiUrl;
    }
}
