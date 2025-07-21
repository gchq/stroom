package stroom.query.common.v2;

import stroom.query.language.functions.Val;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Key {

    public static Key ROOT_KEY = new Key(0, Collections.emptyList());

    private final long timeMs;
    private final List<KeyPart> keyParts;

    Key(final long timeMs,
        final List<KeyPart> keyParts) {
        this.timeMs = timeMs;
        this.keyParts = keyParts;
    }

    long getTimeMs() {
        return timeMs;
    }

    List<KeyPart> getKeyParts() {
        return keyParts;
    }

    Key resolve(final long timeMs, final Val[] groupValues) {
        return resolve(timeMs, new GroupKeyPart(groupValues));
    }

    Key resolve(final long timeMs, final long uniqueId) {
        return resolve(timeMs, new UngroupedKeyPart(uniqueId));
    }

    private Key resolve(final long timeMs, final KeyPart keyPart) {
        final List<KeyPart> parts = new ArrayList<>(keyParts.size() + 1);
        parts.addAll(keyParts);
        parts.add(keyPart);
        return new Key(timeMs, parts);
    }

    Key getParent() {
        if (!keyParts.isEmpty()) {
            return new Key(timeMs, keyParts.subList(0, keyParts.size() - 1));
        }
        return null;
    }

    int getDepth() {
        return keyParts.size() - 1;
    }

    int getChildDepth() {
        return keyParts.size();
    }

    boolean isGrouped() {
        final KeyPart last = getLast();
        return last == null || last.isGrouped();
    }

    private KeyPart getLast() {
        if (!keyParts.isEmpty()) {
            return keyParts.getLast();
        }
        return null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Key key = (Key) o;
        return Objects.equals(timeMs, key.timeMs) &&
               Objects.equals(keyParts, key.keyParts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeMs, keyParts);
    }

    @Override
    public String toString() {
        if (keyParts.isEmpty()) {
            return "root";
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("time: ");
        sb.append(timeMs);
        sb.append(" - ");

        for (int i = 0; i < keyParts.size(); i++) {
            final KeyPart keyPart = keyParts.get(i);
            if (i > 0) {
                sb.append("/");
            }
            keyPart.append(sb);
        }
        return sb.toString();
    }
}
