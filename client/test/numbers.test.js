import { test } from 'node:test';
import assert from 'node:assert/strict';
import { JsonNumber, parse, stringify } from '../json-numbers.js';
import { fields, queryFields, editorObject, filterObject, positiveId, pageNumber } from '../model.js';

test('IDs, area, prices and exponent coordinates survive a JSON round trip exactly', () => {
  const json = '{"id":9223372036854775807,"area":9007199254740993,"price":9007199254740992.01,"coordinates":{"x":1.25e-30,"y":-1E+200},"new":false,"transport":null}';
  const value = parse(json);
  assert.equal(value.id.text, '9223372036854775807'); assert.equal(value.price.text, '9007199254740992.01');
  assert.equal(stringify(value), json); assert.equal(stringify(parse(stringify(value, 2))), json);
});
test('digit-like strings, escapes and marker-like strings remain strings', () => {
  const input = { text: '12345 \\ " & + # \n Ж 🏠', stringNumber: '9007199254740993', marker: '__NUMBER_1__' };
  assert.deepEqual(parse(JSON.stringify(input)), input);
});
test('null, false, zero and empty containers remain distinct', () => {
  const value = parse('[null,false,0,-0,[],{},true]');
  assert.equal(value[0], null); assert.equal(value[1], false); assert.equal(value[2].text, '0');
  assert.equal(stringify(value), '[null,false,0,-0,[],{},true]');
});
for (const invalid of ['', '[1,]', '{"x":1,}', '{"x":1,"x":2}', '01', '+1', '1.', '1e', 'NaN', 'Infinity', 'true false', '"unclosed', '"bad\nstring"', '{x:1}', '[,1]']) {
  test(`rejects malformed JSON ${JSON.stringify(invalid)}`, () => assert.throws(() => parse(invalid), SyntaxError));
}
test('prototype-named JSON properties are preserved without prototype mutation', () => {
  const value = parse('{"__proto__":{"polluted":true},"constructor":1}');
  assert.equal(Object.getPrototypeOf(value), Object.prototype); assert.equal({}.polluted, undefined);
  assert.equal(stringify(value), '{"__proto__":{"polluted":true},"constructor":1}');
});
test('numeric wrapper rejects invalid lexemes before serialization', () => {
  for (const invalid of ['NaN','1,2','01','+1','',1]) assert.throws(() => new JsonNumber(invalid));
  assert.throws(() => stringify({ value: undefined })); assert.throws(() => stringify(NaN));
});
test('all scalar and whole-object filter/sort paths are offered', () => {
  assert.deepEqual(new Set(queryFields.map(field => field.path)), new Set(['id','creationDate','name','coordinates','coordinates.x','coordinates.y','area','price','numberOfRooms','new','view','transport','house','house.name','house.year','house.numberOfFloors','house.numberOfFlatsOnFloor','house.numberOfLifts']));
});
test('PATCH distinguishes omission, null and false and builds nested objects', () => {
  const row = (path, state, text) => ({ field: fields.find(field => field.path === path), state, text });
  const patch = editorObject([row('name','omit','old'),row('new','value','false'),row('transport','null','NONE'),row('house.year','value','9007199254740993')], true);
  assert.equal(stringify(patch), '{"new":false,"transport":null,"house":{"year":9007199254740993}}');
  assert.throws(() => editorObject([row('name','omit','')], true), /хотя бы одно/);
  assert.throws(() => editorObject([row('name','null','')], true), /null/);
});
test('filter builder merges nested conditions, preserves null/NONE and rejects conflicting rows', () => {
  const row = (path, state, text) => ({ field: queryFields.find(field => field.path === path), state, text });
  assert.equal(stringify(filterObject([row('transport','null',''),row('house','value','{"year":1900}'),row('house.name','value','Дом + & #') ])), '{"transport":null,"house":{"year":1900,"name":"Дом + & #"}}');
  assert.equal(stringify(filterObject([row('transport','value','NONE')])), '{"transport":"NONE"}');
  assert.throws(() => filterObject([row('area','value','1'),row('area','value','2')]), /повторяют/);
  assert.throws(() => filterObject([row('house','value','{"year":1900}'),row('house.year','value','1901')]), /повторяют/);
  const value = filterObject([row('house.name','value',''),row('house','value','{"__proto__":{"polluted":true}}')]);
  assert.equal({}.polluted, undefined); assert.ok(stringify(value).includes('"__proto__"'));
});
test('positive IDs retain full int64 range while paging uses the int32 range', () => {
  assert.equal(positiveId('09223372036854775807'), '9223372036854775807');
  for (const invalid of ['0','-1','1.0','1e2','9223372036854775808','']) assert.throws(() => positiveId(invalid));
  assert.equal(pageNumber('2147483647'), 2147483647); assert.throws(() => pageNumber('2147483648'));
});
