package stroom.job.api;

public interface DistributedTaskFetcher {
    void shutdown();

    void execute();

    void fetch();
}
