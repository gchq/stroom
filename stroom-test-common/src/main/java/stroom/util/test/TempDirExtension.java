package stroom.util.test;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.io.FileUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Used for injecting a newly created temporary directory into test instance variables
 * or test method arguments using the @TempDir annotation
 * A new temporary directory is created each time the annotation is used. For instance variable
 * annotations, a new temporary directory is created each time the class is instantiated.
 *
 * TODO This is a stop gap until Junit 5.4 is released which adds its own @TempDir extension
 */
public class TempDirExtension implements ParameterResolver, TestInstancePostProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TempDirExtension.class);

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
            .create("stroom", "util", "test", TempDirExtension.class.getSimpleName());

    @Override
    public boolean supportsParameter(final ParameterContext parameterContext,
                                     final ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.isAnnotated(TempDir.class);
    }

    @Override
    public Object resolveParameter(final ParameterContext parameterContext,
                                   final ExtensionContext extensionContext) throws ParameterResolutionException {

        return createTempDir(extensionContext);
    }

    @Override
    public void postProcessTestInstance(final Object testInstance, final ExtensionContext context) {

        getFieldsUpTo(testInstance.getClass(), Object.class)
                .filter(field ->
                        field.getAnnotation(TempDir.class) != null)
                .forEach(field -> {
                    field.setAccessible(true);
                    try {
                        field.set(testInstance, createTempDir(context));
                    } catch (IllegalAccessException iae) {
                        throw new RuntimeException(iae);
                    }
                });
    }

    private static Stream<Field> getFieldsUpTo(Class<?> startClass,
                                               @Nullable Class<?> exclusiveParent) {

        // get fields for startClass only
        final List<Field> currentClassFields = Arrays.asList(startClass.getDeclaredFields());

        final Class<?> parentClass = startClass.getSuperclass();
        Stream<Field> fieldStream = currentClassFields.stream();

        if (parentClass != null && !parentClass.equals(exclusiveParent)) {
            final Stream<Field> parentClassFields = getFieldsUpTo(parentClass, exclusiveParent);
            fieldStream = Stream.concat(fieldStream, parentClassFields);
        }

        return fieldStream;
    }

    private static Path createTempDir(final ExtensionContext extensionContext) {
        final String testClassName = extensionContext.getTestClass()
                .map(Class::getSimpleName)
                .orElse("UNKNOWN_CLASS");

//        final String storeKey = extensionContext.getTestMethod()
//                .map(method ->
//                    testClassName + "|" + method.getName())
//                .orElse(testClassName) + "|" + suffix;

//        final ClosablePath existingPath = extensionContext.getStore(NAMESPACE).get(storeKey, ClosablePath.class);
//
//        if (existingPath != null) {
//            LOGGER.info("Using existing temporary directory {} with storeKey {}",
//                    existingPath.getPath().toAbsolutePath().toString(),
//                    storeKey);
//            return existingPath.getPath();
//        } else {
            final Path tempDir = FileUtil.createTempDir(testClassName);
            final ClosablePath closablePath = new ClosablePath(tempDir);
            final String storeKey = tempDir.toAbsolutePath().toString();
            extensionContext.getStore(NAMESPACE).put(storeKey, closablePath);
            LOGGER.info("Created temporary directory {} with storeKey {}",
                    FileUtil.getCanonicalPath(tempDir),
                    storeKey);
            return tempDir;
//        }
    }

    private static class ClosablePath implements ExtensionContext.Store.CloseableResource {

        private final Path path;

        private ClosablePath(final Path path) {
            this.path = path;
        }

        Path getPath() {
            return path;
        }

        /**
         * Close underlying resources.
         *
         * @throws Throwable any throwable will be caught and rethrown
         */
        @Override
        public void close() throws Throwable {
            LOGGER.debug("Deleting temporary directory (and its contents) {}", FileUtil.getCanonicalPath(path));
            if (!FileUtil.deleteContents(path)) {
                throw new IOException("Unable to delete " + FileUtil.getCanonicalPath(path));
            }
        }
    }
}

