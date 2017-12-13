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
 */

package stroom.pipeline.server.filter;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "HttpPostFilter", category = PipelineElementType.Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_HAS_TARGETS,
        PipelineElementType.VISABILITY_SIMPLE}, icon = ElementIcons.STREAM)
public class HttpPostFilter extends AbstractSamplingFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpPostFilter.class);

    private final ErrorReceiverProxy errorReceiverProxy;

    private final Client client = ClientBuilder.newClient(new ClientConfig()
            .register(ClientResponse.class));
    private String receivingApiUrl;

    @Inject
    public HttpPostFilter(final ErrorReceiverProxy errorReceiverProxy,
                          final LocationFactoryProxy locationFactory) {
        super(errorReceiverProxy, locationFactory);
        this.errorReceiverProxy = errorReceiverProxy;
    }

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        String xml = getOutput();
        try {
            Response response = client
                    .target(receivingApiUrl)
                    .request()
                    .accept(MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON)
                    .post(Entity.xml(xml));
            if (response.getStatus() != Response.Status.ACCEPTED.ordinal()) {
                String errorMessage = String.format("POST was not accepted by the API: %s", response);
                errorReceiverProxy.log(Severity.ERROR, null, null, errorMessage, null);
                LOGGER.error(errorMessage);
            } else {
                LOGGER.info("POSTed document to API.");
            }
        } catch (Exception e) {
            String errorMessage = String.format("Unable to POST document to API: %s", e);
            errorReceiverProxy.log(Severity.ERROR, null, null, errorMessage, e);
            LOGGER.error(errorMessage);
        }
    }

    @PipelineProperty(description = "The URL of the receiving API.")
    public void setReceivingApiUrl(final String receivingApiUrl) {
        this.receivingApiUrl = receivingApiUrl;
    }
}