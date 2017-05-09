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
import stroom.entity.shared.ImportState.ImportMode;
import stroom.util.shared.Message;
import stroom.util.spring.StroomSpringProfiles;

import java.nio.file.Path;
import java.util.List;

@Profile(StroomSpringProfiles.TEST)
@Component
public class MockImportExportSerializer implements ImportExportSerializer {
    @Override
    public void read(final Path dir, final List<ImportState> importStateList, final ImportMode importMode) {
    }

    @Override
    public void write(final Path dir, final DocRefs docRefs, final boolean omitAuditFields, final List<Message> messageList) {
    }
}
