declare module "@pollyjs/adapter-fetch" {
  const FetchAdapter: any;
  export default FetchAdapter;

  export interface HttpRequest {
    body: any;
    query: any;
    params: any;
  }

  export interface HttpResponse {
    status: (code: number) => HttpResponse;
    setHeader: (key: string, value: string) => Response;
    getHeader: (key: string) => string;
    setHeaders: (headers: { [key: string]: any }) => Response;
    removeHeader: (key: string) => HttpResponse;
    removeHeaders: (keys: string[]) => HttpResponse;
    hasHeader: (key: string) => boolean;
    type: (newType: string) => HttpResponse;
    send: (body: any) => HttpResponse;
    sendStatus: (code: number) => HttpResponse;
    json: (body: any) => HttpResponse;
    jsonBody: () => object;
    end: () => HttpResponse;
  }
}
