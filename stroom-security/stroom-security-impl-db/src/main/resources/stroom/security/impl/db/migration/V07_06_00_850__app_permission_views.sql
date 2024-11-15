-- ------------------------------------------------------------------------
-- Copyright 2024 Crown Copyright
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

-- Create a view to show parent perms for each user by group.
create or replace view v_permission_app_parent_perms as
select
	su.uuid,
	su.name,
	su.display_name,
	su.full_name,
	su.is_group,
	group_concat(pa.permission_id) as perms,
	group_concat(pa_parent.permission_id) as parent_perms,
	sug.group_uuid
from stroom_user su
left outer join stroom_user_group sug on (sug.user_uuid = su.uuid)
left outer join permission_app pa on (pa.user_uuid = su.uuid)
left outer join permission_app pa_parent on (pa_parent.user_uuid = sug.group_uuid)
group by su.uuid, sug.group_uuid;

-- Create a view to recursively aggregate parent permissions for users and groups so we can see all inherited permissions.
create or replace view v_permission_app_inherited_perms as
with recursive cte as (
	select
		v.uuid,
		v.name,
		v.display_name,
		v.full_name,
		v.is_group,
		v.perms,
		v.parent_perms as inherited_perms,
		v.group_uuid,
		name as path
	from v_permission_app_parent_perms as v
	union all
	select
		v.uuid,
		v.name,
		v.display_name,
		v.full_name,
		v.is_group,
		v.perms,
		concat_ws(',', cte.inherited_perms, v.parent_perms),
		v.group_uuid,
		concat_ws('/', cte.path, v.name)
	from cte
    join v_permission_app_parent_perms as v
	on cte.uuid = v.group_uuid
)
select
	uuid,
	name,
	display_name,
	full_name,
	is_group,
	group_concat(distinct perms) as perms,
    group_concat(distinct inherited_perms) as inherited_perms
from cte
group by uuid, name, display_name, full_name, is_group;

SET SQL_NOTES=@OLD_SQL_NOTES;

-- vim: set shiftwidth=4 tabstop=4 expandtab:
