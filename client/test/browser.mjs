// Dedicated, empty local collection required. Uses real HTTPS services and Chrome trust.
import assert from 'node:assert/strict';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { mkdir } from 'node:fs/promises';
const { chromium } = await import(pathToFileURL(path.resolve('.runtime/browser-check/node_modules/playwright/index.mjs')).href);
const base = process.env.FLATS_BASE_URL || 'https://localhost:8443';
const agency = process.env.AGENCY_BASE_URL || 'https://localhost:8444';
const browser = await chromium.launch({ channel: process.env.SOA_BROWSER_CHANNEL || 'chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, ignoreHTTPSErrors: false });
const page = await context.newPage(); page.setDefaultTimeout(15000);
const created = new Set(); const operations = new Set(); const errors = [];
page.on('pageerror', error => errors.push(error.message));
page.on('response', response => {
  const request = response.request(); const url = new URL(response.url());
  if (url.pathname.startsWith('/flats') || url.pathname.startsWith('/agency/')) {
    operations.add(request.method() + ' ' + url.pathname.replace(/\/flats\/\d+$/, '/flats/{id}')
      .replace(/\/agency\/get-cheapest\/\d+\/\d+$/, '/agency/get-cheapest/{id1}/{id2}')
      .replace(/\/agency\/get-most-expensive\/\d+\/\d+\/\d+$/, '/agency/get-most-expensive/{id1}/{id2}/{id3}'));
  }
  if (request.method() === 'POST' && url.pathname === '/flats' && response.status() === 201) {
    const location = response.headers().location; if (location) created.add(new URL(location, base).pathname);
  }
});
const idle = () => page.waitForFunction(() => document.body.getAttribute('aria-busy') !== 'true');
const click = async selector => { await page.locator(selector).click(); await idle(); };
const ok = async () => assert.equal(await page.locator('#error').isVisible(), false, await page.locator('#error').textContent());
const step = async (name, action) => { await action(); console.log('PASS ' + name); };
const editor = () => click('[data-view="editor"]');
const field = name => page.locator('#field-' + name.replaceAll('.', '-'));
const state = name => page.locator(`[data-path="${name}"] .field-state`);
let first, second, third;
const prefix = 'Браузер ' + Date.now();
const firstName = prefix + ' + & # " Ж';
const fixture = (name, price, transport, area = 54) => `{"name":${JSON.stringify(name)},"coordinates":{"x":-150,"y":59.93},"area":${area},"price":${price},"numberOfRooms":2,"new":true,"view":"YARD","transport":${JSON.stringify(transport)},"house":{"name":"Дом проверки","year":1905,"numberOfFloors":5}}`;
async function createRaw(input) {
  await editor(); await page.locator('#editor-mode').selectOption('POST'); await page.locator('#input-mode').selectOption('json');
  await page.locator('#raw-input').fill(input);
  const pending = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/flats');
  await click('#save'); const response = await pending; assert.equal(response.status(), 201); await ok();
  return response.headers().location.split('/').at(-1);
}
try {
  await step('HTTPS client loads an empty collection and handles both 204 extrema', async () => {
    await page.goto(base + '/app/index.html'); await idle(); await ok();
    assert.equal(await page.locator('#total-count').textContent(), '0', 'Use a dedicated fresh Jetty instance');
    await click('#min-date'); assert.match(await page.locator('#operation-result').textContent(), /HTTP 204/); await ok();
    await click('#max-new'); assert.match(await page.locator('#operation-result').textContent(), /HTTP 204/); await ok();
  });
  await step('POST through structured form preserves int64, decimal prices and URL characters', async () => {
    await editor();
    for (const [name, value] of Object.entries({ name:firstName,'coordinates.x':'-1.5e2','coordinates.y':'59.93',area:'9007199254740993',price:'9007199254740992.01',numberOfRooms:'2','house.name':'Дом проверки','house.numberOfFloors':'5' })) await field(name).fill(value);
    await state('house.year').selectOption('value'); await field('house.year').fill('1905');
    const pending = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/flats');
    await click('#save'); const response = await pending; first = response.headers().location.split('/').at(-1); await ok();
    assert.equal(new URL(response.request().url()).searchParams.get('flat').includes('9007199254740993'), true);
    assert.match(await page.locator('#operation-result').textContent(), /9007199254740992\.01/);
    assert.match(await page.locator('#operation-result').textContent(), /9007199254740993/);
    assert.ok((await page.locator('#operation-result').textContent()).includes(firstName));
  });
  await step('JSON input creates additional flats without executing names as HTML', async () => {
    second = await createRaw(fixture(prefix + ' вторая', '9007199254740992.02', 'NONE'));
    third = await createRaw(fixture('<img src=x onerror="window.__soa_xss=1"> ' + prefix, '9007199254740992.02', null, 70));
    assert.equal(await page.evaluate(() => window.__soa_xss), undefined);
    assert.equal(await page.locator('#flats-body img').count(), 0);
  });
  await step('GET populates full form; PUT replaces; PATCH changes chosen fields and null only', async () => {
    await page.locator('#lookup-id').fill(first); await click('#lookup-form button'); await ok();
    await page.locator('#input-mode').selectOption('fields');
    assert.equal(await field('area').inputValue(), '9007199254740993');
    await page.locator('#editor-mode').selectOption('PUT'); await field('name').fill(firstName + ' · заменена'); await field('price').fill('9007199254740992.03');
    await click('#save'); await ok(); assert.match(await page.locator('#operation-result').textContent(), /Квартира|9007199254740992\.03/);
    await page.locator('#editor-mode').selectOption('PATCH');
    await state('new').selectOption('value'); await field('new').selectOption('true');
    await state('transport').selectOption('value'); await field('transport').selectOption('NONE');
    await state('house.year').selectOption('null');
    const pending = page.waitForResponse(response => response.request().method() === 'PATCH');
    await click('#save'); await ok(); const response = await pending;
    assert.deepEqual(JSON.parse(new URL(response.request().url()).searchParams.get('flat')), { new:true,transport:'NONE',house:{year:null} });
    assert.match(await page.locator('#operation-result').textContent(), /Этажей в доме5/);
    assert.match(await page.locator('#operation-result').textContent(), /9007199254740993/);
  });
  await step('HTTP 400/404/414/422 are readable and failed input is retained', async () => {
    await page.locator('#input-mode').selectOption('json');
    for (const [json, status] of [['{"unknown":1}',400],['{"numberOfRooms":9}',422],[JSON.stringify({name:'x'.repeat(24000)}),414]]) {
      await page.locator('#raw-input').fill(json); await click('#save');
      assert.match(await page.locator('#error').textContent(), new RegExp('HTTP ' + status));
      assert.equal(await page.locator('#raw-input').inputValue(), json);
    }
    await page.locator('#lookup-id').fill('9223372036854775807'); await click('#lookup-form button');
    assert.match(await page.locator('#error').textContent(), /HTTP 404/);
  });
  await step('transport NONE differs from null; extrema return full flats', async () => {
    await click('[data-view="collection"]'); await page.locator('#count-transport').selectOption('NONE'); await click('#transport-form button'); await ok();
    assert.match(await page.locator('#operation-result').textContent(), /Количество квартир: 2/);
    await page.locator('#count-transport').selectOption('null'); await click('#transport-form button'); await ok();
    assert.match(await page.locator('#operation-result').textContent(), /Количество квартир: 1/);
    await click('#min-date'); await ok(); await click('#max-new'); await ok();
  });
  await step('nested AND filters and empty selections work with total and page reset', async () => {
    await click('#add-filter'); let row = page.locator('#filters .condition-row').nth(0);
    await row.locator('.field-picker').selectOption('house.numberOfFloors'); await row.locator('.field-value').fill('5');
    await click('#add-filter'); row = page.locator('#filters .condition-row').nth(1);
    await row.locator('.field-picker').selectOption('transport'); await row.locator('.field-state').selectOption('null');
    await click('#query-form button[type=submit]'); await ok(); assert.equal(await page.locator('#total-count').textContent(), '1');
    assert.equal(await page.locator('#flats-body tr').getAttribute('data-id'), third);
    await row.locator('.field-state').selectOption('value'); await row.locator('.field-value').selectOption('NONE');
    await click('#query-form button[type=submit]'); await ok(); assert.equal(await page.locator('#total-count').textContent(), '2');
    await click('#add-filter'); row = page.locator('#filters .condition-row').nth(2); await row.locator('.field-value').fill('missing-' + prefix);
    await click('#query-form button[type=submit]'); await ok(); assert.equal(await page.locator('#empty').isVisible(), true);
    assert.equal(await page.locator('#next-page').isDisabled(), true); assert.equal(await page.locator('#page').inputValue(), '1');
    await click('#reset-query'); await ok();
  });
  await step('multiple sort priorities and pagination preserve the total', async () => {
    await page.locator('#sorts .field-picker').selectOption('price'); await page.locator('#sorts .direction').selectOption('desc'); await click('#add-sort');
    await page.locator('#sorts .field-picker').nth(1).selectOption('area'); await page.locator('#page-size').fill('1');
    await click('#query-form button[type=submit]'); await ok(); assert.equal(await page.locator('#flats-body tr').count(), 1);
    assert.equal(await page.locator('#flats-body tr').getAttribute('data-id'), first); assert.equal(await page.locator('#total-count').textContent(), '3');
    await click('#next-page'); await ok(); assert.equal(await page.locator('#page').inputValue(), '2');
    assert.equal(await page.locator('#flats-body tr').getAttribute('data-id'), second); await click('#previous-page'); await ok();
    await page.locator('#sorts .sort-row').nth(1).getByRole('button',{name:'Выше приоритет'}).click();
    const pending=page.waitForRequest(request=>new URL(request.url()).pathname==='/flats' && request.method()==='GET');
    await click('#query-form button[type=submit]'); await ok();
    assert.deepEqual(new URL((await pending).url()).searchParams.getAll('sort'), ['area:asc','price:desc']);
    assert.equal(await page.locator('#page').inputValue(), '1');
    await page.locator('#page').fill('2147483647'); await click('#page-form button[type=submit]'); await ok(); assert.equal(await page.locator('#empty').isVisible(), true);
    await click('#reset-query'); await ok();
  });
  await step('both agency operations call Payara; same-ID 303 follows back to Jetty through CORS', async () => {
    await click('[data-view="agency"]'); await page.locator('#agency-id1').fill(first); await page.locator('#agency-id2').fill(second);
    await click('#agency-form button'); await ok(); assert.match(await page.locator('#operation-result').textContent(), /9007199254740992\.02/);
    await page.locator('#agency-operation').selectOption('get-most-expensive'); await page.locator('#agency-id3').fill(third);
    await click('#agency-form button'); await ok(); assert.match(await page.locator('#operation-result').textContent(), /9007199254740992\.03/);
    await page.locator('#agency-id2').fill(first); await page.locator('#agency-id3').fill(first);
    await click('#agency-form button'); await ok(); assert.match(await page.locator('#operation-result').textContent(), /Выполнен переход/);
    await page.locator('#agency-operation').selectOption('get-cheapest'); await click('#agency-form button'); await ok();
    assert.match(await page.locator('#operation-result').textContent(), /Выполнен переход/);
  });
  await step('DELETE works from editor and confirmed removal is reflected in collection', async () => {
    await editor(); await page.locator('#editor-mode').selectOption('DELETE'); await page.locator('#editor-id').fill(second);
    page.once('dialog', dialog => dialog.accept()); await click('#save'); await ok();
    assert.match(await page.locator('#operation-result').textContent(), /HTTP 204/); assert.equal(await page.locator('#total-count').textContent(), '2');
  });
  await step('offline errors are readable and a responsive view fits a mobile viewport', async () => {
    await click('[data-view="collection"]'); await context.setOffline(true); await click('#refresh');
    assert.match(await page.locator('#error').textContent(), /Не удалось связаться/); await context.setOffline(false); await click('#refresh'); await ok();
    await mkdir('.runtime/screenshots',{recursive:true}); await page.screenshot({path:'.runtime/screenshots/client-desktop.png',fullPage:true});
    await page.setViewportSize({width:390,height:844});
    await page.screenshot({path:'.runtime/screenshots/client-mobile.png',fullPage:true});
    if(await page.evaluate(()=>document.documentElement.scrollWidth > window.innerWidth))
      console.log('OVERFLOW',await page.evaluate(()=>[...document.querySelectorAll('body *')].map(element=>({name:element.tagName+'#'+element.id+'.'+element.className,right:Math.round(element.getBoundingClientRect().right)})).filter(item=>item.right>innerWidth).slice(0,16)));
    assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth <= window.innerWidth), 'Mobile body must not scroll horizontally');
    await page.setViewportSize({width:1440,height:1000});
  });
  await step('Swagger uses published servers for both specs and executes real HTTPS operations', async () => {
    await page.goto(base + '/docs/index.html');
    await page.waitForFunction(()=>window.ui?.getSystem().specSelectors.specJson().get('servers')?.get(0)?.get('url'));
    assert.equal(await page.evaluate(()=>ui.getSystem().specSelectors.specJson().get('servers').get(0).get('url')),base);
    const list=page.locator('#operations-Flats-listFlats'); await list.locator('.opblock-summary').click();
    const tryList=list.getByRole('button',{name:'Try it out',exact:true}); if(await tryList.isVisible()) await tryList.click();
    const flatsCall=page.waitForResponse(response=>new URL(response.url()).origin===base && new URL(response.url()).pathname==='/flats');
    await list.getByRole('button',{name:'Execute',exact:true}).click(); assert.equal((await flatsCall).status(),200);
    await page.locator('.topbar select').selectOption({label:'Agency Service'});
    await page.waitForFunction(()=>window.ui.getSystem().specSelectors.specJson().get('info')?.get('title')==='Agency Service');
    assert.equal(await page.evaluate(()=>ui.getSystem().specSelectors.specJson().get('servers').get(0).get('url')),agency);
    const operation=page.locator('#operations-Agency-getCheapestFlat'); await operation.locator('.opblock-summary').click();
    const tryIt=operation.getByRole('button',{name:'Try it out',exact:true}); if(await tryIt.isVisible()) await tryIt.click();
    const inputs=operation.locator('.parameters-container input'); await inputs.nth(0).fill(first); await inputs.nth(1).fill(third);
    const agencyCall=page.waitForResponse(response=>new URL(response.url()).origin===agency && new URL(response.url()).pathname.startsWith('/agency/get-cheapest/'));
    await operation.getByRole('button',{name:'Execute',exact:true}).click(); assert.equal((await agencyCall).status(),200);
  });
  const expected = ['GET /flats','POST /flats','GET /flats/{id}','PUT /flats/{id}','PATCH /flats/{id}','DELETE /flats/{id}',
    'GET /flats/min-creation-date','GET /flats/max-new','GET /flats/count-by-transport',
    'GET /agency/get-cheapest/{id1}/{id2}','GET /agency/get-most-expensive/{id1}/{id2}/{id3}'];
  for(const operation of expected) assert.ok(operations.has(operation), 'Missing operation: '+operation);
  assert.deepEqual(errors,[]);console.log('PASS all 11 API operations exercised; no uncaught browser errors');
} finally {
  await context.setOffline(false);
  for(const location of created) {
    try {await page.evaluate(async url=>{await fetch(url,{method:'DELETE',credentials:'omit'});},base+location);} catch {}
  }
  await browser.close();
}
