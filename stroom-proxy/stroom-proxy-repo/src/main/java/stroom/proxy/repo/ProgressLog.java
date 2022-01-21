package stroom.proxy.repo;

public interface ProgressLog {

    void increment(String name);

    void add(String name, long delta);

    String report();

    void selAutoLogCount(long count);
}
