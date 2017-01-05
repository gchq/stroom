package stroom.util.shared;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class EqualsBuilderTest {

    @Test
    public void testIntArrays(){
        int[] array1 = {1, 2, 3};
        int[] array2 = {1, 2, 3};
        int[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    public void testLongArrays() {
        long[] array1 = {1, 2, 3};
        long[] array2 = {1, 2, 3};
        long[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    public void testShortArrays() {
        short[] array1 = {1, 2, 3};
        short[] array2 = {1, 2, 3};
        short[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    public void testCharArrays() {
        char[] array1 = {1, 2, 3};
        char[] array2 = {1, 2, 3};
        char[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    public void testByteArrays() {
        byte[] array1 = {1, 2, 3};
        byte[] array2 = {1, 2, 3};
        byte[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    public void testDoubleArrays() {
        double[] array1 = {1, 2, 3};
        double[] array2 = {1, 2, 3};
        double[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    public void testFloatArrays() {
        float[] array1 = {1, 2, 3};
        float[] array2 = {1, 2, 3};
        float[] array3 = {1, 2, 99};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }

    @Test
    public void testBooleanArrays() {
        boolean[] array1 = {true, false, true};
        boolean[] array2 = {true, false, true};
        boolean[] array3 = {true, false, false};
        assertThat(new EqualsBuilder().append(array1, array2).isEquals(), equalTo(true));
        assertThat(new EqualsBuilder().append(array1, array3).isEquals(), equalTo(false));
    }
}
