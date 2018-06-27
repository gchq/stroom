/*
 * Copyright 2018 Crown Copyright
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

import { testPipelines } from 'prototypes/PipelineEditor/test';

const DOC_REF_TYPES = {
  FOLDER: 'Folder',
  DICTIONARY: 'Dictionary',
  XSLT: 'XSLT',
  TextConverter: 'TextConverter',
  ElasticIndex: 'ElasticIndex',
  AnnotationsIndex: 'AnnotationsIndex',
  Pipeline: 'Pipeline',
  Index: 'Index',
  Dashboard: 'Dashboard',
  Visualisation: 'Visualisation',
};

const testTree = {
  uuid: 'root1234567890',
  name: 'Stroom',
  type: DOC_REF_TYPES.FOLDER,
  children: Object.entries(testPipelines)
    .map(k => ({
      uuid: k[0],
      data: k[1],
    }))
    .map(pipeline => ({
      uuid: pipeline.uuid,
      type: DOC_REF_TYPES.Pipeline,
      name: pipeline.uuid,
    })),
};

export { DOC_REF_TYPES, testTree };
