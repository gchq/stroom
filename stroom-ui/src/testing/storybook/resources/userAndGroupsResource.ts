import * as uuidv4 from "uuid/v4";
import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { ResourceBuilder } from "./types";

const resourceBuilder: ResourceBuilder = (
  server: any,
  apiUrl: any,
  testCache: TestCache,
) => {
  const resource = apiUrl("/users/v1");

  // Get User by UUID
  server
    .get(`${resource}/:userUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let userUuid = req.params.userUuid;
      let user = testCache.data!.usersAndGroups.users.find(
        u => u.uuid === userUuid,
      );

      res.json(user);
    });

  // Find Users
  server.get(resource).intercept((req: HttpRequest, res: HttpResponse) => {
    const { name, uuid, isGroup } = req.query;
    let filtered = testCache
      .data!.usersAndGroups.users.filter(
        u => name === undefined || u.name.includes(name),
      )
      .filter(u => uuid === undefined || u.uuid === uuid)
      .filter(
        u => isGroup === undefined || Boolean(u.group).toString() === isGroup,
      );
    res.json(filtered);
  });

  // Create User
  server
    .post(`${resource}/create/:name/:isGroup`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { name, group } = req.params;
      let newUser = { name, group, uuid: uuidv4() };

      testCache.data!.usersAndGroups.users = testCache.data!.usersAndGroups.users.concat(
        [newUser],
      );

      res.json(newUser);
    });
  // Delete User
  server
    .delete(`${resource}/:userUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let oldUuid = req.params.userUuid;
      testCache.data!.usersAndGroups = {
        users: testCache.data!.usersAndGroups.users.filter(
          u => u.uuid !== oldUuid,
        ),
        userGroupMemberships: testCache.data!.usersAndGroups.userGroupMemberships.filter(
          m => m.groupUuid !== oldUuid && m.userUuid !== oldUuid,
        ),
      };

      res.status(204).send(undefined);
    });
  // Users in Group
  server
    .get(`${resource}/usersInGroup/:groupUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let users = testCache
        .data!.usersAndGroups.userGroupMemberships.filter(
          ugm => ugm.groupUuid === req.params.groupUuid,
        )
        .map(ugm => ugm.userUuid)
        .map(userUuid =>
          testCache
            .data!.usersAndGroups.users.filter(user => !user.group)
            .find(user => user.uuid === userUuid),
        );

      res.json(users);
    });

  // Groups for User
  server
    .get(`${resource}/groupsForUser/:userUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      let users = testCache
        .data!.usersAndGroups.userGroupMemberships.filter(
          ugm => ugm.userUuid === req.params.userUuid,
        )
        .map(ugm => ugm.groupUuid)
        .map(groupUuid =>
          testCache
            .data!.usersAndGroups.users.filter(user => user.group)
            .find(user => user.uuid === groupUuid),
        );
      res.json(users);
    });

  // Add User to Group
  server
    .put(`${resource}/:userUuid/:groupUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      testCache.data!.usersAndGroups.userGroupMemberships = testCache.data!.usersAndGroups.userGroupMemberships.concat(
        [
          {
            userUuid: req.params.userUuid,
            groupUuid: req.params.groupUuid,
          },
        ],
      );

      res.status(204).send(undefined);
    });

  // Remove User from Group
  server
    .delete(`${resource}/:userUuid/:groupUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      testCache.data!.usersAndGroups.userGroupMemberships = testCache.data!.usersAndGroups.userGroupMemberships.filter(
        m =>
          !(
            m.groupUuid === req.params.groupUuid &&
            m.userUuid === req.params.userUuid
          ),
      );

      res.status(204).send(undefined);
    });
};

export default resourceBuilder;
