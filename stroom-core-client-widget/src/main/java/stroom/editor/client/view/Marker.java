/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.editor.client.view;

import edu.ycp.cs.dh.acegwt.client.ace.AceMarkerType;
import edu.ycp.cs.dh.acegwt.client.ace.AceRange;

public class Marker {

    private final AceRange range;
    private final String clazz;
    private final AceMarkerType type;
    private final boolean inFront;

    public Marker(final AceRange range, final String clazz, final AceMarkerType type, final boolean inFront) {
        this.range = range;
        this.clazz = clazz;
        this.type = type;
        this.inFront = inFront;
    }

    public AceRange getRange() {
        return range;
    }

    public String getClazz() {
        return clazz;
    }

    public AceMarkerType getType() {
        return type;
    }

    public boolean isInFront() {
        return inFront;
    }
}
