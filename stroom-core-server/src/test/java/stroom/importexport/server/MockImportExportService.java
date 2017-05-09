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

package stroom.importexport.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.entity.shared.DocRefs;
import stroom.entity.shared.ImportState;
import stroom.util.shared.Message;
import stroom.util.shared.SharedList;
import stroom.util.spring.StroomSpringProfiles;

import java.nio.file.Path;
import java.util.List;

@Profile(StroomSpringProfiles.TEST)
@Component
public class MockImportExportService implements ImportExportService {
    @Override
    public SharedList<ImportState> createImportConfirmationList(final Path data) {
        return null;
    }

    @Override
    public void performImportWithConfirmation(final Path data, final List<ImportState> confirmList) {
    }

    @Override
    public void performImportWithoutConfirmation(final Path data) {
    }

    @Override
    public void exportConfig(final DocRefs criteria, final Path data, final List<Message> messageList) {
    }
}
