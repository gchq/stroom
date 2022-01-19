package stroom.util.client;

import com.google.gwt.user.client.Timer;

public class DelayedUpdate {
    private final int delay;
    private final Timer timer;

    public DelayedUpdate(final Runnable runnable) {
        this(250, runnable);
    }

    public DelayedUpdate(final int delay, final Runnable runnable) {
        this.delay = delay;
        timer = new Timer() {
            @Override
            public void run() {
                runnable.run();
            }
        };
    }

    public void reset() {
        timer.cancel();
    }

    public void update() {
        if (!timer.isRunning()) {
            timer.schedule(delay);
        }
    }

//
//    private final int delay;
//    private final Runnable runnable;
//    private final Timer timer;
//    private long lastUpdate;
//
//    public DelayedUpdate(final int delay, final Runnable runnable) {
//        this.delay = delay;
//        this.runnable = runnable;
//        timer = new Timer() {
//            @Override
//            public void run() {
//                doUpdate();
//            }
//        };
//    }
//
//    public void reset() {
//        timer.cancel();
//        lastUpdate = 0;
//    }
//
//    public void update() {
//        timer.cancel();
//        if (lastUpdate > 0 && lastUpdate < System.currentTimeMillis() - delay) {
//            doUpdate();
//        } else {
//            timer.schedule(delay);
//        }
//    }
//
//    private void doUpdate() {
//        lastUpdate = System.currentTimeMillis();
//        runnable.run();
//    }
}
