package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Output;

interface KeyPart {
    boolean isGrouped();

    void write(Output output);

    void append(StringBuilder sb);
}
