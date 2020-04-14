import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { Config } from "startup/config/types";
import { ResourceBuilder } from "./types";
import { IndexVolume } from "components/IndexVolumes/indexVolumeApi";

let nextIdToCreate = 100000;

const resourceBuilder: ResourceBuilder = (
  server: any,
  { stroomBaseServiceUrl }: Config,
  testCache: TestCache,
) => {
  const resource = `${stroomBaseServiceUrl}/stroom-index/volume/v1`;

  // Get All
  server.get(resource).intercept((req: HttpRequest, res: HttpResponse) => {
    res.json(testCache.data!.indexVolumesAndGroups.volumes);
  });

  // Get By ID
  server
    .get(`${resource}/:indexVolumeId`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let indexVolumeId: string = req.params.indexVolumeId;
      let indexVolume = testCache.data!.indexVolumesAndGroups.volumes.find(
        v => `${v.id}` === indexVolumeId,
      );
      if (!!indexVolume) {
        res.json(indexVolume);
      } else {
        res.sendStatus(404);
      }
    });

  // Create
  server.post(resource).intercept((req: HttpRequest, res: HttpResponse) => {
    const { nodeName, path } = JSON.parse(req.body);
    let now = Date.now();
    let newIndexVolume: IndexVolume = {
      nodeName,
      path,
      id: `${nextIdToCreate++}`,
      indexVolumeGroupName: "No name",
      createTimeMs: now,
      updateTimeMs: now,
      createUser: "test",
      updateUser: "test",
      bytesFree: 100,
      bytesLimit: 100,
      bytesUsed: 100,
      bytesTotal: 100,
      statusMs: now,
    };

    testCache.data!.indexVolumesAndGroups = {
      ...testCache.data!.indexVolumesAndGroups,
      volumes: testCache.data!.indexVolumesAndGroups.volumes.concat([
        newIndexVolume,
      ]),
    };

    res.json(newIndexVolume);
  });

  // Delete
  server
    .delete(`${resource}/:indexVolumeId`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let oldIndexVolumeId = req.params.indexVolumeId;
      testCache.data!.indexVolumesAndGroups = {
        ...testCache.data!.indexVolumesAndGroups,
        volumes: testCache.data!.indexVolumesAndGroups.volumes.filter(
          v => v.id !== oldIndexVolumeId,
        ),
      };

      res.status(204).send(undefined);
    });
};

export default resourceBuilder;
