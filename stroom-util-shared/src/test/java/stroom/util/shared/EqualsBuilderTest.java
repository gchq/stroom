package stroom.util.shared;


import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

class EqualsBuilderTest {

    @Test
    void testIntArrays() {
        int[] array1 = {1, 2, 3};
        int[] array2 = {1, 2, 3};
        int[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    void testLongArrays() {
        long[] array1 = {1, 2, 3};
        long[] array2 = {1, 2, 3};
        long[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    void testShortArrays() {
        short[] array1 = {1, 2, 3};
        short[] array2 = {1, 2, 3};
        short[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    void testCharArrays() {
        char[] array1 = {1, 2, 3};
        char[] array2 = {1, 2, 3};
        char[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    void testByteArrays() {
        byte[] array1 = {1, 2, 3};
        byte[] array2 = {1, 2, 3};
        byte[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    void testDoubleArrays() {
        double[] array1 = {1, 2, 3};
        double[] array2 = {1, 2, 3};
        double[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    void testFloatArrays() {
        float[] array1 = {1, 2, 3};
        float[] array2 = {1, 2, 3};
        float[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    void testBooleanArrays() {
        boolean[] array1 = {true, false, true};
        boolean[] array2 = {true, false, true};
        boolean[] array3 = {true, false, false};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }
}
