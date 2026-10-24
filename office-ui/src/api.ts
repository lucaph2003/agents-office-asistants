export type AgentType = 'MANAGER' | 'RESEARCH' | 'DEVELOPER' | 'REVIEWER';

export type TaskEventType =
  | 'TASK_CREATED'
  | 'TASK_ASSIGNED'
  | 'EXECUTION_STARTED'
  | 'EXECUTION_COMPLETED'
  | 'EXECUTION_FAILED'
  | 'REVIEW_STARTED'
  | 'REVIEW_APPROVED'
  | 'CHANGES_REQUESTED'
  | 'HUMAN_APPROVAL_REQUESTED'
  | 'TASK_BLOCKED'
  | 'TASK_RESUMED'
  | 'TASK_COMPLETED'
  | 'TASK_FAILED'
  | 'TASK_CANCELLED'
  | 'TRELLO_UPDATED'
  | 'TRELLO_SYNC_FAILED'
  | 'USER_NOTIFIED'
  | 'NOTIFICATION_FAILED';

export type TaskStatus = 'INBOX' | 'PLANNED' | 'IN_PROGRESS' | 'REVIEW' | 'BLOCKED' | 'DONE' | 'FAILED' | 'CANCELLED';

/** One committed audit event, as sent by GET /api/v1/office/stream. */
export interface OfficeEvent {
  id: string;
  taskId: string;
  type: TaskEventType;
  agent: AgentType | null;
  actor: string;
  details: string | null;
  createdAt: string;
}

export interface TaskSummary {
  id: string;
  title: string;
  status: TaskStatus;
  assignedAgent: AgentType | null;
  source: string;
}

export type StreamStatus = 'connecting' | 'live' | 'offline';

const TERMINAL: TaskStatus[] = ['DONE', 'FAILED', 'CANCELLED'];

export async function fetchTask(id: string): Promise<TaskSummary | null> {
  const response = await fetch(`/api/v1/tasks/${encodeURIComponent(id)}`);
  return response.ok ? response.json() : null;
}

/** Tasks still moving through the office, so a fresh page shows them where they are. */
export async function fetchActiveTasks(): Promise<TaskSummary[]> {
  const response = await fetch('/api/v1/tasks?size=100');
  if (!response.ok) {
    return [];
  }
  const page: { content: TaskSummary[] } = await response.json();
  return page.content.filter((task) => !TERMINAL.includes(task.status));
}

/**
 * Follows the live feed. EventSource retries on dropped connections by itself but gives up for
 * good when the server answers with an error (core down behind the proxy), so that case
 * reconnects by hand.
 */
export function connectStream(onEvent: (event: OfficeEvent) => void, onStatus: (status: StreamStatus) => void): void {
  onStatus('connecting');
  const source = new EventSource('/api/v1/office/stream');
  source.onopen = () => onStatus('live');
  source.onmessage = (message) => onEvent(JSON.parse(message.data));
  source.onerror = () => {
    onStatus('offline');
    if (source.readyState === EventSource.CLOSED) {
      setTimeout(() => connectStream(onEvent, onStatus), 3000);
    }
  };
}
