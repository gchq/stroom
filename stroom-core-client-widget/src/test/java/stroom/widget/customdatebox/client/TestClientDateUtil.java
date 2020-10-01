package stroom.widget.customdatebox.client;


import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.time.format.DateTimeFormatter;

public class TestClientDateUtil {


    @Test
    public void testConvertJavaFormatToJs() {

        // Formats copied from FormatViewImpl
        doConversionTest("yyyy-MM-dd'T'HH:mm:ss.SSSXX", "YYYY-MM-DD'T'HH:mm:ss.SSSZZ");
        doConversionTest("yyyy-MM-dd'T'HH:mm:ss.SSS xx", "YYYY-MM-DD'T'HH:mm:ss.SSS ZZ");
        doConversionTest("yyyy-MM-dd'T'HH:mm:ss.SSS xxx", "YYYY-MM-DD'T'HH:mm:ss.SSS Z");
//        doConversionTest("yyyy-MM-dd'T'HH:mm:ss.SSS VV", "YYYY-MM-DD'T'HH:mm:ss.SSS VV");
        doConversionTest("yyyy-MM-dd'T'HH:mm:ss.SSS", "YYYY-MM-DD'T'HH:mm:ss.SSS");
        doConversionTest("dd/MM/yyyy HH:mm:ss", "DD/MM/YYYY HH:mm:ss");
        doConversionTest("dd/MM/yy HH:mm:ss", "DD/MM/YY HH:mm:ss");
        doConversionTest("MM/dd/yyyy HH:mm:ss", "MM/DD/YYYY HH:mm:ss");
        doConversionTest("d MMM yyyy HH:mm:ss", "D MMM YYYY HH:mm:ss");
        doConversionTest("yyyy-MM-dd", "YYYY-MM-DD");
        doConversionTest("dd/MM/yyyy", "DD/MM/YYYY");
        doConversionTest("dd/MM/yy", "DD/MM/YY");
        doConversionTest("MM/dd/yyyy", "MM/DD/YYYY");
        doConversionTest("d MMM yyyy", "D MMM YYYY");
    }

    private void doConversionTest(final String javaFormatStr,
                                  final String expectedJsFormatStr) {

        System.out.println("Testing " + javaFormatStr);

        DateTimeFormatter.ofPattern(javaFormatStr);

        final String actual = ClientDateUtil.convertJavaFormatToJs(javaFormatStr).get();

        System.out.println("Actual " + actual);

        Assertions.assertThat(actual).isEqualTo(expectedJsFormatStr);
    }
}