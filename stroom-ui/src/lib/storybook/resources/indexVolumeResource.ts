import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { Config } from "../../../startup/config";
import { ResourceBuilder } from "./resourceBuilder";
import { IndexVolume } from "../../../types";

let nextIdToCreate = 100000;

const resourceBuilder: ResourceBuilder = (
  server: any,
  testConfig: Config,
  testCache: TestCache
) => {
  // Get All
  server
    .get(`${testConfig.stroomBaseServiceUrl}/stroom-index/volume/v1`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(testCache.data!.indexVolumesAndGroups.volumes);
    });

  // Get By ID
  server
    .get(
      `${testConfig.stroomBaseServiceUrl}/stroom-index/volume/v1/:indexVolumeId`
    )
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let indexVolumeId = req.params.indexVolumeId;
      res.json(
        testCache.data!.indexVolumesAndGroups.volumes.find(
          v => v.id === indexVolumeId
        )
      );
    });

  // Create
  server
    .post(`${testConfig.stroomBaseServiceUrl}/stroom-index/volume/v1`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { nodeName, path } = JSON.parse(req.body);
      let now = Date.now();
      let newIndexVolume: IndexVolume = {
        nodeName,
        path,
        id: nextIdToCreate++,
        createTimeMs: now,
        updateTimeMs: now,
        createUser: "test",
        updateUser: "test",
        bytesFree: 100,
        bytesLimit: 100,
        bytesUsed: 100,
        bytesTotal: 100,
        statusMs: now
      };

      testCache.data!.indexVolumesAndGroups = {
        ...testCache.data!.indexVolumesAndGroups,
        volumes: testCache.data!.indexVolumesAndGroups.volumes.concat([
          newIndexVolume
        ])
      };

      res.json(newIndexVolume);
    });

  // Delete
  server
    .delete(
      `${testConfig.stroomBaseServiceUrl}/stroom-index/volume/v1/:indexVolumeId`
    )
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let oldIndexVolumeId = req.params.indexVolumeId;
      testCache.data!.indexVolumesAndGroups = {
        ...testCache.data!.indexVolumesAndGroups,
        volumes: testCache.data!.indexVolumesAndGroups.volumes.filter(
          v => v.id !== oldIndexVolumeId
        )
      };

      res.send(undefined);
      // res.sendStatus(204);
    });

  // Get Volumes in Group

  // Add Volume to Group

  // Remove Volume from Group
};

export default resourceBuilder;
