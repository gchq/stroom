/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.yaml;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestYamlUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestYamlUtil.class);

    @Test
    void testAddingDefaultsToYaml() throws IOException {

        final ImmutablePojo immutablePojoDefault = new ImmutablePojo();

        final ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory())
                .registerModule(new Jdk8Module())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
                    } catch (final IOException e) {
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
        final ObjectMapper yamlObjectMapper = YamlUtil.getMapper();
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
        final ObjectMapper yamlObjectMapper = YamlUtil.getMapper();

        LOGGER.debug("default yaml:\n{}", yamlObjectMapper.writeValueAsString(defaultObject));

        final T mergedObject = YamlUtil.mergeYamlNodeTrees(
                valueType,
                yamlObjectMapper,
                mapper -> {
                    try {
                        return mapper.readTree(sparseYaml);
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                mapper ->
                        yamlObjectMapper.valueToTree(defaultObject));
        objectsConsumer.accept(defaultObject, mergedObject);
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
