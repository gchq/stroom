package stroom.proxy.repo;

import stroom.util.shared.AbstractConfig;

import java.util.List;

public abstract class AbstractRepoDbConfig extends AbstractConfig implements RepoDbConfig {

    private static final List<String> DEFAULT_GLOBAL_PRAGMA = List.of(
            "pragma journal_mode = WAL;");
    private static final List<String> DEFAULT_CONNECTION_PRAGMA = List.of(
            "pragma synchronous = normal;",
            "pragma temp_store = memory;",
            "pragma mmap_size = 30000000000;");

    private final String dbDir;
    private final List<String> globalPragma;
    private final List<String> connectionPragma;

    protected AbstractRepoDbConfig() {
        this.dbDir = "db";
        this.globalPragma = DEFAULT_GLOBAL_PRAGMA;
        this.connectionPragma = DEFAULT_CONNECTION_PRAGMA;
    }

    protected AbstractRepoDbConfig(final String dbDir) {
        this.dbDir = dbDir;
        this.globalPragma = DEFAULT_GLOBAL_PRAGMA;
        this.connectionPragma = DEFAULT_CONNECTION_PRAGMA;
    }

    protected AbstractRepoDbConfig(final String dbDir,
                                   final List<String> globalPragma,
                                   final List<String> connectionPragma) {
        this.dbDir = dbDir;
        this.globalPragma = List.copyOf(globalPragma);
        this.connectionPragma = List.copyOf(connectionPragma);
    }

    @Override
    public String getDbDir() {
        return dbDir;
    }

    @Override
    public List<String> getGlobalPragma() {
        return globalPragma;
    }

    @Override
    public List<String> getConnectionPragma() {
        return connectionPragma;
    }
}
