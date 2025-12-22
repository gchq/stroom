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

import edu.ycp.cs.dh.acegwt.client.ace.AceAnnotationType;

public class Annotation {

    private final int row;
    private final int column;
    private final String text;
    private final AceAnnotationType type;

    public Annotation(final int row, final int column, final String text, final AceAnnotationType type) {
        this.row = row;
        this.column = column;
        this.text = text;
        this.type = type;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public String getText() {
        return text;
    }

    public AceAnnotationType getType() {
        return type;
    }
}
