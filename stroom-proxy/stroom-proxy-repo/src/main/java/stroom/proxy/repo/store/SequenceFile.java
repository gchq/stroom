package stroom.proxy.repo.store;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

class SequenceFile {

    private final Path sequenceFile;
    private final Path tempFile;

    public SequenceFile(final Path dir) {
        sequenceFile = dir.resolve("sequenceid.bin");
        tempFile = dir.resolve("sequenceid.temp");
    }

    public synchronized long read() throws IOException {
        long num = 0;
        if (Files.exists(sequenceFile)) {
            try (final DataInputStream inputStream =
                    new DataInputStream(new BufferedInputStream(Files.newInputStream(sequenceFile)))) {
                num = inputStream.readLong();
            }
        }
        return num;
    }

    public synchronized void write(final long num) throws IOException {
        try (final DataOutputStream outputStream =
                new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
            outputStream.writeLong(num);
        }
        Files.move(tempFile, sequenceFile,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
