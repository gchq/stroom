package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ref.MyByteBufferOutput;

import com.esotericsoftware.kryo.io.Output;

interface KeyPart {

    boolean isGrouped();

    Val[] getGroupValues();

    void write(MyByteBufferOutput output);

    void append(StringBuilder sb);
}
