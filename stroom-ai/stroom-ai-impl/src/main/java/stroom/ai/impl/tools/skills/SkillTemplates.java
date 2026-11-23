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

package stroom.ai.impl.tools.skills;

import stroom.ai.impl.tools.skills.SkillDoc.Skill;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/// The skill templates shipped with the tools: built-in starting points `propose_skill` offers, never live skills
/// (nothing is in an explorer until a user saves a skill). Each is a procedure with `[[...]]` placeholders for what
/// a site supplies (its sources, fields and role conditions), filled with the user from their own indexes, and no
/// product-specific content.
final class SkillTemplates {

    /// The templates, by their names as [SkillDoc#slug] gives them; each is `skill-templates/<name>.md`.
    static final List<String> NAMES = List.of(
            "user-activity-timeline",
            "x-then-y-by-same-entity",
            "who-of-group-did-x");
    private static final String ROOT = "skill-templates/";

    private SkillTemplates() {
        // Static only.
    }

    /// @return Every template, in order.
    static List<Template> all() {
        return NAMES.stream().map(SkillTemplates::read).toList();
    }

    /// @param name A template's name, or its skill's name, ignoring case.
    /// @return The template, if there is one of that name.
    static Optional<Template> find(final String name) {
        final String slug = SkillDoc.slug(name);
        return all().stream()
                .filter(template -> template.name().equals(slug)
                                    || SkillDoc.slug(template.skill().name()).equals(slug))
                .findFirst();
    }

    private static Template read(final String name) {
        try (final InputStream in = SkillTemplates.class.getResourceAsStream(ROOT + name + ".md")) {
            if (in == null) {
                throw new IllegalStateException("No skill template " + name);
            }
            final String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new Template(name, text, SkillDoc.parse(text).skills().getFirst());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    // --------------------------------------------------------------------------------


    /// A skill template.
    ///
    /// @param name  Its name, e.g. `user-activity-timeline`.
    /// @param text  Its Markdown, from its `#` heading.
    /// @param skill It, read.
    record Template(String name, String text, Skill skill) {

    }
}
