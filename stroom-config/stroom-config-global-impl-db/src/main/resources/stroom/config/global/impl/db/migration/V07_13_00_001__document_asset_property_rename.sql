-- ------------------------------------------------------------------------
-- Copyright 2026 Crown Copyright
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
-- ------------------------------------------------------------------------

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

--
-- Re-point database-held property overrides after the visualisation-asset subsystem was
-- generalised into stroom.document.asset, renaming appConfig.visualisationAsset[Db] to
-- appConfig.documentAsset[Db].
--
-- A @JsonAlias on AppConfig covers the same rename for config.yml, but ConfigMapper derives
-- property paths from @JsonProperty alone and never consults the alias. Without this, an
-- override held here under the old path resolves to no known property and
-- GlobalConfigBootstrapService drops it at boot, logging only at DEBUG - so a customised
-- mimetype mapping or asset cache directory would silently revert to its default.
--
-- Only the non-DB block needs this in practice: documentAssetDb is @BootStrapConfig with
-- @ReadOnly connection fields, so it can only ever be set in YAML. The LIKE below covers both
-- for completeness, since 'appConfig.visualisationAsset' prefixes both paths.
--
-- config.name carries a UNIQUE key, so skip any row whose new path is already present rather
-- than failing the migration. That can only happen if someone set the property again under the
-- new name before upgrading; their newer value is the one to keep.
--

DELETE old_prop
FROM config old_prop
JOIN config new_prop
  ON new_prop.name = REPLACE(
       old_prop.name, 'appConfig.visualisationAsset', 'appConfig.documentAsset')
WHERE old_prop.name LIKE 'appConfig.visualisationAsset%';

UPDATE config
SET name = REPLACE(name, 'appConfig.visualisationAsset', 'appConfig.documentAsset')
WHERE name LIKE 'appConfig.visualisationAsset%';

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
