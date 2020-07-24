import { HttpRequest, HttpResponse } from "@pollyjs/adapter-fetch";

import { TestCache } from "../PollyDecorator";
import { ResourceBuilder } from "./types";

const resourceBuilder: ResourceBuilder = (
  server: any,
  apiUrl: any,
  testCache: TestCache,
) => {
  const resource = apiUrl("/streamattributemap/v1");

  /**
   * The StreamAttributeMap resource supports expression-based search.
   * This responds with the datasource for this expression.
   */
  server
    .get(`${resource}/dataSource`)
    .intercept((req: HttpRequest, res: HttpResponse) =>
      res.json(testCache.data!.dataSource),
    );

  /**
   * This responds with a list of streamAttributeMaps
   */
  server.get(resource).intercept((req: HttpRequest, res: HttpResponse) => {
    console.log("Searching Stream Attribute Maps");
    res.json(testCache.data!.dataList);
  });

  /**
   * This responds to getDetailsForSelectedStream
   */
  server
    .get(`${resource}/:metaId`)
    .intercept(({ params: { metaId } }: HttpRequest, res: HttpResponse) => {
      const row = testCache.data!.dataList.streamAttributeMaps.find(
        (s) => `${s.meta.id}` === metaId,
      );
      res.json(row);
    });

  /**
   * This responds to getRelations
   */
  server
    .get(`${resource}/:metaId/:anyStatus/relations`)
    .intercept(({ params: { metaId } }: HttpRequest, res: HttpResponse) => {
      const row = testCache.data!.dataList.streamAttributeMaps.find(
        (s) => `${s.meta.id}` === metaId,
      );
      res.json([row]);
    });

  /**
   * This responds with a list of streamAttributeMaps, body is a search
   */
  server.post(resource).intercept((req: HttpRequest, res: HttpResponse) => {
    const expression = JSON.parse(req.body);
    console.log("Searching Stream Attribute Maps", expression);
    res.json(testCache.data!.dataList);
  });
};

export default resourceBuilder;
