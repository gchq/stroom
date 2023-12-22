package stroom.proxy.app.handler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

public class NumberedFileProvider {

    private final Path parentDir;
    private final AtomicLong sequence = new AtomicLong();

    public NumberedFileProvider(final Path parentDir) {
        this.parentDir = parentDir;
        final long minId = NumericFileNameUtil.getMinId(parentDir);
        sequence.set(minId);
    }

    public Path get(final String extension) throws IOException {
        final long id = sequence.incrementAndGet();
        final String name = NumericFileNameUtil.create(id);
        return parentDir.resolve(name + extension);
    }
}
