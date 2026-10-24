import Phaser from 'phaser';
import type { StreamStatus } from '../api';
import { HEIGHT, HUD_HEIGHT, WIDTH } from '../layout';
import { OUTLINE } from '../pixels';
import { onSoundChange, soundOn, toggleSound } from '../sound';
import { FONT } from './OfficeScene';

const STATUS: Record<StreamStatus, { text: string; color: string }> = {
  connecting: { text: '● connecting', color: '#ffd166' },
  live: { text: '● live', color: '#cdeac0' },
  offline: { text: '● offline', color: '#ffb4a2' },
};

const CREAM = '#fff4e0';

/** Wooden bars on top and bottom: score, connection and an event ticker. */
export class HudScene extends Phaser.Scene {
  private done = 0;
  private doneText!: Phaser.GameObjects.Text;
  private activeText!: Phaser.GameObjects.Text;
  private statusText!: Phaser.GameObjects.Text;
  private statusBlink?: Phaser.Tweens.Tween;
  private ticker: Phaser.GameObjects.Text[] = [];

  constructor() {
    super('hud');
  }

  create(): void {
    this.woodenBar(0, HUD_HEIGHT);
    this.woodenBar(HEIGHT - 38, 38);

    this.add.text(16, 7, '✿ Agent Office', { fontFamily: FONT, fontSize: '20px', color: CREAM })
      .setStroke(OUTLINE, 4);
    this.doneText = this.add.text(320, 10, '', { fontFamily: FONT, fontSize: '14px', color: CREAM }).setStroke(OUTLINE, 3);
    this.activeText = this.add.text(470, 10, '', { fontFamily: FONT, fontSize: '14px', color: CREAM }).setStroke(OUTLINE, 3);
    this.statusText = this.add.text(WIDTH - 16, 10, '', { fontFamily: FONT, fontSize: '14px' })
      .setOrigin(1, 0).setStroke(OUTLINE, 3);
    const sound = this.add.text(640, 12, '', { fontFamily: FONT, fontSize: '11px', color: '#f2cc8f' })
      .setInteractive({ useHandCursor: true });
    const showSound = (on: boolean) => sound.setText(on ? '♪ [M] sound on' : '♪ [M] sound off');
    showSound(soundOn());
    onSoundChange(showSound);
    sound.on('pointerdown', toggleSound);
    this.input.keyboard?.on('keydown-M', toggleSound);

    for (let i = 0; i < 2; i++) {
      this.ticker.push(this.add.text(16, HEIGHT - 34 + i * 16, '', { fontFamily: FONT, fontSize: '11px', color: CREAM }));
    }

    this.renderScore();
    this.setActive(0);
    this.setStatus('connecting');
    const events = this.game.events;
    events.on('score', () => { this.done += 1; this.renderScore(); });
    events.on('active', (count: number) => this.setActive(count));
    events.on('status', (status: StreamStatus) => this.setStatus(status));
    events.on('log', (line: string) => this.log(line));
  }

  private woodenBar(y: number, height: number): void {
    const g = this.add.graphics();
    g.fillStyle(0x7a4e2d).fillRect(0, y, WIDTH, height);
    for (let py = y + 9; py < y + height; py += 10) {
      g.fillStyle(0x6e4527).fillRect(0, py, WIDTH, 1);
    }
    g.fillStyle(0x9c6b43).fillRect(0, y, WIDTH, 2);
    g.fillStyle(0x4a2f27).fillRect(0, y + height - 2, WIDTH, 2);
  }

  private renderScore(): void {
    this.doneText.setText(`✿ done ${this.done}`);
    if (this.done > 0) {
      this.tweens.add({ targets: this.doneText, scale: { from: 1.25, to: 1 }, duration: 300, ease: 'Back.Out' });
    }
  }

  private setActive(count: number): void {
    this.activeText.setText(`✉ on the way ${count}`);
  }

  private setStatus(status: StreamStatus): void {
    const { text, color } = STATUS[status];
    this.statusText.setText(text).setColor(color).setAlpha(1);
    this.statusBlink?.stop();
    if (status !== 'live') {
      this.statusBlink = this.tweens.add({ targets: this.statusText, alpha: 0.4, yoyo: true, repeat: -1, duration: 900 });
    }
  }

  private log(line: string): void {
    const [older, newer] = this.ticker;
    older.setText(newer.text).setColor('#e3c9a0');
    const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false });
    newer.setText(`${time}  ${line.length > 110 ? `${line.slice(0, 109)}…` : line}`).setColor(CREAM);
  }
}
