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

let groups = Array(5)
  .fill(1)
  .map(generateTestGroup);
let users = Array(30)
  .fill(1)
  .map(generateTestUser);
let userGroupMemberships: Array<UserGroupMembership> = [];
let userIndex = 0;
groups.forEach(group => {
  for (let x = 0; x < 10; x++) {
    var user = users[userIndex];
    userGroupMemberships.push({
      userUuid: user.uuid,
      groupUuid: group.uuid
    });

    userIndex++;
    userIndex %= users.length;
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
  }
};
import createStore from "../../startup/store";

import { GlobalStoreState } from "../../startup/reducers";

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
