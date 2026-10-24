import Phaser from 'phaser';
import type { AgentType, TaskSummary } from '../api';
import type { RoomId, Step } from '../choreographer';
import {
  CORRIDOR_Y, HEIGHT, HUD_HEIGHT, PORCH, PORCH_SLOTS, ROOMS, WIDTH, roomById, seat,
  type Point, type Room,
} from '../layout';
import { OUTLINE, paintSprites } from '../pixels';
import { play } from '../sound';

export const FONT = '"Pixelify Sans", monospace';
const BROWN = 0x4a2f27;
const CREAM = '#fff4e0';
const WALK_SPEED = 200;
const WALL = 30;

const SOURCE_TINT: Record<string, number> = {
  TELEGRAM: 0xcde8ff,
  TRELLO: 0xd8f3dc,
  SCHEDULED: 0xfff1c1,
};

const AGENT_SPRITE: Record<AgentType, string> = {
  MANAGER: 'manager',
  DEVELOPER: 'developer',
  RESEARCH: 'research',
  REVIEWER: 'reviewer',
};

const FAIRY_LIGHTS = [0xffd166, 0xffb4a2, 0xcdeac0, 0xbde0fe, 0xfff1c1];
const FLOWERS = [0xffafcc, 0xffd166, 0xfffaf0, 0xcdb4db, 0xffb4a2];

interface Token {
  container: Phaser.GameObjects.Container;
  runner: Phaser.GameObjects.Image;
  envelope: Phaser.GameObjects.Image;
  label: Phaser.GameObjects.Text;
  room: RoomId;
  bell?: Phaser.GameObjects.Image;
}

interface AgentView {
  sprite: Phaser.GameObjects.Image;
  home: Point;
  room: Room;
  bob?: Phaser.Tweens.Tween;
  bubble?: Phaser.GameObjects.Container;
}

interface Light {
  color: number;
  alpha: number;
  night: boolean;
}

/** The office: cottages around a garden path, the agents at their desks and the couriers carrying each task. */
export class OfficeScene extends Phaser.Scene {
  private readonly tokens = new Map<string, Token>();
  private readonly agents = new Map<AgentType, AgentView>();
  private readonly slots = new Map<RoomId, Array<string | null>>();
  private readonly tasks = new Map<string, TaskSummary>();
  private readonly windows: Phaser.GameObjects.Rectangle[] = [];
  private readonly lamps: Phaser.GameObjects.Image[] = [];
  private fairyLights: Phaser.GameObjects.Image[] = [];
  private board!: Phaser.GameObjects.Image;
  private mailbox!: Phaser.GameObjects.Image;
  private compost!: Phaser.GameObjects.Image;
  private drawers!: Phaser.GameObjects.Image;
  private shade!: Phaser.GameObjects.Rectangle;
  private daylight!: Phaser.GameObjects.Rectangle;
  private night = false;

  constructor() {
    super('office');
  }

  create(): void {
    paintSprites(this);
    this.paintGlow();
    this.drawGarden();
    ROOMS.forEach((room) => this.drawRoom(room));
    this.drawPorch();
    this.drawFairyLights();
    this.releaseButterflies();
    this.daylight = this.add.rectangle(0, HUD_HEIGHT, WIDTH, HEIGHT - HUD_HEIGHT, 0x000000, 0).setOrigin(0).setDepth(940);
    this.shade = this.add.rectangle(0, HUD_HEIGHT, WIDTH, HEIGHT - HUD_HEIGHT, 0x3d5a80, 0).setOrigin(0).setDepth(941);
    this.followTheSun();
    this.time.addEvent({ delay: 60_000, loop: true, callback: () => this.followTheSun() });
    this.game.events.emit('office-ready', this);
  }

  /** Records what is known about a task and refreshes its label and envelope color. */
  describeTask(task: TaskSummary): void {
    this.tasks.set(task.id, task);
    const token = this.tokens.get(task.id);
    if (token) {
      this.dress(token, task.id);
    }
  }

  /** Removes tasks without animation, e.g. ones that finished while the stream was down. */
  remove(taskId: string): void {
    const token = this.tokens.get(taskId);
    if (token) {
      this.release(taskId, token.room);
      token.container.destroy();
      this.tokens.delete(taskId);
      this.reportActive();
    }
  }

  /** A fairy light glows a little brighter for every event. */
  twinkle(): void {
    const light = Phaser.Utils.Array.GetRandom(this.fairyLights) as Phaser.GameObjects.Image;
    this.tweens.add({ targets: light, alpha: { from: 1, to: this.lightAlpha() }, scale: { from: 1.6, to: 1 }, duration: 900 });
  }

  async perform(step: Step, hurry = false): Promise<void> {
    const speed = hurry ? 2.5 : 1;
    switch (step.do) {
      case 'spawn': return this.spawn(step.taskId, step.at);
      case 'walk': return this.walk(step.taskId, step.to, speed);
      case 'work': return this.work(step.agent, step.text, speed);
      case 'done': return this.finish(step.agent, step.text, speed);
      case 'oops': return this.oops(step.agent, step.text, speed);
      case 'stamp': return this.stamp(step.taskId, step.ok, step.text, speed);
      case 'bell': return this.ring(step.taskId);
      case 'rain': return this.cloudPasses();
      case 'vanish': return this.vanish(step.taskId, step.how, speed);
      case 'score':
        play('chime');
        this.sparkle(this.drawers.x, this.drawers.y - 40, 0xffd166);
        this.game.events.emit('score');
        return;
      case 'bird': return this.bird(step.ok);
      case 'board': return this.blinkBoard(step.ok);
    }
  }

  // ---- steps ----

  private async spawn(taskId: string, at: RoomId): Promise<void> {
    if (this.tokens.has(taskId)) {
      return;
    }
    const runner = this.add.image(0, 0, 'courier').setOrigin(0.5, 1).setScale(0.6);
    const envelope = this.add.image(0, -29, 'envelope').setScale(0.7);
    const label = this.add.text(0, 2, '', { fontFamily: FONT, fontSize: '10px', color: CREAM })
      .setOrigin(0.5, 0).setStroke(OUTLINE, 3);
    const point = this.claim(taskId, at);
    const container = this.add.container(point.x, point.y, [runner, envelope, label]).setDepth(point.y);
    const token: Token = { container, runner, envelope, label, room: at };
    this.tokens.set(taskId, token);
    this.dress(token, taskId);
    this.reportActive();
    container.setScale(0);
    play('step');
    await this.tween({ targets: container, scale: 1, duration: 350, ease: 'Back.Out' });
    if (at === 'porch') {
      this.sway(token);
    }
  }

  private async walk(taskId: string, to: RoomId, speed: number): Promise<void> {
    const token = this.tokens.get(taskId);
    if (!token) {
      return;
    }
    token.bell?.destroy();
    token.bell = undefined;
    const from = token.room;
    this.release(taskId, from);
    const target = this.claim(taskId, to);
    token.room = to;
    const { container, runner } = token;
    this.tweens.killTweensOf(container);
    container.setAngle(0);

    const path: Point[] = [];
    if (from === 'porch') {
      path.push({ x: container.x, y: CORRIDOR_Y });
    } else {
      const door = roomById(from).door;
      path.push(door, { x: door.x, y: CORRIDOR_Y });
    }
    if (to === 'porch') {
      path.push({ x: target.x, y: CORRIDOR_Y }, target);
    } else {
      const door = roomById(to).door;
      path.push({ x: door.x, y: CORRIDOR_Y }, door, target);
    }

    const hop = this.tweens.add({ targets: runner, y: -2, yoyo: true, repeat: -1, duration: 140 });
    for (const point of path) {
      const distance = Phaser.Math.Distance.Between(container.x, container.y, point.x, point.y);
      if (distance < 1) {
        continue;
      }
      runner.setFlipX(point.x < container.x);
      container.setDepth(Math.max(container.y, point.y));
      await this.tween({ targets: container, x: point.x, y: point.y, duration: (distance / (WALK_SPEED * speed)) * 1000 });
    }
    hop.stop();
    runner.setY(0).setFlipX(false);
    container.setDepth(target.y);
    if (to === 'porch') {
      this.sway(token);
    }
  }

  private async work(agent: AgentType, text: string, speed: number): Promise<void> {
    const view = this.agents.get(agent)!;
    view.bob?.stop();
    view.sprite.setY(view.home.y);
    view.bob = this.tweens.add({ targets: view.sprite, y: view.home.y - 2, yoyo: true, repeat: -1, duration: 220 });
    this.say(view, text, '#4a2f27');
    await this.wait(600 / speed);
  }

  private async finish(agent: AgentType, text: string, speed: number): Promise<void> {
    const view = this.agents.get(agent)!;
    this.rest(view);
    this.say(view, text, '#3a7d44', 3600);
    this.sparkle(view.home.x, view.home.y - 40, 0xffd166, 5);
    await this.wait(1200 / speed);
  }

  private async oops(agent: AgentType, text: string, speed: number): Promise<void> {
    const view = this.agents.get(agent)!;
    this.rest(view);
    play('aww');
    this.say(view, `Oh no... ${text}`, '#c8553d', 3000);
    const drop = this.add.image(view.home.x + 14, view.home.y - 38, 'drop').setScale(2).setTint(0x8ecae6).setDepth(950);
    this.tweens.add({ targets: drop, y: drop.y + 10, alpha: 0, duration: 900, onComplete: () => drop.destroy() });
    for (let i = 0; i < 6; i++) {
      const puff = this.add.image(view.home.x + Phaser.Math.Between(-12, 12), view.home.y - 8, 'dot')
        .setScale(Phaser.Math.Between(4, 6)).setTint(0xe5e5e5).setAlpha(0.8).setDepth(950);
      this.tweens.add({
        targets: puff, y: puff.y - Phaser.Math.Between(16, 30), alpha: 0, duration: 1100, delay: i * 70,
        onComplete: () => puff.destroy(),
      });
    }
    await this.wait(1100 / speed);
  }

  private async stamp(taskId: string, ok: boolean, text: string, speed: number): Promise<void> {
    const token = this.tokens.get(taskId);
    if (!token) {
      return;
    }
    const badge = this.add.text(token.container.x, token.container.y - 46, text, {
      fontFamily: FONT, fontSize: '11px', color: CREAM, backgroundColor: ok ? '#6a994e' : '#e76f51',
      padding: { x: 6, y: 3 },
    }).setOrigin(0.5).setAngle(-5).setScale(0).setDepth(960);
    play('stamp');
    await this.tween({ targets: badge, scale: 1, duration: 260 / speed, ease: 'Back.Out' });
    if (ok) {
      this.sparkle(badge.x, badge.y, 0xcdeac0, 6);
    }
    await this.wait(800 / speed);
    this.tweens.add({ targets: badge, alpha: 0, y: badge.y - 8, duration: 400, onComplete: () => badge.destroy() });
  }

  private async ring(taskId: string): Promise<void> {
    const token = this.tokens.get(taskId);
    if (!token) {
      return;
    }
    play('bell');
    token.bell = this.add.image(14, -40, 'bell').setScale(0.8);
    token.container.add(token.bell);
    this.tweens.add({ targets: token.bell, angle: { from: -15, to: 15 }, yoyo: true, repeat: -1, duration: 300, ease: 'Sine.InOut' });
    await this.wait(300);
  }

  /** A rain cloud drifts over the village when a task has to wait. */
  private async cloudPasses(): Promise<void> {
    play('rain');
    await this.tween({ targets: this.shade, alpha: 0.22, duration: 500, ease: 'Sine.InOut' });
    this.tweens.add({ targets: this.shade, alpha: 0, duration: 1400, delay: 600, ease: 'Sine.InOut' });
  }

  private async vanish(taskId: string, how: 'archive' | 'compost', speed: number): Promise<void> {
    const token = this.tokens.get(taskId);
    if (!token) {
      return;
    }
    const into = how === 'archive' ? this.drawers : this.compost;
    this.release(taskId, token.room);
    this.tokens.delete(taskId);
    this.reportActive();
    await this.tween({ targets: token.container, x: into.x, y: into.y - 12, scale: 0.3, alpha: 0.6, duration: 500 / speed, ease: 'Sine.In' });
    token.container.destroy();
    this.tweens.add({ targets: into, scaleX: 1.08, scaleY: 0.94, yoyo: true, duration: 120 });
    if (how === 'compost') {
      for (let i = 0; i < 8; i++) {
        const leaf = this.add.image(into.x + Phaser.Math.Between(-10, 10), into.y - 24, 'leaf')
          .setScale(2).setTint(Phaser.Utils.Array.GetRandom([0x90be6d, 0xa7c957, 0xe9c46a])).setDepth(into.y + 1);
        this.tweens.add({
          targets: leaf, x: leaf.x + Phaser.Math.Between(-24, 24), y: leaf.y - Phaser.Math.Between(10, 24),
          angle: Phaser.Math.Between(-120, 120), alpha: 0, duration: 1000, delay: i * 50, onComplete: () => leaf.destroy(),
        });
      }
      play('aww');
    }
  }

  private async bird(ok: boolean): Promise<void> {
    const start = { x: this.mailbox.x + 4, y: this.mailbox.y - 34 };
    const bird = this.add.image(start.x, start.y, 'bird0').setDepth(970);
    const flap = this.time.addEvent({
      delay: 130, loop: true,
      callback: () => bird.setTexture(bird.texture.key === 'bird0' ? 'bird1' : 'bird0'),
    });
    play('tweet');
    const done = () => { flap.remove(); bird.destroy(); };
    if (ok) {
      this.tweens.add({ targets: bird, x: -40, y: HUD_HEIGHT + 20, duration: 2600, ease: 'Sine.In', onComplete: done });
    } else {
      this.tweens.add({ targets: bird, x: start.x + 50, y: start.y - 30, yoyo: true, repeat: 1, duration: 500, onComplete: done });
    }
    await this.wait(250);
  }

  private async blinkBoard(ok: boolean): Promise<void> {
    this.board.setTint(ok ? 0xcdeac0 : 0xffb4a2);
    this.time.delayedCall(500, () => this.board.clearTint());
  }

  // ---- helpers ----

  private dress(token: Token, taskId: string): void {
    const task = this.tasks.get(taskId);
    const title = task?.title ?? `#${taskId.slice(0, 4)}`;
    token.label.setText(title.length > 12 ? `${title.slice(0, 11)}…` : title);
    token.envelope.setTint(SOURCE_TINT[task?.source ?? ''] ?? 0xffffff);
  }

  /** Waiting on the porch: a gentle rock back and forth. */
  private sway(token: Token): void {
    this.tweens.add({
      targets: token.container, angle: { from: -3, to: 3 }, yoyo: true, repeat: -1, duration: 1400, ease: 'Sine.InOut',
    });
  }

  private sparkle(x: number, y: number, color: number, count = 10): void {
    for (let i = 0; i < count; i++) {
      const star = this.add.image(x, y, 'dot').setScale(3).setTint(color).setDepth(965);
      const angle = (Math.PI * 2 * i) / count;
      this.tweens.add({
        targets: star, x: x + Math.cos(angle) * 22, y: y + Math.sin(angle) * 16, alpha: 0, scale: 1,
        duration: 700, ease: 'Sine.Out', onComplete: () => star.destroy(),
      });
    }
  }

  private say(view: AgentView, text: string, color: string, hideAfter?: number): void {
    view.bubble?.destroy();
    const body = this.add.text(0, 0, text, {
      fontFamily: FONT, fontSize: '10px', color, lineSpacing: 2,
      wordWrap: { width: 160, useAdvancedWrap: true },
    }).setOrigin(0.5, 1);
    const width = body.width + 14;
    const height = body.height + 10;
    const frame = this.add.graphics();
    frame.fillStyle(0xfffaf0).lineStyle(2, BROWN)
      .fillRoundedRect(-width / 2, -height - 3, width, height, 5)
      .strokeRoundedRect(-width / 2, -height - 3, width, height, 5)
      .fillTriangle(-5, -4, 5, -4, 0, 4)
      .lineBetween(-5, -3, 0, 4).lineBetween(5, -3, 0, 4);
    body.setPosition(0, -8);
    const room = view.room;
    const x = Phaser.Math.Clamp(view.home.x, room.x + width / 2 + 2, room.x + room.width - width / 2 - 2);
    const bubble = this.add.container(x, view.home.y - 44, [frame, body]).setDepth(980).setScale(0);
    this.tweens.add({ targets: bubble, scale: 1, duration: 200, ease: 'Back.Out' });
    view.bubble = bubble;
    if (hideAfter) {
      this.time.delayedCall(hideAfter, () => {
        if (view.bubble === bubble) {
          view.bubble = undefined;
        }
        this.tweens.add({ targets: bubble, alpha: 0, duration: 300, onComplete: () => bubble.destroy() });
      });
    }
  }

  private rest(view: AgentView): void {
    view.bob?.stop();
    view.bob = undefined;
    view.sprite.setY(view.home.y);
  }

  private claim(taskId: string, room: RoomId): Point {
    const points = room === 'porch' ? PORCH_SLOTS : roomById(room).slots;
    const taken = this.slots.get(room) ?? [];
    this.slots.set(room, taken);
    let index = taken.indexOf(null);
    if (index === -1) {
      index = taken.length;
    }
    taken[index] = taskId;
    const point = points[index % points.length];
    const crowd = Math.floor(index / points.length);
    return { x: point.x + crowd * 8, y: point.y - crowd * 6 };
  }

  private release(taskId: string, room: RoomId): void {
    const taken = this.slots.get(room);
    const index = taken?.indexOf(taskId) ?? -1;
    if (taken && index >= 0) {
      taken[index] = null;
    }
  }

  private reportActive(): void {
    this.game.events.emit('active', this.tokens.size);
  }

  private tween(config: Phaser.Types.Tweens.TweenBuilderConfig): Promise<void> {
    return new Promise((resolve) => {
      this.tweens.add({ ...config, onComplete: () => resolve() });
    });
  }

  private wait(ms: number): Promise<void> {
    return new Promise((resolve) => this.time.delayedCall(ms, resolve));
  }

  // ---- time of day ----

  /** The light follows the viewer's clock; `?hour=21` previews another time of day. */
  private followTheSun(): void {
    const forced = Number(new URLSearchParams(location.search).get('hour'));
    const now = new Date();
    const hour = Number.isFinite(forced) && new URLSearchParams(location.search).has('hour')
      ? forced : now.getHours() + now.getMinutes() / 60;
    const light = lightAt(hour);
    this.night = light.night;
    this.tweens.add({ targets: this.daylight, alpha: light.alpha, duration: 2000 });
    this.daylight.setFillStyle(light.color, this.daylight.alpha);
    this.windows.forEach((glass) => glass.setFillStyle(light.night ? 0xffd166 : 0xbde0fe));
    this.lamps.forEach((lamp) => this.tweens.add({ targets: lamp, alpha: light.night ? 0.5 : 0, duration: 2000 }));
    this.fairyLights.forEach((bulb) => bulb.setAlpha(this.lightAlpha()));
  }

  private lightAlpha(): number {
    return this.night ? 1 : 0.6;
  }

  // ---- scenery ----

  /** Soft round light for the desk lamps, faded at the edges. */
  private paintGlow(): void {
    if (this.textures.exists('glow')) {
      return;
    }
    const canvas = this.textures.createCanvas('glow', 128, 128)!;
    const context = canvas.getContext();
    const gradient = context.createRadialGradient(64, 64, 0, 64, 64, 64);
    gradient.addColorStop(0, 'rgba(255, 209, 102, 0.9)');
    gradient.addColorStop(0.5, 'rgba(255, 190, 90, 0.35)');
    gradient.addColorStop(1, 'rgba(255, 180, 80, 0)');
    context.fillStyle = gradient;
    context.fillRect(0, 0, 128, 128);
    canvas.refresh();
  }

  private drawGarden(): void {
    const g = this.add.graphics();
    g.fillStyle(0x8cc084).fillRect(0, HUD_HEIGHT, WIDTH, HEIGHT - HUD_HEIGHT);
    const blocked = (x: number, y: number) =>
      ROOMS.some((r) => x > r.x - 4 && x < r.x + r.width + 4 && y > r.y - 4 && y < r.y + r.height + 4)
      || (y > CORRIDOR_Y - 20 && y < CORRIDOR_Y + 20)
      || (y > PORCH.y - 4 && y < PORCH.y + PORCH.height + 4);
    for (let i = 0; i < 500; i++) {
      const x = Phaser.Math.Between(0, WIDTH);
      const y = Phaser.Math.Between(HUD_HEIGHT, HEIGHT);
      if (!blocked(x, y)) {
        g.fillStyle(0x76b06a).fillRect(x, y, 2, 4).fillRect(x + 3, y + 1, 2, 3);
      }
    }
    for (let i = 0; i < 160; i++) {
      const x = Phaser.Math.Between(0, WIDTH);
      const y = Phaser.Math.Between(HUD_HEIGHT, HEIGHT);
      if (!blocked(x, y)) {
        g.fillStyle(Phaser.Utils.Array.GetRandom(FLOWERS)).fillRect(x - 2, y, 2, 2).fillRect(x + 2, y, 2, 2)
          .fillRect(x, y - 2, 2, 2).fillRect(x, y + 2, 2, 2);
        g.fillStyle(0xffd166).fillRect(x, y, 2, 2);
      }
    }
    // The garden path every courier walks on.
    g.fillStyle(0xd9c48f).fillRect(0, CORRIDOR_Y - 17, WIDTH, 34);
    g.fillStyle(0xe9d8a6).fillRect(0, CORRIDOR_Y - 14, WIDTH, 28);
    for (let x = 10; x < WIDTH; x += 38) {
      g.fillStyle(0xd6c7a1).fillEllipse(x + (x % 3) * 4, CORRIDOR_Y + ((x / 38) % 2 ? 5 : -4), 16, 9);
    }
  }

  private drawRoom(room: Room): void {
    const g = this.add.graphics();
    const { x, y, width, height } = room;
    const [light, dark] = room.colors;
    if (room.floor === 'planks') {
      for (let row = 0, py = y; py < y + height; row++, py += 12) {
        g.fillStyle(row % 2 ? dark : light).fillRect(x, py, width, Math.min(12, y + height - py));
        g.fillStyle(dark - 0x0a0a0a);
        for (let px = x + (row % 3) * 22 + 14; px < x + width; px += 64) {
          g.fillRect(px, py, 2, 12);
        }
      }
    } else {
      for (let ty = y; ty < y + height; ty += 16) {
        for (let tx = x; tx < x + width; tx += 16) {
          const even = ((tx - x) / 16 + (ty - y) / 16) % 2 === 0;
          g.fillStyle(even ? light : dark).fillRect(tx, ty, Math.min(16, x + width - tx), Math.min(16, y + height - ty));
        }
      }
    }
    // Rug where tasks wait.
    g.fillStyle(room.rug, 0.85).fillRoundedRect(x + 16, y + 106, width - 32, height - 112, 8);
    g.lineStyle(2, 0xfffaf0, 0.6).strokeRoundedRect(x + 21, y + 111, width - 42, height - 122, 6);
    // Wallpaper, baseboard and a window.
    g.fillStyle(0xf4e9d8).fillRect(x, y, width, WALL);
    for (let sx = x + 4; sx < x + width; sx += 10) {
      g.fillStyle(0xebdcc5).fillRect(sx, y, 3, WALL);
    }
    g.fillStyle(0x9c6b43).fillRect(x, y + WALL, width, 3);
    const windowX = x + width - 50;
    g.fillStyle(BROWN).fillRect(windowX - 2, y + 4, 34, 22);
    this.windows.push(this.add.rectangle(windowX, y + 6, 30, 18, 0xbde0fe).setOrigin(0));
    const frame = this.add.graphics();
    frame.fillStyle(BROWN).fillRect(windowX + 14, y + 6, 2, 18).fillRect(windowX, y + 14, 30, 2);
    frame.fillStyle(0xffafcc).fillRect(windowX - 4, y + 4, 5, 20).fillRect(windowX + 29, y + 4, 5, 20);
    // Walls and the doorway onto the path.
    frame.lineStyle(4, 0x7a4e2d).strokeRect(x, y, width, height);
    frame.fillStyle(light).fillRect(room.door.x - 18, room.door.y - (room.door.y === y ? 0 : 3), 36, room.door.y === y ? WALL + 3 : 6);
    frame.fillStyle(0xc8553d).fillRect(room.door.x - 14, room.door.y === y ? y - 7 : room.door.y + 2, 28, 5);
    // Hanging wooden sign.
    const sign = this.add.text(x + 14, y + 8, room.label, {
      fontFamily: FONT, fontSize: '11px', color: CREAM, backgroundColor: '#9c6b43', padding: { x: 5, y: 2 },
    });
    frame.lineStyle(2, BROWN).strokeRect(sign.x - 1, sign.y - 1, sign.width + 2, sign.height + 2);

    const spot = seat(room);
    // A warm lamp that only shows after dark.
    this.lamps.push(this.add.image(spot.x, spot.y + 20, 'glow').setScale(1.4).setAlpha(0).setBlendMode(Phaser.BlendModes.ADD).setDepth(942));
    switch (room.id) {
      case 'mail':
        this.board = this.add.image(x + 130, y + 16, 'board').setScale(0.9);
        this.add.image(spot.x - 46, spot.y + 22, 'clerk').setOrigin(0.5, 1);
        this.add.image(spot.x - 46, spot.y + 4, 'desk').setOrigin(0.5, 0);
        this.steam(spot.x - 46, spot.y);
        this.napCat(x + 172, y + 92);
        break;
      case 'outbox':
        this.mailbox = this.add.image(spot.x, spot.y + 34, 'mailbox').setOrigin(0.5, 1).setScale(1.3);
        this.add.image(spot.x + 6, spot.y - 4, 'bird0').setScale(0.9);
        this.add.image(x + 30, spot.y + 26, 'plant');
        break;
      case 'archive':
        this.drawers = this.add.image(spot.x, spot.y + 34, 'drawers').setOrigin(0.5, 1).setScale(0.9);
        this.add.image(spot.x - 46, spot.y + 34, 'drawers').setOrigin(0.5, 1).setScale(0.9);
        this.add.image(spot.x + 46, spot.y + 34, 'drawers').setOrigin(0.5, 1).setScale(0.9);
        break;
      case 'compost':
        this.compost = this.add.image(spot.x, spot.y + 34, 'compost').setOrigin(0.5, 1).setScale(1.3);
        this.add.image(spot.x - 50, spot.y + 26, 'plant');
        this.add.image(spot.x + 50, spot.y + 26, 'plant');
        break;
      case 'library':
        this.add.image(x + 32, y + 66, 'bookshelf');
        this.add.image(x + width - 32, y + 66, 'bookshelf');
        break;
      default:
        this.add.image(x + width - 24, spot.y + 18, 'plant');
        this.add.image(x + 26, spot.y + 22, 'chair').setScale(0.9);
    }
    if (room.agent) {
      const home = { x: spot.x, y: spot.y + 22 };
      const sprite = this.add.image(home.x, home.y, AGENT_SPRITE[room.agent]).setOrigin(0.5, 1);
      this.add.image(spot.x, spot.y + 4, 'desk').setOrigin(0.5, 0);
      this.steam(spot.x, spot.y);
      this.agents.set(room.agent, { sprite, home, room });
      this.add.text(spot.x, spot.y + 34, room.agent.toLowerCase(), {
        fontFamily: FONT, fontSize: '10px', color: '#5c3d2e',
      }).setOrigin(0.5, 0);
    }
  }

  /** Steam curling up from the mug on a desk at this seat. */
  private steam(x: number, y: number): void {
    this.time.addEvent({
      delay: 900, loop: true, startAt: Phaser.Math.Between(0, 800),
      callback: () => {
        const puff = this.add.image(x + 16 + Phaser.Math.Between(-1, 1), y + 10, 'dot').setScale(2).setAlpha(0.7);
        this.tweens.add({
          targets: puff, y: y - 6, x: puff.x + Phaser.Math.Between(-3, 3), alpha: 0, duration: 1400,
          onComplete: () => puff.destroy(),
        });
      },
    });
  }

  private napCat(x: number, y: number): void {
    const cat = this.add.image(x, y, 'cat').setOrigin(0.5, 1).setScale(0.8);
    this.tweens.add({ targets: cat, scaleY: 0.84, yoyo: true, repeat: -1, duration: 1600, ease: 'Sine.InOut' });
    this.time.addEvent({
      delay: 3500, loop: true,
      callback: () => {
        const z = this.add.text(x - 12, y - 22, 'z', { fontFamily: FONT, fontSize: '10px', color: '#7a4e2d' });
        this.tweens.add({ targets: z, y: z.y - 16, x: z.x - 6, alpha: 0, duration: 2200, onComplete: () => z.destroy() });
      },
    });
  }

  private drawPorch(): void {
    const { x, y, width, height } = PORCH;
    const g = this.add.graphics();
    for (let row = 0, py = y; py < y + height; row++, py += 10) {
      g.fillStyle(row % 2 ? 0xa1887f : 0xab9288).fillRect(x, py, width, Math.min(10, y + height - py));
    }
    g.lineStyle(4, 0x7a4e2d).strokeRect(x, y, width, height);
    // Striped awning.
    for (let sx = x, i = 0; sx < x + width; sx += 24, i++) {
      g.fillStyle(i % 2 ? 0xfff4e0 : 0x81b29a).fillRect(sx, y, 24, 14);
      g.fillCircle(sx + 12, y + 14, 12 / 1.2);
    }
    // Puddles and a rocking chair.
    g.fillStyle(0x8fb8c9, 0.7).fillEllipse(x + 120, y + 98, 40, 7).fillEllipse(x + 600, y + 100, 52, 8);
    this.add.image(x + width - 40, y + 70, 'chair').setScale(1.2);
    this.add.image(x + width - 80, y + 72, 'plant');
    this.add.text(x + 10, y + 26, 'Rainy Porch  ·  tasks waiting for sunshine', {
      fontFamily: FONT, fontSize: '11px', color: CREAM, backgroundColor: '#7a4e2d', padding: { x: 5, y: 2 },
    });
    // A soft rain that never stops.
    for (let i = 0; i < 36; i++) {
      const drop = this.add.image(x + Phaser.Math.Between(6, width - 6), y + 24, 'drop')
        .setScale(1.5).setTint(0xa8dadc).setAlpha(0.6);
      this.tweens.add({
        targets: drop, y: y + height - 6, duration: Phaser.Math.Between(1100, 1700), repeat: -1,
        delay: Phaser.Math.Between(0, 1600),
      });
    }
  }

  private drawFairyLights(): void {
    const g = this.add.graphics().lineStyle(1, 0x7a4e2d);
    g.beginPath();
    g.moveTo(0, CORRIDOR_Y - 18);
    for (let x = 0; x <= WIDTH; x += 20) {
      g.lineTo(x, CORRIDOR_Y - 18 + ((x / 20) % 2) * 4);
    }
    g.strokePath();
    for (let i = 0; i * 40 + 10 < WIDTH; i++) {
      this.fairyLights.push(this.add.image(i * 40 + 10, CORRIDOR_Y - 14, 'bulb')
        .setTint(FAIRY_LIGHTS[i % FAIRY_LIGHTS.length]).setAlpha(0.6).setDepth(943));
    }
  }

  private releaseButterflies(): void {
    [0xffafcc, 0xffd166, 0xcdb4db].forEach((tint, i) => {
      const butterfly = this.add.image(100 + i * 300, CORRIDOR_Y, 'butterfly0').setTint(tint).setScale(0.8).setDepth(900);
      this.time.addEvent({
        delay: 180, loop: true,
        callback: () => butterfly.setTexture(butterfly.texture.key === 'butterfly0' ? 'butterfly1' : 'butterfly0'),
      });
      const wander = () => this.tweens.add({
        targets: butterfly,
        x: Phaser.Math.Between(20, WIDTH - 20),
        y: CORRIDOR_Y + Phaser.Math.Between(-14, 14),
        duration: Phaser.Math.Between(4000, 7000), ease: 'Sine.InOut', onComplete: wander,
      });
      wander();
    });
  }
}

function lightAt(hour: number): Light {
  if (hour < 5.5 || hour >= 21) return { color: 0x1d3557, alpha: 0.32, night: true };
  if (hour < 8) return { color: 0xffc49b, alpha: 0.14, night: false };
  if (hour < 17) return { color: 0xffffff, alpha: 0, night: false };
  if (hour < 19.5) return { color: 0xffb26b, alpha: 0.14, night: false };
  return { color: 0x6d597a, alpha: 0.26, night: true };
}
