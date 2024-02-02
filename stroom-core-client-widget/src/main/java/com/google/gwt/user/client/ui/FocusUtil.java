package com.google.gwt.user.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;

public class FocusUtil {
    public static void forceFocus(final Runnable runnable) {
        Timer timer = new Timer() {
            @Override
            public void run() {
                runnable.run();
            }
        };
        timer.schedule(0);
        timer.schedule(100);
        timer.schedule(200);
        timer.schedule(300);
        timer.schedule(400);
        timer.schedule(500);
    }
}
