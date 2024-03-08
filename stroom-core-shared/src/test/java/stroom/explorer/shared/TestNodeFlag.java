package stroom.explorer.shared;

import stroom.test.common.TestUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

class TestNodeFlag {

    @Test
    void validateFlags_pass() {

        final EnumSet<NodeFlag> flags = EnumSet.of(
                NodeFlag.FAVOURITE,
                NodeFlag.CLOSED,
                NodeFlag.FOLDER);

        // Would throw an ex if not good
        NodeFlag.validateFlags(flags);
    }

    @Test
    void validateFlags_fail() {

        final EnumSet<NodeFlag> flags = EnumSet.of(
                NodeFlag.OPEN,
                NodeFlag.CLOSED,
                NodeFlag.FOLDER);

        Assertions.assertThatThrownBy(() ->
                                NodeFlag.validateFlags(flags))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void validateFlag_pass() {

        final EnumSet<NodeFlag> flags = EnumSet.of(
                NodeFlag.FAVOURITE,
                NodeFlag.CLOSED,
                NodeFlag.FOLDER);

        // Would throw an ex if not good
        NodeFlag.validateFlag(NodeFlag.FILTER_MATCH, flags);
    }

    @Test
    void validateFlag_fail() {

        final EnumSet<NodeFlag> flags = EnumSet.of(
                NodeFlag.FAVOURITE,
                NodeFlag.CLOSED,
                NodeFlag.FOLDER);

        Assertions.assertThatThrownBy(() ->
                        NodeFlag.validateFlag(NodeFlag.OPEN, flags))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void testSerDeser() {
        TestUtil.testSerialisation(NodeFlag.OPEN, NodeFlag.class);
    }
}
