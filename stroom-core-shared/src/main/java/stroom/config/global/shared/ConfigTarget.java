/*
 * Copyright 2020 Crown Copyright
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

package stroom.config.global.shared;

/**
 * Identifies the config object that holds a property being set from the screen where its value is chosen, rather
 * than from the global properties screen.
 * <p>
 * A config object's base property path is not serialised to the client, so the client cannot name a property by its
 * full path. It names the object and the leaf property instead and the server resolves the rest. Add a value here
 * when a new screen needs to set one of its own defaults.
 * </p>
 */
public enum ConfigTarget {

    ANALYTIC_UI_DEFAULT,
    REPORT_UI_DEFAULT
}
