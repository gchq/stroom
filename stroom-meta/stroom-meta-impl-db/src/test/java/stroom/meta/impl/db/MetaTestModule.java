package stroom.meta.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.meta.shared.MetaSecurityFilter;

import java.util.Optional;

public class MetaTestModule extends AbstractModule {
    @Provides
    MetaSecurityFilter getMetaSecurityFilter() {
        return permission -> Optional.empty();
    }
}
