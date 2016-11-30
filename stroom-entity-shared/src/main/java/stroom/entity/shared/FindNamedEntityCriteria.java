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

package stroom.entity.shared;

public abstract class FindNamedEntityCriteria extends BaseCriteria {
    public static final OrderBy ORDER_BY_NAME = new OrderBy("Name", "name", true);
    private static final long serialVersionUID = -970306839701196839L;
    private StringCriteria name = new StringCriteria();

    public FindNamedEntityCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindNamedEntityCriteria(final String name) {
        this.name.setString(name);
    }

    public StringCriteria getName() {
        return name;
    }

    public void setName(final StringCriteria name) {
        this.name = name;
    }
}
