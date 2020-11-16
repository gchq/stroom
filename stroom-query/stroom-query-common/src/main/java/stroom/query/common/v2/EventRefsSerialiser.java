package stroom.query.common.v2;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public class EventRefsSerialiser {
    public static final EventRef[] EMPTY_REFS = new EventRef[0];

    static EventRef read(final Input input) {
        final long streamId = input.readLong();
        final long eventId = input.readLong();
        return new EventRef(streamId, eventId);
    }

    static void write(final Output output, final EventRef eventRef) {
        output.writeLong(eventRef.getStreamId());
        output.writeLong(eventRef.getEventId());
    }

    static EventRef[] readArray(final Input input) {
        EventRef[] refs = EMPTY_REFS;

        final int length = input.readInt();
        if (length > 0) {
            refs = new EventRef[length];
            for (int i = 0; i < length; i++) {
                refs[i] = read(input);
            }
        }

        return refs;
    }

    static void writeArray(final Output output, final EventRef[] refs) {
        output.writeInt(refs.length);
        for (final EventRef eventRef : refs) {
            write(output, eventRef);
        }
    }
}
