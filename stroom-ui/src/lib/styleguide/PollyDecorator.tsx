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
import * as uuidv4 from "uuid/v4";
import { connect } from "react-redux";
import { compose, lifecycle } from "recompose";
import { Polly } from "@pollyjs/core";
import FetchAdapter, {
  HttpRequest,
  HttpResponse
} from "@pollyjs/adapter-fetch";

// Register the fetch adapter so its accessible by all future polly instances
Polly.register(FetchAdapter);

import * as JsSearch from "js-search";

import {
  findItem,
  addItemsToTree,
  findByUuids,
  deleteItemsFromTree,
  iterateNodes
} from "../../lib/treeUtils";
import { GlobalStoreState } from "../../startup/reducers";
import { actionCreators as fetchActionCreators } from "../../lib/fetchTracker.redux";
import withConfig from "../../startup/withConfig";
import { Config } from "../../startup/config";
import { DocRefTypeList } from "../../components/DocRefTypes/redux";
import {
  DocRefType,
  DocRefTree,
  DocRefWithLineage,
  PipelineModelType,
  ElementDefinitions,
  ElementPropertiesByElementIdType,
  Dictionary,
  DataSourceType,
  StreamTaskType
} from "../../types";

import { StreamAttributeMapResult } from "../../sections/DataViewer/types";

const { resetAllUrls } = fetchActionCreators;

const testConfig: Config = {
  authenticationServiceUrl: "/authService/authentication/v1",
  authorisationServiceUrl: "/api/authorisation/v1",
  stroomBaseServiceUrl: "/api",
  authUsersUiUrl:
    "auth/users/because/they/are/loaded/in/an/iframe/which/is/beyond/scope/of/these/tests",
  authTokensUiUrl:
    "auth/tokens/because/they/are/loaded/in/an/iframe/which/is/beyond/scope/of/these/tests",
  advertisedUrl: "/",
  appClientId: "stroom-ui"
};

export interface TestData {
  docRefTypes: DocRefTypeList;
  documentTree: DocRefTree;
  pipelines: {
    [pipelineId: string]: PipelineModelType;
  };
  elements: ElementDefinitions;
  elementProperties: ElementPropertiesByElementIdType;
  xslt: {
    [xsltId: string]: string;
  };
  dictionaries: {
    [dictionaryId: string]: Dictionary;
  };
  trackers: Array<StreamTaskType>;
  dataList: StreamAttributeMapResult;
  dataSource: DataSourceType;
}

// The server is created as a singular thing for the whole app
// Much easier to manage it this way
const polly = new Polly("Mock Stroom API");
polly.configure({
  adapters: ["fetch"],
  logging: true
});
const { server } = polly;

// The cache acts as a singular global object who's contents are replaced
export interface TestCache {
  data?: TestData;
}

const testCache: TestCache = {};

const startTime = Date.now();

// This is normally deployed as part of the server
server.get("*.hot-update.json").passthrough();

server.get("/config.json").intercept((req: HttpRequest, res: any) => {
  res.json(testConfig);
});

// Explorer Resource
// // Get Explorer Tree
server
  .get(`${testConfig.stroomBaseServiceUrl}/explorer/v1/all`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    res.json(testCache.data!.documentTree);
  });
// Search
server
  .get(`${testConfig.stroomBaseServiceUrl}/explorer/v1/search`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    const { searchTerm, docRefType, pageOffset, pageSize } = req.query;

    interface TempType extends DocRefType {
      lineage: Array<DocRefType>;
      lineageNames: string;
    }

    let searchResults: Array<TempType> = [];
    const searchTermValid = searchTerm && searchTerm.length > 1;
    const docRefTypeValid = docRefType && docRefType.length > 1;

    if (searchTermValid || docRefTypeValid) {
      iterateNodes(testCache.data!.documentTree, (lineage, node) => {
        searchResults.push({
          name: node.name,
          type: node.type,
          uuid: node.uuid,
          lineage,
          lineageNames: lineage.reduce((acc, curr) => `${acc} ${curr.name}`, "")
        });
      });

      if (searchTermValid) {
        const search = new JsSearch.Search("uuid");
        search.addIndex("name");
        search.addIndex("lineageNames");
        search.addDocuments(searchResults);

        searchResults = search.search(searchTerm) as Array<TempType>;
      }

      if (docRefTypeValid) {
        searchResults = searchResults.filter(d => d.type === docRefType);
      }
    }

    res.json(
      searchResults
        .map(s => ({
          name: s.name,
          type: s.type,
          uuid: s.uuid
        }))
        .splice(pageOffset, pageSize)
    );
  });
// // Get Info
server
  .get(
    `${
      testConfig.stroomBaseServiceUrl
    }/explorer/v1/info/:docRefType/:docRefUuid`
  )
  .intercept((req: HttpRequest, res: HttpResponse) => {
    const { node: docRef } = findItem(
      testCache.data!.documentTree,
      req.params.docRefUuid
    )!;
    const info = {
      docRef,
      createTime: startTime,
      updateTime: Date.now(),
      createUser: "testGuy",
      updateUser: "testGuy",
      otherInfo: "pet peeves - crying babies"
    };
    res.json(info);
  });
// Get Document Types
server
  .get(`${testConfig.stroomBaseServiceUrl}/explorer/v1/docRefTypes`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    res.json(testCache.data!.docRefTypes);
  });
// // Create Document
server
  .post(`${testConfig.stroomBaseServiceUrl}/explorer/v1/create`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    const {
      docRefType,
      docRefName,
      destinationFolderRef
      //permissionInheritance
    } = JSON.parse(req.body);

    const newDocRef = {
      uuid: uuidv4(),
      type: docRefType,
      name: docRefName,
      children: docRefType === "Folder" ? [] : undefined
    };
    testCache.data!.documentTree = addItemsToTree(
      testCache.data!.documentTree,
      destinationFolderRef.uuid,
      [newDocRef]
    );

    res.json(testCache.data!.documentTree);
  });

// Copies need to be deep
const copyDocRef = (docRef: DocRefTree): DocRefTree => ({
  uuid: uuidv4(),
  type: docRef.type,
  name: `${docRef.name}-copy-${uuidv4()}`,
  children: docRef.children ? docRef.children.map(copyDocRef) : undefined
});

// Copy Document
server
  .post(`${testConfig.stroomBaseServiceUrl}/explorer/v1/copy`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    const { destinationFolderRef, docRefs } = JSON.parse(req.body);

    const copies = docRefs
      .map((d: DocRefType) => findItem(testCache.data!.documentTree, d.uuid))
      .map((d: DocRefWithLineage) => d.node)
      .map(copyDocRef);
    testCache.data!.documentTree = addItemsToTree(
      testCache.data!.documentTree,
      destinationFolderRef.uuid,
      copies
    );

    res.json(testCache.data!.documentTree);
  });
// Move Document
server
  .put(`${testConfig.stroomBaseServiceUrl}/explorer/v1/move`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    const { destinationFolderRef, docRefs } = JSON.parse(req.body);

    const docRefUuidsToDelete = docRefs.map((d: DocRefType) => d.uuid);
    const itemsToMove = findByUuids(
      testCache.data!.documentTree,
      docRefUuidsToDelete
    );
    testCache.data!.documentTree = deleteItemsFromTree(
      testCache.data!.documentTree,
      docRefUuidsToDelete
    );
    testCache.data!.documentTree = addItemsToTree(
      testCache.data!.documentTree,
      destinationFolderRef.uuid,
      itemsToMove
    );

    res.json(testCache.data!.documentTree);
  });
// Rename Document
server
  .put(`${testConfig.stroomBaseServiceUrl}/explorer/v1/rename`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    const { docRef, name } = JSON.parse(req.body);
    res.json({ ...docRef, name });
  });
// Delete Document
server
  .delete(`${testConfig.stroomBaseServiceUrl}/explorer/v1/delete`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    //const docRefs = JSON.parse(req.body);
    res.json(testCache.data!.documentTree);
  });

// Elements Resource
server
  .get(`${testConfig.stroomBaseServiceUrl}/elements/v1/elements`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    res.json(testCache.data!.elements);
  });
server
  .get(`${testConfig.stroomBaseServiceUrl}/elements/v1/elementProperties`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    res.json(testCache.data!.elementProperties);
  });

// Pipeline Resource
server
  .get(`${testConfig.stroomBaseServiceUrl}/pipelines/v1/:pipelineId`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    const pipeline = testCache.data!.pipelines[req.params.pipelineId];
    if (pipeline) {
      res.json(pipeline);
    } else {
      res.sendStatus(404);
    }
  });
server
  .get(`${testConfig.stroomBaseServiceUrl}/pipelines/v1/`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    res.json({
      total: Object.keys(testCache.data!.pipelines).length,
      pipelines: Object.keys(testCache.data!.pipelines).map(p => ({
        uuid: p,
        name: p,
        type: "Pipeline"
      }))
    });
  });
server
  .post(`${testConfig.stroomBaseServiceUrl}/pipelines/v1/:pipelineId`)
  .intercept((req: HttpRequest, res: HttpResponse) => res.sendStatus(200));

// XSLT Resource
server
  .get(`${testConfig.stroomBaseServiceUrl}/xslt/v1/:xsltUuid`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    const xslt = testCache.data!.xslt[req.params.xsltUuid];
    if (xslt) {
      res.setHeader("Content-Type", "application/xml");
      res.send(xslt);
    } else {
      res.sendStatus(404);
    }
  });
server
  .post(`${testConfig.stroomBaseServiceUrl}/xslt/v1/:xsltUuid`)
  .intercept((req: HttpRequest, res: HttpResponse) => res.sendStatus(200));

// Dictionary Resource
server
  .get(`${testConfig.stroomBaseServiceUrl}/dictionary/v1/:dictionaryUuid`)
  .intercept((req: HttpRequest, res: HttpResponse) => {
    const dict = testCache.data!.dictionaries[req.params.dictionaryUuid];
    if (dict) {
      res.json(dict);
    } else {
      res.sendStatus(404);
    }
  });
server
  .post(`${testConfig.stroomBaseServiceUrl}/dictionary/v1/:dictionaryUuid`)
  .intercept((req: HttpRequest, res: HttpResponse) => res.sendStatus(200));

// Stream Task Resource (for Tracker Dashboard)
server
  .get(`${testConfig.stroomBaseServiceUrl}/streamTasks/v1/`)
  .intercept((req: HttpRequest, res: HttpResponse) =>
    res.json({
      streamTasks: testCache.data!.trackers || [],
      totalStreamTasks: testCache.data!.trackers
        ? testCache.data!.trackers.length
        : 0
    })
  );

/**
 * The StreamAttributeMap resource supports expression-based search.
 * This responds with the datasource for this expression.
 */
server
  .get(`${testConfig.stroomBaseServiceUrl}/streamattributemap/v1/dataSource`)
  .intercept((req: HttpRequest, res: HttpResponse) =>
    res.json(testCache.data!.dataSource)
  );

/**
 * This responds with a list of streamAttributeMaps
 */
server
  .get(`${testConfig.stroomBaseServiceUrl}/streamattributemap/v1/`)
  .intercept((req: HttpRequest, res: HttpResponse) =>
    res.json(testCache.data!.dataList)
  );

export interface Props {
  testData: TestData;
}

interface ConnectState {
  config: Config;
}

interface ConnectDispatch {
  resetAllUrls: typeof resetAllUrls;
}

export interface EnhancedProps extends ConnectState, ConnectDispatch {}

const setupTestServer = (testData: TestData) =>
  compose<EnhancedProps, Props>(
    connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
      ({ config: { values } }) => ({ config: values }),
      {
        resetAllUrls
      }
    ),
    lifecycle<Props & ConnectDispatch & ConnectState, {}>({
      componentDidMount() {
        // must be done before any children have mounted, but the docs say this function is unsafe...
        // We can't hook the constructor in the lifecycle thing, so if we need to replace this later then
        // we can make a little custom component
        this.props.resetAllUrls();

        // Replace all the 'server side data' with the properties passed in
        testCache.data = {
          documentTree: { ...testData.documentTree },
          docRefTypes: testData.docRefTypes,
          pipelines: { ...testData.pipelines },
          elements: [...testData.elements],
          elementProperties: { ...testData.elementProperties },
          xslt: { ...testData.xslt },
          dictionaries: { ...testData.dictionaries },
          trackers: [...testData.trackers],
          dataList: { ...testData.dataList },
          dataSource: { ...testData.dataSource }
        };
      }
    }),
    withConfig
  );

export default setupTestServer;
