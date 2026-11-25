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

-- Stop NOTE level warnings about objects (not)? existing
SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0;

-- v7.14-beta.19 recorded the streams whose documents were not yet committed to an index shard. Nothing reads
-- those records in this version. They are removed so that a later version which does read them can't act on
-- ones that are long out of date, as that would remove documents that have since been indexed again.
--
-- The lease columns are left alone. A later version may run this script while another node holds leases, and
-- clearing those would let two nodes write to a shard. Leases left over from v7.14-beta.19 have expired, and a
-- version that uses leases gives up expired ones itself.
DELETE FROM index_shard_pending_stream;

UPDATE index_shard
SET recover_pending_streams = 0
WHERE recover_pending_streams <> 0;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
