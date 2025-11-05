package stroom.planb.impl.db;

public class Count {

    private long count;

    public void increment() {
        count++;
    }

    public long incrementAndGet() {
        count++;
        return count;
    }

    public long getAndIncrement() {
        final long l = count;
        count++;
        return l;
    }

    public long get() {
        return count;
    }
}
