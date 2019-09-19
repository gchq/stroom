package stroom.config.global.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import stroom.util.reflection.FieldMapper;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class TestFieldMapper {

    @Test
    void testSimple() {
        MyObject original = new MyObject();
        original.setString("Original");

        MyObject copy = new MyObject();
        copy.setString("Copy");
        FieldMapper.copy(original, copy);

        assertThat(copy.getString())
                .isEqualTo(original.getString());
        assertThat(System.identityHashCode(original))
                .isNotEqualTo(System.identityHashCode(copy));
    }

    @Test
    void testNullSource() {
        MyObject original = new MyObject();
        original.setString(null);

        MyObject copy = new MyObject();
        copy.setString("copy");
        FieldMapper.copy(original, copy);

        assertThat(copy.getString())
                .isEqualTo(original.getString());
        assertThat(System.identityHashCode(original))
                .isNotEqualTo(System.identityHashCode(copy));
    }

    @Test
    void testNullDest() {
        MyObject original = new MyObject();
        original.setString("NotNull");

        MyObject copy = new MyObject();
        copy.setString(null);
        FieldMapper.copy(original, copy);

        assertThat(copy.getString())
                .isEqualTo(original.getString());
        assertThat(System.identityHashCode(original))
                .isNotEqualTo(System.identityHashCode(copy));
    }

    @Test
    void testDeepCopy() throws NoSuchFieldException, IllegalAccessException {
        final MyParent parent1 = new MyParent();
        System.out.println("parent1       " + System.identityHashCode(parent1));
        System.out.println("parent1 child " + System.identityHashCode(parent1.child));

        int parent1Id = System.identityHashCode(parent1);
        int parent1ChildId = System.identityHashCode(parent1.child);

        final MyParent parent2 = new MyParent();
        parent2.setMyInt(99);
        parent2.setMyString("changed");
        MyChild child3 = new MyChild();
        child3.setMyInt(999);
        child3.setMyString("changed child");
        parent2.setChild(child3);

        Assertions.assertThat(parent2)
                .isNotEqualTo(parent1);
        Assertions.assertThat(parent2.getChild())
                .isNotEqualTo(parent1.getChild());

        System.out.println("parent2       " + System.identityHashCode(parent2));
        System.out.println("parent2 child " + System.identityHashCode(parent2.child));

        Assertions.assertThat(System.identityHashCode(parent1))
                .isNotEqualTo(System.identityHashCode(parent2));

        FieldMapper.copy(parent2, parent1);

        System.out.println();
        System.out.println("parent1       " + System.identityHashCode(parent1));
        System.out.println("parent1 child " + System.identityHashCode(parent1.child));
        System.out.println("parent2       " + System.identityHashCode(parent2));
        System.out.println("parent2 child " + System.identityHashCode(parent2.child));

        Assertions.assertThat(System.identityHashCode(parent1))
                .isNotEqualTo(System.identityHashCode(parent2));
        Assertions.assertThat(System.identityHashCode(parent1.child))
                .isNotEqualTo(System.identityHashCode(parent2.child));

        Assertions.assertThat(parent2)
                .isEqualTo(parent1);
        Assertions.assertThat(parent2.getChild())
                .isEqualTo(parent1.getChild());

        Assertions.assertThat(System.identityHashCode(parent1))
                .isEqualTo(parent1Id);
        Assertions.assertThat(System.identityHashCode(parent1.child))
                .isEqualTo(parent1ChildId);
    }

    private static class MyObject {
        private String string;

        String getString() {
            return string;
        }

        void setString(final String string) {
            this.string = string;
        }
    }
    private static class MyParent {
        private String myString = "abc";
        private int myInt = 1;
        private MyChild child = new MyChild();


        String getMyString() {
            return myString;
        }

        void setMyString(final String myString) {
            this.myString = myString;
        }

        int getMyInt() {
            return myInt;
        }

        void setMyInt(final int myInt) {
            this.myInt = myInt;
        }

        MyChild getChild() {
            return child;
        }

        void setChild(final MyChild child) {
            this.child = child;
        }

        @Override
        public String toString() {
            return "MyParent{" +
                    "myString='" + myString + '\'' +
                    ", myInt=" + myInt +
                    ", child=" + child +
                    '}';
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final MyParent myParent = (MyParent) o;
            return myInt == myParent.myInt &&
                    myString.equals(myParent.myString) &&
                    child.equals(myParent.child);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myString, myInt, child);
        }
    }

    private static class MyChild {
        private String myString = "xyz";
        private int myInt = 2;

        String getMyString() {
            return myString;
        }

        void setMyString(final String myString) {
            this.myString = myString;
        }

        int getMyInt() {
            return myInt;
        }

        void setMyInt(final int myInt) {
            this.myInt = myInt;
        }

        @Override
        public String toString() {
            return "MyChild{" +
                    "myString='" + myString + '\'' +
                    ", myInt=" + myInt +
                    '}';
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final MyChild myChild = (MyChild) o;
            return myInt == myChild.myInt &&
                    myString.equals(myChild.myString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myString, myInt);
        }
    }
}
