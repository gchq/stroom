package stroom.query.common.v2;

import com.esotericsoftware.kryo.io.Output;

interface KeyPart {

    boolean isGrouped();

    void write(Output output);

    void append(StringBuilder sb);
}
