package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.util.string.StringIdUtil;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MessageStore implements EventConsumer, AutoCloseable {

    private final Path file;
    private final SeekableByteChannel channel;

    private final ByteBuffer longBuffer = ByteBuffer.wrap(new byte[Long.BYTES]);

    public MessageStore(final Path file) throws IOException {
        this.file = file;
        if (Files.isRegularFile(file)) {
            channel = Files.newByteChannel(file, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } else {
            channel = Files.newByteChannel(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
        }
    }

    @Override
    public synchronized void consume(final AttributeMap attributeMap,
                                     final Consumer<OutputStream> consumer) {
        try {
            final long entrySizePos = channel.position();
            writeLong(channel, 0);

            // Write attributes.
            final long attributesSizePos = channel.position();
            writeLong(channel, 0);

            final long attributesStart = channel.position();
            AttributeMapUtil.write(attributeMap, Channels.newOutputStream(channel));
            final long attributesEnd = channel.position();

            channel.position(attributesSizePos);
            writeLong(channel, attributesEnd - attributesStart);

            channel.position(attributesEnd);

            // Write data.
            final long dataSizePos = channel.position();
            writeLong(channel, 0);

            final long dataStart = channel.position();
            consumer.accept(Channels.newOutputStream(channel));
            final long dataEnd = channel.position();

            channel.position(dataSizePos);
            writeLong(channel, dataEnd - dataStart);

            channel.position(dataEnd);

            // Finally write entry size to complete the entry.
            final long entryEndPos = channel.position();
            channel.position(entrySizePos);
            writeLong(channel, entryEndPos - entrySizePos);
            channel.position(entryEndPos);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeLong(final SeekableByteChannel channel, final long num) throws IOException {
        longBuffer.rewind();
        longBuffer.putLong(num);
        longBuffer.flip();
        channel.write(longBuffer);
    }

    private long readLong(final SeekableByteChannel channel) throws IOException {
        longBuffer.rewind();
        channel.read(longBuffer);
        longBuffer.flip();
        return longBuffer.getLong();
    }

    public synchronized void toZip(final Path path) throws IOException {
        try (final SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            int entryCount = 0;
            final InputStream is = Channels.newInputStream(channel);

            try (final ZipOutputStream zipOutputStream =
                    new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
                while (channel.size() > channel.position()) {
                    final long entrySize = readLong(channel);

                    // Make sure we wrote an entry successfully.
                    if (entrySize > 0) {
                        final long attributesSize = readLong(channel);

                        final String idString = StringIdUtil.idToString(entryCount);

                        zipOutputStream.putNextEntry(new ZipEntry(idString + ".meta"));
                        for (long i = 0; i < attributesSize; i++) {
                            zipOutputStream.write(is.read());
                        }

                        final long dataSize = readLong(channel);

                        zipOutputStream.putNextEntry(new ZipEntry(idString + ".dat"));
                        for (long i = 0; i < dataSize; i++) {
                            zipOutputStream.write(is.read());
                        }

                        entryCount++;
                    }
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
