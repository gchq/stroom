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

package stroom.guice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.fail;

/**
 * This class generates a CSV file that can be visualised using the iunera Code Analysis tool.
 * <p>
 * https://github.com/iunera/codeanalysis
 */
class IuneraDependencyCsvWriter implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(IuneraDependencyCsvWriter.class);

    private final Class<?> testClass;
    private Path path;
    private BufferedWriter writer;

    public IuneraDependencyCsvWriter(final Class<?> testClass) {
        this.testClass = testClass;

        try {
            path = Files.createTempFile(String.format("DependencyTest_%s", testClass.getSimpleName()), ".csv");
            writer = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path)));
            writer.write("creditor,debtor,amount,risk\n");

        } catch (final IOException e) {
            fail(e.getLocalizedMessage());
        }
    }

    public void write(final String creditor,
                      final String debtor,
                      final int amount,
                      final int risk) {
        try {
            writer.write(String.format("%s,%s,%d,%d\n", creditor, debtor, amount, risk));
        } catch (final IOException e) {
            fail(e.getLocalizedMessage());
        }
    }

    @Override
    public void close() {
        try {
            LOGGER.info(String.format("Written Dependencies for %s to %s", testClass, path));
            writer.close();
        } catch (final IOException e) {
            fail(e.getLocalizedMessage());
        }
    }
}
