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

package stroom.planb.impl.data;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class SequentialFile {

    private final Path root;
    private final List<Path> subDirs;
    private final Path zip;
    private final CountDownLatch countDownLatch;

    public SequentialFile(final Path root,
                          final List<Path> subDirs,
                          final Path zip,
                          final CountDownLatch countDownLatch) {
        this.root = root;
        this.subDirs = subDirs;
        this.zip = zip;
        this.countDownLatch = countDownLatch;
    }

    public Path getRoot() {
        return root;
    }

    public List<Path> getSubDirs() {
        return subDirs;
    }

    public Path getZip() {
        return zip;
    }

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
    }

    @Override
    public String toString() {
        return zip.toString();
    }
}
