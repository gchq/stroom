package stroom.explorer.server;

public interface ExplorerActionHandlers {
    void add(int priority, String type, String displayType, ExplorerActionHandler explorerActionHandler);
}
