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

package stroom.test.common.util.test;

import stroom.util.io.HomeDirProvider;
import stroom.util.io.TempDirProvider;

import com.google.inject.AbstractModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestingHomeAndTempProvidersModule extends AbstractModule {

    private final Path tempDir;
    private final Path homeDir;

    public TestingHomeAndTempProvidersModule() {
        try {
            this.tempDir = Files.createTempDirectory("stroom-temp");
            this.homeDir = tempDir.resolve("home");
        } catch (final IOException e) {
            throw new RuntimeException("Error creating temp dir", e);
        }
    }

    public TestingHomeAndTempProvidersModule(final Path tempDir) {
        this.tempDir = tempDir;
        this.homeDir = tempDir.resolve("home");
    }

    @Override
    protected void configure() {
        super.configure();

        bind(HomeDirProvider.class).toInstance(this::getHomeDir);
        bind(TempDirProvider.class).toInstance(this::getTempDir);
    }

    public Path getHomeDir() {
        return homeDir;
    }

    public Path getTempDir() {
        return tempDir;
    }
}
