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

package stroom.util;

import java.util.Map;

/**
 * <p>
 * Class used to merge maps in our spring configuration.
 * </p>
 */
public class MapMerge {
    private Map<Object, Object> source;
    private Map<Object, Object> target;

    public Map<Object, Object> getTarget() {
        return target;
    }

    public void setTarget(final Map<Object, Object> target) {
        this.target = target;
        peformMerge();
    }

    public Map<Object, Object> getSource() {
        return source;
    }

    public void setSource(final Map<Object, Object> list) {
        this.source = list;
        peformMerge();
    }

    private void peformMerge() {
        if (source != null && target != null) {
            target.putAll(source);
        }
    }
}
