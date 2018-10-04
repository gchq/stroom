Minimal tracker with undefined last poll age

```jsx
const enhanceWithTestTrackers = require("../test/enhanceWithTestTrackers")
  .default;
const { trackers } = require("../tracker.testData");

const TestProcessingDetails = enhanceWithTestTrackers(ProcessingDetails);

<TestProcessingDetails
  testTrackers={[trackers.minimalTracker_undefinedLastPollAge]}
  testTrackerSelection={1}
/>;
```

Minimal tracker with null last poll age

```jsx
const enhanceWithTestTrackers = require("../test/enhanceWithTestTrackers")
  .default;
const { trackers } = require("../tracker.testData");

const TestProcessingDetails = enhanceWithTestTrackers(ProcessingDetails);
<TestProcessingDetails
  testTrackers={[trackers.minimalTracker_nullLastPollAge]}
  testTrackerSelection={2}
/>;
```

Minimal tracker with empty last poll age

```jsx
const enhanceWithTestTrackers = require("../test/enhanceWithTestTrackers")
  .default;
const { trackers } = require("../tracker.testData");

const TestProcessingDetails = enhanceWithTestTrackers(ProcessingDetails);

<TestProcessingDetails
  testTrackers={[trackers.minimalTracker_emptyLastPollAge]}
  testTrackerSelection={3}
/>;
```

Maximal tracker

```jsx
const enhanceWithTestTrackers = require("../test/enhanceWithTestTrackers")
  .default;
const { trackers } = require("../tracker.testData");

const TestProcessingDetails = enhanceWithTestTrackers(ProcessingDetails);
<TestProcessingDetails
  testTrackers={[trackers.maximalTracker]}
  testTrackerSelection={4}
/>;
```

Maximal tracker with a long name

```jsx
const enhanceWithTestTrackers = require("../test/enhanceWithTestTrackers")
  .default;
const { trackers } = require("../tracker.testData");

const TestProcessingDetails = enhanceWithTestTrackers(ProcessingDetails);

<TestProcessingDetails
  testTrackers={[trackers.maximalTracker_withLongName]}
  testTrackerSelection={5}
/>;
```
