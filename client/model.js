import { JsonNumber, parse } from './json-numbers.js';

export const views = ['YARD', 'NORMAL', 'GOOD', 'TERRIBLE'];
export const transports = ['FEW', 'NONE', 'LITTLE', 'ENOUGH'];
export const fields = [
  { path: 'name', label: 'Название', type: 'string', nonempty: true },
  { path: 'coordinates.x', label: 'Координата X', type: 'number' },
  { path: 'coordinates.y', label: 'Координата Y', type: 'number' },
  { path: 'area', label: 'Площадь, м²', type: 'long' },
  { path: 'price', label: 'Цена, ₽', type: 'number' },
  { path: 'numberOfRooms', label: 'Количество комнат', type: 'int' },
  { path: 'new', label: 'Новостройка', type: 'boolean' },
  { path: 'view', label: 'Вид из окна', type: 'enum', options: views },
  { path: 'transport', label: 'Транспорт', type: 'enum', options: transports, nullable: true },
  { path: 'house.name', label: 'Название дома', type: 'string' },
  { path: 'house.year', label: 'Год постройки', type: 'long', nullable: true },
  { path: 'house.numberOfFloors', label: 'Этажей в доме', type: 'int' },
  { path: 'house.numberOfFlatsOnFloor', label: 'Квартир на этаже', type: 'int', nullable: true },
  { path: 'house.numberOfLifts', label: 'Лифтов', type: 'int', nullable: true }
];
export const queryFields = [
  { path: 'id', label: 'ID', type: 'long' }, ...fields,
  { path: 'creationDate', label: 'Дата создания', type: 'date' },
  { path: 'coordinates', label: 'Координаты целиком', type: 'object' },
  { path: 'house', label: 'Дом целиком', type: 'object' }
];

export function readPath(object, path) { return path.split('.').reduce((value, key) => value?.[key], object); }
export function setPath(object, path, value) {
  const keys = path.split('.'); let current = object;
  for (const key of keys.slice(0, -1)) current = current[key] ??= {};
  current[keys.at(-1)] = value;
}
export function fieldValue(field, text) {
  if (field.type === 'string') {
    if (field.nonempty && text.length === 0) throw new Error(`${field.label}: строка не должна быть пустой`);
    return text;
  }
  if (field.type === 'boolean') {
    if (!['true', 'false'].includes(text)) throw new Error(`${field.label}: выберите значение`);
    return text === 'true';
  }
  if (field.type === 'enum') {
    if (!field.options.includes(text)) throw new Error(`${field.label}: выберите значение`);
    return text;
  }
  if (field.type === 'date') {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) throw new Error('Дата должна иметь формат YYYY-MM-DD');
    return text;
  }
  if (field.type === 'object') {
    const value = parse(text);
    if (!value || Array.isArray(value) || value instanceof JsonNumber || typeof value !== 'object') throw new Error(`${field.label}: нужен JSON-объект`);
    return value;
  }
  const value = new JsonNumber(text.trim());
  if (['long', 'int'].includes(field.type)) {
    if (!/^-?(?:0|[1-9][0-9]*)$/.test(value.text)) throw new Error(`${field.label}: нужно целое число`);
    const number = BigInt(value.text);
    const limit = field.type === 'long' ? 9223372036854775807n : 2147483647n;
    if (number > limit || number < -limit - 1n) throw new Error(`${field.label}: число вне диапазона ${field.type}`);
  }
  return value;
}
export function positiveId(text) {
  if (!/^[0-9]+$/.test(text) || BigInt(text) < 1n || BigInt(text) > 9223372036854775807n)
    throw new Error('ID должен быть целым числом от 1 до 9223372036854775807');
  return BigInt(text).toString();
}
export function pageNumber(text) {
  if (!/^[0-9]+$/.test(text) || BigInt(text) < 1n || BigInt(text) > 2147483647n)
    throw new Error('Страница и размер должны быть целыми числами от 1 до 2147483647');
  return Number(text);
}
export function editorObject(rows, patch) {
  const result = {};
  for (const { field, state, text } of rows) {
    if (state === 'omit') continue;
    if (state === 'null' && !field.nullable) throw new Error(`${field.label}: null недопустим`);
    setPath(result, field.path, state === 'null' ? null : fieldValue(field, text));
  }
  if (patch && Object.keys(result).length === 0) throw new Error('Выберите хотя бы одно изменяемое поле');
  return result;
}
export function filterObject(rows) {
  const result = {};
  function merge(target, source) {
    for (const [key, value] of Object.entries(source)) {
      if (!Object.hasOwn(target, key)) Object.defineProperty(target, key, { value, enumerable: true, writable: true, configurable: true });
      else if (target[key] && value && typeof target[key] === 'object' && typeof value === 'object'
          && !(target[key] instanceof JsonNumber) && !(value instanceof JsonNumber)) merge(target[key], value);
      else throw new Error(`Фильтры повторяют условие ${key}`);
    }
  }
  for (const { field, state, text } of rows) {
    const part = {};
    if (state === 'null' && !field.nullable) throw new Error(`${field.label}: null недопустим`);
    setPath(part, field.path, state === 'null' ? null : fieldValue(field, text)); merge(result, part);
  }
  return result;
}
