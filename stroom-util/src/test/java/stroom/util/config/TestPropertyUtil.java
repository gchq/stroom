package stroom.util.config;

import stroom.util.config.annotations.ReadOnly;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestPropertyUtil {

    @Test
    void getProperties() {

        MyClass myClass = new MyClass();
        MyClass myChildClass1 = new MyClass();
        MyClass myChildClass2 = new MyClass();
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
    void getProperties_fieldProps() {

        AnnosOnFields annosOnFields = new AnnosOnFields();
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

        testProp(propMap, "includedField", "yes", "yes2", annosOnFields::getIncludedField, String.class);
        testProp(propMap, "readOnlyField", "cheese", "cheese2", annosOnFields::getReadOnlyField, String.class);

        PropertyUtil.Prop includedFieldProp = propMap.get("includedField");
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

        PropertyUtil.Prop readOnlyFieldProp = propMap.get("readOnlyField");
        Assertions.assertThat(includedFieldProp.hasFieldAnnotation(JsonProperty.class))
                .isTrue();
        Assertions.assertThat(includedFieldProp.hasFieldAnnotation(JsonPropertyDescription.class))
                .isTrue();
        Assertions.assertThat(readOnlyFieldProp.hasFieldAnnotation(ReadOnly.class))
                .isTrue();

    }

        private void testProp(final Map<String, PropertyUtil.Prop> propMap,
                          final String name,
                          final Object expectedValue,
                          final Object newValue,
                          final Supplier<Object> newValueSupplier,
                          final Class<?> clazz) {
        PropertyUtil.Prop booleanProp = propMap.get(name);
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

    private static class MyClass {
        private boolean myBoolean = true;
        private int myInt = 99;
        private String myString = "abc";
        private MyClass myClass;

        // should not appear in the property map
        private String nonPublicString = "xxx";

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

}