import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";
import { ResourceBuilder } from "./types";
import { Activity } from "components/Activity/api/types";

const ACTIVITY: Activity = {
  id: "1",
  userId: "testuser",
  details: {
    properties: [
      {
        id: "prop1",
        name: "name1",
        value: "value1",
        showInSelection: true,
        showInList: true,
      },
      {
        id: "prop2",
        name: "name2",
        value: "value2",
        showInSelection: true,
        showInList: true,
      },
    ],
  },
};

const ACTIVITY2: Activity = JSON.parse(JSON.stringify(ACTIVITY));
ACTIVITY2.id = "2";

const ACTIVITIES: Activity[] = [ACTIVITY, ACTIVITY2];

const resourceBuilder: ResourceBuilder = (
  server: any,
  apiUrl: any,
) => {
  const resource = apiUrl("/activity/v1");

  server
    .get(`${resource}/current`)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(ACTIVITY);
    });

  server
    .get(resource)
    .intercept((req: HttpRequest, res: HttpResponse) => {
      res.json(ACTIVITIES);
    });
};

export default resourceBuilder;
