import type { AgentType, OfficeEvent, TaskEventType, TaskSummary } from './api';

export type RoomId =
  | 'mail'
  | 'manager'
  | 'lab'
  | 'library'
  | 'review'
  | 'archive'
  | 'outbox'
  | 'compost'
  | 'porch';

/** What the scene should animate, in order, for one task. */
export type Step =
  | { do: 'spawn'; taskId: string; at: RoomId }
  | { do: 'walk'; taskId: string; to: RoomId }
  | { do: 'work'; taskId: string; agent: AgentType; text: string }
  | { do: 'done'; agent: AgentType; text: string }
  | { do: 'oops'; agent: AgentType; text: string }
  | { do: 'stamp'; taskId: string; ok: boolean; text: string }
  | { do: 'bell'; taskId: string }
  | { do: 'rain' }
  | { do: 'vanish'; taskId: string; how: 'archive' | 'compost' }
  | { do: 'score' }
  | { do: 'bird'; ok: boolean }
  | { do: 'board'; ok: boolean };

export const AGENT_ROOM: Record<AgentType, RoomId> = {
  MANAGER: 'manager',
  DEVELOPER: 'lab',
  RESEARCH: 'library',
  REVIEWER: 'review',
};

/** Events about the paperwork around a task; they never bring a task into the office. */
const AFTERMATH: TaskEventType[] = ['TRELLO_UPDATED', 'TRELLO_SYNC_FAILED', 'USER_NOTIFIED', 'NOTIFICATION_FAILED'];

interface Whereabouts {
  room: RoomId;
  gone: boolean;
}

/**
 * Turns audit events into animation steps. It remembers where each task stands, so it walks a
 * task only when it actually changes rooms and knows where to send it back after a review.
 */
export class Choreographer {
  private readonly tasks = new Map<string, Whereabouts>();

  /** Places a task that was already in flight when the page opened. */
  place(task: TaskSummary): Step[] {
    if (this.tasks.has(task.id)) {
      return [];
    }
    const room = roomForStatus(task);
    this.tasks.set(task.id, { room, gone: false });
    return [{ do: 'spawn', taskId: task.id, at: room }];
  }

  /**
   * Forgets tasks that are no longer in flight, e.g. finished while the stream was down.
   *
   * @return the forgotten task ids, which the scene should take off the floor
   */
  retain(active: Set<string>): string[] {
    const forgotten = [...this.tasks.keys()].filter((id) => !active.has(id) && !this.tasks.get(id)!.gone);
    forgotten.forEach((id) => this.tasks.delete(id));
    return forgotten;
  }

  onEvent(event: OfficeEvent): Step[] {
    const id = event.taskId;
    const steps: Step[] = [];
    if (!this.tasks.has(id)) {
      if (AFTERMATH.includes(event.type)) {
        return this.aftermath(event);
      }
      this.tasks.set(id, { room: 'mail', gone: false });
      steps.push({ do: 'spawn', taskId: id, at: 'mail' });
    }
    const task = this.tasks.get(id)!;
    if (task.gone && !AFTERMATH.includes(event.type)) {
      return [];
    }
    const walk = (to: RoomId) => {
      if (task.room !== to) {
        task.room = to;
        steps.push({ do: 'walk', taskId: id, to });
      }
    };
    const agent = event.agent ?? 'MANAGER';
    switch (event.type) {
      case 'TASK_CREATED':
        break;
      case 'EXECUTION_STARTED':
        walk(AGENT_ROOM[agent]);
        steps.push({ do: 'work', taskId: id, agent, text: workingText(event.details) });
        break;
      case 'EXECUTION_COMPLETED':
        steps.push({ do: 'done', agent, text: brief(event.details) ?? 'Done!' });
        break;
      case 'EXECUTION_FAILED':
        steps.push({ do: 'oops', agent, text: errorCode(event.details) });
        break;
      case 'TASK_ASSIGNED':
        steps.push({ do: 'stamp', taskId: id, ok: true, text: 'Routed' });
        break;
      case 'REVIEW_STARTED':
        walk('review');
        break;
      case 'REVIEW_APPROVED':
        steps.push({ do: 'stamp', taskId: id, ok: true, text: 'Approved!' });
        break;
      case 'CHANGES_REQUESTED':
        steps.push({ do: 'stamp', taskId: id, ok: false, text: 'Needs changes' });
        break;
      case 'HUMAN_APPROVAL_REQUESTED':
        steps.push({ do: 'bell', taskId: id });
        break;
      case 'TASK_BLOCKED':
        steps.push({ do: 'rain' });
        walk('porch');
        break;
      case 'TASK_RESUMED':
        walk('mail');
        break;
      case 'TASK_COMPLETED':
        walk('archive');
        steps.push({ do: 'vanish', taskId: id, how: 'archive' }, { do: 'score' });
        task.gone = true;
        break;
      case 'TASK_FAILED':
      case 'TASK_CANCELLED':
        walk('compost');
        steps.push({ do: 'vanish', taskId: id, how: 'compost' });
        task.gone = true;
        break;
      default:
        steps.push(...this.aftermath(event));
    }
    return steps;
  }

  private aftermath(event: OfficeEvent): Step[] {
    switch (event.type) {
      case 'USER_NOTIFIED':
        return [{ do: 'bird', ok: true }];
      case 'NOTIFICATION_FAILED':
        return [{ do: 'bird', ok: false }];
      case 'TRELLO_UPDATED':
        return [{ do: 'board', ok: true }];
      case 'TRELLO_SYNC_FAILED':
        return [{ do: 'board', ok: false }];
      default:
        return [];
    }
  }
}

function roomForStatus(task: TaskSummary): RoomId {
  switch (task.status) {
    case 'PLANNED':
    case 'IN_PROGRESS':
      return task.assignedAgent ? AGENT_ROOM[task.assignedAgent] : 'manager';
    case 'REVIEW':
      return 'review';
    case 'BLOCKED':
      return 'porch';
    default:
      return 'mail';
  }
}

function workingText(details: string | null): string {
  const model = details?.match(/model=(\S+)/)?.[1];
  const attempt = Number(details?.match(/attempt=(\d+)/)?.[1] ?? 1);
  const retry = attempt > 1 ? ` (try ${attempt})` : '';
  return model ? `${model} thinking${retry}...` : `Thinking${retry}...`;
}

function brief(details: string | null): string | null {
  const text = details?.trim();
  if (!text) {
    return null;
  }
  return text.length > 90 ? `${text.slice(0, 89)}…` : text;
}

function errorCode(details: string | null): string {
  return details?.split(':')[0]?.trim() || 'Oops!';
}

/** One line for the ticker at the bottom of the screen. */
export function describe(event: OfficeEvent, title: string): string {
  const who = event.agent ?? 'OFFICE';
  const what: Partial<Record<TaskEventType, string>> = {
    TASK_CREATED: `A new letter arrived: ${title}`,
    TASK_ASSIGNED: `MANAGER routed "${title}"`,
    EXECUTION_STARTED: `${who} picked up "${title}"`,
    EXECUTION_COMPLETED: `${who} finished "${title}"`,
    EXECUTION_FAILED: `${who} got stuck on "${title}"`,
    REVIEW_STARTED: `"${title}" is off to review`,
    REVIEW_APPROVED: `REVIEWER approved "${title}"`,
    CHANGES_REQUESTED: `REVIEWER asked for a few changes on "${title}"`,
    HUMAN_APPROVAL_REQUESTED: `"${title}" waits for a human`,
    TASK_BLOCKED: `"${title}" is waiting out the rain on the porch`,
    TASK_RESUMED: `The sun is out: "${title}" is back inside`,
    TASK_COMPLETED: `"${title}" is done and filed away`,
    TASK_FAILED: `"${title}" went to the compost`,
    TASK_CANCELLED: `"${title}" was cancelled, back to the garden`,
    TRELLO_UPDATED: `A note was pinned on the Trello board`,
    TRELLO_SYNC_FAILED: `The Trello board is out of reach`,
    USER_NOTIFIED: `A bluebird flew off to Telegram`,
    NOTIFICATION_FAILED: `The bluebird came back with the letter`,
  };
  return what[event.type] ?? event.type;
}
