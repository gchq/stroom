package stroom.explorer.server;

import javax.inject.Provider;

public interface ExplorerActionHandlers {
    <T extends ExplorerActionHandler> void add(int priority, String type, String displayType, Provider<T> provider, String... tags);
}
