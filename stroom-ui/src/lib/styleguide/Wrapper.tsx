import * as React from "react";
import * as PropTypes from "prop-types";
import { compose, withContext } from "recompose";
import { connect } from "react-redux";
import { DragDropContext } from "react-dnd";
import HTML5Backend from "react-dnd-html5-backend";

import FontAwesomeProvider from "../../startup/FontAwesomeProvider";
import KeyIsDown from "../../lib/KeyIsDown";
import createStore from "../../startup/store";
import setupTestServer from "./PollyDecorator";
import { GlobalStoreState } from "../../startup/reducers";

// import { fromSetupSampleData } from '../FolderExplorer/test';
// import { testPipelines, elements, elementProperties } from '../PipelineEditor/test';
import { testDocRefsTypes } from "../../components/DocRefTypes/test";
// import { testXslt } from '../XsltEditor/test';
// import { testDictionaries } from '../DictionaryEditor/test';
// import { generateGenericTracker } from 'sections/Processing/tracker.testData';
// import { dataList, dataSource } from 'sections/DataViewer/test';

const testData = { docRefTypes: testDocRefsTypes };

// documentTree: fromSetupSampleData,

// elements,
// elementProperties,
// pipelines: testPipelines,
// xslt: testXslt,
// dictionaries: testDictionaries,
// dataList,
// dataSource,
// trackers: [...Array(10).keys()].map(i => generateGenericTracker(i)),
//});

export interface Props {
  children: React.ReactNode;
}
export interface ConnectState {
  theme: string;
}
export interface ConnectDispatch {}
export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const WrappedComponent = ({ children, theme = "light" }: EnhancedProps) => (
  <div className={`app-container ${theme}`}>{children}</div>
);

const enhance = compose<EnhancedProps, Props>(
  withContext(
    {
      store: PropTypes.object
    },
    () => ({
      store: createStore()
    })
  ),
  DragDropContext(HTML5Backend),
  FontAwesomeProvider,
  KeyIsDown(),
  setupTestServer(testData),
  connect<ConnectState, ConnectDispatch, {}, GlobalStoreState>(
    ({ userSettings: { theme } }) => ({
      theme
    }),
    {}
  )
);

const Wrapper = enhance(WrappedComponent);

export default Wrapper;
