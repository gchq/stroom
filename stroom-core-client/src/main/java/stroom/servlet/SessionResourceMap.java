/*
 * Copyright 2017 Crown Copyright
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

package stroom.servlet;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.util.shared.ResourceKey;
import stroom.util.spring.StroomScope;

import java.util.HashMap;

@Component
@Scope(value = StroomScope.SESSION)
public class SessionResourceMap {
    private final HashMap<ResourceKey, ResourceKey> sessionResourceMap = new HashMap<>();

    public void put(final ResourceKey key, final ResourceKey value) {
        sessionResourceMap.put(key, value);
    }

    public ResourceKey get(final ResourceKey key) {
        return sessionResourceMap.get(key);
    }

    public ResourceKey remove(final ResourceKey key) {
        return sessionResourceMap.remove(key);
    }
}

