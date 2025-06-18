package stroom.meta.impl.db;

import stroom.meta.api.MetaSecurityFilter;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.Optional;

public class MetaTestModule extends AbstractModule {

    @Provides
    MetaSecurityFilter getMetaSecurityFilter() {
        return (permission, fields) -> Optional.empty();
    }
}
