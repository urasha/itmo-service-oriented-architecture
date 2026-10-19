import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ApiError, createApi, httpsBase, requestUrl } from '../api.js';
import { parse } from '../json-numbers.js';

const config = { flatsBaseUrl: 'https://flats.example', agencyBaseUrl: 'https://agency.example', requestTimeoutMillis: 100 };
test('JSON query is encoded once; sort parameters preserve priority and repetition', () => {
  const json = '{"name":"Квартира + & # \"","area":9007199254740993}';
  const url = requestUrl(config.flatsBaseUrl, '/flats', [['flat',json],['sort','area:desc'],['sort','name:asc']]);
  assert.equal(url.searchParams.get('flat'), json); assert.ok(url.href.includes('%2B')); assert.ok(url.href.includes('%26'));
  assert.deepEqual(url.searchParams.getAll('sort'), ['area:desc','name:asc']);
});
test('HTTPS config rejects mixed content, credentials and query injection', () => {
  assert.equal(httpsBase('', 'https://flats.example'), 'https://flats.example');
  for (const value of ['http://flats.example','https://user:pass@flats.example','https://flats.example?q=1','https://flats.example/#fragment']) assert.throws(() => httpsBase(value));
});
test('HTTP validation errors keep status, timestamp and original safe message', async () => {
  const api = createApi(config, config.flatsBaseUrl, async () => new Response('{"timestamp":"2026-10-19T12:00:00Z","message":"price must be positive"}', { status:422 }));
  await assert.rejects(api.request('flats','PATCH','/flats/1'), error => error.status === 422 && error.timestamp.endsWith('Z') && error.message.includes('price'));
});
test('empty 204, transport count and large JSON numbers are read without rounding', async () => {
  let response = new Response(null, { status:204 });
  const api = createApi(config, config.flatsBaseUrl, async () => response);
  assert.equal((await api.request('flats','DELETE','/flats/1')).data, null);
  response = new Response('{"count":9007199254740993}', { status:200 });
  assert.equal((await api.request('flats','GET','/flats/count-by-transport')).data.count.text, '9007199254740993');
});
test('create sends URL JSON without a body and then gets the Location resource', async () => {
  const calls=[];
  const api=createApi(config,config.flatsBaseUrl,async(url,options)=>{
    calls.push({url:String(url),options});
    return calls.length===1 ? new Response(null,{status:201,headers:{Location:'/flats/9223372036854775807'}})
      : new Response('{"id":9223372036854775807,"price":9007199254740992.01}',{status:200});
  });
  const result=await api.create(parse('{"price":9007199254740992.01}'));
  assert.equal(result.status,201); assert.equal(result.data.id.text,'9223372036854775807');
  assert.equal(calls[0].options.method,'POST'); assert.equal(calls[0].options.body,undefined);
  assert.equal(new URL(calls[0].url).searchParams.get('flat'),'{"price":9007199254740992.01}');
  assert.equal(calls[1].url,'https://flats.example/flats/9223372036854775807');
});
test('agency follows redirects, has no credentials and uses the distinct configured origin', async () => {
  let call;
  const api=createApi(config,config.flatsBaseUrl,async(url,options)=>{call={url:String(url),options};return new Response('{}');});
  await api.request('agency','GET','/agency/get-cheapest/1/1');
  assert.equal(call.url,'https://agency.example/agency/get-cheapest/1/1');
  assert.equal(call.options.redirect,'follow');assert.equal(call.options.credentials,'omit');
});
test('opaque network failures do not claim a specific TLS or CORS cause', async () => {
  const api=createApi(config,config.flatsBaseUrl,async()=>{throw new TypeError('Failed to fetch');});
  await assert.rejects(api.request('agency','GET','/agency/get-cheapest/1/2'),error=>error instanceof ApiError && error.status===null && error.message.includes('Проверьте'));
});
test('deadline aborts waiting requests', async () => {
  const api=createApi({...config,requestTimeoutMillis:15},config.flatsBaseUrl,(_url,options)=>new Promise((_resolve,reject)=>{
    options.signal.addEventListener('abort',()=>reject(new DOMException('aborted','AbortError')));
  }));
  await assert.rejects(api.request('flats','GET','/flats'),/Время ожидания истекло/);
});
test('malformed success JSON and non-JSON errors are readable protocol errors', async () => {
  let response=new Response('<html>error</html>',{status:200});
  const api=createApi(config,config.flatsBaseUrl,async()=>response);
  await assert.rejects(api.request('flats','GET','/flats'),/некорректный JSON/);
  response=new Response('<html>error</html>',{status:414});
  await assert.rejects(api.request('flats','GET','/flats'),error=>error.status===414);
});
