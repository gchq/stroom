/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.data.store.impl.fs;


import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.shared.SimpleMeta;

public class NoOpPhysicalDeleteOutcome implements PhysicalDeleteOutcome {

    private final DataVolume dataVolume;
    private final SimpleMeta simpleMeta;

    public NoOpPhysicalDeleteOutcome(final DataVolume dataVolume,
                                     final SimpleMeta simpleMeta) {
        this.dataVolume = dataVolume;
        this.simpleMeta = simpleMeta;
    }

    @Override
    public boolean wasSuccessful() {
        return true;
    }

    @Override
    public DataVolume dataVolume() {
        return null;
    }

    @Override
    public SimpleMeta simpleMeta() {
        return null;
    }
}
