package stroom.test;

import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.test.common.data.DataGenerator;
import stroom.test.common.data.DataWriter;
import stroom.test.common.data.FlatDataWriterBuilder;
import stroom.test.common.data.XmlAttributesDataWriterBuilder;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogUtil;

import io.vavr.Tuple;
import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.inject.Inject;

public class SampleDataGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SampleDataGenerator.class);

    private static final String multipleLanguagesFileName = "multiple_languages.json";

//    private final StoreCreationTool storeCreationTool;

    private final Path templatesDir;

    private final List<CompletableFuture<Void>> futures = new ArrayList<>();

    @Inject
    public SampleDataGenerator() {
        templatesDir = StroomCoreServerTestFileUtil.getTestResourcesDir()
                .resolve(SetupSampleDataBean.ROOT_DIR_NAME)
                .resolve("templates");
    }

    /**
     * To aid testing the generation without running {@link SetupSampleData}
     */
    public static void main(String[] args) {
        final Path dir = StroomCoreServerTestFileUtil.getTestResourcesDir()
                .resolve(SetupSampleDataBean.ROOT_DIR_NAME)
                .resolve("generated")
                .resolve("input");

        new SampleDataGenerator()
                .generateData(dir);
    }

    public void generateData(final Path dir) {

        ensureAndCleanDir(dir);

        generateDataViewingData(dir);

        generateRefDataForEffectiveDateTesting(dir);

        generateCharsetData(dir);

        LOGGER.info("Waiting for {} async tasks to complete", futures.size());
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        LOGGER.info("Completed generation");
    }

    private void generateDataViewingData(final Path dir) {
        final int shortLoremText = 4;
        final int longLoremText = 200;
        // Increment the random seed each time so each data set has different but predictable data
        long randomSeed = 0;

        final DataWriter csvDataWriter = FlatDataWriterBuilder.builder()
                .delimitedBy(",")
                .enclosedBy("\"")
                .outputHeaderRow(true)
                .build();

        final DataWriter xmlDataWriter = XmlAttributesDataWriterBuilder.builder()
                .namespace("records:2")
                .rootElementName("records")
                .recordElementName("record")
                .fieldValueElementName("data")
                .build();


        // Data that has one record per line
        // One with long lines, one with short
        generateDataViewRawData(
                dir,
                1,
                "DATA_VIEWING_MULTI_LINE-EVENTS",
                "\n",
                csvDataWriter,
                shortLoremText,
                LocalDateTime.of(2020, 6, 1, 0, 0),
                randomSeed++);

        generateDataViewRawData(
                dir,
                2,
                "DATA_VIEWING_MULTI_LINE-EVENTS",
                "\n",
                csvDataWriter,
                longLoremText,
                LocalDateTime.of(2020, 7, 1, 0, 0),
                randomSeed++);

        // Data that is all on one massive single line
        generateDataViewRawData(
                dir,
                1,
                "DATA_VIEWING_SINGLE_LINE-EVENTS",
                "|",
                csvDataWriter,
                shortLoremText,
                LocalDateTime.of(2020, 8, 1, 0, 0),
                randomSeed++);

        // XML data that is all on one massive single line
        generateDataViewRawData(
                dir,
                1,
                "DATA_VIEWING_XML_SINGLE_LINE-EVENTS",
                "",
                xmlDataWriter,
                shortLoremText,
                LocalDateTime.of(2020, 9, 1, 0, 0),
                randomSeed++);

        // Data that has one record per line
        // One with long lines, one with short
        generateDataViewRawData(
                dir,
                1,
                "DATA_VIEWING_XML_MULTI_LINE-EVENTS",
                "\n",
                xmlDataWriter,
                shortLoremText,
                LocalDateTime.of(2020, 10, 1, 0, 0),
                randomSeed++);

        generateDataViewRawData(
                dir,
                2,
                "DATA_VIEWING_XML_MULTI_LINE-EVENTS",
                "\n",
                xmlDataWriter,
                longLoremText,
                LocalDateTime.of(2020, 11, 1, 0, 0),
                randomSeed++);

    }

    private void generateDataViewRawData(final Path dir,
                                         final int fileNo,
                                         final String feedName,
                                         final String recordSeparator,
                                         final DataWriter dataWriter,
                                         final int loremWordCount,
                                         final LocalDateTime startDate,
                                         final long randomSeed) {
        final Path file = makeInputFilePath(dir, fileNo, feedName);

        futures.add(CompletableFuture.runAsync(() -> {
            LOGGER.info("Generating file {}", file.toAbsolutePath().normalize());
            DataGenerator.buildDefinition()
                    .addFieldDefinition(DataGenerator.randomDateTimeField(
                            "dateTime",
                            startDate,
                            startDate.plusDays(28),
                            DateTimeFormatter.ISO_DATE_TIME
                    ))
                    .addFieldDefinition(DataGenerator.randomIpV4Field(
                            "machineIp"))
                    .addFieldDefinition(DataGenerator.fakerField(
                            "machineMacAddress",
                            faker -> faker.internet().macAddress()
                    ))
                    .addFieldDefinition(DataGenerator.fakerField(
                            "firstName",
                            faker -> faker.name().firstName()))
                    .addFieldDefinition(DataGenerator.fakerField(
                            "surname",
                            faker -> faker.name().lastName()))
                    .addFieldDefinition(DataGenerator.fakerField(
                            "username",
                            faker -> faker.name().username()))
                    .addFieldDefinition(DataGenerator.fakerField(
                            "bloodGroup",
                            faker -> faker.bloodtype().bloodGroup()))
                    .addFieldDefinition(DataGenerator.randomEmoticonEmojiField(
                            "emotionalState"))
                    .addFieldDefinition(DataGenerator.fakerField(
                            "address",
                            faker -> faker.address().fullAddress()))
                    .addFieldDefinition(DataGenerator.fakerField(
                            "company",
                            faker -> faker.company().name()))
                    .addFieldDefinition(DataGenerator.fakerField(
                            "companyLogo",
                            faker -> faker.company().logo()))
                    .addFieldDefinition(DataGenerator.fakerField(
                            "lorum",
                            faker -> String.join(" ", faker.lorem().words(loremWordCount))))
                    .setDataWriter(dataWriter)
                    .consumedBy(DataGenerator.getFileOutputConsumer(file, recordSeparator))
                    .rowCount(2_000)
                    .withRandomSeed(randomSeed)
                    .generate();
        }));
    }

    private void generateRefDataForEffectiveDateTesting(final Path dir) {
        final String refFeed = "USER_TO_EFF_DATE-REFERENCE";
        final String eventsFeed = "TEST_REFERENCE_DATA_EFF_DATE-EVENTS";

        final int dateCount = 10;
        final int userCount = 10;
        final LocalDateTime startTime = LocalDateTime.of(
                2010, 1, 1, 0, 0, 0);
        LocalDateTime effectiveDateTime = startTime;

        for (int i = 1; i <= dateCount; i++) {
            final int finalI = i;
            final LocalDateTime finalEffectiveDateTime = effectiveDateTime;

            futures.add(CompletableFuture.runAsync(() -> {
                final Path refFile = makeInputFilePath(dir, finalI, finalEffectiveDateTime, refFeed);
                LOGGER.info("Generating file {}", refFile.toAbsolutePath().normalize().toString());

                DataGenerator.buildDefinition()
                        .addFieldDefinition(DataGenerator.sequentiallyNumberedValueField(
                                "user",
                                "user%s",
                                1,
                                userCount + 1))
                        .addFieldDefinition(DataGenerator.sequentiallyNumberedValueField(
                                "effectiveDateTime",
                                "user%s-" + finalEffectiveDateTime.toString(),
                                1,
                                userCount + 1))
                        .setDataWriter(XmlAttributesDataWriterBuilder.builder()
                                .namespace("records:2")
                                .build())
                        .consumedBy(DataGenerator.getFileOutputConsumer(refFile))
                        .rowCount(userCount)
                        .generate();

            }));
            effectiveDateTime = effectiveDateTime.plusDays(1);
        }

        futures.add(CompletableFuture.runAsync(() -> {
            final Path eventsFile = makeInputFilePath(dir, 1, eventsFeed);
            LOGGER.info("Generating file {}", eventsFile.toAbsolutePath().normalize());

            DataGenerator.buildDefinition()
                    .addFieldDefinition(DataGenerator.sequentiallyNumberedValueField(
                            "user",
                            "user%s",
                            1,
                            userCount + 1))
                    .addFieldDefinition(DataGenerator.sequentialDateTimeField(
                            "time",
                            startTime.plusHours(1),
                            Duration.ofDays(1),
                            DateTimeFormatter.ISO_DATE_TIME))
                    .setDataWriter(XmlAttributesDataWriterBuilder.builder()
                            .namespace("records:2")
                            .build())
                    .consumedBy(DataGenerator.getFileOutputConsumer(eventsFile))
                    .rowCount(userCount)
                    .multiThreaded()
                    .generate();
        }));
    }

    private void generateCharsetData(final Path dir) {

        final Path multipleLanguagesFile = templatesDir.resolve(multipleLanguagesFileName);
        final AtomicInteger counter = new AtomicInteger(0);

        try {
            final String sourceContent = Files.readString(
                    multipleLanguagesFile, StandardCharsets.UTF_8);

            Stream.of(
                            Tuple.of("UTF8_BOM", StandardCharsets.UTF_8, ByteOrderMark.UTF_8),
                            Tuple.of("UTF8_NO_BOM", StandardCharsets.UTF_8, (ByteOrderMark) null),

                            // Stroom doesn't support straight UTF16
                            Tuple.of("UTF16LE_BOM", StandardCharsets.UTF_16LE, ByteOrderMark.UTF_16LE),
                            Tuple.of("UTF16LE_NO_BOM", StandardCharsets.UTF_16LE, (ByteOrderMark) null),
                            Tuple.of("UTF16BE_BOM", StandardCharsets.UTF_16BE, ByteOrderMark.UTF_16BE),
                            Tuple.of("UTF16BE_NO_BOM", StandardCharsets.UTF_16BE, (ByteOrderMark) null),

                            // Stroom doesn't support straight UTF32
                            Tuple.of("UTF32LE_BOM", Charset.forName("UTF-32LE"), ByteOrderMark.UTF_32LE),
                            Tuple.of("UTF32LE_NO_BOM", Charset.forName("UTF-32LE"), (ByteOrderMark) null),
                            Tuple.of("UTF32BE_BOM", Charset.forName("UTF-32BE"), ByteOrderMark.UTF_32BE),
                            Tuple.of("UTF32BE_NO_BOM", Charset.forName("UTF-32BE"), (ByteOrderMark) null)
                    )
                    .forEach(tuple3 -> {
                        final String feedName = "TEST_CHARSETS_" + tuple3._1() + "-REFERENCE";
                        generateDataForCharset(
                                feedName,
                                dir,
                                tuple3._2(),
                                tuple3._3(),
                                sourceContent,
                                counter.getAndIncrement());
                    });
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error reading file {}",
                    multipleLanguagesFile.toAbsolutePath()), e);
        }
    }

    private void generateDataForCharset(final String feedName,
                                        final Path dir,
                                        final Charset charset,
                                        final ByteOrderMark byteOrderMark,
                                        final String sourceContent,
                                        final int iteration) {
        futures.add(CompletableFuture.runAsync(() -> {
            LOGGER.info("Creating feed {} in {} for charset {} and BOM {}",
                    feedName, dir, charset, byteOrderMark);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            // Get the bytes of the desired charset
            byte[] sourceBytes = sourceContent.getBytes(charset);

            final Path file = makeInputFilePath(
                    dir,
                    1,
                    LocalDateTime.of(2020, 1, 1, 0, 0, 0)
                            .plusMonths(iteration),
                    feedName);

            LOGGER.info("Generating file {}, with charset {}, BOM {}",
                    file, charset, byteOrderMark);

            try (OutputStream outputStream = new FileOutputStream(file.toFile())) {

                // Write the BOM to the stream if we have one. We control the
                // presence of the BOM, not the java.
                if (byteOrderMark != null) {
                    byteArrayOutputStream.writeBytes(byteOrderMark.getBytes());
                }

                final BOMInputStream bomFreeInputStream = new BOMInputStream(
                        new ByteArrayInputStream(sourceBytes),
                        false);

                // now write the encoded bytes without the BOM
                bomFreeInputStream.transferTo(byteArrayOutputStream);

                byteArrayOutputStream.writeTo(outputStream);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
    }

    private Path makeInputFilePath(final Path dir,
                                   final int index,
                                   final String feedName) {
        return dir.resolve(feedName + "~" + index + ".in");
    }

    private Path makeInputFilePath(final Path dir,
                                   final int index,
                                   final LocalDateTime effectiveDate,
                                   final String feedName) {
        final String effectiveDateStr = DataLoader.EFFECTIVE_DATE_FORMATTER.format(effectiveDate);
        return dir.resolve(feedName + "~" + index + "~" + effectiveDateStr + ".in");
    }

    private void ensureAndCleanDir(final Path dir) {
        try {
            Files.createDirectories(dir);
            LOGGER.info("Clearing contents of {}", dir.toAbsolutePath().normalize());
            FileUtil.deleteContents(dir);
        } catch (IOException e) {
            throw new RuntimeException(LogUtil.message("Error ensuring directory {} exists",
                    dir.toAbsolutePath().normalize()), e);
        }
    }

}
