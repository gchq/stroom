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

package stroom.index.client.component;

import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;

import com.google.gwt.junit.GWTMockUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexVolumeGroupEditorController {

    @BeforeEach
    void setUp() {
        // The controller's REST resources are made with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void sharedAndLocalVolumesAreMixed() {
        assertThat(IndexVolumeGroupEditorController.holdsSharedAndLocal(
                List.of(volume(VolumeType.SHARED), volume(VolumeType.LOCAL))))
                .isTrue();
    }

    @Test
    void onlySharedVolumesAreNotMixed() {
        assertThat(IndexVolumeGroupEditorController.holdsSharedAndLocal(
                List.of(volume(VolumeType.SHARED), volume(VolumeType.SHARED))))
                .isFalse();
    }

    @Test
    void onlyLocalVolumesAreNotMixed() {
        assertThat(IndexVolumeGroupEditorController.holdsSharedAndLocal(
                List.of(volume(VolumeType.LOCAL))))
                .isFalse();
    }

    @Test
    void noVolumesAreNotMixed() {
        assertThat(IndexVolumeGroupEditorController.holdsSharedAndLocal(List.of()))
                .isFalse();
    }

    private static IndexVolume volume(final VolumeType volumeType) {
        return IndexVolume.builder()
                .volumeType(volumeType)
                .build();
    }
}
