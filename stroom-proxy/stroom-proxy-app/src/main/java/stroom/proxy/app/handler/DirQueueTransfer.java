/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.proxy.app.handler;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DirQueueTransfer implements Runnable {

    private final Supplier<Dir> supplier;
    private final Consumer<Path> consumer;

    public DirQueueTransfer(final Supplier<Dir> supplier,
                            final Consumer<Path> consumer) {
        this.supplier = supplier;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        try (final Dir dir = supplier.get()) {
            consumer.accept(dir.getPath());
        }
    }
}
