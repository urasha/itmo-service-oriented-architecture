// JSON numbers retain their wire spelling. No conversion of IDs or prices to JS Number.
const numberPattern = /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?$/;
export class JsonNumber {
  constructor(text) {
    if (typeof text !== 'string' || !numberPattern.test(text)) throw new SyntaxError('Некорректное JSON-число');
    this.text = text;
    Object.freeze(this);
  }
  toString() { return this.text; }
}

export function parse(text) {
  let cursor = 0;
  const fail = () => { throw new SyntaxError(`Некорректный JSON, позиция ${cursor + 1}`); };
  const whitespace = () => { while (/[\t\r\n ]/.test(text[cursor] ?? '\0')) cursor++; };
  function string() {
    const start = cursor++;
    while (cursor < text.length) {
      const char = text[cursor++];
      if (char === '\\') { cursor++; continue; }
      if (char === '"') return JSON.parse(text.slice(start, cursor));
    }
    fail();
  }
  function value() {
    whitespace();
    const char = text[cursor];
    if (char === '"') return string();
    if (char === '{') {
      cursor++; whitespace(); const object = {};
      if (text[cursor] === '}') { cursor++; return object; }
      while (cursor < text.length) {
        whitespace(); if (text[cursor] !== '"') fail();
        const key = string(); whitespace(); if (text[cursor++] !== ':') fail();
        if (Object.hasOwn(object, key)) throw new SyntaxError(`Повтор поля JSON: ${key}`);
        Object.defineProperty(object, key, { value: value(), enumerable: true, writable: true, configurable: true });
        whitespace(); const separator = text[cursor++];
        if (separator === '}') return object;
        if (separator !== ',') fail();
      }
      fail();
    }
    if (char === '[') {
      cursor++; whitespace(); const array = [];
      if (text[cursor] === ']') { cursor++; return array; }
      while (cursor < text.length) {
        array.push(value()); whitespace(); const separator = text[cursor++];
        if (separator === ']') return array;
        if (separator !== ',') fail();
      }
      fail();
    }
    for (const [literal, result] of [['true', true], ['false', false], ['null', null]]) {
      if (text.startsWith(literal, cursor)) { cursor += literal.length; return result; }
    }
    const match = /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/.exec(text.slice(cursor));
    if (!match) fail();
    cursor += match[0].length; return new JsonNumber(match[0]);
  }
  const result = value(); whitespace(); if (cursor !== text.length) fail();
  return result;
}

export function stringify(value, indent = 0) {
  const unit = ' '.repeat(Math.min(Math.max(indent, 0), 8));
  function encode(item, depth) {
    if (item instanceof JsonNumber) return item.text;
    if (item === null || typeof item === 'string' || typeof item === 'boolean') return JSON.stringify(item);
    if (typeof item === 'number' && Number.isFinite(item)) return String(item);
    if (typeof item === 'bigint') return item.toString();
    if (typeof item !== 'object') throw new TypeError('Значение не представимо в JSON');
    const array = Array.isArray(item);
    const parts = array ? item.map(value => encode(value, depth + 1))
      : Object.entries(item).map(([key, value]) => JSON.stringify(key) + (unit ? ': ' : ':') + encode(value, depth + 1));
    const [open, close] = array ? ['[', ']'] : ['{', '}'];
    if (!parts.length) return open + close;
    return unit ? open + '\n' + unit.repeat(depth + 1) + parts.join(',\n' + unit.repeat(depth + 1)) + '\n' + unit.repeat(depth) + close
      : open + parts.join(',') + close;
  }
  return encode(value, 0);
}
