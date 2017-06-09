/*
 * Copyright 2016 Crown Copyright
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

package stroom.gwt;

import com.google.gwt.dev.Compiler;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class GwtCompilerWrapper {

    public static final long TIME_BETWEEN_CONSOLE_OUTPUT_MS = 20_000;

    /**
     * Simple wrapper script to run the GWT compiler in the background while outputting something to standard out
     * every 20s. This is intended for use to prevent travis-ci from timing out during the compilation step
     * of the build
     */
    public static void main(String[] args) {
        System.out.println("Starting GWT Compiler in the background...");
        if (args != null) {
            System.out.println("GWT Compiler args: " + Arrays.stream(args).collect(Collectors.joining(" ")));
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> Compiler.main(args));

        future.exceptionally(throwable -> {
            System.out.println("Error during GWT Compile");
            throw new RuntimeException(throwable);
        });

        Instant startTime = Instant.now();
        while (!future.isDone()) {
            doSleep(TIME_BETWEEN_CONSOLE_OUTPUT_MS);
            Duration duration = Duration.between(startTime, Instant.now());
            System.out.println(String.format("GWT Compile still running... (Duration: %s)", duration));
        }

//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            Duration duration = Duration.between(startTime, Instant.now());
//            System.out.println(String.format("Finished compilation. (Duration: %s)", duration));
//        }));
    }


    private static void doSleep(final long timeMs) {
        try {
            Thread.sleep(timeMs);
        } catch (InterruptedException e) {
            System.out.println("Thread interrupted");
            Thread.currentThread().interrupt();
        }
    }


}
