package stroom.app.guice;

import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Map;

class WebTargetProxy implements WebTarget {
    private final WebTarget webTarget;

    WebTargetProxy(final WebTarget webTarget) {
        this.webTarget = webTarget;
    }

    @Override
    public URI getUri() {
        return webTarget.getUri();
    }

    @Override
    public UriBuilder getUriBuilder() {
        return webTarget.getUriBuilder();
    }

    @Override
    public WebTarget path(final String path) {
        return webTarget.path(path);
    }

    @Override
    public WebTarget resolveTemplate(final String name, final Object value) {
        return webTarget.resolveTemplate(name, value);
    }

    @Override
    public WebTarget resolveTemplate(final String name, final Object value, final boolean encodeSlashInPath) {
        return webTarget.resolveTemplate(name, value, encodeSlashInPath);
    }

    @Override
    public WebTarget resolveTemplateFromEncoded(final String name, final Object value) {
        return webTarget.resolveTemplateFromEncoded(name, value);
    }

    @Override
    public WebTarget resolveTemplates(final Map<String, Object> templateValues) {
        return webTarget.resolveTemplates(templateValues);
    }

    @Override
    public WebTarget resolveTemplates(final Map<String, Object> templateValues, final boolean encodeSlashInPath) {
        return webTarget.resolveTemplates(templateValues, encodeSlashInPath);
    }

    @Override
    public WebTarget resolveTemplatesFromEncoded(final Map<String, Object> templateValues) {
        return webTarget.resolveTemplatesFromEncoded(templateValues);
    }

    @Override
    public WebTarget matrixParam(final String name, final Object... values) {
        return webTarget.matrixParam(name, values);
    }

    @Override
    public WebTarget queryParam(final String name, final Object... values) {
        return webTarget.queryParam(name, values);
    }

    @Override
    public Builder request() {
        return webTarget.request();
    }

    @Override
    public Builder request(final String... acceptedResponseTypes) {
        return webTarget.request(acceptedResponseTypes);
    }

    @Override
    public Builder request(final MediaType... acceptedResponseTypes) {
        return webTarget.request(acceptedResponseTypes);
    }

    @Override
    public Configuration getConfiguration() {
        return webTarget.getConfiguration();
    }

    @Override
    public WebTarget property(final String name, final Object value) {
        return webTarget.property(name, value);
    }

    @Override
    public WebTarget register(final Class<?> componentClass) {
        return webTarget.register(componentClass);
    }

    @Override
    public WebTarget register(final Class<?> componentClass, final int priority) {
        return webTarget.register(componentClass, priority);
    }

    @Override
    public WebTarget register(final Class<?> componentClass, final Class<?>... contracts) {
        return webTarget.register(componentClass, contracts);
    }

    @Override
    public WebTarget register(final Class<?> componentClass, final Map<Class<?>, Integer> contracts) {
        return webTarget.register(componentClass, contracts);
    }

    @Override
    public WebTarget register(final Object component) {
        return webTarget.register(component);
    }

    @Override
    public WebTarget register(final Object component, final int priority) {
        return webTarget.register(component, priority);
    }

    @Override
    public WebTarget register(final Object component, final Class<?>... contracts) {
        return webTarget.register(component, contracts);
    }

    @Override
    public WebTarget register(final Object component, final Map<Class<?>, Integer> contracts) {
        return webTarget.register(component, contracts);
    }
}