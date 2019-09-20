package stroom.annotation.impl.db.spring;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.annotation.impl.db.AnnotationsConfig;
import stroom.annotation.impl.db.AnnotationsDbModule;
import stroom.annotation.impl.db.ConnectionProvider;
import stroom.util.spring.StroomScope;

@Component
@Scope(StroomScope.PROTOTYPE)
public class ConnectionProviderFactoryBean implements FactoryBean<ConnectionProvider> {
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

    public void setAnnotationsConfig(final AnnotationsConfig annotationsConfig) {
        this.connectionProvider = new AnnotationsDbModule(annotationsConfig).getConnectionProvider(() -> annotationsConfig);
    }
}
