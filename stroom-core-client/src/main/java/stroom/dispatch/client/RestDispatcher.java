package stroom.dispatch.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.logging.client.LogConfiguration;
import org.fusesource.restygwt.client.Dispatcher;
import org.fusesource.restygwt.client.Method;

import java.util.logging.Logger;

class RestDispatcher implements Dispatcher {
    @Override
    public Request send(Method method, RequestBuilder builder) throws RequestException {
        if (GWT.isClient() && LogConfiguration.loggingIsEnabled()) {
            Logger logger = Logger.getLogger(org.fusesource.restygwt.client.dispatcher.DefaultDispatcher.class.getName());
            logger.fine("Sending http request: " + builder.getHTTPMethod() + " " + builder.getUrl() + " ,timeout:" +
                    builder.getTimeoutMillis());

            String content = builder.getRequestData();
            if (content != null && !content.isEmpty()) {
                logger.fine(content);
            }
        }
        return builder.send();
    }
}