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

package stroom.test.common.data;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.fail;


public class FlatFileTestDataExtension implements BeforeAllCallback {

    private Path folder = null;
    private final int numberOfFiles;
    private final Consumer<Consumer<String>> testDataGenerator;
    private final List<Path> dataFiles = new ArrayList<>();

    private FlatFileTestDataExtension(final Builder builder) {
        this.numberOfFiles = builder.numberOfFiles;
        this.testDataGenerator = builder.testDataGenerator;
    }

    @Override
    public void beforeAll(final ExtensionContext context) throws Exception {
        final String testName = context.getDisplayName();
        this.folder = Files.createTempDirectory(testName + "-");
        for (int x = 0; x < numberOfFiles; x++) {

            final String filename = String.format("%s.csv", UUID.randomUUID().toString());
            final File file = Files.createTempFile(folder, testName, "").toFile();

            try (final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)))) {

                testDataGenerator.accept(s -> {
                    try {
                        bw.write(String.format("%s\n", s));
                    } catch (final IOException e) {
                        fail(e.getLocalizedMessage());
                    }
                });
            }

            dataFiles.add(file.toPath());
        }
    }

//    @Override
//    public Statement apply(final Statement statement, Description description) {
//
//        return folder.apply(new Statement() {
//            @Override
//            public void evaluate() throws Throwable {
//                try {
//                    before();
//                    statement.evaluate();
//                } finally {
//                    after();
//                }
//            }
//        }, description);
//    }

    public Path getFolder() {
        return folder;
    }

    public List<Path> getDataFiles() {
        return dataFiles;
    }

//    private void before() throws IOException {
//        for (int x=0; x<numberOfFiles; x++) {
//            final String filename = String.format("%s.csv", UUID.randomUUID().toString());
//            final File file = this.folder.newFile(filename);
//
//            try (final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)))) {
//
//                testDataGenerator.accept(s -> {
//                    try {
//                        bw.write(String.format("%s\n", s));
//                    } catch (final IOException e) {
//                        fail(e.getLocalizedMessage());
//                    }
//                });
//            }
//
//            dataFiles.add(file.toPath());
//        }
//    }

    private void after() {

    }

    public static Builder withTempDirectory() {
        return new Builder();
    }


    public static final class Builder {

        private int numberOfFiles = 10;
        private Consumer<Consumer<String>> testDataGenerator;

        public Builder numberOfFiles(final int value) {
            this.numberOfFiles = value;
            return this;
        }

        public Builder testDataGenerator(final Consumer<Consumer<String>> value) {
            this.testDataGenerator = value;
            return this;
        }

        public FlatFileTestDataExtension build() {
            Objects.requireNonNull(this.testDataGenerator, "Test Data Generator Must be Specified");
            return new FlatFileTestDataExtension(this);
        }
    }
}
