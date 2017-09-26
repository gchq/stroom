package stroom.module;

import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;

public interface StroomModule {
    void initialize(Configuration configuration, Environment environment);
}
