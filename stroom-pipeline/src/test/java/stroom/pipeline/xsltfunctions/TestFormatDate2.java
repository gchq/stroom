package stroom.pipeline.xsltfunctions;

import stroom.meta.shared.Meta;
import stroom.pipeline.state.MetaHolder;
import stroom.util.date.DateUtil;

import net.sf.saxon.om.Sequence;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

class TestFormatDate2 extends AbstractXsltFunctionTest<FormatDate> {

    private static final Instant META_CREATE_TIME = LocalDateTime.of(
            2010, 3, 1, 12, 45, 22, 643 * 1_000_000)
            .toInstant(ZoneOffset.UTC);

    @Mock
    private Meta mockMeta;
    @SuppressWarnings("unused") // Used by @InjectMocks
    @Mock
    private MetaHolder mockMetaHolder;
    @InjectMocks
    private FormatDate formatDate;

    @Test
    void call_millis() {

        final Instant now = Instant.now();
        final Sequence sequence = callFunctionWithSimpleArgs(now.toEpochMilli());

        final Optional<String> optVal = getStringValue(sequence);

        Assertions.assertThat(optVal)
                .hasValue(DateUtil.createNormalDateTimeString(now.toEpochMilli()));
    }

    @Test
    void call_standardFormat1() {

        final Sequence sequence = callFunctionWithSimpleArgs(
                "2001/08/01", "yyyy/MM/dd", "-07:00");

        final Optional<String> optVal = getStringValue(sequence);

        Assertions.assertThat(optVal)
                .hasValue("2001-08-01T07:00:00.000Z");
    }

    @Test
    void call_standardFormat2() {

        final Sequence sequence = callFunctionWithSimpleArgs(
                "2001/08/01 01:00:00", "yyyy/MM/dd HH:mm:ss", "-08:00");

        final Optional<String> optVal = getStringValue(sequence);

        Assertions.assertThat(optVal)
                .hasValue("2001-08-01T09:00:00.000Z");
    }

    @Test
    void call_standardFormat3() {

        final Sequence sequence = callFunctionWithSimpleArgs(
                "2001/08/01 01:00:00", "yyyy/MM/dd HH:mm:ss", "+01:00");

        final Optional<String> optVal = getStringValue(sequence);

        Assertions.assertThat(optVal)
                .hasValue("2001-08-01T00:00:00.000Z");
    }

    @Test
    void call_standardFormat4() {

        final Sequence sequence = callFunctionWithSimpleArgs(
                "2001/08/01 00:00:00", "yyyy/MM/dd HH:mm:ss");

        final Optional<String> optVal = getStringValue(sequence);

        Assertions.assertThat(optVal)
                .hasValue("2001-08-01T00:00:00.000Z");
    }

    @Test
    void call_specificFormat1() {

        final Sequence sequence = callFunctionWithSimpleArgs(
                "2001/08/01 01:00:00", "yyyy/MM/dd HH:mm:ss", "+01:00", "yyyy/MMM/ddd HH:mm:ss");

        final Optional<String> optVal = getStringValue(sequence);

        Assertions.assertThat(optVal)
                .hasValue("2001-08-01T00:00:00.000Z");
    }

    private void initMetaCreateTime() {
        Mockito.when(mockMeta.getCreateMs())
                        .thenReturn(META_CREATE_TIME.toEpochMilli());
        Mockito.when(mockMetaHolder.getMeta())
                .thenReturn(mockMeta);
    }

    @Override
    FormatDate getXsltFunction() {
        return formatDate;
    }

    @Override
    String getFunctionName() {
        return FormatDate.FUNCTION_NAME;
    }
}
