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

package stroom.index.impl;

import stroom.index.api.IndexVolumeGroupService;
import stroom.index.shared.IndexVolumeGroup;
import stroom.util.shared.PathSafeNames;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TestIndexVolumeGroupResourceImpl {

    @Mock
    private IndexVolumeGroupService service;

    private IndexVolumeGroupResourceImpl resource;

    @BeforeEach
    void setUp() {
        resource = new IndexVolumeGroupResourceImpl(() -> service);
    }

    @Test
    void create_nameWithSlash() {
        // Regression test (gwt-bugs #42): a name with a '/' is looked up in a URL's path, where the
        // server refuses it, so the name can't be used; it is now refused when created
        assertThatThrownBy(() -> resource.create("A/B volumes"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PathSafeNames.CONTAINS_SLASH_MESSAGE);
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void create() {
        resource.create("Volumes");

        Mockito.verify(service).getOrCreate("Volumes");
    }

    @Test
    void update_nameWithSlash() {
        final IndexVolumeGroup renamed = IndexVolumeGroup.builder().id(1).name("A/B volumes").build();

        assertThatThrownBy(() -> resource.update(1, renamed))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PathSafeNames.CONTAINS_SLASH_MESSAGE);
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void update() {
        final IndexVolumeGroup renamed = IndexVolumeGroup.builder().id(1).name("Volumes").build();

        resource.update(1, renamed);

        Mockito.verify(service).update(renamed);
    }
}
