import type { AgentType } from './api';
import type { RoomId } from './choreographer';

export const WIDTH = 960;
export const HEIGHT = 600;
export const HUD_HEIGHT = 36;
export const CORRIDOR_Y = 241;

export interface Point {
  x: number;
  y: number;
}

export type Floor = 'planks' | 'tiles';

export interface Room {
  id: RoomId;
  label: string;
  x: number;
  y: number;
  width: number;
  height: number;
  floor: Floor;
  colors: [number, number];
  /** Rug under the spot where tasks wait. */
  rug: number;
  /** Where people walk in and out, on the wall facing the path. */
  door: Point;
  /** Where tasks wait while they are in the room. */
  slots: Point[];
  agent?: AgentType;
}

const ROOM_WIDTH = 223;
const ROOM_HEIGHT = 176;
const TOP_ROW = 44;
const BOTTOM_ROW = 262;

function room(id: RoomId, label: string, column: number, top: boolean, floor: Floor, colors: [number, number],
              rug: number, agent?: AgentType): Room {
  const x = 16 + column * (ROOM_WIDTH + 12);
  const y = top ? TOP_ROW : BOTTOM_ROW;
  const slots: Point[] = [];
  for (let row = 0; row < 2; row++) {
    for (let col = 0; col < 3; col++) {
      slots.push({ x: x + 40 + col * 72 + row * 18, y: y + 128 + row * 30 - (top ? 0 : 4) });
    }
  }
  return {
    id, label, x, y, width: ROOM_WIDTH, height: ROOM_HEIGHT, floor, colors, rug, agent, slots,
    door: { x: x + ROOM_WIDTH / 2, y: top ? y + ROOM_HEIGHT : y },
  };
}

export const ROOMS: Room[] = [
  room('mail', 'Mail Room', 0, true, 'planks', [0xdcae7a, 0xd3a470], 0xe9c46a),
  room('manager', 'Manager', 1, true, 'planks', [0xc99468, 0xbf8a5e], 0x81b29a, 'MANAGER'),
  room('lab', 'Dev Lab', 2, true, 'tiles', [0xcfe1b9, 0xc2d6aa], 0xf4a261, 'DEVELOPER'),
  room('library', 'Library', 3, true, 'planks', [0xb07d56, 0xa6734c], 0xb8a1d9, 'RESEARCH'),
  room('outbox', 'Post Office', 0, false, 'planks', [0xe8d2ad, 0xdfc8a2], 0x8ecae6),
  room('archive', 'Archive', 1, false, 'planks', [0xd2b48c, 0xc8aa82], 0xe07a5f),
  room('review', 'Review', 2, false, 'planks', [0xf0dcb0, 0xe7d2a5], 0x90be6d, 'REVIEWER'),
  room('compost', 'Garden Shed', 3, false, 'tiles', [0xc9c5b1, 0xbdb9a5], 0xa7c957),
];

export const PORCH = { x: 16, y: 450, width: WIDTH - 32, height: 108 };

/** Tasks waiting out the rain sit side by side on the porch. */
export const PORCH_SLOTS: Point[] = Array.from({ length: 14 }, (_, i) => ({
  x: PORCH.x + 60 + i * 62,
  y: PORCH.y + 80,
}));

export function roomById(id: RoomId): Room {
  return ROOMS.find((r) => r.id === id)!;
}

/** Where an agent sits, behind the desk. */
export function seat(room: Room): Point {
  return { x: room.x + room.width / 2, y: room.y + 62 };
}
