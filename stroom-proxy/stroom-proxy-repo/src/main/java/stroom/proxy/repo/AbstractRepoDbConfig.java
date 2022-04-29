package stroom.proxy.repo;

import stroom.util.shared.AbstractConfig;

import java.util.List;

public abstract class AbstractRepoDbConfig extends AbstractConfig implements RepoDbConfig {

    private static final List<String> DEFAULT_GLOBAL_PRAGMA = List.of(
            "pragma journal_mode = WAL;"
//            "pragma auto_vacuum = incremental;"
    );
    private static final List<String> DEFAULT_CONNECTION_PRAGMA = List.of(
            "pragma synchronous = normal;",
            "pragma temp_store = memory;",
            "pragma mmap_size = 30000000000;");
//            "pragma busy_timeout = 600000;"

    private static final int DEFAULT_BATCH_SIZE = 1_0000_000;

    private final String dbDir;
    private final List<String> globalPragma;
    private final List<String> connectionPragma;
    private final int batchSize;

    protected AbstractRepoDbConfig() {
        this.dbDir = "db";
        this.globalPragma = DEFAULT_GLOBAL_PRAGMA;
        this.connectionPragma = DEFAULT_CONNECTION_PRAGMA;
        this.batchSize = DEFAULT_BATCH_SIZE;
    }

    protected AbstractRepoDbConfig(final String dbDir) {
        this.dbDir = dbDir;
        this.globalPragma = DEFAULT_GLOBAL_PRAGMA;
        this.connectionPragma = DEFAULT_CONNECTION_PRAGMA;
        this.batchSize = DEFAULT_BATCH_SIZE;
    }

    protected AbstractRepoDbConfig(final String dbDir,
                                   final List<String> globalPragma,
                                   final List<String> connectionPragma,
                                   final int batchSize) {
        this.dbDir = dbDir;
        this.globalPragma = List.copyOf(globalPragma);
        this.connectionPragma = List.copyOf(connectionPragma);
        if (batchSize > 9) {
            this.batchSize = batchSize;
        } else {
            this.batchSize = DEFAULT_BATCH_SIZE;
        }
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

    @Override
    public int getBatchSize() {
        return batchSize;
    }
}
