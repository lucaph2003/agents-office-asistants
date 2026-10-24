/**
 * Soft music-box blips made with WebAudio, so there are no sound files. Browsers only allow
 * audio after the viewer interacts with the page, so sound starts off until the first click or key.
 */
type Blip = 'chime' | 'stamp' | 'aww' | 'bell' | 'step' | 'tweet' | 'rain';

const NOTES: Record<Blip, Array<[frequency: number, duration: number]>> = {
  chime: [[1047, 0.09], [1319, 0.09], [1568, 0.3]],
  stamp: [[392, 0.06], [523, 0.12]],
  aww: [[440, 0.14], [392, 0.14], [330, 0.3]],
  bell: [[1760, 0.12], [1760, 0.2]],
  step: [[880, 0.04]],
  tweet: [[1568, 0.05], [2093, 0.07], [1760, 0.06]],
  rain: [[262, 0.25], [220, 0.4]],
};

let context: AudioContext | null = null;
let muted = false;
const listeners: Array<(on: boolean) => void> = [];

export function soundOn(): boolean {
  return context !== null && !muted;
}

export function onSoundChange(listener: (on: boolean) => void): void {
  listeners.push(listener);
}

export function toggleSound(): void {
  if (context === null) {
    context = new AudioContext();
    muted = false;
  } else {
    muted = !muted;
  }
  listeners.forEach((listener) => listener(soundOn()));
}

export function play(blip: Blip): void {
  if (!context || muted) {
    return;
  }
  let at = context.currentTime;
  for (const [frequency, duration] of NOTES[blip]) {
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    oscillator.type = blip === 'stamp' || blip === 'aww' ? 'triangle' : 'sine';
    oscillator.frequency.value = frequency;
    gain.gain.setValueAtTime(0.0001, at);
    gain.gain.exponentialRampToValueAtTime(0.05, at + 0.015);
    gain.gain.exponentialRampToValueAtTime(0.0001, at + duration + 0.2);
    oscillator.connect(gain).connect(context.destination);
    oscillator.start(at);
    oscillator.stop(at + duration + 0.25);
    at += duration;
  }
}
