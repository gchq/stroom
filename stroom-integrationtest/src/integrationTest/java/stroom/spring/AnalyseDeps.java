package stroom.spring;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AnalyseDeps {
    private static final String PACKAGE = "stroom";

    public static void main(final String[] args) {
        new AnalyseDeps().run();
    }

    public void run() {
        final Path dir = Paths.get("/Users/stroomdev66/analysis");
        try (final Writer writer = Files.newBufferedWriter(dir.resolve("packages.csv"))) {
            writer.write("creditor,debtor,amount,risk\n");

            new FastClasspathScanner(PACKAGE)
                    .matchClassesWithAnnotation(Configuration.class, from -> {
                        final String fromPackage = getPackage(from);

                        final Map<Class<?>, Ref> classRefMap = new HashMap<>();
                        final Map<String, AtomicInteger> packageRefMap = new HashMap<>();

                        final Constructor[] constructors = from.getConstructors();
                        for (final Constructor constructor : constructors) {
                            for (final Class to : constructor.getParameterTypes()) {
                                addRef(classRefMap, packageRefMap, to, RefType.CONSTRUCTOR);
                            }
                        }

                        final Field[] fields = from.getDeclaredFields();
                        for (final Field field : fields) {
                            final Class to = field.getType();
                            addRef(classRefMap, packageRefMap, to, RefType.FIELD);
                        }

                        final Method[] methods = from.getMethods();
                        for (final Method method : methods) {
                            for (final Class to : method.getParameterTypes()) {
                                addRef(classRefMap, packageRefMap, to, RefType.PARAM);
                            }
                        }

                        packageRefMap.forEach((toPackage, count) -> {
                            try {
                                writer.write(fromPackage);
                                writer.write(",");
                                writer.write(toPackage);
                                writer.write(",");
                                writer.write(count.toString());
                                writer.write(",");
                                writer.write("1");
                                writer.write("\n");
                            } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                    })
                    .scan();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addRef(final Map<Class<?>, Ref> classRefMap, final Map<String, AtomicInteger> packageRefMap, final Class<?> to, final RefType refType) {
        if (to.getName().startsWith(PACKAGE + ".")) {
            classRefMap.computeIfAbsent(to, Ref::new).increment(refType);
            packageRefMap.computeIfAbsent(getPackage(to), k -> new AtomicInteger()).incrementAndGet();
        }
    }

    private String getPackage(final Class<?> clazz) {
        String pkg = clazz.getName();
        int index = pkg.lastIndexOf(".");
        if (index != -1) {
            pkg = pkg.substring(0, pkg.lastIndexOf("."));
        }
        return pkg;
    }

    private enum RefType {
        CONSTRUCTOR, PARAM, FIELD;
    }

    private static class Ref {
        private final Class<?> refClass;
        private final Map<RefType, AtomicInteger> countByType = new HashMap<>();
        private final AtomicInteger totalCount = new AtomicInteger();

        Ref(final Class<?> refClass) {
            this.refClass = refClass;
        }

        void increment(final RefType refType) {
            countByType.computeIfAbsent(refType, k -> new AtomicInteger()).incrementAndGet();
            totalCount.incrementAndGet();
        }
    }
}
