package stroom.query.language.functions;

import stroom.test.common.TestCase;
import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

public class TestVal {

//    @TestFactory
//    Stream<DynamicTest> testSerDeSer() {
//        return TestUtil.buildDynamicTestStream()
//                .withInputTypes(Val.class, Class.class)
//                .withOutputType(Val.class)
//                .withTestFunction(tesatCase -> {
//                    final Val val = tesatCase.getInput()._1();
//                    final Class clazz = tesatCase.getInput()._2();
//                    final Val val2 = TestUtil.testSerialisation(val, clazz);
//                    return val2;
//                })
//                .withAssertions(tuple2ValTestOutcome -> {
//                    final Val input = tuple2ValTestOutcome.getInput()._1();
//                    final Val output = tuple2ValTestOutcome.getActualOutput();
//                    Assertions.assertThat(output)
//                            .isEqualTo(input);
//                })
//                .addCase(Tuple.of(ValString.create("foo"), ValString.class), null)
//                .addCase(Tuple.of(ValLong.create(123L), ValLong.class), null)
//                .build();
//    }


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

    @TestFactory
    Stream<DynamicTest> testToNumber() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Val.class)
                .withOutputType(Object.class)
                .withSingleArgTestFunction(Val::toNumber)
                .withSimpleEqualityAssertion()
                .withNameFunction(TestVal::getTestName)
                .addCase(ValNull.INSTANCE, null)
                .addCase(ValErr.create("foo"), null)
                .addCase(Val.create("foo"), null)
                .addCase(Val.create("123"), 123L)
                .addCase(Val.create("1.23"), 1.23D)
                .addCase(Val.create(123), 123)
                .addCase(Val.create(123L), 123L)
                .addCase(Val.create(123D), 123D)
                .addCase(Val.create(1.23D), 1.23D)
                .addCase(Val.create(123F), 123F)
                .addCase(Val.create(1.23F), 1.23F)
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

    static String getTestName(final TestCase<Val, ?> testCase) {
        final Object input = testCase.getInput();
        if (input == null) {
            return "null";
        } else {
            return input.getClass().getSimpleName() + "(" + input + ")";
        }
    }
}
