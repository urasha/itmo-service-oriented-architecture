import { JsonNumber, parse, stringify } from './json-numbers.js';
import { fields, queryFields, readPath, editorObject, filterObject, positiveId, pageNumber } from './model.js';
import { createApi } from './api.js';

const $ = id => document.getElementById(id);
const node = (tag, text = '', className = '') => {
  const element = document.createElement(tag); element.textContent = text; element.className = className; return element;
};
const text = value => value === null ? 'Не указан (null)' : value instanceof JsonNumber ? value.text
  : typeof value === 'boolean' ? (value ? 'Да (true)' : 'Нет (false)') : String(value ?? '—');
function option(select, value, label = value) { const item = node('option', label); item.value = value; select.append(item); }
function button(label, action, className = 'secondary') {
  const element = node('button', label, className); element.type = 'button'; element.addEventListener('click', action); return element;
}
let api;
let busy = false;
const selection = { page: 1, size: 20, total: null, parameters: [] };

function displayError(error) {
  $('status').hidden = true; $('error').hidden = false;
  $('error').textContent = (error.status ? `HTTP ${error.status} · ` : '') + error.message
    + (error.timestamp ? `\nВремя сервиса (UTC): ${error.timestamp}` : '');
}
async function run(message, action) {
  if (busy) return;
  busy = true; $('error').hidden = true; $('status').hidden = false; $('status').textContent = 'Запрос выполняется…';
  document.querySelectorAll('button').forEach(element => { element.disabled = true; });
  document.body.setAttribute('aria-busy', 'true');
  try { const result = await action(); $('status').textContent = result || message; }
  catch (error) { displayError(error); }
  finally {
    busy = false; document.body.removeAttribute('aria-busy');
    document.querySelectorAll('button').forEach(element => { element.disabled = false; }); updatePagination();
  }
}
function form(id, message, action) {
  $(id).addEventListener('submit', event => { event.preventDefault(); run(message, action); });
}
function showView(name) {
  document.querySelectorAll('[data-panel]').forEach(element => { element.hidden = element.dataset.panel !== name; });
  document.querySelectorAll('[data-view]').forEach(element => {
    if (element.dataset.view === name) element.setAttribute('aria-current', 'page'); else element.removeAttribute('aria-current');
  });
}
document.querySelectorAll('[data-view]').forEach(element => element.addEventListener('click', () => showView(element.dataset.view)));
$('close-result').addEventListener('click', () => { $('result-panel').hidden = true; });

function flatDetails(flat) {
  const list = node('dl');
  for (const field of [{ path: 'id', label: 'ID' }, { path: 'creationDate', label: 'Дата создания' }, ...fields]) {
    list.append(node('dt', field.label), node('dd', text(readPath(flat, field.path))));
  }
  return list;
}
function showResult(title, response, emptyMessage = 'Коллекция пуста.') {
  $('result-panel').hidden = false; $('result-title').textContent = title;
  const target = $('operation-result'); target.replaceChildren();
  target.append(node('p', `HTTP ${response.status}` + (response.redirected ? ' · Выполнен переход к первому сервису' : ''), 'muted'));
  if (response.data === null) target.append(node('p', response.status === 204 ? emptyMessage : 'Операция выполнена.'));
  else if (Object.hasOwn(response.data, 'count')) target.append(node('p', `Количество квартир: ${text(response.data.count)}`));
  else {
    target.append(flatDetails(response.data));
    const details = node('details'); details.append(node('summary', 'Посмотреть JSON'), node('pre', stringify(response.data, 2))); target.append(details);
  }
}
function fieldInput(field, id) {
  let input;
  if (field.type === 'boolean' || field.type === 'enum') {
    input = node('select');
    if (field.type === 'boolean') { option(input, 'false', 'Нет (false)'); option(input, 'true', 'Да (true)'); }
    else for (const value of field.options) option(input, value);
  } else if (field.type === 'object') { input = node('textarea'); input.rows = 2; input.placeholder = '{}'; }
  else { input = node('input'); input.type = field.type === 'date' ? 'date' : 'text';
    if (['long', 'int'].includes(field.type)) input.inputMode = 'numeric';
    if (field.type === 'number') input.inputMode = 'decimal';
  }
  input.id = id; return input;
}

function addFilter(initial = 'name') {
  const row = node('div', '', 'condition-row'); const picker = node('select'); picker.className = 'field-picker';
  picker.setAttribute('aria-label', 'Поле фильтра');
  for (const field of queryFields) option(picker, field.path, `${field.label} · ${field.path}`);
  picker.value = initial;
  const state = node('select'); state.className = 'field-state'; state.setAttribute('aria-label', 'Условие фильтра');
  const valueContainer = node('span'); valueContainer.style.display = 'contents';
  function changeField() {
    const field = queryFields.find(field => field.path === picker.value);
    state.replaceChildren(); option(state, 'value', 'Равно'); if (field.nullable) option(state, 'null', 'Не указан (null)');
    const input = fieldInput(field, 'filter-' + crypto.randomUUID()); input.classList.add('field-value');
    input.setAttribute('aria-label', `Значение фильтра ${field.path}`); valueContainer.replaceChildren(input);
    state.onchange = () => { input.disabled = state.value === 'null'; }; state.onchange();
  }
  picker.addEventListener('change', changeField); changeField();
  row.append(picker, state, valueContainer, button('×', () => row.remove(), 'quiet remove'));
  row.lastChild.setAttribute('aria-label', 'Удалить фильтр'); $('filters').append(row);
}
function addSort(initial) {
  const used = new Set([...$('sorts').querySelectorAll('.field-picker')].map(input => input.value));
  const field = initial || queryFields.find(field => !used.has(field.path))?.path;
  if (!field) return;
  const row = node('div', '', 'sort-row'); const picker = node('select'); picker.className = 'field-picker'; picker.setAttribute('aria-label', 'Поле сортировки');
  for (const field of queryFields) option(picker, field.path, `${field.label} · ${field.path}`);
  picker.value = field;
  const direction = node('select'); direction.className = 'direction'; direction.setAttribute('aria-label', 'Направление сортировки');
  option(direction, 'asc', '↑ По возрастанию'); option(direction, 'desc', '↓ По убыванию');
  const up = button('↑', () => { if (row.previousElementSibling) row.parentNode.insertBefore(row, row.previousElementSibling); }); up.setAttribute('aria-label', 'Выше приоритет');
  const down = button('↓', () => { if (row.nextElementSibling) row.parentNode.insertBefore(row.nextElementSibling, row); }); down.setAttribute('aria-label', 'Ниже приоритет');
  const remove = button('×', () => row.remove(), 'quiet remove'); remove.setAttribute('aria-label', 'Удалить сортировку');
  row.append(picker, direction, up, down, remove); $('sorts').append(row);
}
function readQuery() {
  const filter = filterObject([...$('filters').children].map(row => ({
    field: queryFields.find(field => field.path === row.querySelector('.field-picker').value),
    state: row.querySelector('.field-state').value, text: row.querySelector('.field-value').value
  })));
  const parameters = []; if (Object.keys(filter).length) parameters.push(['filter', stringify(filter)]);
  const seen = new Set();
  for (const row of $('sorts').children) {
    const path = row.querySelector('.field-picker').value;
    if (seen.has(path)) throw new Error(`Поле сортировки повторяется: ${path}`);
    seen.add(path); parameters.push(['sort', `${path}:${row.querySelector('.direction').value}`]);
  }
  return { parameters, size: pageNumber($('page-size').value) };
}
function updatePagination() {
  $('previous-page').disabled = busy || selection.page <= 1;
  $('next-page').disabled = busy || selection.total === null || BigInt(selection.page) * BigInt(selection.size) >= selection.total;
}
function renderFlats(flats) {
  if (!Array.isArray(flats)) throw new Error('Сервис вернул не массив квартир');
  $('flats-body').replaceChildren(); $('empty').hidden = flats.length !== 0;
  for (const flat of flats) {
    const row = node('tr'); row.dataset.id = String(flat.id);
    const name = node('td'); name.append(node('span', `#${flat.id}`, 'id'), node('span', flat.name, 'name')); row.append(name);
    row.append(node('td', text(flat.price), 'price'), node('td', text(flat.area) + ' м²'), node('td', text(flat.numberOfRooms)), node('td', text(flat.new)), node('td', text(flat.transport)));
    const more = node('td'); const details = node('details'); details.append(node('summary', 'Подробнее'), flatDetails(flat)); more.append(details); row.append(more);
    const actions = node('td'); const controls = node('div', '', 'row-actions');
    controls.append(button('Изменить', () => run('Квартира загружена · HTTP 200', async () => {
      const response = await api.request('flats', 'GET', '/flats/' + positiveId(String(flat.id)));
      fillEditor(response.data); showView('editor'); showResult('Квартира для редактирования', response);
    })), button('Удалить', () => run('Квартира удалена · HTTP 204', async () => {
      if (!window.confirm(`Удалить квартиру #${flat.id} «${flat.name}»?`)) return 'Удаление отменено';
      await api.request('flats', 'DELETE', '/flats/' + positiveId(String(flat.id))); await loadList(selection.page);
    }), 'quiet'));
    actions.append(controls); row.append(actions); $('flats-body').append(row);
  }
}
async function loadList(page) {
  const response = await api.request('flats', 'GET', '/flats', [...selection.parameters, ['page', String(page)], ['size', String(selection.size)]]);
  if (!/^\d+$/.test(response.total ?? '')) throw new Error('Сервис не передал корректный X-Total-Count');
  renderFlats(response.data); selection.page = page; selection.total = BigInt(response.total); $('page').value = String(page);
  $('total-count').textContent = response.total;
  const pages = (selection.total + BigInt(selection.size) - 1n) / BigInt(selection.size);
  $('page-summary').textContent = `Страница ${page} · Всего страниц: ${pages} · Квартир: ${response.total}`;
}
$('add-filter').addEventListener('click', () => addFilter());
$('add-sort').addEventListener('click', () => addSort());
$('reset-query').addEventListener('click', () => {
  $('filters').replaceChildren(); $('sorts').replaceChildren(); addSort('id'); $('page-size').value = '20';
  run('Выборка сброшена · HTTP 200', async () => { Object.assign(selection, readQuery()); await loadList(1); });
});
form('query-form', 'Коллекция загружена · HTTP 200', async () => { Object.assign(selection, readQuery()); await loadList(1); });
form('page-form', 'Страница загружена · HTTP 200', () => loadList(pageNumber($('page').value)));
$('refresh').addEventListener('click', () => run('Коллекция обновлена · HTTP 200', () => loadList(selection.page)));
$('previous-page').addEventListener('click', () => run('Страница загружена · HTTP 200', () => loadList(Math.max(1, selection.page - 1))));
$('next-page').addEventListener('click', () => run('Страница загружена · HTTP 200', () => loadList(selection.page + 1)));

function editorRows() {
  return [...$('editor-fields').children].map(row => ({ field: fields.find(field => field.path === row.dataset.path),
    state: row.querySelector('.field-state')?.value || 'value', text: row.querySelector('.field-value').value }));
}
function buildEditor(previous = {}) {
  const patch = $('editor-mode').value === 'PATCH'; $('editor-fields').replaceChildren();
  for (const field of fields) {
    const id = 'field-' + field.path.replaceAll('.', '-');
    const row = node('div', '', 'field'); row.dataset.path = field.path;
    const heading = node('div', '', 'field-heading'); const label = node('label', `${field.label} · ${field.path}`); label.htmlFor = id; heading.append(label);
    const input = fieldInput(field, id); input.className = 'field-value';
    if (previous[field.path] !== undefined && previous[field.path] !== null) input.value = String(previous[field.path]);
    if (patch || field.nullable) {
      const state = node('select'); state.className = 'field-state'; state.setAttribute('aria-label', `Действие ${field.path}`);
      option(state, 'omit', patch ? 'Не менять' : 'Не передавать'); option(state, 'value', 'Задать значение');
      if (field.nullable) option(state, 'null', 'Задать null');
      state.value = patch ? 'omit' : previous[field.path] === null ? 'null' : previous[field.path] !== undefined ? 'value' : field.path === 'transport' ? 'null' : 'omit';
      state.onchange = () => { input.disabled = state.value !== 'value'; }; state.onchange(); heading.append(state);
    }
    row.append(heading, input); $('editor-fields').append(row);
  }
}
function updateEditor() {
  const method = $('editor-mode').value; const deleting = method === 'DELETE'; const raw = $('input-mode').value === 'json';
  $('editor-id-label').hidden = method === 'POST'; $('editor-id').required = method !== 'POST';
  $('input-mode-label').hidden = deleting; $('editor-fields').hidden = deleting || raw;
  $('raw-input-label').hidden = deleting || !raw; $('delete-help').hidden = !deleting;
  $('save').textContent = { POST: 'Создать квартиру', PUT: 'Заменить квартиру', PATCH: 'Сохранить изменения', DELETE: 'Удалить квартиру' }[method];
  $('editor-help').textContent = method === 'PATCH' ? 'Передаются только выбранные поля. Для nullable-полей отдельно доступно значение null.'
    : method === 'PUT' ? 'Полная замена: заполните все обязательные поля. Непереданные необязательные поля сбросятся в null.'
      : method === 'POST' ? 'Заполните обязательные поля. Для nullable-полей доступны значение, null и пропуск.' : 'Укажите ID удаляемой квартиры.';
}
function fillEditor(flat) {
  if ($('editor-mode').value === 'POST') $('editor-mode').value = 'PUT';
  $('editor-id').value = String(flat.id); $('lookup-id').value = String(flat.id);
  const previous = Object.fromEntries(fields.map(field => [field.path, readPath(flat, field.path)]));
  buildEditor(previous); updateEditor();
  const input = { ...flat }; delete input.id; delete input.creationDate; $('raw-input').value = stringify(input, 2);
}
$('editor-mode').addEventListener('change', () => {
  const previous = Object.fromEntries(editorRows().map(row => [row.field.path, row.state === 'null' ? null
    : row.state === 'omit' && row.field.nullable && row.text === '' ? undefined : row.text])); buildEditor(previous); updateEditor();
});
$('input-mode').addEventListener('change', updateEditor);
$('clear-editor').addEventListener('click', () => { $('editor-id').value = ''; $('raw-input').value = ''; buildEditor(); updateEditor(); });
form('lookup-form', 'Квартира загружена · HTTP 200', async () => {
  const response = await api.request('flats', 'GET', '/flats/' + positiveId($('lookup-id').value)); fillEditor(response.data); showResult('Квартира по ID', response);
});
form('editor-form', 'Изменение выполнено', async () => {
  const method = $('editor-mode').value; const id = method === 'POST' ? null : positiveId($('editor-id').value);
  let response;
  if (method === 'DELETE') {
    if (!window.confirm(`Удалить квартиру #${id}?`)) return 'Удаление отменено';
    response = await api.request('flats', method, '/flats/' + id);
  } else {
    const value = $('input-mode').value === 'json' ? parse($('raw-input').value) : editorObject(editorRows(), method === 'PATCH');
    response = method === 'POST' ? await api.create(value) : await api.request('flats', method, '/flats/' + id, [['flat', stringify(value)]]);
    if (method === 'POST') $('editor-id').value = String(response.data.id);
  }
  showResult({ POST: 'Квартира создана', PUT: 'Квартира заменена', PATCH: 'Квартира обновлена', DELETE: 'Квартира удалена' }[method], response, 'Квартира удалена.');
  await loadList(selection.page); return `${$('result-title').textContent} · HTTP ${response.status}`;
});
for (const [id, path, title] of [['min-date', 'min-creation-date', 'Минимальная дата создания'], ['max-new', 'max-new', 'Максимальное значение new']]) {
  $(id).addEventListener('click', () => run('Операция выполнена', async () => {
    const response = await api.request('flats', 'GET', '/flats/' + path); showResult(title, response); return `${title} · HTTP ${response.status}`;
  }));
}
form('transport-form', 'Количество подсчитано · HTTP 200', async () => {
  const value = $('count-transport').value;
  showResult('Количество квартир по транспорту', await api.request('flats', 'GET', '/flats/count-by-transport', [['transport', stringify(value === 'null' ? null : value)]]));
});
$('agency-operation').addEventListener('change', () => {
  const three = $('agency-operation').value === 'get-most-expensive'; $('third-id-label').hidden = !three; $('agency-id3').required = three;
});
form('agency-form', 'Сравнение выполнено', async () => {
  const operation = $('agency-operation').value;
  const ids = [positiveId($('agency-id1').value), positiveId($('agency-id2').value)];
  if (operation === 'get-most-expensive') ids.push(positiveId($('agency-id3').value));
  const response = await api.request('agency', 'GET', '/agency/' + operation + '/' + ids.join('/'));
  showResult(operation === 'get-cheapest' ? 'Более дешёвая квартира' : 'Самая дорогая квартира', response);
  return `Сравнение выполнено · HTTP ${response.status}` + (response.redirected ? ' · Переход к первому сервису' : '');
});

addSort('id'); buildEditor(); updateEditor(); updatePagination();
try {
  api = createApi(window.SOA_CONFIG || {}, window.location.origin);
  $('service-addresses').textContent = `Коллекция: ${api.flats} · Агентство: ${api.agency || 'не настроено'}`;
  if (api.agency) $('agency-link').href = api.agency + '/agency/get-cheapest/1/1'; else $('agency-link').hidden = true;
  run('Коллекция загружена · HTTP 200', () => loadList(1));
} catch (error) {
  displayError(error); document.querySelectorAll('form button').forEach(button => { button.disabled = true; });
}
