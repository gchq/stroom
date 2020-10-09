package stroom.test;

import stroom.test.common.StroomCoreServerTestFileUtil;
import stroom.testdata.DataGenerator;
import stroom.testdata.FlatDataWriterBuilder;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        // Data that has one record per line
        // One with long lines, one with short
        generateDataViewRawData(
                dir,
                1,
                "DATA_VIEWING_MULTI_LINE-EVENTS",
                "\n",
                shortLoremText);

        generateDataViewRawData(
                dir,
                2,
                "DATA_VIEWING_MULTI_LINE-EVENTS",
                "\n",
                longLoremText);

        // Data that is all on one massive single line
        // One with long lines, one with short
        generateDataViewRawData(
                dir,
                1,
                "DATA_VIEWING_SINGLE_LINE-EVENTS",
                "|",
                shortLoremText);
    }

    private void generateDataViewRawData(final Path dir,
                                         final int fileNo,
                                         final String feedName,
                                         final String recordSeparator,
                                         final int loremWordCount) {
        final Path file = makeInputFilePath(dir, fileNo, feedName);
        LOGGER.info("Generating file {}", file.toAbsolutePath().normalize().toString());

        DataGenerator.buildDefinition()
                .addFieldDefinition(DataGenerator.randomDateTimeField(
                        "dateTime",
                        LocalDateTime.of(2020,06,01,00,00),
                        LocalDateTime.of(2020,10,01,00,00),
                        DateTimeFormatter.ISO_DATE_TIME
                ))
                .addFieldDefinition(DataGenerator.randomIpV4Field(
                        "machineIp")
                        faker -> faker.internet().ipV4Address()
                ))
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
                .withRandomSeed(fileNo)
                .generate();
    }

    private Path makeInputFilePath(final Path dir, final int index, final String feedName) {
        return dir.resolve(feedName + "~" + index + ".in");
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
