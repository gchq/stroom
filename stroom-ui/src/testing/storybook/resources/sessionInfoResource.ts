import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";
import { ResourceBuilder } from "./types";
import { SessionInfo } from "components/SessionInfo/api/types";

const SESSION_INFO: SessionInfo = {
  userName: "testuser",
  nodeName: "testnode",
  buildInfo: {
    upDate: "2019-01-02T00:00:00.000Z",
    buildDate: "2019-01-01T00:00:00.000Z",
    buildVersion: "1.2.3",
  },
};

const resourceBuilder: ResourceBuilder = (server: any, apiUrl: any) => {
  const resource = apiUrl("/sessionInfo/v1");

  server.get(resource).intercept((req: HttpRequest, res: HttpResponse) => {
    res.json(SESSION_INFO);
  });
};

export default resourceBuilder;
