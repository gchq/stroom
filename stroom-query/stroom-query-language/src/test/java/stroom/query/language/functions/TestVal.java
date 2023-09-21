package stroom.query.language.functions;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

public class TestVal {

    @TestFactory
    Stream<DynamicTest> testNullSafeCreate() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Long.class)
                .withOutputType(Val.class)
                .withSingleArgTestFunction(input ->
                        Val.nullSafeCreate(input, ValDate::create))
                .withSimpleEqualityAssertion()
                .addCase(null, ValNull.INSTANCE)
                .addCase(1234L, ValDate.create(1234L))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testNullSafeCreate2() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Val.class)
                .withSingleArgTestFunction(input ->
                        Val.nullSafeCreate(
                                input,
                                this::myLongConverter,
                                ValDate::create))
                .withSimpleEqualityAssertion()
                .addCase(null, ValNull.INSTANCE)
                .addCase("", ValNull.INSTANCE)
                .addCase("1234", ValDate.create(1234L))
                .build();
    }

    private Long myLongConverter(final String str) {
        if (str == null) {
            return null;
        } else if (str.isBlank()) {
            return null;
        } else {
            return Long.valueOf(str);
        }
    }
}
