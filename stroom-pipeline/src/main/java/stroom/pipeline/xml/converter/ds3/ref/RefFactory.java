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

package stroom.pipeline.xml.converter.ds3.ref;

public abstract class RefFactory {
    private final String reference;
    private final String section;

    public RefFactory(final String reference, final String section) {
        this.reference = reference;
        this.section = section;
    }

    public String getReference() {
        return reference;
    }

    public String getSection() {
        return section;
    }

    @Override
    public String toString() {
        return section;
    }

    public abstract boolean isText();
}
