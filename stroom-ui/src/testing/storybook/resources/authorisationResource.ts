import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { ResourceBuilder } from "./types";

const resourceBuilder: ResourceBuilder = (
  server: any,
  apiUrl: any,
  testCache: TestCache,
) => {
  const resource = apiUrl("/authorisation/v1");
  server
    .post(`${resource}/hasAppPermission`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      const { permissionName } = JSON.parse(req.body);
      if (testCache.data!.allAppPermissions.includes(permissionName)) {
        res.sendStatus(200);
      } else {
        res.sendStatus(401);
      }
    });
};

export default resourceBuilder;
