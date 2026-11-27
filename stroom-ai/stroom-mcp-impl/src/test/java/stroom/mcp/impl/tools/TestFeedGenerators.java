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

package stroom.mcp.impl.tools;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// An aggregated feed's shared Generator names no source type (decided by the user 2026-11-23): a Generator naming
/// the registry's source types is told by their names, in any case or spacing, and the feed's own proposed without
/// them.
class TestFeedGenerators {

    private static final String FEED = "Azure-Audit-V1.0-EVENTS";
    private static final List<String> NAMES = List.of("SharePoint", "ExchangeOnline", "AzureActiveDirectory",
            "MicrosoftTeams", "OneDrive");

    @Test
    void aGeneratorNamingSourceTypesIsToldAndTheFeedsProposedWithoutThem() {
        // Seen in chat 188: the first group's source name became the shared module's Generator.
        final String generator = "Microsoft 365 audit (Exchange Online and Teams)";

        assertThat(FeedGenerators.named(generator, NAMES, FEED)).containsExactly("ExchangeOnline", "MicrosoftTeams");
        assertThat(FeedGenerators.withoutSourceTypes(generator, NAMES, FEED, FEED)).isEqualTo("Microsoft 365 audit");
        // In any case or spacing, the whole name or its last word.
        assertThat(FeedGenerators.named("exchangeonline audit", NAMES, FEED)).containsExactly("ExchangeOnline");
        assertThat(FeedGenerators.named("M365: SHAREPOINT, onedrive", NAMES, FEED))
                .containsExactly("SharePoint", "OneDrive");
        assertThat(FeedGenerators.named("Entra Active Directory audit", NAMES, FEED))
                .containsExactly("AzureActiveDirectory");
        assertThat(FeedGenerators.withoutSourceTypes("Teams / OneDrive audit", NAMES, FEED, FEED))
                .isEqualTo("audit");
    }

    @Test
    void aGeneratorNamingNoSourceTypeIsTheFeeds() {
        assertThat(FeedGenerators.named("Microsoft 365 audit", NAMES, FEED)).isEmpty();
        // A word inside another word, or a first word alone, names none.
        assertThat(FeedGenerators.named("Microsoft Steams log", NAMES, FEED)).isEmpty();
        assertThat(FeedGenerators.named("Exchange gateway", NAMES, FEED)).isEmpty();
        assertThat(FeedGenerators.named(null, NAMES, FEED)).isEmpty();
        assertThat(FeedGenerators.named("", NAMES, FEED)).isEmpty();
        // A source type's last word that is the feed's own names none alone.
        assertThat(FeedGenerators.named("Azure audit", List.of("TenantAudit"), FEED)).isEmpty();
        assertThat(FeedGenerators.named("Tenant Audit", List.of("TenantAudit"), FEED)).containsExactly("TenantAudit");
    }

    @Test
    void nothingLeftProposesTheFallback() {
        assertThat(FeedGenerators.withoutSourceTypes("Exchange Online and Teams", NAMES, FEED, FEED)).isEqualTo(FEED);
    }

    @Test
    void theRegistryGivesItsSourceTypesNamesAndTheFeedsGeneratorKept() {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "MicrosoftTeams");
        row.put("value", "Teams");
        row.put("system_name", "Microsoft Teams");
        final Map<String, Object> other = new LinkedHashMap<>();
        other.put("name", "OneDrive");
        other.put("value", "OneDrive");
        other.put("system_name", null);
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put("rows", List.of(row, other));

        assertThat(FeedGenerators.sourceTypeNames(state)).containsExactly("MicrosoftTeams", "Teams",
                "Microsoft Teams", "OneDrive");
        assertThat(FeedGenerators.kept(state)).isNull();
        state.put(FeedGenerators.REGISTRY_KEY, Map.of("value", "Microsoft 365 audit", "by", "user"));
        assertThat(FeedGenerators.kept(state)).isEqualTo("Microsoft 365 audit");
    }
}
