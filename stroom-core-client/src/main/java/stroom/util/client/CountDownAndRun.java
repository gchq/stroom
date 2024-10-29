package stroom.util.client;

public class CountDownAndRun {

    private int count;
    private Runnable runnable;

    public CountDownAndRun(final int count, final Runnable runnable) {
        this.count = count;
        this.runnable = runnable;
    }

    public synchronized void countdown() {
        count--;
        if (count == 0) {
            runnable.run();
        }
    }
}
