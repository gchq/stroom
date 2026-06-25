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

package stroom.index.impl.db;

import java.util.UUID;

public final class TestData {

    private TestData() {

    }

    static String createVolumeGroupName() {
        return String.format("VolumeGroup_%s", UUID.randomUUID());
    }

    static String createVolumeGroupName(final Object o) {
        return String.format("VolumeGroup_%s_%s", o, UUID.randomUUID());
    }

    static String createNodeName() {
        return String.format("Node_%s", UUID.randomUUID());
    }

    static String createNodeName(final Object o) {
        return String.format("Node_%s_%s", o, UUID.randomUUID());
    }

    static String createPath() {
        return String.format("/tmp/index/data/%s", UUID.randomUUID());
    }

    static String createPath(final Object o) {
        return String.format("/tmp/index/data/%s/%s", o, UUID.randomUUID());
    }

    static String createIndexName() {
        return String.format("Index_%s", UUID.randomUUID());
    }

    static String createIndexName(final Object o) {
        return String.format("Index_%s_%s", o, UUID.randomUUID());
    }
}
