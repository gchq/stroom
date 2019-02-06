import * as uuidv4 from "uuid/v4";
import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { Config } from "../../../startup/config";
import { ResourceBuilder } from "./resourceBuilder";

const resourceBuilder: ResourceBuilder = (
  server: any,
  testConfig: Config,
  testCache: TestCache
) => {
  server
    .delete(`${testConfig.stroomBaseServiceUrl}/users/v1/:userUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let oldUuid = req.params.userUuid;
      testCache.data!.usersAndGroups = {
        users: testCache.data!.usersAndGroups.users.filter(
          u => u.uuid !== oldUuid
        ),
        userGroupMemberships: testCache.data!.usersAndGroups.userGroupMemberships.filter(
          m => m.groupUuid !== oldUuid && m.userUuid !== oldUuid
        )
      };

      res.send(undefined);
      // res.sendStatus(204);
    });
  server
    .post(`${testConfig.stroomBaseServiceUrl}/users/v1`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { name, isGroup } = JSON.parse(req.body);
      let newUser = { name, isGroup, uuid: uuidv4() };

      testCache.data!.usersAndGroups.users = testCache.data!.usersAndGroups.users.concat(
        [newUser]
      );

      res.json(newUser);
    });
  server
    .get(`${testConfig.stroomBaseServiceUrl}/users/v1`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { name, uuid, isGroup } = req.query;
      let filtered = testCache
        .data!.usersAndGroups.users.filter(
          u => name === undefined || u.name.includes(name)
        )
        .filter(u => uuid === undefined || u.uuid === uuid)
        .filter(
          u =>
            isGroup === undefined || Boolean(u.isGroup).toString() === isGroup
        );
      res.json(filtered);
    });
  server
    .get(`${testConfig.stroomBaseServiceUrl}/users/v1/usersInGroup/:groupUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let users = testCache
        .data!.usersAndGroups.userGroupMemberships.filter(
          ugm => ugm.groupUuid === req.params.groupUuid
        )
        .map(ugm => ugm.userUuid)
        .map(userUuid =>
          testCache
            .data!.usersAndGroups.users.filter(user => !user.isGroup)
            .find(user => user.uuid === userUuid)
        );

      res.json(users);
    });
  server
    .get(`${testConfig.stroomBaseServiceUrl}/users/v1/groupsForUser/:userUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let users = testCache
        .data!.usersAndGroups.userGroupMemberships.filter(
          ugm => ugm.userUuid === req.params.userUuid
        )
        .map(ugm => ugm.groupUuid)
        .map(groupUuid =>
          testCache
            .data!.usersAndGroups.users.filter(user => user.isGroup)
            .find(user => user.uuid === groupUuid)
        );
      res.json(users);
    });
};

export default resourceBuilder;
