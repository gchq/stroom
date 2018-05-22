import loremIpsum from 'lorem-ipsum';

const getRandomInt = (min, max) => Math.floor(Math.random() * (max - min)) + min;

const minimalTracker_undefinedLastPollAge = {
  filterId: 1,
  enabled: true,
  lastPollAge: undefined,
  pipelineName: loremIpsum({ count: 3, units: 'words' }),
  priority: getRandomInt(1, 99),
  filterXml: '<xml/>',
  createUser: 'tester',
  createdOn: 87134234234,
};

const minimalTracker_nullLastPollAge = {
  filterId: 2,
  enabled: true,
  lastPollAge: null,
  pipelineName: loremIpsum({ count: 3, units: 'words' }),
  priority: getRandomInt(1, 99),
  filterXml: '<xml/>',
  createUser: 'tester',
  createdOn: 87134234234,
};

const minimalTracker_emptyLastPollAge = {
  filterId: 3,
  enabled: true,
  lastPollAge: '',
  pipelineName: loremIpsum({ count: 3, units: 'words' }),
  priority: getRandomInt(1, 99),
  filterXml: '<xml/>',
  createUser: 'tester',
  createdOn: 87134234234,
};

const maximalTracker = {
  filterId: 4,
  enabled: true,
  pipelineName: loremIpsum({ count: 3, units: 'words' }),
  trackerPercent: getRandomInt(0, 100),
  priority: getRandomInt(1, 99),
  filterXml: '<xml/>',
  lastPollAge: '1.5d',
  taskCount: 4,
  trackerMs: 87834234234,
  status: 'Active',
  streamCount: 5,
  eventCount: 6,
  createUser: 'tester',
  createdOn: 87134234234,
  updateUser: 'tester2',
  updatedOn: 87934234234,
};

const maximalTracker_withLongName = {
  filterId: 5,
  enabled: true,
  pipelineName: loremIpsum({ count: 10, units: 'words' }),
  trackerPercent: getRandomInt(0, 100),
  priority: getRandomInt(1, 99),
  filterXml: '<xml/>',
  lastPollAge: '1.5d',
  taskCount: 4,
  trackerMs: 87834234234,
  status: 'Active',
  streamCount: 5,
  eventCount: 6,
  createUser: 'tester',
  createdOn: 87134234234,
  updateUser: 'tester2',
  updatedOn: 87934234234,
};

export const generateGenericTracker = filterId => ({
  filterId,
  enabled: true,
  pipelineName: loremIpsum({ count: 3, units: 'words' }),
  trackerPercent: getRandomInt(0, 100),
  priority: getRandomInt(1, 99),
  filterXml: '<xml/>',
  lastPollAge: '1.5d',
  taskCount: 4,
  trackerMs: 87834234234,
  status: 'Active',
  streamCount: 5,
  eventCount: 6,
  createUser: 'tester',
  createdOn: 87134234234,
  updateUser: 'tester2',
  updatedOn: 87934234234,
});

export const trackers = {
  minimalTracker_undefinedLastPollAge,
  minimalTracker_nullLastPollAge,
  minimalTracker_emptyLastPollAge,
  maximalTracker,
  maximalTracker_withLongName,
};
