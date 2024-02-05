package com.google.gwt.user.client.ui;

import com.google.gwt.user.client.Timer;

public class FocusUtil {

    public static void forceFocus(final Runnable runnable) {
        for (int i = 0; i <= 500; i += 100) {
            Timer timer = new Timer() {
                @Override
                public void run() {
                    runnable.run();
                }
            };
            timer.schedule(i);
        }
    }
}
