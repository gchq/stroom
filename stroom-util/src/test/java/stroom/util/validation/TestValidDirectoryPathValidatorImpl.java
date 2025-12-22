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

package stroom.util.validation;

import stroom.util.io.SimplePathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.validation.ValidDirectoryPath;

import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestValidDirectoryPathValidatorImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestValidDirectoryPathValidatorImpl.class);

    @Mock
    private ConstraintValidatorContext mockConstraintValidatorContext;
    @Captor
    private ArgumentCaptor<String> msgCaptor;

    @ValidDirectoryPath(ensureExistence = true)
    @Test
    public void testIsValid_ensureExists_notExists(@TempDir final Path tempDir) throws NoSuchMethodException {
        final ValidDirectoryPathValidatorImpl validDirectoryPathValidator = new ValidDirectoryPathValidatorImpl(
                new SimplePathCreator(() -> tempDir, () -> tempDir));

        final String methodName = new Object() {
        }
                .getClass()
                .getEnclosingMethod()
                .getName();

        final ValidDirectoryPath validDirectoryPathAnno = TestValidDirectoryPathValidatorImpl.class.getMethod(
                        methodName, Path.class)
                .getAnnotation(ValidDirectoryPath.class);

        validDirectoryPathValidator.initialize(validDirectoryPathAnno);
        final Path dir = tempDir.resolve("notExists");

        assertThat(dir)
                .doesNotExist();
        LOGGER.debug("Testing {}", dir);

        final boolean isValid = validDirectoryPathValidator.isValid(dir.toString(), mockConstraintValidatorContext);

        assertIsValid(isValid, dir);
    }

    @ValidDirectoryPath(ensureExistence = true)
    @Test
    public void testIsValid_ensureExists_dirExists(@TempDir final Path tempDir)
            throws NoSuchMethodException, IOException {

        final ValidDirectoryPathValidatorImpl validDirectoryPathValidator = new ValidDirectoryPathValidatorImpl(
                new SimplePathCreator(() -> tempDir, () -> tempDir));

        final String methodName = new Object() {
        }
                .getClass()
                .getEnclosingMethod()
                .getName();

        final ValidDirectoryPath validDirectoryPathAnno = TestValidDirectoryPathValidatorImpl.class.getMethod(
                        methodName, Path.class)
                .getAnnotation(ValidDirectoryPath.class);

        validDirectoryPathValidator.initialize(validDirectoryPathAnno);
        final Path dir = tempDir.resolve("exists");
        Files.createDirectories(dir);

        assertThat(dir)
                .exists();
        LOGGER.debug("Testing {}", dir);

        final boolean isValid = validDirectoryPathValidator.isValid(dir.toString(), mockConstraintValidatorContext);

        assertIsValid(isValid, dir);
    }

    @ValidDirectoryPath()
    @Test
    public void testIsValid_dirExists(@TempDir final Path tempDir) throws NoSuchMethodException, IOException {
        final ValidDirectoryPathValidatorImpl validDirectoryPathValidator = new ValidDirectoryPathValidatorImpl(
                new SimplePathCreator(() -> tempDir, () -> tempDir));

        final String methodName = new Object() {
        }
                .getClass()
                .getEnclosingMethod()
                .getName();

        final ValidDirectoryPath validDirectoryPathAnno = TestValidDirectoryPathValidatorImpl.class.getMethod(
                        methodName, Path.class)
                .getAnnotation(ValidDirectoryPath.class);

        validDirectoryPathValidator.initialize(validDirectoryPathAnno);
        final Path dir = tempDir.resolve("exists");
        Files.createDirectories(dir);

        assertThat(dir)
                .exists()
                .isWritable();
        LOGGER.debug("Testing {}", dir);

        final boolean isValid = validDirectoryPathValidator.isValid(dir.toString(), mockConstraintValidatorContext);

        assertIsValid(isValid, dir);
    }

    @ValidDirectoryPath(ensureExistence = true)
    @Test
    public void testIsValid_ensureExists_isFile(@TempDir final Path tempDir)
            throws NoSuchMethodException, IOException {

        final ValidDirectoryPathValidatorImpl validDirectoryPathValidator = new ValidDirectoryPathValidatorImpl(
                new SimplePathCreator(() -> tempDir, () -> tempDir));

        final String methodName = new Object() {
        }
                .getClass()
                .getEnclosingMethod()
                .getName();

        final ValidDirectoryPath validDirectoryPathAnno = TestValidDirectoryPathValidatorImpl.class.getMethod(
                        methodName, Path.class)
                .getAnnotation(ValidDirectoryPath.class);

        validDirectoryPathValidator.initialize(validDirectoryPathAnno);
        final Path path = tempDir.resolve("existsAsFile");
        Files.createFile(path);

        assertThat(path)
                .exists()
                .isRegularFile();
        LOGGER.debug("Testing {}", path);

        Mockito.when(mockConstraintValidatorContext.buildConstraintViolationWithTemplate(Mockito.anyString()))
                .thenReturn(Mockito.mock(ConstraintViolationBuilder.class));

        final boolean isValid = validDirectoryPathValidator.isValid(path.toString(), mockConstraintValidatorContext);

        assertThat(isValid)
                .isFalse();
        assertThat(path)
                .exists()
                .isRegularFile();

        Mockito.verify(mockConstraintValidatorContext, Mockito.times(1))
                .buildConstraintViolationWithTemplate(msgCaptor.capture());

        assertThat(msgCaptor.getAllValues().get(0))
                .containsIgnoringCase("path is not a directory");
    }

    @ValidDirectoryPath()
    @Test
    public void testIsValid_isFile(@TempDir final Path tempDir) throws NoSuchMethodException, IOException {
        final ValidDirectoryPathValidatorImpl validDirectoryPathValidator = new ValidDirectoryPathValidatorImpl(
                new SimplePathCreator(() -> tempDir, () -> tempDir));

        final String methodName = new Object() {
        }
                .getClass()
                .getEnclosingMethod()
                .getName();

        final ValidDirectoryPath validDirectoryPathAnno = TestValidDirectoryPathValidatorImpl.class.getMethod(
                        methodName, Path.class)
                .getAnnotation(ValidDirectoryPath.class);

        validDirectoryPathValidator.initialize(validDirectoryPathAnno);
        final Path path = tempDir.resolve("existsAsFile");
        Files.createFile(path);

        assertThat(path)
                .exists()
                .isRegularFile();
        LOGGER.debug("Testing {}", path);

        Mockito.when(mockConstraintValidatorContext.buildConstraintViolationWithTemplate(Mockito.anyString()))
                .thenReturn(Mockito.mock(ConstraintViolationBuilder.class));

        final boolean isValid = validDirectoryPathValidator.isValid(path.toString(), mockConstraintValidatorContext);

        assertThat(isValid)
                .isFalse();
        assertThat(path)
                .exists()
                .isRegularFile();

        Mockito.verify(mockConstraintValidatorContext, Mockito.times(1))
                .buildConstraintViolationWithTemplate(msgCaptor.capture());

        assertThat(msgCaptor.getAllValues().get(0))
                .containsIgnoringCase("path is not a directory");
    }

    @ValidDirectoryPath()
    @Test
    public void testIsValid_nullDir(@TempDir final Path tempDir) throws NoSuchMethodException, IOException {
        final ValidDirectoryPathValidatorImpl validDirectoryPathValidator = new ValidDirectoryPathValidatorImpl(
                new SimplePathCreator(() -> tempDir, () -> tempDir));

        final String methodName = new Object() {
        }
                .getClass()
                .getEnclosingMethod()
                .getName();

        final ValidDirectoryPath validDirectoryPathAnno = TestValidDirectoryPathValidatorImpl.class.getMethod(
                        methodName, Path.class)
                .getAnnotation(ValidDirectoryPath.class);

        validDirectoryPathValidator.initialize(validDirectoryPathAnno);
        final String dirStr = null;

        LOGGER.debug("Testing {}", dirStr);

        final boolean isValid = validDirectoryPathValidator.isValid(dirStr, mockConstraintValidatorContext);

        assertThat(isValid)
                .isTrue();
        Mockito.verify(mockConstraintValidatorContext, Mockito.never())
                .buildConstraintViolationWithTemplate(msgCaptor.capture());
    }

    @ValidDirectoryPath()
    @Test
    public void testIsValid_blankDir(@TempDir final Path tempDir) throws NoSuchMethodException, IOException {
        final ValidDirectoryPathValidatorImpl validDirectoryPathValidator = new ValidDirectoryPathValidatorImpl(
                new SimplePathCreator(() -> tempDir, () -> tempDir));

        final String methodName = new Object() {
        }
                .getClass()
                .getEnclosingMethod()
                .getName();

        final ValidDirectoryPath validDirectoryPathAnno = TestValidDirectoryPathValidatorImpl.class.getMethod(
                        methodName, Path.class)
                .getAnnotation(ValidDirectoryPath.class);

        validDirectoryPathValidator.initialize(validDirectoryPathAnno);
        final String dirStr = "  ";

        LOGGER.debug("Testing {}", dirStr);

        final boolean isValid = validDirectoryPathValidator.isValid(dirStr, mockConstraintValidatorContext);

        assertThat(isValid)
                .isTrue();
        Mockito.verify(mockConstraintValidatorContext, Mockito.never())
                .buildConstraintViolationWithTemplate(msgCaptor.capture());
    }

    private void assertIsValid(final boolean isValid, final Path path) {
        assertThat(isValid)
                .isTrue();
        assertThat(path)
                .exists()
                .isDirectory()
                .isWritable();

        Mockito.verify(mockConstraintValidatorContext, Mockito.never())
                .buildConstraintViolationWithTemplate(msgCaptor.capture());
    }
}
