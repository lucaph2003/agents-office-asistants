import { describe, expect, it } from 'vitest';
import { TaskQueues } from './queues';

const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('TaskQueues', () => {
  it('runs jobs for one key in order and keys side by side', async () => {
    const queues = new TaskQueues();
    const log: string[] = [];
    let releaseFirst!: () => void;

    queues.run('a', () => new Promise<void>((resolve) => { log.push('a1 start'); releaseFirst = resolve; }));
    queues.run('a', async () => { log.push('a2'); });
    const b = queues.run('b', async () => { log.push('b1'); });
    await b;

    expect(log).toEqual(['a1 start', 'b1']);
    expect(queues.backlog('a')).toBe(2);

    releaseFirst();
    await tick();
    await tick();
    expect(log).toEqual(['a1 start', 'b1', 'a2']);
    expect(queues.backlog('a')).toBe(0);
  });

  it('keeps going after a job fails', async () => {
    const queues = new TaskQueues();
    const log: string[] = [];
    const quiet = console.error;
    console.error = () => {};
    try {
      queues.run('a', async () => { throw new Error('boom'); });
      await queues.run('a', async () => { log.push('after'); });
    } finally {
      console.error = quiet;
    }
    expect(log).toEqual(['after']);
  });
});
