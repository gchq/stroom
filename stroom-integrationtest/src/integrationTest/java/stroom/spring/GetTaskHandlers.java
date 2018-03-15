package stroom.spring;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import stroom.explorer.ExplorerActionHandler;
import stroom.importexport.ImportExportActionHandler;
import stroom.pipeline.factory.Element;
import stroom.task.TaskHandler;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.AbstractProcessIntegrationTest;

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

public class GetTaskHandlers {
    private static final String PACKAGE = "stroom";

    public static void main(final String[] args) {
        new GetTaskHandlers().run();
    }

    public void run() {
        final StringBuilder sb = new StringBuilder();

            new FastClasspathScanner(PACKAGE)
                    .matchAllClasses(classRef -> {
                        if (AbstractProcessIntegrationTest.class.isAssignableFrom(classRef)) {
                            sb.append("        ");
                            sb.append(classRef.getName());
                            sb.append(".class,\n");
                        }
                    })
                    .scan();
        System.out.println(sb.toString());
    }
}
