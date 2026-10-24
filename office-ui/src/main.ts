import Phaser from 'phaser';
import { connectStream, fetchActiveTasks, fetchTask, type OfficeEvent, type StreamStatus, type TaskSummary } from './api';
import { Choreographer, describe, type Step } from './choreographer';
import { HEIGHT, WIDTH } from './layout';
import { TaskQueues } from './queues';
import { HudScene } from './scenes/HudScene';
import { OfficeScene } from './scenes/OfficeScene';

/** Past this many waiting animations, a task's courier starts running to catch up. */
const HURRY_BACKLOG = 3;

function start(office: OfficeScene, events: Phaser.Events.EventEmitter): void {
  const choreographer = new Choreographer();
  const queues = new TaskQueues();
  const titles = new Map<string, Promise<TaskSummary | null>>();

  const learn = (taskId: string) => {
    if (!titles.has(taskId)) {
      titles.set(taskId, fetchTask(taskId).then((task) => {
        if (task) {
          office.describeTask(task);
        }
        return task;
      }));
    }
    return titles.get(taskId)!;
  };

  const enqueue = (taskId: string, steps: Step[]) => {
    for (const step of steps) {
      if (step.do === 'board') {
        void office.perform(step);
      } else {
        void queues.run(taskId, () => office.perform(step, queues.backlog(taskId) > HURRY_BACKLOG));
      }
    }
  };

  const onEvent = (event: OfficeEvent) => {
    const task = learn(event.taskId);
    office.twinkle();
    // The ticker line waits its turn, so it describes what is on screen, not what already happened.
    void queues.run(event.taskId, async () => {
      events.emit('log', describe(event, (await task)?.title ?? `#${event.taskId.slice(0, 4)}`));
    });
    enqueue(event.taskId, choreographer.onEvent(event));
  };

  const onStatus = (status: StreamStatus) => {
    events.emit('status', status);
    if (status === 'live') {
      // On every (re)connect, line the office up with what is actually in flight.
      void fetchActiveTasks().then((tasks) => {
        for (const id of choreographer.retain(new Set(tasks.map((task) => task.id)))) {
          office.remove(id);
        }
        for (const task of tasks) {
          titles.set(task.id, Promise.resolve(task));
          office.describeTask(task);
          enqueue(task.id, choreographer.place(task));
        }
      });
    }
  };

  connectStream(onEvent, onStatus);
}

async function boot(): Promise<void> {
  // Text drawn before the pixel font loads would stay in the fallback font.
  await Promise.race([document.fonts.load('16px "Pixelify Sans"'), new Promise((resolve) => setTimeout(resolve, 2000))]);
  const game = new Phaser.Game({
    type: Phaser.AUTO,
    width: WIDTH,
    height: HEIGHT,
    parent: 'game',
    pixelArt: true,
    backgroundColor: '#8cc084',
    scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_BOTH },
    scene: [OfficeScene, HudScene],
  });
  if (import.meta.env.DEV) {
    Object.assign(window, { game });
  }
  game.events.once('office-ready', (office: OfficeScene) => {
    // The HUD has to be listening before the first status and score arrive.
    office.scene.get('hud').events.once(Phaser.Scenes.Events.CREATE, () => start(office, game.events));
    office.scene.launch('hud');
  });
}

void boot();
