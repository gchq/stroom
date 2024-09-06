package stroom.query.common.v2;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.DataWriter;

interface KeyPart {

    boolean isGrouped();

    Val[] getGroupValues();

    void write(DataWriter writer);

    void append(StringBuilder sb);
}
