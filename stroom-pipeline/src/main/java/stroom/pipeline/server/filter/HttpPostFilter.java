package stroom.pipeline.server.filter;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import stroom.pipeline.server.factory.ConfigurableElement;
import stroom.pipeline.server.factory.ElementIcons;
import stroom.pipeline.server.factory.PipelineProperty;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomScope;

import javax.ws.rs.core.MediaType;

@Component
@Scope(StroomScope.PROTOTYPE)
@ConfigurableElement(type = "HttpPostFilter", category = PipelineElementType.Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_HAS_TARGETS,
        PipelineElementType.VISABILITY_SIMPLE }, icon = ElementIcons.STREAM)
public class HttpPostFilter extends TestFilter {
    private final Client client = Client.create();
    private WebResource webResource;

    private static final StroomLogger LOGGER = StroomLogger.getLogger(HttpPostFilter.class);

    @Override
    public void endDocument() throws SAXException {
        super.endDocument();
        String xml = getOutput();
        try {
            ClientResponse response = webResource
                    .accept("application/xml", "application/json")
                    .type(MediaType.APPLICATION_XML)
                    .post(ClientResponse.class, xml);
            LOGGER.info("POSTed document to API.");
        } catch ( Exception e){
            LOGGER.error("Unable to POST document to API.");
        }
    }

    @PipelineProperty(description = "The URL of the receiving API.")
    public void setReceivingApiUrl(final String receivingApiUrl){
        try {
            webResource = client.resource(receivingApiUrl);
        } catch(IllegalArgumentException | NullPointerException e){
            LOGGER.error("Bad URL: {}", receivingApiUrl);
        }
    }

}
