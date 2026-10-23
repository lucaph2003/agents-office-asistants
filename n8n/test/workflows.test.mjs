// Runs the Code nodes of the exported workflows exactly as stored, with n8n's globals stubbed.
// Usage: node --test n8n/test/*.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const load = (file) => JSON.parse(readFileSync(new URL(`../${file}`, import.meta.url), 'utf8'));
const intake = load('telegram-task-intake.json');
const notification = load('task-completed-notification.json');

function codeNode(workflow, name) {
  const node = workflow.nodes.find((candidate) => candidate.name === name);
  assert.ok(node, `node "${name}" exists`);
  return new Function('$json', '$env', '$', '$input', node.parameters.jsCode);
}

test('the API call always sends its JSON body', () => {
  // n8n does not evaluate expressions in "sendBody": an expression there silently sends no body.
  const http = intake.nodes.find((node) => node.name === 'Call Agent Office');
  assert.equal(http.parameters.sendBody, true);
  assert.equal(http.parameters.specifyBody, 'json');
});

const parse = codeNode(intake, 'Parse command');
const format = codeNode(intake, 'Format reply');
const notify = codeNode(notification, 'Format notification');

const ENV = { TELEGRAM_ALLOWED_CHAT_IDS: '111, 222', TELEGRAM_DEFAULT_CHAT_ID: '111' };
const telegram = (text, chatId = 111) => ({ update_id: 9001, message: { chat: { id: chatId }, text } });
const run = (text, chatId) => parse(telegram(text, chatId), ENV).json;
const reply = (parsed, response) => format(response, ENV, () => ({ item: { json: parsed } })).json;

const TASK_ID = '4f0c2a1e-1234-4abc-8def-000000000001';
const TASK = { id: TASK_ID, title: 'Java <21> plan', status: 'INBOX', assignedAgent: null, priority: 'MEDIUM',
  attempt: 0, boardUrl: 'https://trello.com/c/abc' };

test('/task becomes a POST with the chat as external reference', () => {
  const parsed = run('/task Prepare a technical plan to migrate service X to Java 21\nwith details');
  assert.equal(parsed.needsApi, true);
  assert.equal(parsed.method, 'POST');
  assert.equal(parsed.path, '/api/v1/tasks');
  assert.equal(parsed.correlationId, 'telegram-9001');
  assert.deepEqual(parsed.body, {
    title: 'Prepare a technical plan to migrate service X to Java 21',
    description: 'Prepare a technical plan to migrate service X to Java 21\nwith details',
    source: 'TELEGRAM',
    externalReference: '111',
  });
});

test('long first lines are shortened into a title', () => {
  const parsed = run(`/task ${'x'.repeat(200)}`);
  assert.equal(parsed.body.title.length, 80);
  assert.ok(parsed.body.title.endsWith('...'));
});

test('commands addressed to the bot by name still work', () => {
  assert.equal(run('/tasks@AgentOfficeBot').command, 'tasks');
});

test('/tasks lists only this chat', () => {
  const parsed = run('/tasks', 222);
  assert.equal(parsed.method, 'GET');
  assert.equal(parsed.path, '/api/v1/tasks?source=TELEGRAM&externalReference=222&size=10');
});

test('/tasks from the owner chat also lists tasks created from the Trello inbox', () => {
  assert.equal(run('/tasks', 111).path, '/api/v1/tasks?size=10');
});

test('/status, /cancel and /retry need a task id', () => {
  assert.equal(run(`/status ${TASK_ID}`).path, `/api/v1/tasks/${TASK_ID}`);
  assert.deepEqual([run(`/cancel ${TASK_ID}`).method, run(`/cancel ${TASK_ID}`).path],
    ['POST', `/api/v1/tasks/${TASK_ID}/cancel`]);
  assert.equal(run(`/retry ${TASK_ID}`).path, `/api/v1/tasks/${TASK_ID}/execute`);
  const invalid = run('/status 42');
  assert.equal(invalid.needsApi, false);
  assert.match(invalid.text, /Usage: \/status/);
});

test('/start, /help, unknown commands and plain text show the help', () => {
  for (const text of ['/start', '/help', '/dance', 'hello there']) {
    const parsed = run(text);
    assert.equal(parsed.needsApi, false, text);
    assert.match(parsed.text, /\/task &lt;description&gt;/, text);
  }
  assert.match(run('/task').text, /Usage: \/task/);
});

test('chats outside the allow list are refused, and an empty list refuses everyone', () => {
  assert.match(run('/tasks', 333).text, /not allowed/);
  assert.match(run('/tasks', 333).text, /add <code>333<\/code> to TELEGRAM_ALLOWED_CHAT_IDS/);
  assert.match(parse(telegram('/tasks'), {}).json.text, /not allowed/);
});

test('a created task is confirmed with its id and board link, escaping HTML', () => {
  const text = reply(run('/task plan'), { statusCode: 201, body: TASK }).text;
  assert.match(text, /Task created/);
  assert.match(text, new RegExp(`<code>${TASK_ID}</code>`));
  assert.match(text, /Assigned: Manager is routing/);
  assert.match(text, /Trello: https:\/\/trello.com\/c\/abc/);
  assert.equal(reply(run(`/status ${TASK_ID}`), { statusCode: 200, body: TASK }).text.includes('Java &lt;21&gt; plan'), true);
});

test('task lists and empty lists read well', () => {
  const list = reply(run('/tasks'), { statusCode: 200, body: { content: [TASK] } }).text;
  assert.match(list, /• INBOX — Java &lt;21&gt; plan/);
  assert.match(reply(run('/tasks'), { statusCode: 200, body: { content: [] } }).text, /No tasks yet/);
});

test('API problems become readable messages', () => {
  const status = run(`/status ${TASK_ID}`);
  assert.match(reply(status, { statusCode: 404, body: {} }).text, /Task not found/);
  assert.match(reply(status, { statusCode: 401, body: {} }).text, /not authorized/);
  assert.match(reply(run(`/cancel ${TASK_ID}`), { statusCode: 409, body: { message: 'Task cannot move from DONE to CANCELLED' } }).text,
    /cannot move from DONE/);
  assert.match(reply(status, { error: { message: 'ECONNREFUSED' } }).text, /not reachable/);
});

const notificationFor = (body) => notify(undefined, ENV, undefined, { all: () => [{ json: { body } }] });
const COMPLETED = { event: 'TASK_COMPLETED', taskId: TASK_ID, title: 'Java 21 plan', status: 'DONE', agent: 'DEVELOPER',
  review: 'APPROVED', summary: 'Three phases & a rollback', boardUrl: 'https://trello.com/c/abc',
  source: 'TELEGRAM', externalReference: '111' };

test('completed tasks are reported with agent, review and summary', () => {
  const [message] = notificationFor(COMPLETED);
  assert.equal(message.json.chatId, '111');
  assert.match(message.json.text, /Task completed/);
  assert.match(message.json.text, /Agent: DEVELOPER\nReview: APPROVED/);
  assert.match(message.json.text, /Three phases &amp; a rollback/);
  assert.match(message.json.text, /Trello: https:\/\/trello.com\/c\/abc/);
});

test('blocked tasks explain why and how to resume', () => {
  const [message] = notificationFor({ ...COMPLETED, event: 'TASK_BLOCKED', reason: 'OLLAMA_UNAVAILABLE: down' });
  assert.match(message.json.text, /Reason: OLLAMA_UNAVAILABLE: down/);
  assert.match(message.json.text, new RegExp(`/retry ${TASK_ID}`));
});

test('tasks from the Trello inbox are reported to the owner chat', () => {
  const [message] = notificationFor({ ...COMPLETED, source: 'TRELLO', externalReference: 'trello-card-id' });
  assert.equal(message.json.chatId, '111');
  assert.match(message.json.text, /Trello: https:\/\/trello.com\/c\/abc/);
});

test('API tasks, and Trello tasks without an owner chat, send nothing', () => {
  assert.deepEqual(notificationFor({ ...COMPLETED, source: 'API', externalReference: null }), []);
  const noOwner = notify(undefined, {}, undefined, { all: () => [{ json: { body: { ...COMPLETED, source: 'TRELLO' } } }] });
  assert.deepEqual(noOwner, []);
});

test('the Trello inbox is polled every minute through the sync endpoint', () => {
  const sync = load('trello-sync.json');
  const trigger = sync.nodes.find((node) => node.type === 'n8n-nodes-base.scheduleTrigger');
  const http = sync.nodes.find((node) => node.type === 'n8n-nodes-base.httpRequest');
  assert.deepEqual(trigger.parameters.rule.interval, [{ field: 'minutes', minutesInterval: 1 }]);
  assert.equal(http.parameters.method, 'POST');
  assert.match(http.parameters.url, /\/api\/v1\/board\/sync$/);
  assert.equal(http.credentials.httpHeaderAuth.id, 'agent-office-api-key');
});
