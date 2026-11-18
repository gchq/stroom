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

package stroom.data.store.impl.fs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TestFsStreamStore {

    @Test
    void deleteLater_deletesTheFileAndEnds(@TempDir final Path dir) throws IOException, InterruptedException {
        final Path file = Files.createTempFile(dir, "stroomFsVolVal", null);

        final ScheduledExecutorService executorService = FsStreamStore.deleteLater(file, 10, TimeUnit.MILLISECONDS);

        // Its thread isn't a daemon, so if it never ended the JVM couldn't exit (e.g. setupSampleData hung)
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS))
                .isTrue();
        assertThat(file)
                .doesNotExist();
    }
}
