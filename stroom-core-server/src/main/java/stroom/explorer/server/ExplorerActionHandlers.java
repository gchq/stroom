package stroom.explorer.server;

public interface ExplorerActionHandlers {
    void add(boolean system, int priority, String type, String displayType, ExplorerActionHandler explorerActionHandler);
}
