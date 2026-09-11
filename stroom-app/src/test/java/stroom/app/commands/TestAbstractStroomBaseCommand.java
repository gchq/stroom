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

package stroom.app.commands;

import stroom.config.app.Config;

import com.google.inject.Injector;
import com.google.inject.Module;
import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock("SYSTEM_OUT")
class TestAbstractStroomBaseCommand {

    @Test
    void infoWritesFormattedMessageToStdout() {
        final PrintStream originalOut = System.out;
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (final PrintStream printStream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(printStream);
            new TestCommand().writeInfo("Created {} for {}", "key", "user");
        } finally {
            System.setOut(originalOut);
        }

        final long standaloneLines = output.toString(StandardCharsets.UTF_8)
                .lines()
                .filter("Created key for user"::equals)
                .count();
        assertThat(standaloneLines).isOne();
    }

    private static final class TestCommand extends AbstractStroomBaseCommand {
        private static final Logger LOGGER = LoggerFactory.getLogger(TestCommand.class);

        private TestCommand() {
            super(Path.of("test.yml"), "test", "test command");
        }

        private void writeInfo(final String template, final Object... args) {
            info(LOGGER, template, args);
        }

        @Override
        protected void runCommand(final Bootstrap<Config> bootstrap,
                                  final Namespace namespace,
                                  final Config config,
                                  final Injector injector) {
        }

        @Override
        protected Set<String> getArgumentNames() {
            return Set.of();
        }

        @Override
        protected Optional<Module> getChildInjectorModule() {
            return Optional.empty();
        }
    }
}
