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

package stroom.widget.menu.client.presenter;

public class GroupHeading extends Item {

    private final String groupName;

    public GroupHeading(final int priority, final String groupName) {
        super(priority);
        this.groupName = groupName;
    }

    public String getGroupName() {
        return groupName;
    }

//    @Override
//    public boolean equals(final Object obj) {
//        if (!(obj instanceof GroupHeading)) {
//            return false;
//        }
//        final GroupHeading groupHeading = (GroupHeading) obj;
//        return groupHeading.groupName.equals(this.groupName);
//    }
//
//    @Override
//    public int hashCode() {
//        return groupName.hashCode();
//    }
//
//    @Override
//    public String toString() {
//        return groupName;
//    }
}
