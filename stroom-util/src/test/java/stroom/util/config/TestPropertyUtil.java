package stroom.util.config;

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

}