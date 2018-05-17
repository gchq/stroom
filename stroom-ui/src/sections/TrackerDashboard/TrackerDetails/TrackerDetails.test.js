import React from "react";
import TrackerDetails from "./TrackerDetails";
import TrackerDashboard from "../TrackerDashboard/TrackerDashboard";

import renderer from "react-test-renderer";

import configureMockStore from "redux-mock-store";
import thunk from "redux-thunk";
import fetchMock from "fetch-mock";
import expect from "expect"; // You can use any testing library

import { fetchTrackers } from "../streamTasksResourceClient";

const middlewares = [thunk];
const mockStore = configureMockStore(middlewares);

describe("test", () => {
  afterEach(() => {
    fetchMock.reset();
    fetchMock.restore();
  });

  it("creates trackers when fetching trackers has been done", () => {
    fetchMock.getOnce("/trackers", {
      body: { streamTrackers: ["tracker"] },
      headers: { "content-type": "application/json" }
    });

    const expectedActions = [{ type: "UPDATE_TRACKERS" }];
    const store = mockStore({
      trackerDashboard: { trackers: [], selectedTrackerId: 1 },
      authentication: { idToken: "dummyToken" },
      config: {streamTaskServiceUrl: 'http://doesntmatter.com'}
    });

    return store.dispatch(fetchTrackers()).then(() => {
      // return of async actions
      expect(store.getActions()).toEqual(expectedActions);
    });
  });
});

// test('Hello world test', () => {
//   const component = renderer.create(
//     <TrackerDetails/>,
//   );
//   let tree = component.toJSON();
//   expect(tree).toMatchSnapshot();

//   // manually trigger the callback
//   tree.props.onMouseEnter();
//   // re-rendering
//   tree = component.toJSON();
//   expect(tree).toMatchSnapshot();

//   // manually trigger the callback
//   tree.props.onMouseLeave();
//   // re-rendering
//   tree = component.toJSON();
//   expect(tree).toMatchSnapshot();
// });
