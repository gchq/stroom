package stroom.config.app;

import stroom.util.io.DiffUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestYamlUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestYamlUtil.class);

    static final String EXPECTED_YAML_FILE_NAME = "expected.yaml";
    static final String ACTUAL_YAML_FILE_NAME = "actual.yaml";

    /**
     * *** IMPORTANT ***
     * If the test fails it is because you have made changes to part of the
     * {@link stroom.util.shared.AbstractConfig} object model
     * and the resulting generated yaml is different to what it was before. If you are happy that
     * the change to the yaml matches what you expect then run {@link GenerateExpectedYaml#main} to
     * re-generate the expected yaml file, then the test will pass. If you are not happy then re-think
     * your change to the object model.
     * *** IMPORTANT ***
     */
    @Test
    void testGeneratedYamlAgainstExpected() throws IOException {
        final Path expectedFile = getExpectedYamlFilePath();
        final Path actualFile = getActualYamlFilePath();

        final String actual = getYamlFromJavaModel();

        // The expected file has already had the DW lines removed
        final List<String> actualLines = GenerateExpectedYaml.removeDropWizardLines(actual);

        // write the actual out so we can compare in other tools
        Files.write(actualFile, actualLines);

        final Consumer<List<String>> diffLinesConsumer = diffLines -> {
            LOGGER.error(
                    "\n  Differences exist between the expected serialised form of AppConfig and the actual. " +
                            "\n  If the difference is what you would expect based on the changes you have made to " +
                            "the config model " +
                            "\n  then run the main() method in GenerateExpectedYaml to re-generate the " +
                            "expected yaml\n{}",
                    String.join("\n", diffLines));

            LOGGER.info("\nvimdiff {} {}", expectedFile, actualFile);
        };

        final boolean haveDifferences = DiffUtil.unifiedDiff(
                expectedFile,
                actualFile,
                true,
                3,
                diffLinesConsumer);

        assertThat(haveDifferences)
                .withFailMessage("Expected and actual YAML do not match!")
                .isFalse();
    }

    static Path getExpectedYamlFilePath() {
        return getBasePath().resolve(EXPECTED_YAML_FILE_NAME);
    }

    static Path getActualYamlFilePath() {
        return getBasePath().resolve(ACTUAL_YAML_FILE_NAME);
    }

    static Path getBasePath() {
        final String codeSourceLocation = TestYamlUtil.class
                .getProtectionDomain().getCodeSource().getLocation().getPath();

        Path path = Paths.get(codeSourceLocation);

        while (path != null && !path.getFileName().toString().equals("stroom-config-app")) {
            path = path.getParent();
        }

        return path.resolve("src")
                .resolve("test")
                .resolve("resources")
                .resolve("stroom")
                .resolve("config")
                .resolve("app")
                .normalize()
                .toAbsolutePath();
    }

    static String getYamlFromJavaModel() throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        AppConfig appConfig = new AppConfig();
        YamlUtil.writeConfig(appConfig, byteArrayOutputStream);
        return byteArrayOutputStream.toString();
    }

    /**
     * Verify dev.yml can be de-serialised into the config object model
     */
    @Test
    void testDevYaml() throws FileNotFoundException {
        loadYamlFile("dev.yml");

        // prod.yml is tested as part of GenerateDistributionConfig
    }


    @Test
    void testAddingDefaultsToYaml() throws IOException {

        final ImmutablePojo immutablePojoDefault = new ImmutablePojo();

        final ObjectMapper yamlObjectMapper = YamlUtil.createYamlObjectMapper();

        final String defaultYaml = yamlObjectMapper.writeValueAsString(immutablePojoDefault);

        LOGGER.info("yaml:\n{}", defaultYaml);

        final ImmutablePojo immutablePojoDefault2 = yamlObjectMapper.readValue(defaultYaml, ImmutablePojo.class);

        assertThat(immutablePojoDefault2)
                .isEqualTo(immutablePojoDefault);

        final String sparseYaml = """
                immutableChild:
                  myInt: 13
                  grandChild:
                myTrueBoolean: false
                myFalseBoolean: true
                myString:
                """;

        final ImmutablePojo immutablePojoMerged = YamlUtil.mergeYamlNodeTrees(
                ImmutablePojo.class,
                yamlObjectMapper,
                mapper -> {
                    try {
                        return mapper.readTree(sparseYaml);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                mapper ->
                        yamlObjectMapper.valueToTree(immutablePojoDefault));
    }

    @Test
    void testMergeYamlNodeTrees_empty() throws JsonProcessingException {
        doYamlMergeTest("""
                        """,
                (defaultPojo, mergedPojo) -> {
                    assertThat(mergedPojo)
                            .isEqualTo(defaultPojo);
                });
    }

    @Test
    void testMergeYamlNodeTrees_noChange() throws JsonProcessingException {
        final ImmutablePojo immutablePojoDefault = new ImmutablePojo();
        final ObjectMapper yamlObjectMapper = YamlUtil.createYamlObjectMapper();
        // sparse is identical to default
        final String sparseYaml = yamlObjectMapper.writeValueAsString(immutablePojoDefault);

        doYamlMergeTest(sparseYaml, (defaultPojo, mergedPojo) -> {
            assertThat(mergedPojo)
                    .isEqualTo(defaultPojo);
        });
    }

    @Test
    void testMergeYamlNodeTrees_oneRootFieldChanged() throws JsonProcessingException {
        final ImmutablePojo expectedPojo = new ImmutablePojo().withMyInt(999);
        doYamlMergeTest("""
                        myInt: 999
                                """,
                (defaultPojo, mergedPojo) -> {
                    assertThat(mergedPojo)
                            .isEqualTo(expectedPojo);
                });
    }

    @Test
    void testMergeYamlNodeTrees_nullBranch() throws JsonProcessingException {
        final ImmutablePojo expectedPojo = new ImmutablePojo().withMyInt(999);
        doYamlMergeTest("""
                        myInt: 999
                        immutableChild:
                                """,
                (defaultPojo, mergedPojo) -> {
                    assertThat(mergedPojo)
                            .isEqualTo(expectedPojo);
                });
    }

    @Test
    void testMergeYamlNodeTrees_multipleChanges() throws JsonProcessingException {
        final ImmutablePojo expectedPojo = new ImmutablePojo()
                .withMyInt(999)
                .withImmutableChild(new ImmutableChildPojo()
                        .withMyString("a new value")
                        .withImmutableGrandChild(new ImmutableChildPojo2()
                                .withMyString("another new value")));

        doYamlMergeTest("""
                        myInt: 999
                        immutableChild:
                          myString: a new value
                          immutableGrandChild:
                            myString: another new value
                                """,
                (defaultPojo, mergedPojo) -> {
                    assertThat(mergedPojo)
                            .isEqualTo(expectedPojo);
                });
    }

    @Test
    void testAppConfigMerge() throws JsonProcessingException {

        doYamlMergeTest("""
                        """,
                AppConfig.class,
                AppConfig::new,
                (defaultPojo, mergedPojo) -> {
                    // can't do equality test as not many of the classes implement
//                    assertThat(mergedPojo)
//                            .isEqualTo(defaultPojo);
                });
    }

    private void doYamlMergeTest(final String sparseYaml,
                                 final BiConsumer<ImmutablePojo, ImmutablePojo> objectsConsumer)
            throws JsonProcessingException {
        doYamlMergeTest(sparseYaml, ImmutablePojo.class, ImmutablePojo::new, objectsConsumer);
    }

    private <T> void doYamlMergeTest(final String sparseYaml,
                                     final Class<T> valueType,
                                     final Supplier<T> defaultObjectSupplier,
                                     final BiConsumer<T, T> objectsConsumer)
            throws JsonProcessingException {
        final T defaultObject = defaultObjectSupplier.get();
        final ObjectMapper yamlObjectMapper = YamlUtil.createYamlObjectMapper();

        LOGGER.debug("default yaml:\n{}", yamlObjectMapper.writeValueAsString(defaultObject));

        final T mergedObject = YamlUtil.mergeYamlNodeTrees(
                valueType,
                yamlObjectMapper,
                mapper -> {
                    try {
                        return mapper.readTree(sparseYaml);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                mapper ->
                        yamlObjectMapper.valueToTree(defaultObject));
        objectsConsumer.accept(defaultObject, mergedObject);
    }


    private static AppConfig loadYamlFile(final String filename) throws FileNotFoundException {
        Path path = getStroomAppFile(filename);

        try {
            return YamlUtil.readAppConfig(path);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path getStroomAppFile(final String filename) throws FileNotFoundException {
        final String codeSourceLocation = TestYamlUtil.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath();

        Path path = Paths.get(codeSourceLocation);
        while (path != null && !path.getFileName().toString().equals("stroom-config")) {
            path = path.getParent();
        }
        if (path != null) {
            path = path.getParent();
            path = path.resolve("stroom-app");
            path = path.resolve(filename);
        }

        if (path == null) {
            throw new FileNotFoundException("Unable to find " + filename);
        }
        return path;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @JsonPropertyOrder(alphabetic = true)
    @JsonRootName("stroom")
    private static class ImmutablePojo {

        @JsonProperty
        private final boolean myTrueBoolean;
        @JsonProperty
        private final boolean myFalseBoolean;
        @JsonProperty
        private final int myInt;

        private final String myString;
        @JsonProperty
        private final ImmutableChildPojo immutableChild;

        @JsonIgnore
        // should not appear in the property map
        private final String nonPublicString = "xxx";

        public ImmutablePojo() {
            myTrueBoolean = true;
            myFalseBoolean = false;
            myInt = 42;
            myString = "abc";
            immutableChild = new ImmutableChildPojo();
        }

        @JsonCreator
        public ImmutablePojo(@JsonProperty("myTrueBoolean") final boolean myTrueBoolean,
                             @JsonProperty("myFalseBoolean") final boolean myFalseBoolean,
                             @JsonProperty("myInt") final int myInt,
                             @JsonProperty("myString") final String myString,
                             @JsonProperty("immutableChild") final ImmutableChildPojo immutableChild) {
            this.myTrueBoolean = myTrueBoolean;
            this.myFalseBoolean = myFalseBoolean;
            this.myInt = myInt;
            this.myString = myString;
            this.immutableChild = immutableChild;
        }

        public ImmutablePojo withMyInt(final int myInt) {
            return new ImmutablePojo(myTrueBoolean, myFalseBoolean, myInt, myString, immutableChild);
        }

        public ImmutablePojo withMyString(final String myString) {
            return new ImmutablePojo(myTrueBoolean, myFalseBoolean, myInt, myString, immutableChild);
        }

        public ImmutablePojo withImmutableChild(final ImmutableChildPojo immutableChild) {
            return new ImmutablePojo(myTrueBoolean, myFalseBoolean, myInt, myString, immutableChild);
        }

        @JsonIgnore
        public String getNonPublicString() {
            return nonPublicString;
        }

        public boolean isMyTrueBoolean() {
            return myTrueBoolean;
        }

        public boolean isMyFalseBoolean() {
            return myFalseBoolean;
        }

        public int getMyInt() {
            return myInt;
        }

        public String getMyString() {
            return myString;
        }

        public ImmutableChildPojo getImmutableChild() {
            return immutableChild;
        }

        @Override
        public String toString() {
            return "ImmutablePojo{" +
                    "myTrueBoolean=" + myTrueBoolean +
                    ", myFalseBoolean=" + myFalseBoolean +
                    ", myInt=" + myInt +
                    ", myString='" + myString + '\'' +
                    ", immutableChild=" + immutableChild +
                    ", nonPublicString='" + nonPublicString + '\'' +
                    '}';
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ImmutablePojo that = (ImmutablePojo) o;
            return myTrueBoolean == that.myTrueBoolean
                    && myFalseBoolean == that.myFalseBoolean
                    && myInt == that.myInt
                    && Objects.equals(myString, that.myString)
                    && Objects.equals(immutableChild, that.immutableChild)
                    && Objects.equals(nonPublicString, that.nonPublicString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myTrueBoolean, myFalseBoolean, myInt, myString, immutableChild, nonPublicString);
        }
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @JsonPropertyOrder(alphabetic = true)
    private static class ImmutableChildPojo {

        @JsonProperty
        private final boolean myBoolean;
        @JsonProperty
        private final int myInt;
        @JsonProperty
        private final String myString;
        @JsonProperty
        private final ImmutableChildPojo2 immutableGrandChild;

        public ImmutableChildPojo() {
            myBoolean = false;
            myInt = 13;
            myString = "def";
            immutableGrandChild = new ImmutableChildPojo2();
        }

        @JsonCreator
        public ImmutableChildPojo(@JsonProperty("myBoolean") final boolean myBoolean,
                                  @JsonProperty("myInt") final int myInt,
                                  @JsonProperty("myString") final String myString,
                                  @JsonProperty("immutableGrandChild") final ImmutableChildPojo2 immutableGrandChild) {
            this.myBoolean = myBoolean;
            this.myInt = myInt;
            this.myString = myString;
            this.immutableGrandChild = immutableGrandChild;
        }

        public ImmutableChildPojo withMyBoolean(final boolean myBoolean) {
            return new ImmutableChildPojo(myBoolean, myInt, myString, immutableGrandChild);
        }

        public ImmutableChildPojo withMyString(final String myString) {
            return new ImmutableChildPojo(myBoolean, myInt, myString, immutableGrandChild);
        }

        public ImmutableChildPojo withImmutableGrandChild(final ImmutableChildPojo2 immutableGrandChild) {
            return new ImmutableChildPojo(myBoolean, myInt, myString, immutableGrandChild);
        }

        public boolean getMyBoolean() {
            return myBoolean;
        }

        public int getMyInt() {
            return myInt;
        }

        public String getMyString() {
            return myString;
        }

        public ImmutableChildPojo2 getImmutableGrandChild() {
            return immutableGrandChild;
        }

        @Override
        public String toString() {
            return "ImmutableChildPojo{" +
                    "myBoolean=" + myBoolean +
                    ", myInt=" + myInt +
                    ", myString='" + myString + '\'' +
                    ", grandChild=" + immutableGrandChild +
                    '}';
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ImmutableChildPojo that = (ImmutableChildPojo) o;
            return myBoolean == that.myBoolean && myInt == that.myInt && Objects.equals(myString,
                    that.myString) && Objects.equals(immutableGrandChild, that.immutableGrandChild);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myBoolean, myInt, myString, immutableGrandChild);
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @JsonPropertyOrder(alphabetic = true)
    private static class ImmutableChildPojo2 {

        @JsonProperty
        private final boolean myBoolean;
        @JsonProperty
        private final int myInt;
        @JsonProperty
        private final String myString;

        public ImmutableChildPojo2() {
            myBoolean = false;
            myInt = 27;
            myString = "ghi";
        }

        @JsonCreator
        public ImmutableChildPojo2(@JsonProperty("myBoolean") final boolean myBoolean,
                                   @JsonProperty("myInt") final int myInt,
                                   @JsonProperty("myString") final String myString) {
            this.myBoolean = myBoolean;
            this.myInt = myInt;
            this.myString = myString;
        }

        public ImmutableChildPojo2 withMyBoolean(final boolean myBoolean) {
            return new ImmutableChildPojo2(myBoolean, myInt, myString);
        }

        public ImmutableChildPojo2 withMyString(final String myString) {
            return new ImmutableChildPojo2(myBoolean, myInt, myString);
        }

        public boolean getMyBoolean() {
            return myBoolean;
        }

        public int getMyInt() {
            return myInt;
        }

        public String getMyString() {
            return myString;
        }

        @Override
        public String toString() {
            return "ImmutableChildPojo{" +
                    "myBoolean=" + myBoolean +
                    ", myInt=" + myInt +
                    ", myString='" + myString + '\'' +
                    '}';
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ImmutableChildPojo2 that = (ImmutableChildPojo2) o;
            return myBoolean == that.myBoolean && myInt == that.myInt && Objects.equals(myString, that.myString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myBoolean, myInt, myString);
        }
    }
}
