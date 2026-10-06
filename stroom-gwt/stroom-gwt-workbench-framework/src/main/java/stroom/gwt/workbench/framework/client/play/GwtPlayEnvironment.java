/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.gwt.workbench.framework.client.play;

import stroom.gwt.workbench.framework.client.play.PlayRunner.Cancellable;
import stroom.gwt.workbench.framework.client.play.PlayRunner.Environment;

import com.google.gwt.core.client.Duration;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.GWT.UncaughtExceptionHandler;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.Timer;

import java.util.function.Consumer;

/// The browser side of [PlayRunner]: GWT timers and catching the errors the story's code throws.
///
/// Errors are caught in two ways while the runner catches them (from when it starts until it is
/// disposed):
///
/// * GWT's uncaught exception handler, which GWT calls for exceptions thrown in event handlers,
///   timers and other code it calls from the browser;
/// * the window's `error` and `unhandledrejection` events, for errors thrown by plain JavaScript
///   listeners and promises.
final class GwtPlayEnvironment implements Environment {

    private UncaughtExceptionHandler previousHandler;
    // The window listeners, to remove them
    private JavaScriptObject listeners;

    /// @return The current time in milliseconds, from GWT's `Duration`.
    @Override
    public double now() {
        return Duration.currentTimeMillis();
    }

    /// Runs a task after a delay with a GWT `Timer`.
    ///
    /// @param delayMillis How long to wait.
    /// @param task        What to do then.
    /// @return A way to cancel the timer.
    @Override
    public Cancellable schedule(final int delayMillis, final Runnable task) {
        final Timer timer = new Timer() {
            /// Runs the task.
            @Override
            public void run() {
                task.run();
            }
        };
        timer.schedule(delayMillis);
        return timer::cancel;
    }

    /// Starts catching errors: replaces GWT's uncaught exception handler and listens for the
    /// window's `error` and `unhandledrejection` events, replacing any catching already started.
    ///
    /// @param onError Given each error's message.
    @Override
    public void catchErrors(final Consumer<String> onError) {
        stopCatchingErrors();
        previousHandler = GWT.getUncaughtExceptionHandler();
        GWT.setUncaughtExceptionHandler(e -> onError.accept(describe(e)));
        listeners = addWindowListeners(onError);
    }

    /// Stops catching errors, restoring GWT's previous uncaught exception handler and removing
    /// the window listeners.
    @Override
    public void stopCatchingErrors() {
        if (listeners != null) {
            removeWindowListeners(listeners);
            listeners = null;
            GWT.setUncaughtExceptionHandler(previousHandler);
            previousHandler = null;
        }
    }

    private static String describe(final Throwable e) {
        // Unwrap GWT's wrapper for several exceptions thrown by event handlers
        Throwable cause = e;
        while (cause.getCause() != null && cause.getMessage() != null
               && cause.getMessage().startsWith("Exception caught")) {
            cause = cause.getCause();
        }
        return cause.toString();
    }

    private static native JavaScriptObject addWindowListeners(Consumer<String> onError) /*-{
        var report = function (message) {
            onError.@java.util.function.Consumer::accept(Ljava/lang/Object;)(message);
        };
        var onErrorEvent = function (event) {
            var error = event.error;
            report(error ? String(error) : String(event.message));
        };
        var onRejection = function (event) {
            report('Unhandled promise rejection: ' + String(event.reason));
        };
        $wnd.addEventListener('error', onErrorEvent);
        $wnd.addEventListener('unhandledrejection', onRejection);
        return {error: onErrorEvent, rejection: onRejection};
    }-*/;

    private static native void removeWindowListeners(JavaScriptObject listeners) /*-{
        $wnd.removeEventListener('error', listeners.error);
        $wnd.removeEventListener('unhandledrejection', listeners.rejection);
    }-*/;
}
