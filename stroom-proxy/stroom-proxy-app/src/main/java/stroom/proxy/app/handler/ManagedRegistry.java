package stroom.proxy.app.handler;

import io.dropwizard.lifecycle.Managed;

import java.util.ArrayList;
import java.util.List;

public class ManagedRegistry implements Managed {

    private final List<Managed> managedList = new ArrayList<>();

    public void register(final Managed managed) {
        managedList.add(managed);
    }

    @Override
    public void start() throws Exception {
        for (final Managed managed : managedList) {
            managed.start();
        }
    }

    @Override
    public void stop() throws Exception {
        for (final Managed managed : managedList) {
            managed.stop();
        }
    }
}
