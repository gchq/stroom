package stroom.startup;

import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;

public interface AppAware {
    void initialize(Configuration configuration, Environment environment);
}
