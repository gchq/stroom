import * as loremIpsum from "lorem-ipsum";
import { StreamTaskType, QueryDataType } from "../../types";

const getRandomInt = (min: number, max: number) =>
  Math.floor(Math.random() * (max - min)) + min;

const LOREM_CONFIG = { count: 3, units: "words" };

const createTestFilter = (): QueryDataType => ({
  dataSource: {
    type: "StreamStore",
    uuid: "0",
    name: "StreamStore"
  },
  expression: {
    type: "operator",
    op: "AND",
    children: [
      {
        type: "term",
        field: "feedName",
        condition: "EQUALS",
        value: loremIpsum(LOREM_CONFIG as any),
        dictionary: null,
        enabled: true
      },
      {
        type: "operator",
        op: "OR",
        children: [
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "pipelineUuid",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "pipelineUuid",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "pipelineUuid",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          },
          {
            type: "term",
            field: "streamTypeName",
            condition: "EQUALS",
            value: loremIpsum(LOREM_CONFIG as any),
            dictionary: null,
            enabled: true
          }
        ],
        enabled: true
      }
    ],
    enabled: true
  }
  //limits: true, this should be LimitsType?
});

const minimalTracker_undefinedLastPollAge = {
  filterId: 1,
  enabled: true,
  lastPollAge: undefined,
  pipelineId: 1,
  pipelineName: loremIpsum(LOREM_CONFIG as any),
  priority: getRandomInt(1, 99),
  filterName: loremIpsum(LOREM_CONFIG as any),
  filter: createTestFilter(),
  createUser: "tester",
  createdOn: 87134234234
};

const minimalTracker_nullLastPollAge = {
  filterId: 2,
  enabled: true,
  lastPollAge: null,
  pipelineId: 1,
  pipelineName: loremIpsum(LOREM_CONFIG as any),
  priority: getRandomInt(1, 99),
  filterName: loremIpsum(LOREM_CONFIG as any),
  filter: createTestFilter(),
  createUser: "tester",
  createdOn: 87134234234
};

const minimalTracker_emptyLastPollAge = {
  filterId: 3,
  enabled: true,
  lastPollAge: "",
  pipelineId: 1,
  pipelineName: loremIpsum(LOREM_CONFIG as any),
  priority: getRandomInt(1, 99),
  filterName: loremIpsum(LOREM_CONFIG as any),
  filter: createTestFilter(),
  createUser: "tester",
  createdOn: 87134234234
};

const maximalTracker = {
  filterId: 4,
  enabled: true,
  pipelineId: 1,
  pipelineName: loremIpsum(LOREM_CONFIG as any),
  trackerPercent: getRandomInt(0, 100),
  priority: getRandomInt(1, 99),
  filterName: loremIpsum(LOREM_CONFIG as any),
  filter: createTestFilter(),
  lastPollAge: "1.5d",
  taskCount: 4,
  trackerMs: 87834234234,
  status: "Active",
  streamCount: 5,
  eventCount: 6,
  createUser: "tester",
  createdOn: 87134234234,
  updateUser: "tester2",
  updatedOn: 87934234234
};

const maximalTracker_withLongName = {
  filterId: 5,
  enabled: true,
  pipelineId: 1,
  pipelineName: loremIpsum({ count: 10, units: "words" }),
  trackerPercent: getRandomInt(0, 100),
  priority: getRandomInt(1, 99),
  filterName: loremIpsum(LOREM_CONFIG as any),
  filter: createTestFilter(),
  lastPollAge: "1.5d",
  taskCount: 4,
  trackerMs: 87834234234,
  status: "Active",
  streamCount: 5,
  eventCount: 6,
  createUser: "tester",
  createdOn: 87134234234,
  updateUser: "tester2",
  updatedOn: 87934234234
};

export const generateGenericTracker = (filterId: number): StreamTaskType => ({
  filterId,
  enabled: true,
  pipelineId: 1,
  pipelineName: loremIpsum(LOREM_CONFIG as any),
  trackerPercent: getRandomInt(0, 100),
  priority: getRandomInt(1, 99),
  filterName: loremIpsum(LOREM_CONFIG as any),
  filter: createTestFilter(),
  lastPollAge: "1.5d",
  taskCount: 4,
  trackerMs: 87834234234,
  status: "Active",
  streamCount: 5,
  eventCount: 6,
  createUser: "tester",
  createdOn: 87134234234,
  updateUser: "tester2",
  updatedOn: 87934234234
});

export const trackers = {
  minimalTracker_undefinedLastPollAge,
  minimalTracker_nullLastPollAge,
  minimalTracker_emptyLastPollAge,
  maximalTracker,
  maximalTracker_withLongName
};
