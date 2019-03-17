package stroom.streamtask.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.task.server.TaskContext;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

class FileWalker {
    private final Logger LOGGER = LoggerFactory.getLogger(FileWalker.class);

    void walk(final Path dir, final FileFilter fileFilter, final FileProcessor fileProcessor, final TaskContext taskContext) {
        taskContext.setName("Walk Repository - " + FileUtil.getCanonicalPath(dir));
        try {
            Files.walkFileTree(dir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new AbstractFileVisitor() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    taskContext.info(FileUtil.getCanonicalPath(file));

                    if (taskContext.isTerminated()) {
                        return FileVisitResult.TERMINATE;
                    }

                    if (fileFilter.match(file, attrs)) {
                        fileProcessor.process(file, attrs);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    interface FileProcessor {
        void process(Path file, BasicFileAttributes attrs);
    }

    interface FileFilter {
        boolean match(Path file, BasicFileAttributes attrs);
    }
}