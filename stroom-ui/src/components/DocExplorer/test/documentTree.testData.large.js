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

const docRefsFromSetupSampleData = [
  'Dashboard',
  'Dictionary',
  'Feed',
  'Folder',
  'Index',
  'Pipeline',
  'Script',
  'StatisticStore',
  'StroomStatsStore',
  'System',
  'TextConverter',
  'Visualisation',
  'XMLSchema',
  'XSLT',
];

const fromSetupSampleData = {
  uuid: '0',
  type: 'System',
  name: 'System',
  children: [
    {
      uuid: 'a4bcf7e6-e4b9-4781-b97a-0fa0af0b700b',
      type: 'Folder',
      name: 'Feeds and Translations',
      children: [
        {
          uuid: '218c158b-d3e6-4667-9a0a-564612a03076',
          type: 'Folder',
          name: 'Internal',
          children: [
            {
              uuid: 'c2b9b9b0-45fe-473a-84b6-936ab1b629b2',
              type: 'XSLT',
              name: 'DECORATION',
              children: null,
            },
            {
              uuid: 'b8def0bd-cfe5-4830-8e21-eed09d55ca2a',
              type: 'XSLT',
              name: 'OUTPUT',
              children: null,
            },
          ],
        },
        {
          uuid: '672a3f74-abac-4209-8e48-7d06af04f19e',
          type: 'Folder',
          name: 'Test',
          children: [
            {
              uuid: 'd865a073-9c21-442f-be53-efa385cb7aa2',
              type: 'Feed',
              name: 'BITMAP-REFERENCE',
              children: null,
            },
            {
              uuid: 'e3cbbc0e-db85-4a5a-9ca9-7685235e3b53',
              type: 'Feed',
              name: 'DATA_SPLITTER-EVENTS',
              children: null,
            },
            {
              uuid: '60f9f51d-e5d6-41f5-86b9-ae866b8c9fa3',
              type: 'Feed',
              name: 'FILENO_TO_LOCATION-REFERENCE',
              children: null,
            },
            {
              uuid: '0ece027a-87a2-42c6-a6dd-e05adce6703b',
              type: 'Feed',
              name: 'IP_RANGE_TO_LOCATION-REFERENCE',
              children: null,
            },
            {
              uuid: 'f925da86-72be-44ec-9afd-7ebc278f9be1',
              type: 'Feed',
              name: 'JSON-EVENTS',
              children: null,
            },
            {
              uuid: '5f7fe309-96a3-427d-b174-30c7646087f2',
              type: 'Feed',
              name: 'RAW_STREAMING-EVENTS',
              children: null,
            },
            {
              uuid: 'a3280267-6507-447d-aa1b-2a28b0960eba',
              type: 'Feed',
              name: 'RAW_STREAMING_FORK-EVENTS',
              children: null,
            },
            {
              uuid: '5f7je309-96a3-427d-b174-30c7646087f2',
              type: 'Feed',
              name: 'RAW_STREAMING_READER-EVENTS',
              children: null,
            },
            {
              uuid: 'c3280267-6507-447d-aa1b-2a28b0960eba',
              type: 'Feed',
              name: 'RAW_STREAMING_READER_FORK-EVENTS',
              children: null,
            },
            {
              uuid: '5d668615-d5ee-4026-8cd3-062e79bedc57',
              type: 'Feed',
              name: 'TEST_DS_PARSER-EVENTS',
              children: null,
            },
            {
              uuid: '6517b569-ab5a-458b-93dc-8f3a2f16aea6',
              type: 'Feed',
              name: 'TEST_JSON_PARSER-EVENTS',
              children: null,
            },
            {
              uuid: 'b3740b81-2936-4994-a22d-2c3209347f22',
              type: 'Feed',
              name: 'TEST_XML_FRAGMENT_PARSER-EVENTS',
              children: null,
            },
            {
              uuid: '5725e1b2-333a-4d9b-95b0-32089307651f',
              type: 'Feed',
              name: 'TEST_XML_PARSER-EVENTS',
              children: null,
            },
            {
              uuid: '215f63ec-1e89-4fae-9cb4-6e8abccb6d30',
              type: 'Feed',
              name: 'XML-EVENTS',
              children: null,
            },
            {
              uuid: '1c09667a-fe2a-4758-80cd-1b9342b1475a',
              type: 'Feed',
              name: 'XML_FILTERING_ANALYTIC',
              children: null,
            },
            {
              uuid: '6dcb59c4-5f1d-4a63-becc-ec88c6315f77',
              type: 'Feed',
              name: 'XML_FRAGMENT-EVENTS',
              children: null,
            },
            {
              uuid: '58f9a3f1-c805-4c44-bbdd-64e933ed75d7',
              type: 'Feed',
              name: 'XML_JSON-EVENTS',
              children: null,
            },
            {
              uuid: '215y63ec-1e89-4fae-9cb4-6e8aiwxb6d30',
              type: 'Feed',
              name: 'XML_READER-EVENTS',
              children: null,
            },
            {
              uuid: 'cb305f67-a460-40f2-a9bb-d855010e2922',
              type: 'Feed',
              name: 'ZIP_TEST-DATA_SPLITTER-EVENTS',
              children: null,
            },
            {
              uuid: '8e43a4dd-6780-4055-b8dc-03c6318e31e0',
              type: 'TextConverter',
              name: 'BITMAP-REFERENCE',
              children: null,
            },
            {
              uuid: '7e4452ed-bf26-4187-b849-e3e721780cba',
              type: 'TextConverter',
              name: 'CSV_WITH_HEADER',
              children: null,
            },
            {
              uuid: '46ceda6c-ba3e-4196-9855-a5dc5fd0ab33',
              type: 'TextConverter',
              name: 'IP_RANGE_TO_LOCATION-REFERENCE',
              children: null,
            },
            {
              uuid: '63aad52c-4e9c-424f-8836-0d2e1c3b23c0',
              type: 'TextConverter',
              name: 'XML_FRAGMENT-EVENTS',
              children: null,
            },
            {
              uuid: '760d0912-858f-4b8b-b11d-c69a8b412142',
              type: 'XSLT',
              name: 'BITMAP-REFERENCE',
              children: null,
            },
            {
              uuid: 'c688dc4a-f2a9-4888-9837-d254ba25a0a6',
              type: 'XSLT',
              name: 'DATA_SPLITTER-EVENTS',
              children: null,
            },
            {
              uuid: '35aa9869-8aaf-4ca5-a38c-10d0e4000b55',
              type: 'XSLT',
              name: 'FILENO_TO_LOCATION-REFERENCE',
              children: null,
            },
            {
              uuid: 'a531dfb5-b0e7-401a-afea-d2ee00ed3cbf',
              type: 'XSLT',
              name: 'IP_RANGE_TO_LOCATION-REFERENCE',
              children: null,
            },
            {
              uuid: '2b1146c3-2541-469b-8d8c-da8bbb898b57',
              type: 'XSLT',
              name: 'JSON-EVENTS',
              children: null,
            },
            {
              uuid: '6f684aed-4bd6-4ad8-af08-dccd75ae14ab',
              type: 'XSLT',
              name: 'XML_FILTERING_ANALYTIC',
              children: null,
            },
            {
              uuid: '7902509c-9fa2-4211-a51e-a1be199ab5ee',
              type: 'XSLT',
              name: 'XML_JSON-EVENTS',
              children: null,
            },
            {
              uuid: 'fda3fa22-c9cd-4536-9c5e-6482e1f9e841',
              type: 'XSLT',
              name: 'ZIP_TEST-DATA_SPLITTER-CONTEXT',
              children: null,
            },
            {
              uuid: '7e890661-3ed3-4903-b0c7-95eb62a8aa9d',
              type: 'XSLT',
              name: 'ZIP_TEST-DATA_SPLITTER-EVENTS',
              children: null,
            },
            {
              uuid: '5ab7ec67-1444-46ea-89c6-d46d84b9d012',
              type: 'Pipeline',
              name: 'BITMAP-REFERENCE',
              children: null,
            },
            {
              uuid: 'bd7be478-632b-40fe-8fda-f578ec57e959',
              type: 'Pipeline',
              name: 'Common Test Event Pipeline',
              children: null,
            },
            {
              uuid: '7630b1ff-e65d-4744-b05f-0ea178a97a41',
              type: 'Pipeline',
              name: 'Common Test Reference Pipeline',
              children: null,
            },
            {
              uuid: 'd6fb12ff-fb94-437e-90c3-b95372572efd',
              type: 'Pipeline',
              name: 'DATA_SPLITTER-EVENTS',
              children: null,
            },
            {
              uuid: '7826035a-b248-4d86-bf15-3fbdd03084a6',
              type: 'Pipeline',
              name: 'FILENO_TO_LOCATION-REFERENCE',
              children: null,
            },
            {
              uuid: '5cf275ab-33a6-4c34-9439-c68fdd41bbbe',
              type: 'Pipeline',
              name: 'IP_RANGE_TO_LOCATION-REFERENCE',
              children: null,
            },
            {
              uuid: '3f48d217-b5e6-4cde-a431-1b69c68b0cab',
              type: 'Pipeline',
              name: 'JSON-EVENTS',
              children: null,
            },
            {
              uuid: 'dc3b6921-6602-4eeb-865f-01cbaea5024b',
              type: 'Pipeline',
              name: 'RAW_STREAMING-EVENTS',
              children: null,
            },
            {
              uuid: 'a092e9bc-3e55-405a-a9ab-5927b78e2410',
              type: 'Pipeline',
              name: 'RAW_STREAMING_FORK-EVENTS',
              children: null,
            },
            {
              uuid: 'dc3b6921-6602-4eeb-835f-01cbaea5024b',
              type: 'Pipeline',
              name: 'RAW_STREAMING_READER-EVENTS',
              children: null,
            },
            {
              uuid: 'g092e9bc-3e55-405a-a9ab-5927b78e2410',
              type: 'Pipeline',
              name: 'RAW_STREAMING_READER_FORK-EVENTS',
              children: null,
            },
            {
              uuid: '34c1b25a-8bda-4761-aef1-ed98cbae0661',
              type: 'Pipeline',
              name: 'TEST_DS_PARSER-EVENTS',
              children: null,
            },
            {
              uuid: '40fd858d-9e0b-4547-bad9-032500ea390b',
              type: 'Pipeline',
              name: 'TEST_JSON_PARSER-EVENTS',
              children: null,
            },
            {
              uuid: 'db3e2c61-b83e-4dc1-a62d-993ed5476333',
              type: 'Pipeline',
              name: 'TEST_XML_FRAGMENT_PARSER-EVENTS',
              children: null,
            },
            {
              uuid: '2ce16bef-3e86-4492-bcb5-0281e4e1914a',
              type: 'Pipeline',
              name: 'TEST_XML_PARSER-EVENTS',
              children: null,
            },
            {
              uuid: 'e40dfda1-0210-49cf-9695-c3f6fa8c67d7',
              type: 'Pipeline',
              name: 'XML-EVENTS',
              children: null,
            },
            {
              uuid: 'b7649b31-836b-4fba-a0d6-8931e8e061d7',
              type: 'Pipeline',
              name: 'XML_FILTERING_ANALYTIC',
              children: null,
            },
            {
              uuid: '81e78e80-b31e-4669-8e28-44c16eb577cf',
              type: 'Pipeline',
              name: 'XML_FRAGMENT-EVENTS',
              children: null,
            },
            {
              uuid: '05dfe822-2a9d-47e7-9251-79260d4c6244',
              type: 'Pipeline',
              name: 'XML_JSON-EVENTS',
              children: null,
            },
            {
              uuid: 'e40ifda1-0210-49cn-9695-c3f6fa8c67d7',
              type: 'Pipeline',
              name: 'XML_READER-EVENTS',
              children: null,
            },
            {
              uuid: '5099b649-9992-4335-abba-ebe30a86f374',
              type: 'Pipeline',
              name: 'ZIP_TEST-DATA_SPLITTER-CONTEXT',
              children: null,
            },
            {
              uuid: 'ee00de01-cd54-4f89-8587-8bf2d0228b9c',
              type: 'Pipeline',
              name: 'ZIP_TEST-DATA_SPLITTER-EVENTS',
              children: null,
            },
          ],
        },
      ],
    },
    {
      uuid: 'f67ede7f-2b32-401b-8642-8adbdd349fd5',
      type: 'Folder',
      name: 'HDFS File Appender',
      children: [
        {
          uuid: 'e2ddbec6-25c3-4863-94d9-9801e5c5cecd',
          type: 'Feed',
          name: 'HDFS_INPUT',
          children: null,
        },
        {
          uuid: '72171ccc-b8b4-4a1d-91fb-974bd2bac1c2',
          type: 'Feed',
          name: 'HDFS_OUTPUT',
          children: null,
        },
        {
          uuid: '85fa215e-7a7f-4ad3-8b88-7155ff0ade37',
          type: 'Pipeline',
          name: 'HDFS',
          children: null,
        },
      ],
    },
    {
      uuid: 'a6e40e0e-b7d3-4c6f-aa45-a6dca412d141',
      type: 'Folder',
      name: 'Indexes',
      children: [
        {
          uuid: 'd914010c-789a-4db9-a7e6-ab1b4aa44f21',
          type: 'Folder',
          name: 'Example index',
          children: [
            {
              uuid: '87f17469-6a90-4003-9ca3-cd79e653c215',
              type: 'XSLT',
              name: 'Example index',
              children: null,
            },
            {
              uuid: 'c9c879ee-d88a-40ff-8654-d018f1c2d957',
              type: 'Pipeline',
              name: 'Example index',
              children: null,
            },
            {
              uuid: '57a35b9a-083c-4a93-a813-fc3ddfe1ff44',
              type: 'Index',
              name: 'Example index',
              children: null,
            },
          ],
        },
      ],
    },
    {
      uuid: '488ba95c-2807-4fe7-9241-0cdce66a4068',
      type: 'Folder',
      name: 'Internal Statistics',
      children: [
        {
          uuid: '962e3867-0642-4627-87b7-67b303484e77',
          type: 'Folder',
          name: 'SQL',
          children: [
            {
              uuid: '603f10b3-26ae-11e7-b9dc-0242ac110002',
              type: 'StatisticStore',
              name: 'Benchmark-Cluster Test',
              children: null,
            },
            {
              uuid: 'af08c4a7-ee7c-44e4-8f5e-e9c6be280434',
              type: 'StatisticStore',
              name: 'CPU',
              children: null,
            },
            {
              uuid: 'a9936548-2572-448b-9d5b-8543052c4d92',
              type: 'StatisticStore',
              name: 'EPS',
              children: null,
            },
            {
              uuid: '77c09ccb-e251-4ca5-bca0-56a842654397',
              type: 'StatisticStore',
              name: 'Memory',
              children: null,
            },
            {
              uuid: '603f1058-26ae-11e7-b9dc-0242ac110002',
              type: 'StatisticStore',
              name: 'Meta Data-Bytes Received',
              children: null,
            },
            {
              uuid: '603f0fff-26ae-11e7-b9dc-0242ac110002',
              type: 'StatisticStore',
              name: 'Meta Data-Stream Size',
              children: null,
            },
            {
              uuid: '603f0fa7-26ae-11e7-b9dc-0242ac110002',
              type: 'StatisticStore',
              name: 'Meta Data-Streams Received',
              children: null,
            },
            {
              uuid: '603f0853-26ae-11e7-b9dc-0242ac110002',
              type: 'StatisticStore',
              name: 'PipelineStreamProcessor',
              children: null,
            },
            {
              uuid: '603f0695-26ae-11e7-b9dc-0242ac110002',
              type: 'StatisticStore',
              name: 'Stream Task Queue Size',
              children: null,
            },
            {
              uuid: 'ac4d8d10-6f75-4946-9708-18b8cb42a5a3',
              type: 'StatisticStore',
              name: 'Volumes',
              children: null,
            },
          ],
        },
      ],
    },
    {
      uuid: '69e3edd8-e3d1-47eb-a956-d7863a99dc3b',
      type: 'Folder',
      name: 'Search',
      children: [
        {
          uuid: 'b325d4a3-b4b2-483b-9c21-99a20bcdf14a',
          type: 'Folder',
          name: 'Display',
          children: [
            {
              uuid: 'd3d451df-fd7e-4205-913a-65146d1d63c9',
              type: 'XSLT',
              name: 'Example display',
              children: null,
            },
            {
              uuid: 'e5ecdf99-d433-45ac-b14a-1f77f16ae4f7',
              type: 'Pipeline',
              name: 'Example display',
              children: null,
            },
          ],
        },
        {
          uuid: '456c56f9-55e6-4188-a067-2c954869d0c7',
          type: 'Folder',
          name: 'Extraction',
          children: [
            {
              uuid: 'e5ecdf93-d433-45ac-b14a-1f77f16ae4f7',
              type: 'Pipeline',
              name: 'Example extraction',
              children: null,
            },
          ],
        },
        {
          uuid: '346fa8d0-50fb-4283-845a-3f47f1938809',
          type: 'Dashboard',
          name: 'Internal Statistics',
          children: null,
        },
        {
          uuid: 'e177cf16-da6c-4c7d-a19c-09a201f5a2da',
          type: 'Dashboard',
          name: 'Test Dashboard',
          children: null,
        },
        {
          uuid: '30965593-d679-4185-9793-6409bc8c4dca',
          type: 'Dashboard',
          name: 'Test Dashboard With Dictionary',
          children: null,
        },
        {
          uuid: 'e793783c-f7b1-4fda-9350-a9fd28126908',
          type: 'Dashboard',
          name: 'Test Dashboard With Missing Dictionary',
          children: null,
        },
        {
          uuid: 'ab0b8da5-914d-48b7-b459-41504f694ab1',
          type: 'Dashboard',
          name: 'Test Dashboard With Unresolved Data Source',
          children: null,
        },
        {
          uuid: 'fa2e0d33-cf12-4336-93e4-44f132487788',
          type: 'Dashboard',
          name: 'Test Dashboard With Unresolved Visualisation',
          children: null,
        },
        {
          uuid: '628a0742-7177-432f-ae9f-a221f8ec9185',
          type: 'Visualisation',
          name: 'Test Settings',
          children: null,
        },
        {
          uuid: '352082ae-5b7c-4105-8d34-39ec7c713523',
          type: 'Dictionary',
          name: 'Test Dictionary',
          children: null,
        },
      ],
    },
    {
      uuid: '9b43128e-c93b-4922-a446-f03484db47b1',
      type: 'Folder',
      name: 'Standard Pipelines',
      children: [
        {
          uuid: '67510b80-c65f-402d-acc5-c74fd7876d70',
          type: 'Pipeline',
          name: 'Batch Search',
          children: null,
        },
        {
          uuid: 'fc281170-360d-4773-ad79-5378c5dcf52e',
          type: 'Pipeline',
          name: 'Context Data',
          children: null,
        },
        {
          uuid: '7740cfc4-3443-4001-bf0b-6adc77d5a3cf',
          type: 'Pipeline',
          name: 'Event Data',
          children: null,
        },
        {
          uuid: 'fcef1b20-083e-436c-ab95-47a6ce453435',
          type: 'Pipeline',
          name: 'Indexing',
          children: null,
        },
        {
          uuid: 'b15e0cc8-3f82-446d-b106-04f43c38e19c',
          type: 'Pipeline',
          name: 'Reference Data',
          children: null,
        },
        {
          uuid: 'da1c7351-086f-493b-866a-b42dbe990700',
          type: 'Pipeline',
          name: 'Reference Loader',
          children: null,
        },
        {
          uuid: '3d9d60e9-61c2-4c88-a57b-7bc584dd970e',
          type: 'Pipeline',
          name: 'Search Extraction',
          children: null,
        },
        {
          uuid: 'kdyu6a2e-8881-4f5d-9316-e994717233f9',
          type: 'Pipeline',
          name: 'Statistic',
          children: null,
        },
      ],
    },
    {
      uuid: '9f8a3b13-979a-4f77-8d12-34f38ebf9bf8',
      type: 'Folder',
      name: 'Statistics',
      children: [
        {
          uuid: 'kdyu6a2e-1862-4f5d-9316-e994717233f9',
          type: 'XSLT',
          name: 'Feed Statistics',
          children: null,
        },
      ],
    },
    {
      uuid: 'b8a1a4e5-3c5a-48fc-a12f-1bf690888a4f',
      type: 'Folder',
      name: 'Statistics Test Data',
      children: [
        {
          uuid: '8c94b841-cb25-4632-9b09-5a8e2df73442',
          type: 'Folder',
          name: 'Count Data',
          children: [
            {
              uuid: '6ed6b1be-6b62-4a85-ae09-7f8eb4c6c1e5',
              type: 'Folder',
              name: 'SQL',
              children: [
                {
                  uuid: '28175ad7-d86d-407d-bba8-4417ff19573a',
                  type: 'Feed',
                  name: 'COUNT_OUTPUT_FEED_SQL',
                  children: null,
                },
                {
                  uuid: 'ca55998a-8ec2-487b-a228-85666b839ae6',
                  type: 'Pipeline',
                  name: 'CountPipelineSQL',
                  children: null,
                },
                {
                  uuid: '9a1a9732-df96-415f-8f5a-67973c60d73d',
                  type: 'Dashboard',
                  name: 'CountStatisticsSQL',
                  children: null,
                },
                {
                  uuid: 'e2528c85-722e-46e0-91b6-742149691047',
                  type: 'StatisticStore',
                  name: 'CountStatisticSQL',
                  children: null,
                },
              ],
            },
            {
              uuid: '1051f62e-b3fb-4c1c-a3f9-46c12bbf932c',
              type: 'Folder',
              name: 'StroomStats',
              children: [
                {
                  uuid: '9ad1ef02-a474-4832-879e-563e5c8475ef',
                  type: 'XSLT',
                  name: 'CountXSLTStroomStats',
                  children: null,
                },
                {
                  uuid: 'a26508d9-7632-44bd-ac98-6b3fb04c824c',
                  type: 'Pipeline',
                  name: 'CountStatisticsStroomStats',
                  children: null,
                },
                {
                  uuid: '71017bc8-4d01-460d-9274-dab077a98f94',
                  type: 'Dashboard',
                  name: 'CountStatisticsStroomStats',
                  children: null,
                },
                {
                  uuid: 'c5dd1e72-5484-41c9-8e7a-f9429f648f3e',
                  type: 'StroomStatsStore',
                  name: 'CountStatisticsStroomStats',
                  children: null,
                },
              ],
            },
            {
              uuid: '74330bde-59b9-41fd-86e0-dd521351c571',
              type: 'Feed',
              name: 'COUNT_FEED',
              children: null,
            },
            {
              uuid: '860ccece-a3fb-4142-ace7-451718726307',
              type: 'Feed',
              name: 'COUNT_FEED_SMALL',
              children: null,
            },
            {
              uuid: 'eefdaca9-a84b-43d0-a8a9-8f79b75d29f1',
              type: 'XSLT',
              name: 'CountXSLT',
              children: null,
            },
          ],
        },
        {
          uuid: 'a49bf49b-4d2d-424b-91f2-ae9f37767e31',
          type: 'Folder',
          name: 'Count Data to external API',
          children: [
            {
              uuid: '6193413d-3536-436c-900e-2a67b892c8b1',
              type: 'Feed',
              name: 'COUNT_V3',
              children: null,
            },
            {
              uuid: 'def78501-c5a4-4e0a-aafe-3ee97f45bd9a',
              type: 'XSLT',
              name: 'Count to statistics schema v3.0',
              children: null,
            },
            {
              uuid: '0eb23206-28f9-418c-9a7f-441e0e5a1a60',
              type: 'Pipeline',
              name: 'COUNT_FEED to external API',
              children: null,
            },
          ],
        },
        {
          uuid: 'c380de52-1b9b-452e-8a60-674a7f1fcd91',
          type: 'Folder',
          name: 'Value Data',
          children: [
            {
              uuid: '6c36351b-dac1-4342-8080-640fde339118',
              type: 'Folder',
              name: 'StroomStats',
              children: [
                {
                  uuid: '5ef82330-f244-476c-a198-a8d0b2aa142d',
                  type: 'XSLT',
                  name: 'ValueXSLTStroomStats',
                  children: null,
                },
                {
                  uuid: '9c8d9b1b-c1e4-4615-90ae-b85db8227543',
                  type: 'Pipeline',
                  name: 'ValueStatisticsStroomStats',
                  children: null,
                },
                {
                  uuid: '3119b59d-a0a4-448b-b5a3-6b7391cc9ed6',
                  type: 'Dashboard',
                  name: 'ValueStatisticsStroomStats',
                  children: null,
                },
                {
                  uuid: '063f820b-8795-478f-a721-de724f9b4dc3',
                  type: 'StroomStatsStore',
                  name: 'ValueStatisticsStroomStats',
                  children: null,
                },
              ],
            },
            {
              uuid: '2a4986e5-45db-45ec-bbfa-535da0c78b8c',
              type: 'Feed',
              name: 'VALUE_FEED_SMALL',
              children: null,
            },
          ],
        },
        {
          uuid: 'ec3e28ce-e0d8-4e8a-ba40-807f86d61a84',
          type: 'Folder',
          name: 'ValueData',
          children: [
            {
              uuid: '636360f0-8603-4ef8-9101-238900aa901c',
              type: 'Folder',
              name: 'SQL',
              children: [
                {
                  uuid: '2291a856-17cc-4715-a373-7ffee867ec80',
                  type: 'Feed',
                  name: 'VALUE_OUTPUT_FEED_SQL',
                  children: null,
                },
                {
                  uuid: 'a5b76a2e-7689-4f5d-9316-e994710423f9',
                  type: 'Pipeline',
                  name: 'ValuePipelineSQL',
                  children: null,
                },
                {
                  uuid: 'a5b76a2e-7649-4f5d-9316-e994717233f9',
                  type: 'Dashboard',
                  name: 'ValueDashboardSQL',
                  children: null,
                },
                {
                  uuid: 'a5b76a2e-7689-4f5d-9316-e948140423f9',
                  type: 'StatisticStore',
                  name: 'ValueStatisticSQL',
                  children: null,
                },
              ],
            },
            {
              uuid: '28175ad7-d86d-407d-bba8-4417ff19592a',
              type: 'Feed',
              name: 'VALUE_FEED',
              children: null,
            },
            {
              uuid: 'd7812ed8-40c6-4315-90b9-af3de72fd5b7',
              type: 'XSLT',
              name: 'ValueXSLT',
              children: null,
            },
          ],
        },
      ],
    },
    {
      uuid: 'cd8c4eff-430b-4d4d-93ce-a7145e3a54b0',
      type: 'Folder',
      name: 'Visualisations',
      children: [
        {
          uuid: 'eeb752af-75ac-4bd9-bbbe-b62b61567b43',
          type: 'Folder',
          name: 'Version3',
          children: [
            {
              uuid: '10fc90ae-9f12-475d-83f6-c7ba991caac0',
              type: 'Folder',
              name: 'Dependencies',
              children: [
                {
                  uuid: 'bf59d603-46e2-40aa-8547-0d07bd3d2c6a',
                  type: 'Folder',
                  name: 'Chroma',
                  children: [
                    {
                      uuid: 'd3bd7a70-397e-4c85-bfca-c09cc1d3bca8',
                      type: 'Script',
                      name: 'Chroma',
                      children: null,
                    },
                  ],
                },
                {
                  uuid: 'ff96b7b5-8f48-4ef6-9c61-4c980ce6957f',
                  type: 'Folder',
                  name: 'D3',
                  children: [
                    {
                      uuid: '61cc6424-9899-4f38-ad77-f4204f9de166',
                      type: 'Script',
                      name: 'D3',
                      children: null,
                    },
                  ],
                },
                {
                  uuid: 'ddd0deb4-a2e8-4b9d-947b-41c66d21c47e',
                  type: 'Folder',
                  name: 'D3-Grid',
                  children: [
                    {
                      uuid: 'cac6a6cd-1874-4932-a51c-e16ef9303f0c',
                      type: 'Script',
                      name: 'D3-Grid',
                      children: null,
                    },
                  ],
                },
                {
                  uuid: '0b83c33c-03be-4af7-ad70-ae53627a872a',
                  type: 'Folder',
                  name: 'D3-Tip',
                  children: [
                    {
                      uuid: '4f336f7d-5357-4fb3-a42b-88c967588e0a',
                      type: 'Script',
                      name: 'D3-Tip',
                      children: null,
                    },
                  ],
                },
                {
                  uuid: 'ee9a21ac-1195-4b25-8403-9978e39e3b06',
                  type: 'Folder',
                  name: 'JSHashes',
                  children: [
                    {
                      uuid: '72c19c4d-25d7-4bfd-bd1e-fb0ba6dc86bb',
                      type: 'Script',
                      name: 'JSHashes',
                      children: null,
                    },
                  ],
                },
              ],
            },
            {
              uuid: '547b440d-4bb1-4d3b-86b7-ff2e41b49311',
              type: 'Visualisation',
              name: 'BarChart',
              children: null,
            },
            {
              uuid: 'b6ab11cd-ab60-42a5-885d-5c4d5911f964',
              type: 'Visualisation',
              name: 'Bubble',
              children: null,
            },
            {
              uuid: '195fbed2-5a75-456f-bd98-f2dd184b94f9',
              type: 'Visualisation',
              name: 'DayWeekHeatMap',
              children: null,
            },
            {
              uuid: 'ce55cb67-22ef-4146-b866-f8ba955b9ba6',
              type: 'Visualisation',
              name: 'Doughnut',
              children: null,
            },
            {
              uuid: 'aad53ec0-c33d-4430-b2b2-5a83eccb13e7',
              type: 'Visualisation',
              name: 'HourDayMultiHeatMap',
              children: null,
            },
            {
              uuid: '4a6f5935-4e75-4579-8f74-112fbe8aee88',
              type: 'Visualisation',
              name: 'HourDayPointMap',
              children: null,
            },
            {
              uuid: '63a3a18a-3651-4833-ba82-6a2d6794f5f4',
              type: 'Visualisation',
              name: 'HourDaySessionMap',
              children: null,
            },
            {
              uuid: '567f4ba6-c420-4068-86a6-cc854165926b',
              type: 'Visualisation',
              name: 'LineChart',
              children: null,
            },
            {
              uuid: '1666949a-2b32-4a95-a25f-64916b4ace56',
              type: 'Visualisation',
              name: 'RAGStatus',
              children: null,
            },
            {
              uuid: '8b5e5f4f-c1a8-474f-9ff2-88bdb0f9a0a5',
              type: 'Visualisation',
              name: 'Scatter',
              children: null,
            },
            {
              uuid: 'b7d942e9-19b2-406d-a5ae-48324c635d4f',
              type: 'Visualisation',
              name: 'SeriesSessionMap',
              children: null,
            },
            {
              uuid: 'e4c3baee-2a5c-4d6e-93af-aadfe374835c',
              type: 'Visualisation',
              name: 'StackedArea',
              children: null,
            },
            {
              uuid: '7e484d5d-29af-4776-b1c2-4cdad2d9ea1b',
              type: 'Visualisation',
              name: 'TextValue',
              children: null,
            },
            {
              uuid: '1eae814a-c11a-4cc5-865d-b906908a5b28',
              type: 'Visualisation',
              name: 'TrafficLights',
              children: null,
            },
            {
              uuid: 'dac25a4c-7a4e-4e7e-b861-74e16edd1b60',
              type: 'Script',
              name: 'BarChart',
              children: null,
            },
            {
              uuid: '866a3f1d-26e3-44d1-819e-968bc767a4af',
              type: 'Script',
              name: 'Bubble',
              children: null,
            },
            {
              uuid: '84c6fa29-3f4b-4ccc-971a-741259566d2b',
              type: 'Script',
              name: 'CSS',
              children: null,
            },
            {
              uuid: '49a5f46c-f627-49ed-afb9-0066b99e235a',
              type: 'Script',
              name: 'Common',
              children: null,
            },
            {
              uuid: 'abb0e349-b61a-4946-9231-86d5a98596c7',
              type: 'Script',
              name: 'DayWeekHeatMap',
              children: null,
            },
            {
              uuid: '213eb661-579e-4c08-b7a5-1355d7b782f8',
              type: 'Script',
              name: 'Doughnut',
              children: null,
            },
            {
              uuid: '5d4252d6-6f13-4f24-9481-9e98e038747c',
              type: 'Script',
              name: 'GenericGrid',
              children: null,
            },
            {
              uuid: 'f51ae8dc-ceda-43e0-813a-b2735c9fad25',
              type: 'Script',
              name: 'HourDayMultiHeatMap',
              children: null,
            },
            {
              uuid: 'e733aa62-ede1-4530-bd05-5702de05f9fd',
              type: 'Script',
              name: 'HourDayPointMap',
              children: null,
            },
            {
              uuid: '69305470-0ff8-4797-9f6e-baf52123547f',
              type: 'Script',
              name: 'HourDaySessionMap',
              children: null,
            },
            {
              uuid: '788b5981-7199-4f7e-a9db-33594f910134',
              type: 'Script',
              name: 'LineChart',
              children: null,
            },
            {
              uuid: '9bc156e3-2655-4fb3-a809-16fbaeda3022',
              type: 'Script',
              name: 'RAGStatus',
              children: null,
            },
            {
              uuid: '80aac919-980e-43e8-83a1-83eed536ae51',
              type: 'Script',
              name: 'Scatter',
              children: null,
            },
            {
              uuid: '3296b381-f383-496f-8f54-9d42a0fd5bf1',
              type: 'Script',
              name: 'SeriesSessionMap',
              children: null,
            },
            {
              uuid: '187f4abd-db8b-43d6-ade5-eea19c0b1bf4',
              type: 'Script',
              name: 'StackedArea',
              children: null,
            },
            {
              uuid: 'a2765cd2-8081-4230-8c16-05127a3a102d',
              type: 'Script',
              name: 'TextValue',
              children: null,
            },
            {
              uuid: 'bee10b7d-023d-487a-af83-414ee1b7174f',
              type: 'Script',
              name: 'TrafficLights',
              children: null,
            },
          ],
        },
      ],
    },
    {
      uuid: '43e5e3f2-e6e3-49e7-9475-9fa523048492',
      type: 'Folder',
      name: 'XML Schemas',
      children: [
        {
          uuid: '6d59e204-51fe-4a06-9f7a-ec3b66b76e47',
          type: 'Folder',
          name: 'analytic-output',
          children: [
            {
              uuid: 'd1cf8a3b-fd1d-403d-bbd0-ca5c9150cf98',
              type: 'XMLSchema',
              name: 'analytic-output v1.0',
              children: null,
            },
          ],
        },
        {
          uuid: 'bc653373-366c-4733-b9e7-7f6213eba6a5',
          type: 'Folder',
          name: 'data-splitter',
          children: [
            {
              uuid: '9e1e2567-ba83-4720-95c0-f882b951bd3e',
              type: 'XMLSchema',
              name: 'data-splitter v3.0',
              children: null,
            },
          ],
        },
        {
          uuid: 'dc9f7129-57ba-4f48-a0e6-844e5e3b4827',
          type: 'Folder',
          name: 'event-logging',
          children: [
            {
              uuid: '4fe14042-770e-4297-8711-d92e607bc4d5',
              type: 'XMLSchema',
              name: 'event-logging v3.0.0',
              children: null,
            },
          ],
        },
        {
          uuid: 'a9592620-010b-4123-a139-9abbe2743854',
          type: 'Folder',
          name: 'json',
          children: [
            {
              uuid: '5dd0956b-2e8b-4d6e-9a3e-8e3756dae746',
              type: 'XMLSchema',
              name: 'json',
              children: null,
            },
          ],
        },
        {
          uuid: '35329210-e776-4140-b7a0-db003fbf0162',
          type: 'Folder',
          name: 'records',
          children: [
            {
              uuid: '47f062d9-8191-4535-b35b-74c6f020320f',
              type: 'XMLSchema',
              name: 'records v2.0',
              children: null,
            },
          ],
        },
        {
          uuid: 'c2500c3b-1cde-41a3-a774-39e9c9853885',
          type: 'Folder',
          name: 'reference-data',
          children: [
            {
              uuid: '848d11d1-381e-4cc8-9f23-acfd68189f62',
              type: 'XMLSchema',
              name: 'reference-data v2.0.1',
              children: null,
            },
          ],
        },
        {
          uuid: '940c5042-aa19-434b-9836-99578dcfbbe1',
          type: 'Folder',
          name: 'statistics',
          children: [
            {
              uuid: '82e6393a-b19d-4970-9dae-e095e7a6d4bc',
              type: 'XMLSchema',
              name: 'statistics v2.0',
              children: null,
            },
          ],
        },
      ],
    },
  ],
};

export { fromSetupSampleData, docRefsFromSetupSampleData };
