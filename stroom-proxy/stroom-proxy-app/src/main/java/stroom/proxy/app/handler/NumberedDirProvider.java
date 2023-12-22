package stroom.proxy.app.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public class NumberedDirProvider {

    private final Path parentDir;
    private final AtomicLong sequence = new AtomicLong();

    public NumberedDirProvider(final Path parentDir) {
        this.parentDir = parentDir;
        final long minId = NumericFileNameUtil.getMinId(parentDir);
        sequence.set(minId);
    }

    public Path get() throws IOException {
        final long id = sequence.incrementAndGet();
        final String name = NumericFileNameUtil.create(id);
        final Path path = parentDir.resolve(name);
        return Files.createDirectory(path);
    }
}
