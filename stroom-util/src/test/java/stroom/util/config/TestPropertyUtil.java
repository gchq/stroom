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

package stroom.util.config;

import stroom.util.config.PropertyUtil.ObjectInfo;
import stroom.util.config.annotations.ReadOnly;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonCreator.Mode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestPropertyUtil {

    @Test
    void getProperties() {

        final MyClass myClass = new MyClass();
        final MyClass myChildClass1 = new MyClass();
        final MyClass myChildClass2 = new MyClass();
        myClass.setMyClass(myChildClass1);
        final Map<String, PropertyUtil.Prop> propMap = PropertyUtil.getProperties(myClass);

        assertThat(propMap)
                .hasSize(4);

        assertThat(propMap.values().stream()
                .map(PropertyUtil.Prop::getParentObject)
                .map(System::identityHashCode)
                .distinct()
                .collect(Collectors.toList()))
                .containsExactly(System.identityHashCode(myClass));

        testProp(propMap, "myBoolean", true, false, myClass::isMyBoolean, boolean.class);
        testProp(propMap, "myInt", 99, 101, myClass::getMyInt, int.class);
        testProp(propMap, "myString", "abc", "def", myClass::getMyString, String.class);
        testProp(propMap, "myClass", myChildClass1, myChildClass2, myClass::getMyClass, MyClass.class);
    }

    @Test
    void testGetBranchProperties() {
        final ImmutablePojo immutablePojo = new ImmutablePojo();

        final ObjectInfo<ImmutablePojo> objectInfo = PropertyUtil.getObjectInfo(
                JsonUtil.getMapper(), "stroom", immutablePojo);

        assertThat(objectInfo.getName())
                .isEqualTo("stroom");

        assertThat(objectInfo.getPropertyMap().keySet())
                .contains("myCustomBoolean", "myInt", "myString", "immutableChild");

        assertThat(objectInfo.getConstructorArgList())
                .containsExactly("myCustomBoolean", "myInt", "myString", "immutableChild");

        // use the original object as a source of values to create a new instance that has the same values
        final ImmutablePojo immutablePojo2 = objectInfo.createInstance(name ->
                objectInfo.getPropertyMap().get(name).getValueFromConfigObject());

        assertThat(immutablePojo2)
                .isEqualTo(immutablePojo);
    }

    @Test
    void getProperties_fieldProps() {

        final AnnosOnFields annosOnFields = new AnnosOnFields();
        annosOnFields.setIncludedField("yes");
        annosOnFields.setReadOnlyField("cheese");
        annosOnFields.setIgnoredField("No");

        final Map<String, PropertyUtil.Prop> propMap = PropertyUtil.getProperties(annosOnFields);

        assertThat(propMap)
                .hasSize(2);

        assertThat(propMap.values().stream()
                .map(PropertyUtil.Prop::getParentObject)
                .map(System::identityHashCode)
                .distinct()
                .collect(Collectors.toList()))
                .containsExactly(System.identityHashCode(annosOnFields));

        testProp(propMap,
                "includedField",
                "yes",
                "yes2",
                annosOnFields::getIncludedField,
                String.class);
        testProp(propMap,
                "readOnlyField",
                "cheese",
                "cheese2",
                annosOnFields::getReadOnlyField,
                String.class);

        final PropertyUtil.Prop includedFieldProp = propMap.get("includedField");
        Assertions.assertThat(includedFieldProp.hasFieldAnnotation(JsonProperty.class))
                .isTrue();
        Assertions.assertThat(includedFieldProp.hasFieldAnnotation(JsonPropertyDescription.class))
                .isTrue();

        Assertions.assertThat(includedFieldProp.hasGetterAnnotation(JsonProperty.class))
                .isFalse();
        Assertions.assertThat(includedFieldProp.hasGetterAnnotation(JsonPropertyDescription.class))
                .isFalse();

        Assertions.assertThat(includedFieldProp.hasAnnotation(JsonProperty.class))
                .isTrue();
        Assertions.assertThat(includedFieldProp.hasAnnotation(JsonPropertyDescription.class))
                .isTrue();

        final PropertyUtil.Prop readOnlyFieldProp = propMap.get("readOnlyField");
        Assertions.assertThat(includedFieldProp.hasFieldAnnotation(JsonProperty.class))
                .isTrue();
        Assertions.assertThat(includedFieldProp.hasFieldAnnotation(JsonPropertyDescription.class))
                .isTrue();
        Assertions.assertThat(readOnlyFieldProp.hasFieldAnnotation(ReadOnly.class))
                .isTrue();

    }

    @Test
    void testMerge1_nonNull() {
        doMergeValueTest("a",
                "b",
                "c",
                true,
                true,
                "b");
    }

    @Test
    void testMerge2_null_copy() {
        doMergeValueTest("a",
                null,
                "c",
                true,
                true,
                null);
    }

    @Test
    void testMerge2_null_dontCopy() {
        doMergeValueTest("a",
                null,
                "c",
                false,
                true,
                "a");
    }

    @Test
    void testMerge2_default_copy() {
        doMergeValueTest("a",
                "c",
                "c",
                true,
                true,
                "c");
    }

    @Test
    void testMerge2_default_dontCopy() {
        doMergeValueTest("a",
                "c",
                "c",
                true,
                false,
                "a");
    }

    @Test
    void testCopy() {
        final MyClass source = new MyClass();
        source.setMyClass(new MyClass());
        final MyClass copy = PropertyUtil.copyObject(source);
        Assertions.assertThat(copy)
                .isEqualTo(source);
    }

    private void doMergeValueTest(final String thisValue,
                                  final String otherValue,
                                  final String defaultValue,
                                  final boolean copyNulls,
                                  final boolean copyDefaults,
                                  final String expectedValue) {
        final String actualValue = PropertyUtil.mergeValues(
                thisValue, otherValue, defaultValue, copyNulls, copyDefaults);
        Assertions.assertThat(actualValue)
                .isEqualTo(expectedValue);
    }

    private void testProp(final Map<String, PropertyUtil.Prop> propMap,
                          final String name,
                          final Object expectedValue,
                          final Object newValue,
                          final Supplier<Object> newValueSupplier,
                          final Class<?> clazz) {
        final PropertyUtil.Prop booleanProp = propMap.get(name);
        assertThat(booleanProp.getValueFromConfigObject())
                .isEqualTo(expectedValue);
        booleanProp.setValueOnConfigObject(newValue);
        assertThat(booleanProp.getValueFromConfigObject())
                .isEqualTo(newValue);
        assertThat(newValueSupplier.get())
                .isEqualTo(newValue);
        assertThat(booleanProp.getValueClass())
                .isEqualTo(clazz);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static class MyClass {

        private boolean myBoolean = true;
        private int myInt = 99;
        private String myString = "abc";
        private MyClass myClass;

        // should not appear in the property map
        private final String nonPublicString = "xxx";

        public boolean isMyBoolean() {
            return myBoolean;
        }

        public void setMyBoolean(final boolean myBoolean) {
            this.myBoolean = myBoolean;
        }

        public int getMyInt() {
            return myInt;
        }

        public void setMyInt(final int myInt) {
            this.myInt = myInt;
        }

        public String getMyString() {
            return myString;
        }

        public void setMyString(final String myString) {
            this.myString = myString;
        }

        public MyClass getMyClass() {
            return myClass;
        }

        public void setMyClass(final MyClass myClass) {
            this.myClass = myClass;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final MyClass myClass1 = (MyClass) o;
            return myBoolean == myClass1.myBoolean && myInt == myClass1.myInt && Objects.equals(myString,
                    myClass1.myString) && Objects.equals(myClass, myClass1.myClass) && Objects.equals(
                    nonPublicString,
                    myClass1.nonPublicString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myBoolean, myInt, myString, myClass, nonPublicString);
        }
    }

    private static class AnnosOnFields {

        @JsonIgnore
        private String ignoredField;

        @JsonProperty
        @JsonPropertyDescription("description1")
        private String includedField;

        @JsonProperty
        @JsonPropertyDescription("description2")
        @ReadOnly
        private String readOnlyField;

        public String getIncludedField() {
            return includedField;
        }

        public void setIncludedField(final String includedField) {
            this.includedField = includedField;
        }

        public String getReadOnlyField() {
            return readOnlyField;
        }

        public void setReadOnlyField(final String readOnlyField) {
            this.readOnlyField = readOnlyField;
        }

        @JsonIgnore
        public String getIgnoredField() {
            return ignoredField;
        }

        public void setIgnoredField(final String ignoredField) {
            this.ignoredField = ignoredField;
        }
    }

    private static class ImmutablePojo {

        @JsonProperty("myCustomBoolean")
        private Boolean myBoolean = true;
        @JsonProperty
        private Integer myInt = 99;

        private String myString = "abc";
        @JsonProperty
        private ImmutableChildPojo immutableChild = new ImmutableChildPojo();

        // should not appear in the property map
        private final String nonPublicString = "xxx";

        public ImmutablePojo() {
        }

        @JsonCreator(mode = Mode.PROPERTIES)
        public ImmutablePojo(@JsonProperty("myCustomBoolean") final Boolean myBoolean,
                             @JsonProperty("myInt") final Integer myInt,
                             @JsonProperty("myString") final String myString,
                             @JsonProperty("immutableChild") final ImmutableChildPojo immutableChild) {
            this.myBoolean = myBoolean;
            this.myInt = myInt;
            this.myString = myString;
            this.immutableChild = immutableChild;
        }

        @JsonIgnore
        public String getNonPublicString() {
            return nonPublicString;
        }

        public Boolean getMyBoolean() {
            return myBoolean;
        }

        public Integer getMyInt() {
            return myInt;
        }

        public String getMyString() {
            return myString;
        }

        public ImmutableChildPojo getImmutableChild() {
            return immutableChild;
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
            return Objects.equals(myBoolean, that.myBoolean) && Objects.equals(myInt,
                    that.myInt) && Objects.equals(myString, that.myString) && Objects.equals(immutableChild,
                    that.immutableChild) && Objects.equals(nonPublicString, that.nonPublicString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myBoolean, myInt, myString, immutableChild, nonPublicString);
        }
    }

    private static class ImmutableChildPojo {

        @JsonProperty
        private Boolean myBoolean = false;
        @JsonProperty
        private Integer myInt = 13;
        @JsonProperty
        private String myString = "def";

        public ImmutableChildPojo() {
        }

        @JsonCreator
        public ImmutableChildPojo(@JsonProperty("myBoolean") final Boolean myBoolean,
                                  @JsonProperty("myInt") final Integer myInt,
                                  @JsonProperty("myString") final String myString) {
            this.myBoolean = myBoolean;
            this.myInt = myInt;
            this.myString = myString;
        }

        public Boolean getMyBoolean() {
            return myBoolean;
        }

        public Integer getMyInt() {
            return myInt;
        }

        public String getMyString() {
            return myString;
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
            return Objects.equals(myBoolean, that.myBoolean) && Objects.equals(myInt,
                    that.myInt) && Objects.equals(myString, that.myString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myBoolean, myInt, myString);
        }
    }

}
