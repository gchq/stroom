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

package stroom.query.common.v2;

public interface OpenGroups {

    OpenGroups NONE = new OpenGroups() {
        @Override
        public boolean isOpen(final Key key) {
            return false;
        }

        @Override
        public void complete(final Key key) {

        }

        @Override
        public boolean isNotEmpty() {
            return false;
        }
    };

    OpenGroups ALL = new OpenGroups() {
        @Override
        public boolean isOpen(final Key key) {
            return true;
        }

        @Override
        public void complete(final Key key) {

        }

        @Override
        public boolean isNotEmpty() {
            return true;
        }
    };

    boolean isOpen(Key key);

    void complete(Key key);

    boolean isNotEmpty();
}
