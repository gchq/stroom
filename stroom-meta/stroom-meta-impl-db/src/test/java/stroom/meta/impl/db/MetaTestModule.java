package stroom.meta.impl.db;

import stroom.data.retention.api.DataRetentionRulesProvider;
import stroom.meta.api.MetaSecurityFilter;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.Optional;

public class MetaTestModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DataRetentionRulesProvider.class).toInstance(() -> null);
    }

    @Provides
    MetaSecurityFilter getMetaSecurityFilter() {
        return (permission, fields) -> Optional.empty();
    }
}
