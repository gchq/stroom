import * as JsSearch from "js-search";
import * as uuidv4 from "uuid/v4";
import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import {
  findItem,
  addItemsToTree,
  findByUuids,
  deleteItemsFromTree,
  iterateNodes
} from "../../../lib/treeUtils";
import { DocRefType, DocRefTree, DocRefWithLineage } from "../../../types";
import { TestCache } from "../PollyDecorator";
import { Config } from "../../../startup/config";
import { ResourceBuilder } from "./resourceBuilder";

const resourceBuilder: ResourceBuilder = (
  server: any,
  testConfig: Config,
  testCache: TestCache
) => {
  const startTime = Date.now();

  server
    .get(`${testConfig.stroomBaseServiceUrl}/explorer/v1/all`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(testCache.data!.documentTree);
    });
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
            lineageNames: lineage.reduce(
              (acc, curr) => `${acc} ${curr.name}`,
              ""
            )
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
  // // Get Document Types
  server
    .get(`${testConfig.stroomBaseServiceUrl}/explorer/v1/docRefTypes`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(testCache.data!.docRefTypes);
    });
  // // Create Document
  server
    .post(`${testConfig.stroomBaseServiceUrl}/explorer/v1/create`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { docRefType, docRefName, destinationFolderRef } = JSON.parse(
        req.body
      );

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
};

export default resourceBuilder;
