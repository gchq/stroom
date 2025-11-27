package stroom.aws.s3.impl;

import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDictCompress;
import com.github.luben.zstd.ZstdDictTrainer;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.google.common.base.Strings;
import event.logging.AuthenticateAction;
import event.logging.AuthenticateEventAction;
import event.logging.Data;
import event.logging.Device;
import event.logging.Event;
import event.logging.EventDetail;
import event.logging.EventSource;
import event.logging.EventTime;
import event.logging.SystemDetail;
import event.logging.User;
import event.logging.impl.DefaultEventLoggingService;
import event.logging.impl.LogReceiver;
import event.logging.impl.LogReceiverFactory;
import net.datafaker.Faker;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.IOUtils;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ZstdComparison {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZstdComparison.class);

    public static final long RANDOM_SEED = 57498237573L;
    public static final Instant START_INSTANT = LocalDateTime.of(
                    2025, 4, 24, 10, 54, 32, 0)
            .toInstant(ZoneOffset.UTC);
    public static final String COMBINED_SUB_DIR = "combined";
    public static final String INDIVIDUAL_SUB_DIR = "individual";

    private static final Pattern BASE_NAME_PATTERN = Pattern.compile("^[0-9]+(_[0-9]+)?\\.");
    private static final int SECS_IN_ONE_YEAR = 365 * 24 * 60 * 60;

    public static void main(final String[] args) throws IOException {
        if (args == null || args.length != 3) {
            errorExit("""
                    Invalid arguments!
                    Usage: java -jar jar_file outputDir fileCount recordsPerFile""");
        } else {
            final String pathStr = args[0];
            if (NullSafe.isBlankString(pathStr)) {
                errorExit("outputDir '" + pathStr + "' is blank.");
            }
            final Path outputDir = Path.of(args[0]);
            if (!Files.isDirectory(outputDir)) {
                errorExit("outputDir '" + pathStr + "' is not a directory.");
            }

            final int fileCount = Integer.parseInt(args[1]);
            final int recordsPerFile = Integer.parseInt(args[2]);

            new ZstdComparison().run(outputDir, fileCount, recordsPerFile);
        }
    }

    private void run(final Path outputDir,
                     final int fileCount,
                     final int recordsPerFile) {

        final DefaultEventLoggingService eventLoggingService = new DefaultEventLoggingService();
        System.setProperty("event.logging.logreceiver", FileLogReceiver.class.getName());
        final FileLogReceiver logReceiver = (FileLogReceiver) LogReceiverFactory.getInstance().getLogReceiver();

        logReceiver.setOutputDir(outputDir);

        int recCnt = 0;
        for (int streamIdx = 0; streamIdx < fileCount; streamIdx++) {
            final Random random = new Random(RANDOM_SEED + streamIdx);
            final Faker faker = new Faker();
            logReceiver.setStreamIdx(streamIdx);

            for (int recIdx = 0; recIdx < recordsPerFile; recIdx++) {
                logReceiver.setRecordIdx(recIdx);
                final String firstName = faker.name().firstName();
                final String lastName = faker.name().lastName();
                final User user = User.builder()
                        .withId(firstName + "-" + lastName)
                        .withName(firstName + " " + lastName)
                        .withEmailAddress(firstName + "." + lastName + "@somedomain.com")
                        .addData(Data.builder()
                                .withName("bloodGroup")
                                .withValue(faker.bloodtype().bloodGroup())
                                .build())
                        .addData(Data.builder()
                                .withName("address")
                                .withValue(faker.address().fullAddress())
                                .build())
                        .addData(Data.builder()
                                .withName("sex")
                                .withValue(faker.demographic().sex())
                                .build())
                        .build();
                final Event event = Event.builder()
                        .withEventTime(EventTime.builder()
                                .withTimeCreated(randomDate(random))
                                .build())
                        .withEventSource(EventSource.builder()
                                .withSystem(SystemDetail.builder()
                                        .withName("MY_SYSTEM")
                                        .withEnvironment("DEV")
                                        .withVersion("1.2.3")
                                        .build())
                                .withGenerator("MY_GENERATOR")
                                .withDevice(Device.builder()
                                        .withHostName(faker.internet().domainName())
                                        .withIPAddress(faker.internet().ipV4Address())
                                        .build())
                                .withClient(Device.builder()
                                        .withHostName(faker.internet().domainName())
                                        .withIPAddress(faker.internet().ipV4Address())
                                        .build())
                                .withServer(Device.builder()
                                        .withHostName(faker.internet().domainName())
                                        .withIPAddress(faker.internet().ipV4Address())
                                        .build())
                                .withUser(user)
                                .build())
                        .withEventDetail(EventDetail.builder()
                                .withTypeId("type-" + random.nextInt(0, 20))
                                .withDescription(faker.text().text(30))
                                .withEventAction(AuthenticateEventAction.builder()
                                        .withUser(user)
                                        .withAction(AuthenticateAction.LOGON)
                                        .withDevice(Device.builder()
                                                .withHostName(faker.internet().domainName())
                                                .withIPAddress(faker.internet().ipV4Address())
                                                .build())
                                        .build())
                                .build())
                        .build();

                eventLoggingService.log(event);
                recCnt++;
            }
            logReceiver.closeStreamFile();
        }
        LOGGER.info("Finished recCnt: " + recCnt);
    }

    private Date randomDate(final Random random) {
        return new Date(START_INSTANT.plusSeconds(random.nextInt(0, SECS_IN_ONE_YEAR)).toEpochMilli());
    }

    private static void errorExit(final String msg) {
        System.err.println(msg);
        System.exit(1);
    }


    // --------------------------------------------------------------------------------


    public static class FileLogReceiver implements LogReceiver {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileLogReceiver.class);
        // Some useful info on training here https://github.com/facebook/zstd/issues/3769
        private static final int DICT_SIZE_BYTES = 100 * 1024;
        // Docs suggest that the training sample size should be ~100x the dict size
        private static final int SAMPLE_SIZE_BYTES = DICT_SIZE_BYTES * 100;
        private static final int COMPRESSION_LEVEL = 10; // Level 10 seems similar to gzip in time taken

        private Path streamFile = null;
        private Path framedZstdFile = null;
        private BufferedWriter bufferedWriter = null;
        private int streamIdx;
        private int recordIdx;
        private Path outputDir;
        private final ZstdDictTrainer zstdDictTrainer;
        private boolean createdDict = false;
        private byte[] dict = null;
        private ZstdOutputStream framedZstdOutputStream;

        public FileLogReceiver() {
            zstdDictTrainer = new ZstdDictTrainer(SAMPLE_SIZE_BYTES, DICT_SIZE_BYTES, COMPRESSION_LEVEL);
        }

        public void setOutputDir(final Path outputDir) {
            this.outputDir = outputDir;
            try {
                Files.createDirectories(outputDir.resolve(INDIVIDUAL_SUB_DIR));
                Files.createDirectories(outputDir.resolve(COMBINED_SUB_DIR));
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void setStreamIdx(final int streamIdx) {
            this.streamIdx = streamIdx;
            this.streamFile = getStreamFile();
            final String dictExt = dict != null
                    ? ".dict"
                    : ".nodict";
            this.framedZstdFile = appendExtension(streamFile, dictExt + ".frames.zst");
            try {
                Files.createFile(streamFile);
                this.bufferedWriter = new BufferedWriter(new FileWriter(streamFile.toFile()));

                this.framedZstdOutputStream = createZstdOutputStream(framedZstdFile, dict != null);
                // So we can make one frame per event
                this.framedZstdOutputStream.setCloseFrameOnFlush(true);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void setRecordIdx(final int recordIdx) {
            this.recordIdx = recordIdx;
        }

        public void closeStreamFile() {
            if (bufferedWriter != null) {
                try {
                    bufferedWriter.flush();
                    bufferedWriter.close();
                    LOGGER.info("Written file " + streamFile);
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }
            if (!createdDict) {
                final Path dictFile = outputDir.resolve("dictionary");
                final DurationTimer timer = DurationTimer.start();
                this.dict = zstdDictTrainer.trainSamples();
                LOGGER.info("Created dict {} in {}", dictFile, timer);
                try {
                    // Write the dict to file
                    Files.write(
                            dictFile,
                            dict,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE);
                    createdDict = true;
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }
            }

            LOGGER.logDurationIfInfoEnabled(() ->
                            writeGzipFile(streamFile),
                    "writeGzipFile");
            LOGGER.logDurationIfInfoEnabled(() ->
                            writeZstdFile(streamFile, false),
                    "writeZstdFile (dict)");
            LOGGER.logDurationIfInfoEnabled(() ->
                            writeZstdFile(streamFile, false),
                    "writeZstdFile (no dict)");

            try {
                framedZstdOutputStream.close();
                framedZstdOutputStream = null;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }

            writeSummaryFile();
            streamFile = null;
        }

        @Override
        public void log(final String data) {
            Objects.requireNonNull(streamFile);
            final byte[] bytes = (data + "\n").getBytes(StandardCharsets.UTF_8);
            try {
                if (!createdDict) {
                    zstdDictTrainer.addSample(bytes);
                }
                // Append the event to the stream file
                bufferedWriter.append(data);

                // Write the event as a frame
                framedZstdOutputStream.write(bytes);
                framedZstdOutputStream.flush();
                // Write the individual file
                final Path uncompressedIndividualFile = getIndividualFile();
                Files.writeString(
                        uncompressedIndividualFile,
                        data,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);

                LOGGER.logDurationIfTraceEnabled(() ->
                                writeZstdFile(uncompressedIndividualFile, bytes, true),
                        "writeZstdFile (dict)");
                LOGGER.logDurationIfTraceEnabled(() ->
                                writeZstdFile(uncompressedIndividualFile, bytes, false),
                        "writeZstdFile (no dict)");
                LOGGER.logDurationIfTraceEnabled(() ->
                                writeGzipFile(uncompressedIndividualFile, bytes),
                        "writeGzipFile");

            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void writeSummaryFile() {
            final Path dir = getIndividualFilesDir();
            try (final Stream<Path> pathStream = Files.list(dir)) {
                final Map<String, Long> totals = pathStream.map(path -> {
                            final String ext = BASE_NAME_PATTERN.matcher(path.getFileName().toString())
                                    .replaceAll("");
                            try {
                                final long size = Files.size(path);
                                return Map.entry(ext, size);
                            } catch (final IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        })
                        .collect(Collectors.groupingBy(Entry::getKey, Collectors.summingLong(Entry::getValue)));

                final Path totalsFile = dir.resolve("_totals.txt");
                final String str = totals.entrySet()
                        .stream()
                        .sorted(Entry.comparingByKey())
                        .map(entry -> Strings.padEnd(entry.getKey(), 20, ' ')
                                      + ": "
                                      + ModelStringUtil.formatCsv(entry.getValue()))
                        .collect(Collectors.joining("\n"));
                Files.writeString(totalsFile, str + "\n", StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void writeGzipFile(final Path uncompressedIndividualFile) {
            try {
                final Path compressedIndividualFile = appendExtension(uncompressedIndividualFile, ".gz");
                try (final GzipCompressorOutputStream outputStream = new GzipCompressorOutputStream(
                        new FileOutputStream(compressedIndividualFile.toFile()))) {
                    try (final FileInputStream fileInputStream = new FileInputStream(uncompressedIndividualFile.toFile())) {
                        IOUtils.copy(fileInputStream, outputStream);
                    }
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void writeGzipFile(final Path uncompressedIndividualFile, final byte[] bytes) {
            try {
                final Path compressedIndividualFile = appendExtension(uncompressedIndividualFile, ".gz");
                try (final GzipCompressorOutputStream outputStream = new GzipCompressorOutputStream(new FileOutputStream(
                        compressedIndividualFile.toFile()))) {
                    outputStream.write(bytes);
                    outputStream.flush();
                    outputStream.finish();
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void writeZstdFile(final Path uncompressedFile,
                                   final boolean useDict) {
            try {
                final Path compressedFile;
                if (dict != null && useDict) {
                    compressedFile = appendExtension(uncompressedFile, ".dict.zst");
                } else {
                    compressedFile = appendExtension(uncompressedFile, ".nodict.zst");
                }

                try (final ZstdOutputStream zstdOutputStream = createZstdOutputStream(compressedFile, useDict)) {
                    try (final FileInputStream fileInputStream = new FileInputStream(uncompressedFile.toFile())) {
                        IOUtils.copy(fileInputStream, zstdOutputStream);
                    }
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void verifyZstdFile(final Path uncompressedFile,
                                    final boolean useDict) {
            try {
                final Path compressedFile;
                if (dict != null && useDict) {
                    compressedFile = appendExtension(uncompressedFile, ".dict.zst");
                } else {
                    compressedFile = appendExtension(uncompressedFile, ".nodict.zst");
                }

                try (ZstdInputStream zstdInputStream = createZstdInputStream(compressedFile, useDict)) {
                    final Path tempFile = appendExtension(compressedFile, ".uncompressed");


                }


                try (final ZstdOutputStream zstdOutputStream = createZstdOutputStream(compressedFile, useDict)) {
                    try (final FileInputStream fileInputStream = new FileInputStream(uncompressedFile.toFile())) {
                        IOUtils.copy(fileInputStream, zstdOutputStream);
                    }
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private ZstdInputStream createZstdInputStream(final Path inputFile,
                                                      final boolean useDict) {
            try {
                final ZstdInputStream zstdInputStream = new ZstdInputStream(
                        new FileInputStream(inputFile.toFile()));
                if (useDict && dict != null) {
                    zstdInputStream.setDict(dict);
                }
                return zstdInputStream;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private ZstdOutputStream createZstdOutputStream(final Path outputFile,
                                                        final boolean useDict) {
            try {
                final ZstdOutputStream zstdOutputStream = new ZstdOutputStream(
                        new FileOutputStream(outputFile.toFile()));
                zstdOutputStream.setLevel(COMPRESSION_LEVEL);
                if (useDict && dict != null) {
                    zstdOutputStream.setDict(dict);
                }
                return zstdOutputStream;
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private void writeZstdFile(final Path uncompressedIndividualFile,
                                   final byte[] bytes,
                                   final boolean useDict) {
            try {
                final byte[] compressedBytes;
                final Path compressedIndividualFile;
                if (dict != null && useDict) {
                    compressedIndividualFile = appendExtension(uncompressedIndividualFile, ".dict.zst");
                    compressedBytes = LOGGER.logDurationIfDebugEnabled(() -> {
                        try (final ZstdDictCompress zstdDictCompress = new ZstdDictCompress(dict, COMPRESSION_LEVEL)) {
                            return Zstd.compress(bytes, zstdDictCompress);
                        }
                    }, () -> LogUtil.message("Compress {} with dict", compressedIndividualFile));
                } else {
                    compressedIndividualFile = appendExtension(uncompressedIndividualFile, ".nodict.zst");
                    compressedBytes = LOGGER.logDurationIfDebugEnabled(() ->
                                    Zstd.compress(bytes, COMPRESSION_LEVEL),
                            () -> LogUtil.message("Compress {} without dict", compressedIndividualFile));
                }
                Files.write(
                        compressedIndividualFile,
                        compressedBytes,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private String getPaddedStreamIdx() {
            return Strings.padStart(
                    String.valueOf(streamIdx),
                    6,
                    '0');
        }

        private String getPaddedRecordIdx() {
            return Strings.padStart(
                    String.valueOf(recordIdx),
                    10,
                    '0');
        }

        private Path getStreamFile() {
            final String fileName = getPaddedStreamIdx() + ".xml";
            return outputDir.resolve(COMBINED_SUB_DIR)
                    .resolve(fileName)
                    .toAbsolutePath();
        }

        private Path getIndividualFilesDir() {
            final Path dir = outputDir.resolve(INDIVIDUAL_SUB_DIR)
                    .resolve(getPaddedStreamIdx());
            try {
                Files.createDirectories(dir);
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
            return dir;
        }

        private Path getIndividualFile() {
            return getIndividualFilesDir()
                    .resolve(getPaddedStreamIdx() + "_" + getPaddedRecordIdx() + ".xml")
                    .toAbsolutePath();
        }

        private Path appendExtension(final Path file, final String extension) {
            return file.getParent().resolve(file.getFileName().toString() + extension);
        }
    }
}
