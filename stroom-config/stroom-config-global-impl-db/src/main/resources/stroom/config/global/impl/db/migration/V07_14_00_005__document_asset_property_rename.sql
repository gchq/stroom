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
-- generalised into stroom.document.asset, renaming stroom.visualisationAsset[Db] to
-- stroom.documentAsset[Db].
--
-- config.name holds the full property path rooted at AppConfig.NAME ('stroom'), e.g.
-- 'stroom.visualisationAsset.mimetypes' - not at the YAML root key 'appConfig'. An override
-- held here under the old path resolves to no known property, and GlobalConfigBootstrapService
-- drops it at boot, logging only at DEBUG - so a customised mimetype mapping or asset cache
-- directory would silently revert to its default.
--
-- The old names are rejected outright in config.yml (see
-- StroomConfigurationSourceProvider.rejectRenamedKeys), but that check never sees this table.
--
-- Only the non-DB block needs this in practice: documentAssetDb is @BootStrapConfig with
-- @ReadOnly connection fields, so it can only ever be set in YAML. The LIKE below covers both
-- for completeness, since 'stroom.visualisationAsset' prefixes both paths. Only the leading
-- prefix is rewritten, never a later occurrence in the path.
--
-- config.name carries a UNIQUE key, so skip any row whose new path is already present rather
-- than failing the migration. That can only happen if someone set the property again under the
-- new name before upgrading; their newer value is the one to keep.
--

SET @OLD_PREFIX = 'stroom.visualisationAsset';
SET @NEW_PREFIX = 'stroom.documentAsset';

DELETE old_prop
FROM config old_prop
JOIN config new_prop
  ON new_prop.name = CONCAT(@NEW_PREFIX, SUBSTRING(old_prop.name, CHAR_LENGTH(@OLD_PREFIX) + 1))
WHERE old_prop.name LIKE CONCAT(@OLD_PREFIX, '%');

UPDATE config
SET name = CONCAT(@NEW_PREFIX, SUBSTRING(name, CHAR_LENGTH(@OLD_PREFIX) + 1))
WHERE name LIKE CONCAT(@OLD_PREFIX, '%');

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
