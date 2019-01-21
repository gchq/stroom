package stroom.util.shared;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EqualsBuilderTest {

    @Test
    void testIntArrays() {
        int[] array1 = {1, 2, 3};
        int[] array2 = {1, 2, 3};
        int[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals()).isTrue();
        assertThat(new EqualsBuilder().append(array1, array3).isEquals()).isFalse();
    }

    @Test
    void testLongArrays() {
        long[] array1 = {1, 2, 3};
        long[] array2 = {1, 2, 3};
        long[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals()).isTrue();
        assertThat(new EqualsBuilder().append(array1, array3).isEquals()).isFalse();
    }

    @Test
    void testShortArrays() {
        short[] array1 = {1, 2, 3};
        short[] array2 = {1, 2, 3};
        short[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals()).isTrue();
        assertThat(new EqualsBuilder().append(array1, array3).isEquals()).isFalse();
    }

    @Test
    void testCharArrays() {
        char[] array1 = {1, 2, 3};
        char[] array2 = {1, 2, 3};
        char[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals()).isTrue();
        assertThat(new EqualsBuilder().append(array1, array3).isEquals()).isFalse();
    }

    @Test
    void testByteArrays() {
        byte[] array1 = {1, 2, 3};
        byte[] array2 = {1, 2, 3};
        byte[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals()).isTrue();
        assertThat(new EqualsBuilder().append(array1, array3).isEquals()).isFalse();
    }

    @Test
    void testDoubleArrays() {
        double[] array1 = {1, 2, 3};
        double[] array2 = {1, 2, 3};
        double[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals()).isTrue();
        assertThat(new EqualsBuilder().append(array1, array3).isEquals()).isFalse();
    }

    @Test
    void testFloatArrays() {
        float[] array1 = {1, 2, 3};
        float[] array2 = {1, 2, 3};
        float[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals()).isTrue();
        assertThat(new EqualsBuilder().append(array1, array3).isEquals()).isFalse();
    }

    @Test
    void testBooleanArrays() {
        boolean[] array1 = {true, false, true};
        boolean[] array2 = {true, false, true};
        boolean[] array3 = {true, false, false};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals()).isTrue();
        assertThat(new EqualsBuilder().append(array1, array3).isEquals()).isFalse();
    }
}
