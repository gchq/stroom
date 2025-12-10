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

package stroom.proxy.app.handler;

/**
 * The mode for handling unknown parameters in subPathTemplate, e.g.
 * <pre>{@code
 * 'cat/${unknownparam}/dog'
 * }</pre>
 */
public enum TemplatingMode {
    /**
     * Ignore any unknown parameters, e.g.
     * {@code 'cat/${unknownparam}/dog'} => {@code 'cat/${unknownparam}/dog'}
     */
    IGNORE_UNKNOWN_PARAMS,
    /**
     * Remove any unknown parameters, e.g.
     * {@code 'cat/${unknownparam}/dog'} => {@code 'cat/dog'}
     */
    REMOVE_UNKNOWN_PARAMS,
    /**
     * Replace any unknown parameters with '{@code XXX}', e.g.
     * {@code 'cat/${unknownparam}/dog'} => {@code 'cat/XXX/dog'}
     */
    REPLACE_UNKNOWN_PARAMS,
//    /**
//     * The path template will not be used at all.
//     */
//    DISABLED,
    ;
}
