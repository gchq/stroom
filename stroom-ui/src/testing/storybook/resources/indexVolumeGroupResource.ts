import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { ResourceBuilder } from "./types";

const resourceBuilder: ResourceBuilder = (
  server: any,
  apiUrl: any,
  testCache: TestCache,
) => {
  const resource = apiUrl("/stroom-index/volumeGroup/v1");

  // Get All
  server.get(resource).intercept((req: HttpRequest, res: HttpResponse) => {
    res.json(testCache.data!.indexVolumesAndGroups.groups);
  });

  // Get All Names
  server
    .get(`${resource}/names`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(testCache.data!.indexVolumesAndGroups.groups.map(g => g.name));
    });

  // Get by Name
  server
    .get(`${resource}/:name`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const group = testCache.data!.indexVolumesAndGroups.groups.find(
        g => g.name === req.params.name,
      );
      if (!!group) {
        res.json(group);
      } else {
        res.sendStatus(404);
      }
    });

  // Create
  server
    .post(`${resource}/:name`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const name = req.params.name;
      const now = Date.now();
      const newIndexVolumeGroup = {
        id: "-1",
        name,
        createTimeMs: now,
        updateTimeMs: now,
        createUser: "test",
        updateUser: "test",
      };
      testCache.data!.indexVolumesAndGroups = {
        ...testCache.data!.indexVolumesAndGroups,
        groups: testCache.data!.indexVolumesAndGroups.groups.concat([
          newIndexVolumeGroup,
        ]),
      };

      res.json(newIndexVolumeGroup);
    });

  // Delete
  server
    .delete(`${resource}/:name`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const oldName = req.params.name;
      testCache.data!.indexVolumesAndGroups = {
        ...testCache.data!.indexVolumesAndGroups,
        groups: testCache.data!.indexVolumesAndGroups.groups.filter(
          g => g.name !== oldName,
        ),
      };

      res.status(204).send(undefined);
    });
};

export default resourceBuilder;
