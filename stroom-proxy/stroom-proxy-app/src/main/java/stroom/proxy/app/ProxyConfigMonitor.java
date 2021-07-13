package stroom.proxy.app;

import stroom.util.config.AbstractFileChangeMonitor;

import java.nio.file.Path;

public class ProxyConfigMonitor extends AbstractFileChangeMonitor {

    public ProxyConfigMonitor(final Path monitoredFile) {
        super(monitoredFile);
    }

    @Override
    protected void onFileChange() {

    }
}
