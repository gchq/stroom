package stroom.annotation.impl.db.spring;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;
import stroom.annotation.impl.db.AnnotationDbConfig;
import stroom.annotation.impl.db.AnnotationDbModule;
import stroom.annotation.impl.db.ConnectionProvider;

import javax.inject.Singleton;

@Component
@Singleton
public class ConnectionProviderFactoryBean implements FactoryBean<ConnectionProvider> {
    private static ConnectionProvider connectionProvider;

    @Override
    public ConnectionProvider getObject() {
        if (connectionProvider == null) {
            synchronized (ConnectionProviderFactoryBean.class) {
                if (connectionProvider == null) {
                    final AnnotationDbConfig annotationDbConfig = new AnnotationDbConfig();
                    connectionProvider = new AnnotationDbModule(annotationDbConfig).getConnectionProvider(() -> annotationDbConfig);
                }
            }
        }

        return connectionProvider;
    }

    @Override
    public Class<?> getObjectType() {
        return ConnectionProvider.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public static void setAnnotationDbConfig(final AnnotationDbConfig annotationDbConfig) {
        connectionProvider = new AnnotationDbModule(annotationDbConfig).getConnectionProvider(() -> annotationDbConfig);
    }
}
