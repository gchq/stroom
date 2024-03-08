package stroom.query.common.v2;

import stroom.query.language.functions.Val;

import com.esotericsoftware.kryo.io.Output;

interface KeyPart {

    boolean isGrouped();

    Val[] getGroupValues();

    void write(Output output);

    void append(StringBuilder sb);
}
