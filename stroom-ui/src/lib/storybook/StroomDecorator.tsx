import * as React from "react";
import { compose } from "recompose";
import { connect } from "react-redux";
import { RenderFunction } from "@storybook/react";
import { Provider } from "react-redux";
import { DragDropContext } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";
import StoryRouter from "storybook-react-router";

import {
  setupTestServer,
  TestData,
  UserGroupMembership
} from "../../lib/storybook/PollyDecorator";

import FontAwesomeProvider from "../../startup/FontAwesomeProvider";
import KeyIsDown from "../../lib/KeyIsDown";
import { fromSetupSampleData } from "../../components/FolderExplorer/test";
import {
  testPipelines,
  elements,
  elementProperties
} from "../../components/PipelineEditor/test";
import { testDocRefsTypes } from "../../components/DocRefTypes/test";
import { testXslt } from "../../components/XsltEditor/test";
import { testDictionaries } from "../../components/DictionaryEditor/test";
import { generateGenericTracker } from "../../sections/Processing/tracker.testData";
import { dataList, dataSource } from "../../sections/DataViewer/test";
import {
  generateTestUser,
  generateTestGroup
} from "../../sections/UserPermissions/test";
import createStore from "../../startup/store";

import { GlobalStoreState } from "../../startup/reducers";
import {
  generateTestIndexVolumeGroup,
  generateTestIndexVolume
} from "../../sections/IndexVolumes/test";
import {
  IndexVolumeGroupMembership,
  User,
  IndexVolumeGroup,
  IndexVolume
} from "../../types";

let groups: Array<User> = Array(5)
  .fill(1)
  .map(generateTestGroup);
let users: Array<User> = Array(30)
  .fill(1)
  .map(generateTestUser);
let userGroupMemberships: Array<UserGroupMembership> = [];
let userIndex = 0;
groups.forEach(group => {
  for (let x = 0; x < 10; x++) {
    var user: User = users[userIndex];
    userGroupMemberships.push({
      userUuid: user.uuid,
      groupUuid: group.uuid
    });

    userIndex = (userIndex + 1) % users.length;
  }
});

let indexVolumeGroups: Array<IndexVolumeGroup> = Array(5)
  .fill(1)
  .map(generateTestIndexVolumeGroup);

let indexVolumes: Array<IndexVolume> = Array(30)
  .fill(1)
  .map(generateTestIndexVolume);

let indexVolumeGroupMemberships: Array<IndexVolumeGroupMembership> = [];
let indexVolumeIndex = 0; // Best variable name ever
indexVolumeGroups.forEach(group => {
  for (let x = 0; x < 10; x++) {
    let indexVolume: IndexVolume = indexVolumes[indexVolumeIndex];
    indexVolumeGroupMemberships.push({
      groupName: group.name,
      volumeId: indexVolume.id
    });

    indexVolumeIndex = (indexVolumeIndex + 1) % indexVolumes.length;
  }
});

export const testData: TestData = {
  documentTree: fromSetupSampleData,
  docRefTypes: testDocRefsTypes,
  elements,
  elementProperties,
  pipelines: testPipelines,
  xslt: testXslt,
  dictionaries: testDictionaries,
  dataList,
  dataSource,
  trackers: Array(10)
    .fill(null)
    .map(generateGenericTracker),
  usersAndGroups: {
    users: users.concat(groups),
    userGroupMemberships
  },
  indexVolumesAndGroups: {
    volumes: indexVolumes,
    groups: indexVolumeGroups,
    groupMemberships: indexVolumeGroupMemberships
  }
};

interface Props {}
interface ConnectState {
  theme: string;
}
interface ConnectDispatch {}
interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhanceLocal = compose(
  setupTestServer(testData),
  DragDropContext(HTML5Backend),
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ userSettings: { theme } }) => ({
      theme
    }),
    {}
  ),
  KeyIsDown(),
  FontAwesomeProvider
);

const store = createStore();

class WrappedComponent extends React.Component<EnhancedProps> {
  render() {
    return (
      <div className={`app-container ${this.props.theme}`}>
        {this.props.children}
      </div>
    );
  }
}

const ThemedComponent = enhanceLocal(WrappedComponent);

export default (storyFn: RenderFunction) => (
  <Provider store={store}>
    <ThemedComponent>{StoryRouter()(storyFn)}</ThemedComponent>
  </Provider>
);
