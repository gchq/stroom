package stroom.proxy.app;

import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.client.JerseyClientConfiguration;

@JsonPropertyOrder(alphabetic = true)
public class RestClientConfig extends JerseyClientConfiguration implements IsProxyConfig {

    // This class makes it easier for us to use/bind the JerseyClientConfiguration in our config tree
    // and makes it easier to find its usages in IJ
}
