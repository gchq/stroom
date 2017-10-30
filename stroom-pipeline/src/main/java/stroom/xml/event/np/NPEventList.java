/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.xml.event.np;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import stroom.xml.event.EventList;

public final class NPEventList implements EventList {
    public static final byte START_ELEMENT = 1;
    public static final byte START_ELEMENT_WITH_ATTS = 2;
    public static final byte END_ELEMENT = 3;
    public static final byte CHARACTERS = 4;

    private static final String DELIMITER = "|";

    final NPEventListNamePool namePool;
    final byte[] eventTypeArr;
    final int[] nameCodeArr;
    final NPAttributes[] attsArr;
    final char[] charArr;
    final int[] charPosArr;
    private final int hash;

    public NPEventList(final NPEventListNamePool namePool, final byte[] eventTypeArr, final int[] nameCodeArr,
            final NPAttributes[] attsArr, final char[] charArr, final int[] charPosArr) {
        this.namePool = namePool;
        this.eventTypeArr = eventTypeArr;
        this.nameCodeArr = nameCodeArr;
        this.attsArr = attsArr;
        this.charArr = charArr;
        this.charPosArr = charPosArr;

        // Build a hash code straight away as we will need it.
        int code = 31;
        if (namePool == null) {
            code = code * 31;
        } else {
            code = code * 31 + namePool.hashCode();
        }
        if (eventTypeArr == null) {
            code = code * 31;
        } else {
            code = code * 31 + eventTypeArr.length;
            for (int i = 0; i < eventTypeArr.length; i++) {
                code = code * 31 + eventTypeArr[i];
            }
        }
        if (nameCodeArr == null) {
            code = code * 31;
        } else {
            code = code * 31 + nameCodeArr.length;
            for (int i = 0; i < nameCodeArr.length; i++) {
                code = code * 31 + nameCodeArr[i];
            }
        }
        if (attsArr == null) {
            code = code * 31;
        } else {
            code = code * 31 + attsArr.length;
            for (int i = 0; i < attsArr.length; i++) {
                if (attsArr[i] == null) {
                    code = code * 31;
                } else {
                    code = code * 31 + attsArr[i].hashCode();
                }
            }
        }
        if (charArr == null) {
            code = code * 31;
        } else {
            code = code * 31 + charArr.length;
            for (int i = 0; i < charArr.length; i++) {
                code = code * 31 + charArr[i];
            }
        }
        if (charPosArr == null) {
            code = code * 31;
        } else {
            code = code * 31 + charPosArr.length;
            for (int i = 0; i < charPosArr.length; i++) {
                code = code * 31 + charPosArr[i];
            }
        }
        hash = code;
    }

    @Override
    public void fire(final ContentHandler handler) throws SAXException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof NPEventList)) {
            return false;
        }

        final NPEventList list = (NPEventList) obj;

        if ((namePool != null && list.namePool == null) || (namePool == null && list.namePool != null)) {
            return false;
        } else if (namePool != null && list.namePool != null && !namePool.equals(list.namePool)) {
            return false;
        }
        if ((eventTypeArr != null && list.eventTypeArr == null)
                || (eventTypeArr == null && list.eventTypeArr != null)) {
            return false;
        } else if (eventTypeArr != null && list.eventTypeArr != null) {
            if (eventTypeArr.length != list.eventTypeArr.length) {
                return false;
            }
            for (int i = 0; i < eventTypeArr.length; i++) {
                if (eventTypeArr[i] != list.eventTypeArr[i]) {
                    return false;
                }
            }
        }
        if ((nameCodeArr != null && list.nameCodeArr == null) || (nameCodeArr == null && list.nameCodeArr != null)) {
            return false;
        } else if (nameCodeArr != null && list.nameCodeArr != null) {
            if (nameCodeArr.length != list.nameCodeArr.length) {
                return false;
            }
            for (int i = 0; i < nameCodeArr.length; i++) {
                if (nameCodeArr[i] != list.nameCodeArr[i]) {
                    return false;
                }
            }
        }
        if ((attsArr != null && list.attsArr == null) || (attsArr == null && list.attsArr != null)) {
            return false;
        } else if (attsArr != null && list.attsArr != null) {
            if (attsArr.length != list.attsArr.length) {
                return false;
            }
            for (int i = 0; i < attsArr.length; i++) {
                if ((attsArr[i] != null && list.attsArr[i] == null)
                        || (attsArr[i] == null && list.attsArr[i] != null)) {
                    return false;
                }
                if (attsArr[i] != null && list.attsArr[i] != null) {
                    if (!attsArr[i].equals(list.attsArr[i])) {
                        return false;
                    }
                }
            }
        }
        if ((charArr != null && list.charArr == null) || (charArr == null && list.charArr != null)) {
            return false;
        } else if (charArr != null && list.charArr != null) {
            if (charArr.length != list.charArr.length) {
                return false;
            }
            for (int i = 0; i < charArr.length; i++) {
                if (charArr[i] != list.charArr[i]) {
                    return false;
                }
            }
        }
        if ((charPosArr != null && list.charPosArr == null) || (charPosArr == null && list.charPosArr != null)) {
            return false;
        } else if (charPosArr != null && list.charPosArr != null) {
            if (charPosArr.length != list.charPosArr.length) {
                return false;
            }
            for (int i = 0; i < charPosArr.length; i++) {
                if (charPosArr[i] != list.charPosArr[i]) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public String toString() {
        int eventTypeIndex = 0;
        int nameCodeIndex = 0;

        int attsIndex = 0;
        int charPosIndex = 0;

        int lastPos = 0;

        int nameCode = 0;
        NPAttributes atts = null;
        final StringBuilder sb = new StringBuilder();

        for (eventTypeIndex = 0; eventTypeIndex < eventTypeArr.length; eventTypeIndex++) {
            {
                switch (eventTypeArr[eventTypeIndex]) {
                case START_ELEMENT:
                    nameCode = nameCodeArr[nameCodeIndex++];
                    sb.append(START_ELEMENT);
                    sb.append(namePool.getURI(nameCode));
                    sb.append(DELIMITER);
                    sb.append(namePool.getLocalName(nameCode));
                    sb.append(DELIMITER);
                    sb.append(namePool.getDisplayName(nameCode));
                    sb.append(DELIMITER);
                    break;
                case START_ELEMENT_WITH_ATTS:
                    nameCode = nameCodeArr[nameCodeIndex++];
                    sb.append(START_ELEMENT);
                    sb.append(namePool.getURI(nameCode));
                    sb.append(DELIMITER);
                    sb.append(namePool.getLocalName(nameCode));
                    sb.append(DELIMITER);
                    sb.append(namePool.getDisplayName(nameCode));
                    sb.append(DELIMITER);

                    atts = attsArr[attsIndex++];
                    sb.append(atts.toString());
                    break;
                case END_ELEMENT:
                    sb.append(END_ELEMENT);
                    break;
                case CHARACTERS:
                    final int pos = charPosArr[charPosIndex++];
                    sb.append(charArr, lastPos, pos - lastPos);
                    lastPos = pos;
                    break;
                }
            }
        }

        return sb.toString();
    }
}
