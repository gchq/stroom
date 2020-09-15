package stroom.dispatch.client;

import stroom.util.shared.RandomId;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ApplicationInstanceIdProvider {
    private final String applicationInstanceId;

    @Inject
    ApplicationInstanceIdProvider() {
        this.applicationInstanceId = RandomId.createDiscrimiator();
    }

    public String get() {
        return applicationInstanceId;
    }
}
