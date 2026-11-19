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

package stroom.widget.form.client.component;

import stroom.util.shared.PropertyMap;
import stroom.util.shared.ResourceKey;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileChooserController {

    private final List<ResourceKey> successes = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();

    @Test
    void readReply_success() {
        final PropertyMap reply = new PropertyMap();
        reply.setSuccess(true);
        reply.put(ResourceKey.KEY, "res-1");
        reply.put(ResourceKey.NAME, "import.zip");

        FileChooserController.readReply(reply.toArgLine(), successes::add, failures::add);

        assertThat(successes).hasSize(1);
        assertThat(successes.get(0).getKey()).isEqualTo("res-1");
        assertThat(successes.get(0).getName()).isEqualTo("import.zip");
        assertThat(failures).isEmpty();
    }

    @Test
    void readReply_failure() {
        final PropertyMap reply = new PropertyMap();
        reply.setSuccess(false);
        reply.put("exception", "Too big");

        FileChooserController.readReply(reply.toArgLine(), successes::add, failures::add);

        assertThat(successes).isEmpty();
        assertThat(failures).containsExactly("Too big");
    }

    @Test
    void readReply_none() {
        FileChooserController.readReply(null, successes::add, failures::add);

        assertThat(successes).isEmpty();
        assertThat(failures).containsExactly("Unable to read file");
    }

    @Test
    void readReply_notAPropertyMap() {
        // Not a property map at all (e.g. a proxy's error page): not a success
        FileChooserController.readReply("<html>Bad gateway</html>", successes::add, failures::add);

        assertThat(successes).isEmpty();
        assertThat(failures).hasSize(1);
    }
}
