/**
 * Runs jobs one after another per key and keys independently of each other. Events for a task
 * arrive in bursts, faster than they can be animated; this keeps each task's animations in order
 * while two tasks still move at the same time.
 */
export class TaskQueues {
  private readonly tails = new Map<string, Promise<void>>();
  private readonly pending = new Map<string, number>();

  run(key: string, job: () => Promise<void>): Promise<void> {
    this.pending.set(key, this.backlog(key) + 1);
    const previous = this.tails.get(key) ?? Promise.resolve();
    const next = previous
      .then(job)
      .catch((error) => console.error(`office: animation for ${key} failed`, error))
      .finally(() => {
        const left = this.backlog(key) - 1;
        if (left > 0) {
          this.pending.set(key, left);
        } else {
          this.pending.delete(key);
          if (this.tails.get(key) === next) {
            this.tails.delete(key);
          }
        }
      });
    this.tails.set(key, next);
    return next;
  }

  /** Jobs queued or running for this key, the current one included. */
  backlog(key: string): number {
    return this.pending.get(key) ?? 0;
  }
}
