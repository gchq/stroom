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

package stroom.processor.impl;

import stroom.processor.shared.ProcessorProfile;
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
class TestProcessorProfileResourceImpl {

    @Mock
    private ProcessorProfileService service;

    private ProcessorProfileResourceImpl resource;

    @BeforeEach
    void setUp() {
        resource = new ProcessorProfileResourceImpl(() -> service);
    }

    @Test
    void create_nameWithSlash() {
        // Regression test (gwt-bugs #42): a name with a '/' is looked up in a URL's path, where the
        // server refuses it, so the name can't be used; it is now refused when created
        assertThatThrownBy(() -> resource.create(ProcessorProfile.builder().name("A/B profiles").build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PathSafeNames.CONTAINS_SLASH_MESSAGE);
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void create() {
        resource.create(ProcessorProfile.builder().name("Profiles").build());

        Mockito.verify(service).create(Mockito.argThat(profile -> "Profiles".equals(profile.getName())));
    }

    @Test
    void update_nameWithSlash() {
        final ProcessorProfile renamed = ProcessorProfile.builder().id(1).name("A/B profiles").build();

        assertThatThrownBy(() -> resource.update(1, renamed))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PathSafeNames.CONTAINS_SLASH_MESSAGE);
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void update() {
        final ProcessorProfile renamed = ProcessorProfile.builder().id(1).name("Profiles").build();

        resource.update(1, renamed);

        Mockito.verify(service).update(renamed);
    }
}
