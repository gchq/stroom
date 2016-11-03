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

insert into strm_proc (ver, crt_dt, crt_user, upd_dt, upd_user, task_tp, proc_stat, fk_pipe_id) 
values (1, curdate(), 'upgrade', curdate(), 'upgrade', 'streamProcessorTaskTester', 1, null);

-- EG 1000 records
insert into strm_proc_filt (ver, crt_dt, crt_user, upd_dt, upd_user, dat, prior, task_tp, strm_min, strm_max, strm_min_ms, fk_strm_proc_id, proc_stat) values
(1, curdate(), 'upgrade', curdate(), 'upgrade', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><FindStreamCriteria></FindStreamCriteria>', 
1, 1, 0, (select min(id)+1000 from strm), null, (select id from strm_proc where task_tp = 'streamProcessorTaskTester'), 1);

insert into strm_proc_filt (ver, crt_dt, crt_user, upd_dt, upd_user, dat, prior, task_tp, strm_min, strm_max, strm_min_ms, fk_strm_proc_id, proc_stat) values
(1, curdate(), 'upgrade', curdate(), 'upgrade', '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><FindStreamCriteria></FindStreamCriteria>', 
1, 1, 0, (select max(id) from strm), null, (select id from strm_proc where task_tp = 'streamProcessorTaskTester'), 1);

