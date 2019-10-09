package stroom.annotation.impl.db.spring;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;
import stroom.annotation.impl.db.AnnotationDbConfig;
import stroom.annotation.impl.db.AnnotationDbModule;
import stroom.annotation.impl.db.ConnectionProvider;

@Component
public class ConnectionProviderFactoryBean implements FactoryBean<ConnectionProvider> {
    private static AnnotationDbConfig annotationDbConfig;
    private static ConnectionProvider connectionProvider;

    @Override
    public ConnectionProvider getObject() {
        return connectionProvider;
    }

    @Override
    public Class<?> getObjectType() {
        return ConnectionProvider.class;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    public static void setAnnotationDbConfig(final AnnotationDbConfig annotationDbConfig) {
        connectionProvider = new AnnotationDbModule(annotationDbConfig).getConnectionProvider(() -> annotationDbConfig);
    }
}
