/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.presenter.client;

/// Something that registers handlers while it is bound, and removes them when it is unbound.
public interface HandlerContainer {

    /// Registers its handlers, if it hasn't already ([HandlerContainerImpl#onBind()]).
    void bind();

    /// @return Whether it is bound.
    boolean isBound();

    /// Removes the handlers it registered, if it is bound.
    void unbind();
}
