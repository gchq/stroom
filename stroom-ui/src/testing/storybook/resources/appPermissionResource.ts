import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { Config } from "startup/config/types";
import { ResourceBuilder } from "./types";
import { onlyUnique } from "lib/formUtils";

const resourceBuilder: ResourceBuilder = (
  server: any,
  { stroomBaseServiceUrl }: Config,
  testCache: TestCache,
) => {
  const resource = `${stroomBaseServiceUrl}/appPermissions/v1`;

  // Get All App Permission Names
  server.get(resource).intercept((req: HttpRequest, res: HttpResponse) => {
    res.json(testCache.data!.allAppPermissions);
  });
  server
    .get(`${resource}/:userUuid`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(testCache.data!.userAppPermissions[req.params.userUuid] || []);
    });
  server
    .post(`${resource}/:userUuid/:permissionName`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      testCache.data!.userAppPermissions[req.params.userUuid] = [
        ...(testCache.data!.userAppPermissions[req.params.userUuid] || []),
        req.params.permissionName,
      ].filter(onlyUnique);

      res.status(204).send(undefined);
    });

  server
    .delete(`${resource}/:userUuid/:permissionName`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      testCache.data!.userAppPermissions[
        req.params.userUuid
      ] = testCache.data!.userAppPermissions[req.params.userUuid].filter(
        p => p !== req.params.permissionName,
      );

      res.status(204).send(undefined);
    });
};

export default resourceBuilder;
