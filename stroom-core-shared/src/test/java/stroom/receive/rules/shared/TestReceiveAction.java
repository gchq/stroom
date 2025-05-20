package stroom.receive.rules.shared;

import stroom.test.common.TestUtil;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestReceiveAction {

    @Test
    void test() {
        TestUtil.testSerialisation(ReceiveAction.DROP, ReceiveAction.class);
    }

    @Test
    void testFromCiString() {
        final String json = "\"DrOp\"";

        final ReceiveAction receiveAction = JsonUtil.readValue(json, ReceiveAction.class);
        assertThat(receiveAction)
                .isEqualTo(ReceiveAction.DROP);
    }
}
