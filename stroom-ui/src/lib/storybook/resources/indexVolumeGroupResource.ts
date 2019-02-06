import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { Config } from "../../../startup/config";
import { ResourceBuilder } from "./resourceBuilder";

const resourceBuilder: ResourceBuilder = (
  server: any,
  testConfig: Config,
  testCache: TestCache
) => {
  // Get All
  server
    .get(`${testConfig.stroomBaseServiceUrl}/stroom-index/volumeGroup/v1`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(testCache.data!.indexVolumesAndGroups.groups);
    });

  // Get All Names
  server
    .get(`${testConfig.stroomBaseServiceUrl}/stroom-index/volumeGroup/v1/names`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(testCache.data!.indexVolumesAndGroups.groups.map(g => g.name));
    });

  // Get by Name
  server
    .get(`${testConfig.stroomBaseServiceUrl}/stroom-index/volumeGroup/v1/:name`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(
        testCache.data!.indexVolumesAndGroups.groups.find(
          g => g.name === req.params.name
        )
      );
    });

  // Create
  server
    .post(
      `${testConfig.stroomBaseServiceUrl}/stroom-index/volumeGroup/v1/:name`
    )
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let name = req.params.name;
      let now = Date.now();
      let newIndexVolumeGroup = {
        name,
        createTimeMs: now,
        updateTimeMs: now,
        createUser: "test",
        updateUser: "test"
      };
      testCache.data!.indexVolumesAndGroups = {
        ...testCache.data!.indexVolumesAndGroups,
        groups: testCache.data!.indexVolumesAndGroups.groups.concat([
          newIndexVolumeGroup
        ])
      };

      res.json(newIndexVolumeGroup);
    });

  // Delete
  server
    .delete(
      `${testConfig.stroomBaseServiceUrl}/stroom-index/volumeGroup/v1/:name`
    )
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let oldName = req.params.name;
      testCache.data!.indexVolumesAndGroups = {
        ...testCache.data!.indexVolumesAndGroups,
        groups: testCache.data!.indexVolumesAndGroups.groups.filter(
          g => g.name !== oldName
        ),
        groupMemberships: testCache.data!.indexVolumesAndGroups.groupMemberships.filter(
          m => m.groupName !== oldName
        )
      };

      res.send(undefined);
      // res.sendStatus(204);
    });
};

export default resourceBuilder;
