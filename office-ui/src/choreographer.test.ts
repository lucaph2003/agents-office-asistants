import { describe as suite, expect, it } from 'vitest';
import type { AgentType, OfficeEvent, TaskEventType } from './api';
import { Choreographer, describe } from './choreographer';

let sequence = 0;

function event(type: TaskEventType, agent: AgentType | null = null, details: string | null = null, taskId = 't1'): OfficeEvent {
  sequence += 1;
  return { id: `e${sequence}`, taskId, type, agent, actor: agent ? `agent:${agent.toLowerCase()}` : 'system', details, createdAt: '2026-10-24T12:00:00Z' };
}

suite('Choreographer', () => {
  it('walks a request through the whole office on the happy path', () => {
    const office = new Choreographer();
    const steps = [
      event('TASK_CREATED', null, null),
      event('EXECUTION_STARTED', 'MANAGER', 'attempt=1 model=qwen3:8b prompt=v1'),
      event('EXECUTION_COMPLETED', 'MANAGER', 'Needs code'),
      event('TASK_ASSIGNED', 'MANAGER', 'Needs code'),
      event('EXECUTION_STARTED', 'DEVELOPER', 'attempt=1 model=qwen3:8b prompt=v1'),
      event('EXECUTION_COMPLETED', 'DEVELOPER', 'Three-phase plan'),
      event('REVIEW_STARTED', null, 'attempt=1'),
      event('EXECUTION_STARTED', 'REVIEWER', 'attempt=1 model=qwen3:8b prompt=v1'),
      event('EXECUTION_COMPLETED', 'REVIEWER', 'Looks good'),
      event('REVIEW_APPROVED', 'REVIEWER', 'Looks good'),
      event('TASK_COMPLETED', null, 'agent=DEVELOPER attempts=1'),
      event('USER_NOTIFIED', null, null),
    ].flatMap((e) => office.onEvent(e));

    expect(steps.map((step) => ('to' in step ? `walk:${step.to}` : step.do))).toEqual([
      'spawn',
      'walk:manager', 'work', 'done', 'stamp',
      'walk:lab', 'work', 'done',
      'walk:review', 'work', 'done', 'stamp',
      'walk:archive', 'vanish', 'score',
      'bird',
    ]);
    expect(steps[2]).toEqual({ do: 'work', taskId: 't1', agent: 'MANAGER', text: 'qwen3:8b thinking...' });
  });

  it('sends work back to the same worker after changes are requested', () => {
    const office = new Choreographer();
    office.onEvent(event('EXECUTION_STARTED', 'RESEARCH', 'attempt=1 model=m'));
    office.onEvent(event('REVIEW_STARTED'));
    expect(office.onEvent(event('CHANGES_REQUESTED', 'REVIEWER', 'Add sources'))).toEqual([
      { do: 'stamp', taskId: 't1', ok: false, text: 'Needs changes' },
    ]);

    expect(office.onEvent(event('EXECUTION_STARTED', 'RESEARCH', 'attempt=2 model=m'))).toEqual([
      { do: 'walk', taskId: 't1', to: 'library' },
      { do: 'work', taskId: 't1', agent: 'RESEARCH', text: 'm thinking (try 2)...' },
    ]);
  });

  it('sends blocked tasks to wait on the porch and brings them back on resume', () => {
    const office = new Choreographer();
    office.onEvent(event('TASK_CREATED'));
    expect(office.onEvent(event('TASK_BLOCKED', null, 'MODEL_UNAVAILABLE: connection refused'))).toEqual([
      { do: 'rain' },
      { do: 'walk', taskId: 't1', to: 'porch' },
    ]);
    expect(office.onEvent(event('TASK_RESUMED'))).toEqual([{ do: 'walk', taskId: 't1', to: 'mail' }]);
  });

  it('composts failed and cancelled tasks and ignores them afterwards', () => {
    const office = new Choreographer();
    office.onEvent(event('TASK_CREATED'));
    expect(office.onEvent(event('TASK_CANCELLED', null, 'from=INBOX'))).toEqual([
      { do: 'walk', taskId: 't1', to: 'compost' },
      { do: 'vanish', taskId: 't1', how: 'compost' },
    ]);
    expect(office.onEvent(event('EXECUTION_STARTED', 'MANAGER'))).toEqual([]);
    expect(office.onEvent(event('TRELLO_UPDATED'))).toEqual([{ do: 'board', ok: true }]);
  });

  it('brings in tasks it never saw being created, except for paperwork about them', () => {
    const office = new Choreographer();
    expect(office.onEvent(event('USER_NOTIFIED', null, null, 'old'))).toEqual([{ do: 'bird', ok: true }]);
    expect(office.onEvent(event('REVIEW_STARTED', null, null, 'new'))).toEqual([
      { do: 'spawn', taskId: 'new', at: 'mail' },
      { do: 'walk', taskId: 'new', to: 'review' },
    ]);
  });

  it('places tasks already in flight where their status says', () => {
    const office = new Choreographer();
    const base = { title: 'x', source: 'API' };
    expect(office.place({ ...base, id: 'a', status: 'IN_PROGRESS', assignedAgent: 'DEVELOPER' })).toEqual([
      { do: 'spawn', taskId: 'a', at: 'lab' },
    ]);
    expect(office.place({ ...base, id: 'b', status: 'BLOCKED', assignedAgent: null })[0]).toMatchObject({ at: 'porch' });
    expect(office.place({ ...base, id: 'a', status: 'REVIEW', assignedAgent: 'DEVELOPER' })).toEqual([]);
    expect(office.onEvent(event('EXECUTION_STARTED', 'DEVELOPER', null, 'a'))).toEqual([
      { do: 'work', taskId: 'a', agent: 'DEVELOPER', text: 'Thinking...' },
    ]);
  });

  it('forgets tasks that left while the stream was down', () => {
    const office = new Choreographer();
    office.onEvent(event('TASK_CREATED', null, null, 'stale'));
    office.onEvent(event('TASK_CREATED', null, null, 'live'));

    expect(office.retain(new Set(['live']))).toEqual(['stale']);
    expect(office.onEvent(event('REVIEW_STARTED', null, null, 'live'))).toEqual([{ do: 'walk', taskId: 'live', to: 'review' }]);
  });

  it('reports failed executions by their error code', () => {
    const office = new Choreographer();
    office.onEvent(event('TASK_CREATED'));
    expect(office.onEvent(event('EXECUTION_FAILED', 'MANAGER', 'INVALID_AGENT_OUTPUT: not JSON'))).toEqual([
      { do: 'oops', agent: 'MANAGER', text: 'INVALID_AGENT_OUTPUT' },
    ]);
  });
});

suite('describe', () => {
  it('writes a ticker line with the task title', () => {
    expect(describe(event('EXECUTION_STARTED', 'DEVELOPER'), 'Java 21 plan')).toBe('DEVELOPER picked up "Java 21 plan"');
  });
});
