package stroom.test;

import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.testdata.DataGenerator;
import stroom.testdata.FlatDataWriterBuilder;
import stroom.testdata.XmlAttributesDataWriterBuilder;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SampleDataGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(SampleDataGenerator.class);

    /**
     * To aid testing the generation without running {@link SetupSampleData}
     */
    public static void main(String[] args) {
        final Path dir = StroomCoreServerTestFileUtil.getTestResourcesDir()
                .resolve(SetupSampleDataBean.ROOT_DIR_NAME)
                .resolve("generated")
                .resolve("input");

        new SampleDataGenerator().generateData(dir);
    }

    public void generateData(final Path dir) {

        ensureAndCleanDir(dir);

        int shortLoremText = 4;
        int longLoremText = 200;
        // Increment the random seed each time so each data set has different but predictable data
        long randomSeed = 0;

        // Data that has one record per line
        // One with long lines, one with short
        generateDataViewRawData(
                dir,
                1,
                "DATA_VIEWING_MULTI_LINE-EVENTS",
                "\n",
                shortLoremText,
                LocalDateTime.of(2020,6,1,0,0),
                randomSeed++);

        generateDataViewRawData(
                dir,
                2,
                "DATA_VIEWING_MULTI_LINE-EVENTS",
                "\n",
                longLoremText,
                LocalDateTime.of(2020,7,1,0,0),
                randomSeed++);

        // Data that is all on one massive single line
        // One with long lines, one with short
        generateDataViewRawData(
                dir,
                1,
                "DATA_VIEWING_SINGLE_LINE-EVENTS",
                "|",
                shortLoremText,
                LocalDateTime.of(2020,8,1,0,0),
                randomSeed++);

        generateRefDataForEffectiveDateTesting(
                dir);
    }

    private void generateDataViewRawData(final Path dir,
                                         final int fileNo,
                                         final String feedName,
                                         final String recordSeparator,
                                         final int loremWordCount,
                                         final LocalDateTime startDate,
                                         final long randomSeed) {
        final Path file = makeInputFilePath(dir, fileNo, feedName);
        LOGGER.info("Generating file {}", file.toAbsolutePath().normalize().toString());

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
                        faker -> faker.name().bloodGroup()))
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
                .setDataWriter(FlatDataWriterBuilder.builder()
                        .delimitedBy(",")
                        .enclosedBy("\"")
                        .outputHeaderRow(true)
                        .build())
                .consumedBy(DataGenerator.getFileOutputConsumer(file, recordSeparator))
                .rowCount(5_000)
                .withRandomSeed(randomSeed)
                .generate();
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
            final Path refFile = makeInputFilePath(dir, i, effectiveDateTime, refFeed);
            LOGGER.info("Generating file {}", refFile.toAbsolutePath().normalize().toString());

            DataGenerator.buildDefinition()
                    .addFieldDefinition(DataGenerator.sequentiallyNumberedValueField(
                            "user",
                            "user%s",
                            1,
                            userCount + 1))
                    .addFieldDefinition(DataGenerator.sequentiallyNumberedValueField(
                            "effectiveDateTime",
                            "user%s-" + effectiveDateTime.toString(),
                            1,
                            userCount + 1))
                    .setDataWriter(XmlAttributesDataWriterBuilder.builder()
                            .namespace("records:2")
                            .build())
                    .consumedBy(DataGenerator.getFileOutputConsumer(refFile))
                    .rowCount(userCount)
                    .generate();

            effectiveDateTime = effectiveDateTime.plusDays(1);
        }

        final Path eventsFile = makeInputFilePath(dir, 1, eventsFeed);
        LOGGER.info("Generating file {}", eventsFile.toAbsolutePath().normalize().toString());

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
                .generate();
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
