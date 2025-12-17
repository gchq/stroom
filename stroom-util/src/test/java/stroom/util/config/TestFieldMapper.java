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

import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class TestFieldMapper {

    @Test
    void testSimple() {
        final MyObject original = new MyObject();
        original.setString("Original");

        final MyObject copy = new MyObject();
        copy.setString("Copy");
        FieldMapper.copy(original, copy);

        assertThat(copy.getString())
                .isEqualTo(original.getString());
        assertThat(System.identityHashCode(original))
                .isNotEqualTo(System.identityHashCode(copy));
    }

    @Test
    void testNoChanges() {
        final MyParent parent1 = new MyParent();
        final MyParent parent2 = new MyParent();

        final int parent1Id = System.identityHashCode(parent1);
        final int parent1ChildId = System.identityHashCode(parent1.child);

        assertThat(parent2)
                .isEqualTo(parent1);
        assertThat(parent2.getChild())
                .isEqualTo(parent1.getChild());

        assertThat(System.identityHashCode(parent1))
                .isNotEqualTo(System.identityHashCode(parent2));
        assertThat(System.identityHashCode(parent1.child))
                .isNotEqualTo(System.identityHashCode(parent2.child));

        FieldMapper.copy(parent2, parent1);

        assertThat(parent2)
                .isEqualTo(parent1);
        assertThat(parent2.getChild())
                .isEqualTo(parent1.getChild());

        assertThat(System.identityHashCode(parent1))
                .isNotEqualTo(System.identityHashCode(parent2));
        assertThat(System.identityHashCode(parent1.child))
                .isNotEqualTo(System.identityHashCode(parent2.child));

        assertThat(System.identityHashCode(parent1))
                .isEqualTo(parent1Id);
        assertThat(System.identityHashCode(parent1.child))
                .isEqualTo(parent1ChildId);
    }

    @Test
    void testNullSource() {
        final MyObject original = new MyObject();
        original.setString(null);

        final MyObject copy = new MyObject();
        copy.setString("copy");
        FieldMapper.copy(original, copy);

        assertThat(copy.getString())
                .isEqualTo(original.getString());
        assertThat(System.identityHashCode(original))
                .isNotEqualTo(System.identityHashCode(copy));
    }

    @Test
    void testNullSourceNoCopy() {
        final MyObject original = new MyObject();
        original.setString(null);

        final MyObject copy = new MyObject();
        copy.setString("copy");
        FieldMapper.copyNonNulls(original, copy);

        // we requested not to copy nulls so value is unchanged.
        assertThat(copy.getString())
                .isEqualTo("copy");

        assertThat(System.identityHashCode(original))
                .isNotEqualTo(System.identityHashCode(copy));
    }

    @Test
    void testNullDest() {
        final MyObject original = new MyObject();
        original.setString("NotNull");

        final MyObject copy = new MyObject();
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

        final int parent1Id = System.identityHashCode(parent1);
        final int parent1ChildId = System.identityHashCode(parent1.child);

        final MyParent parent2 = new MyParent();
        parent2.setMyInt(99);
        parent2.setMyString("changed");
        final MyChild child3 = new MyChild();
        child3.setMyInt(999);
        child3.setMyString("changed child");
        parent2.setChild(child3);

        assertThat(parent2)
                .isNotEqualTo(parent1);
        assertThat(parent2.getChild())
                .isNotEqualTo(parent1.getChild());

        System.out.println("parent2       " + System.identityHashCode(parent2));
        System.out.println("parent2 child " + System.identityHashCode(parent2.child));

        assertThat(System.identityHashCode(parent1))
                .isNotEqualTo(System.identityHashCode(parent2));

        FieldMapper.copy(parent2, parent1);

        System.out.println();
        System.out.println("parent1       " + System.identityHashCode(parent1));
        System.out.println("parent1 child " + System.identityHashCode(parent1.child));
        System.out.println("parent2       " + System.identityHashCode(parent2));
        System.out.println("parent2 child " + System.identityHashCode(parent2.child));

        assertThat(System.identityHashCode(parent1))
                .isNotEqualTo(System.identityHashCode(parent2));
        assertThat(System.identityHashCode(parent1.child))
                .isNotEqualTo(System.identityHashCode(parent2.child));

        assertThat(parent2)
                .isEqualTo(parent1);
        assertThat(parent2.getChild())
                .isEqualTo(parent1.getChild());

        assertThat(System.identityHashCode(parent1))
                .isEqualTo(parent1Id);
        assertThat(System.identityHashCode(parent1.child))
                .isEqualTo(parent1ChildId);
    }

    @Test
    void testDeepCopyDontCopyNulls() throws NoSuchFieldException, IllegalAccessException {
        // The dest
        final MyParent parent1 = new MyParent();
        System.out.println("parent1       " + System.identityHashCode(parent1));
        System.out.println("parent1 child " + System.identityHashCode(parent1.child));

        final int parent1Id = System.identityHashCode(parent1);
        final int parent1ChildId = System.identityHashCode(parent1.child);

        // the source
        final MyParent parent2 = new MyParent();
        parent2.setMyInt(99);
        parent2.setMyString("changed");
        parent2.setChild(null);

        assertThat(parent1)
                .isNotEqualTo(parent2);
        assertThat(parent1.getChild())
                .isNotEqualTo(parent2.getChild());

        System.out.println("parent2       " + System.identityHashCode(parent2));
        System.out.println("parent2 child " + System.identityHashCode(parent2.child));

        assertThat(System.identityHashCode(parent1))
                .isNotEqualTo(System.identityHashCode(parent2));

        System.out.println("parent1: " + parent1);
        System.out.println("parent2: " + parent2);

        FieldMapper.copyNonNulls(parent2, parent1);
        System.out.println("parent1: " + parent1);
        System.out.println("parent2: " + parent2);

        System.out.println();
        System.out.println("parent1       " + System.identityHashCode(parent1));
        System.out.println("parent1 child " + System.identityHashCode(parent1.child));
        System.out.println("parent2       " + System.identityHashCode(parent2));
        System.out.println("parent2 child " + System.identityHashCode(parent2.child));

        assertThat(System.identityHashCode(parent1))
                .isNotEqualTo(System.identityHashCode(parent2));
        assertThat(System.identityHashCode(parent1.child))
                .isNotEqualTo(System.identityHashCode(parent2.child));

        assertThat(parent1.myInt)
                .isEqualTo(parent2.myInt);
        assertThat(parent1.myString)
                .isEqualTo(parent2.myString);

        assertThat(parent1.getChild())
                .isEqualTo(new MyChild());

        assertThat(System.identityHashCode(parent1))
                .isEqualTo(parent1Id);
        assertThat(System.identityHashCode(parent1.child))
                .isEqualTo(parent1ChildId);
    }

    @Test
    void testDeepCopyDontCopyNullsOrDefaults() throws NoSuchFieldException, IllegalAccessException {
        // The dest
        final MyParent destParent = new MyParent();
        // make values non default
        final int nonDefaultIntValue = destParent.getChild().getMyInt() + 10;
        final String nonDefaultStrValue = destParent.getMyString() + "_nonDefault";

        destParent.getChild().setMyInt(nonDefaultIntValue);
        destParent.setMyString(nonDefaultStrValue);

        System.out.println("parent1       " + System.identityHashCode(destParent));
        System.out.println("parent1 child " + System.identityHashCode(destParent.child));

        final int destParentId = System.identityHashCode(destParent);
        final int destParentChildId = System.identityHashCode(destParent.child);

        final MyParent sourceParent = new MyParent();
        sourceParent.getChild().setMyString("nonDefault");

        assertThat(destParent)
                .isNotEqualTo(sourceParent);
        assertThat(destParent.getChild())
                .isNotEqualTo(sourceParent.getChild());

        System.out.println("parent2       " + System.identityHashCode(sourceParent));
        System.out.println("parent2 child " + System.identityHashCode(sourceParent.child));

        assertThat(System.identityHashCode(destParent))
                .isNotEqualTo(System.identityHashCode(sourceParent));

        System.out.println("parent1: " + destParent);
        System.out.println("parent2: " + sourceParent);

        FieldMapper.copyNonDefaults(
                sourceParent,
                destParent,
                new MyParent());

        System.out.println("parent1: " + destParent);
        System.out.println("parent2: " + sourceParent);

        System.out.println();
        System.out.println("parent1       " + System.identityHashCode(destParent));
        System.out.println("parent1 child " + System.identityHashCode(destParent.child));
        System.out.println("parent2       " + System.identityHashCode(sourceParent));
        System.out.println("parent2 child " + System.identityHashCode(sourceParent.child));

        assertThat(System.identityHashCode(destParent))
                .isNotEqualTo(System.identityHashCode(sourceParent));

        assertThat(System.identityHashCode(destParent.child))
                .isNotEqualTo(System.identityHashCode(sourceParent.child));

        assertThat(destParent.myInt)
                .isEqualTo(sourceParent.myInt);
        assertThat(destParent.myString)
                .isEqualTo(nonDefaultStrValue);
        assertThat(destParent.child.myInt)
                .isEqualTo(nonDefaultIntValue);
        assertThat(destParent.child.myString)
                .isEqualTo(sourceParent.child.myString);

        assertThat(System.identityHashCode(destParent))
                .isEqualTo(destParentId);
        assertThat(System.identityHashCode(destParent.child))
                .isEqualTo(destParentChildId);
    }

    private static class MyObject {

        private String string;

        public String getString() {
            return string;
        }

        public void setString(final String string) {
            this.string = string;
        }
    }

    private static class MyParent {

        private String myString = "abc";
        private int myInt = 1;
        private MyChild child = new MyChild();


        public String getMyString() {
            return myString;
        }

        public void setMyString(final String myString) {
            this.myString = myString;
        }

        public int getMyInt() {
            return myInt;
        }

        public void setMyInt(final int myInt) {
            this.myInt = myInt;
        }

        public MyChild getChild() {
            return child;
        }

        public void setChild(final MyChild child) {
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
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
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

        public String getMyString() {
            return myString;
        }

        public void setMyString(final String myString) {
            this.myString = myString;
        }

        public int getMyInt() {
            return myInt;
        }

        public void setMyInt(final int myInt) {
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
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
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
