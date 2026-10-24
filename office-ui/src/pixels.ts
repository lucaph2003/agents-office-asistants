import Phaser from 'phaser';

/**
 * Every sprite is drawn here as text, one character per pixel, so the game needs no image
 * files. '.' is transparent; other characters map to the sprite's palette.
 */
interface PixelSprite {
  rows: string[];
  palette: Record<string, string>;
}

/** Soft dark brown instead of black keeps outlines warm. */
export const OUTLINE = '#4a2f27';
const SKIN = '#f6d1a8';
const BLUSH = '#f2a48b';

function person(hair: string, shirt: string, accent: string, pants: string, head?: string[]): PixelSprite {
  const face = head ?? ['..HHHHHH..', '.HHHHHHHH.', '.HSSSSSSH.'];
  return {
    rows: [
      ...face,
      '.SSKSSKSS.',
      '.BSSSSSSB.',
      '..SSKKSS..',
      '...SSSS...',
      '.CCCCCCCC.',
      'CCCCAACCCC',
      'CCCCAACCCC',
      'SCCCCCCCCS',
      '.PPPPPPPP.',
      '.PP....PP.',
      '.KK....KK.',
    ],
    palette: { H: hair, S: SKIN, B: BLUSH, K: OUTLINE, C: shirt, A: accent, P: pants, V: hair, G: '#5a7d9a' },
  };
}

const glasses = (sprite: PixelSprite): PixelSprite => ({
  ...sprite,
  rows: sprite.rows.map((row, i) => (i === 3 ? '.GGGSSGGG.' : row)),
});

const SPRITES: Record<string, PixelSprite> = {
  manager: person('#c9c3b8', '#4f8a8b', '#e07a5f', '#5c4a3d'),
  developer: person('#e07a5f', '#8fb996', '#fff4e0', '#5c4a3d', ['..VVVVVV..', '.VVVVVVVV.', '.VSSSSSSV.']),
  research: glasses(person('#b5651d', '#b8a1d9', '#fff4e0', '#5c4a3d')),
  reviewer: person('#f2cc8f', '#f4f1de', '#81b29a', '#6d597a', ['..VVVVVV..', 'VVVVVVVVVV', '.HSSSSSSH.']),
  courier: person('#e8c170', '#81b29a', '#f2cc8f', '#6d597a', ['...VVVV...', 'VVVVVVVVVV', '.HSSSSSSH.']),
  clerk: person('#7a4e2d', '#f2cc8f', '#e07a5f', '#5c4a3d'),
  envelope: {
    rows: ['KKKKKKKKK', 'KWKWWWKWK', 'KWWKWKWWK', 'KWWWRWWWK', 'KWWWWWWWK', 'KKKKKKKKK'],
    palette: { K: OUTLINE, W: '#fff4e0', R: '#e07a5f' },
  },
  desk: {
    rows: [
      '....KKKKKK......',
      '....KMMMMK......',
      '....KMMMMK...KK.',
      '....KKKKKK..KCCK',
      '...KKKKKKKK.KCCK',
      'KKKKKKKKKKKKKKKK',
      'KDDDDDDDDDDDDDDK',
      'KddddddddddddddK',
      '.Kd..........dK.',
      '.KK..........KK.',
    ],
    palette: { K: OUTLINE, M: '#bde0fe', D: '#c68b59', d: '#a0663b', C: '#f4a261' },
  },
  bookshelf: {
    rows: [
      'KKKKKKKKKKKK',
      'KrrbbgyyrbgK',
      'KrrbbgyyrbgK',
      'KKKKKKKKKKKK',
      'KgyrbbrgyybK',
      'KgyrbbrgyybK',
      'KKKKKKKKKKKK',
      'KbbgyrrbgyrK',
      'KbbgyrrbgyrK',
      'KKKKKKKKKKKK',
    ],
    palette: { K: OUTLINE, r: '#e07a5f', b: '#3d5a80', g: '#81b29a', y: '#f2cc8f' },
  },
  drawers: {
    rows: ['KKKKKKKKKK', 'KDDDDDDDDK', 'KDDDYYDDDK', 'KddddddddK', 'KKKKKKKKKK', 'KDDDDDDDDK', 'KDDDYYDDDK', 'KddddddddK', 'KKKKKKKKKK', 'KDDDDDDDDK', 'KDDDYYDDDK', 'KddddddddK', 'KKKKKKKKKK'],
    palette: { K: OUTLINE, D: '#c68b59', d: '#a0663b', Y: '#f2cc8f' },
  },
  compost: {
    rows: ['...g.g....', '....g.....', 'KKKKKKKKKK', 'KGGGGGGGGK', 'KGgGGGGgGK', 'KGGGGGGGGK', 'KGGgGGGGgK', 'KGGGGGGGGK', '.KKKKKKKK.'],
    palette: { K: OUTLINE, G: '#6a994e', g: '#a7c957' },
  },
  mailbox: {
    rows: ['.KKKKKKK..', 'KRRRRRRRK.', 'KRRRRRRRKY', 'KRRRRRRRKY', 'KKKKKKKKK.', '....KK....', '....KK....', '....KK....', '...KKKK...'],
    palette: { K: OUTLINE, R: '#e07a5f', Y: '#ffb703' },
  },
  board: {
    rows: ['KKKKKKKKKKKKKK', 'KCCCCCCCCCCCCK', 'KCyyCCbbCCggCK', 'KCyyCCbbCCggCK', 'KCCCCCCCCCCCCK', 'KCCbbCCyyCCCCK', 'KCCbbCCyyCCCCK', 'KCCCCCCCCCCCCK', 'KKKKKKKKKKKKKK'],
    palette: { K: OUTLINE, C: '#d4a373', y: '#fefae0', b: '#bde0fe', g: '#cce3c3' },
  },
  bell: {
    rows: ['...K...', '..KYK..', '.KYYYK.', '.KYYYK.', 'KYYYYYK', 'KKKKKKK', '...K...'],
    palette: { K: OUTLINE, Y: '#ffd166' },
  },
  plant: {
    rows: ['.g..g.', 'ggggg.', '.gGgg.', 'gggGgg', '.gggg.', '.KKKK.', '.KTTK.', '.KTTK.', '..KK..'],
    palette: { K: OUTLINE, g: '#90be6d', G: '#6a994e', T: '#d4a373' },
  },
  bird0: {
    rows: ['.W....W.', 'WWW..WWW', '.WWGGWW.', '..WGOWK.', '...OOWYY', '....KK..'],
    palette: { W: '#8ecae6', G: '#219ebc', O: '#ffcfb3', K: OUTLINE, Y: '#ffb703' },
  },
  bird1: {
    rows: ['........', '........', 'WWWGGWWW', '..WGOWK.', '...OOWYY', '....KK..'],
    palette: { W: '#8ecae6', G: '#219ebc', O: '#ffcfb3', K: OUTLINE, Y: '#ffb703' },
  },
  cat: {
    rows: ['.K.K.........', 'KOKOK........', 'KOOOKKKKKKK..', 'KKOKOOOOOOOK.', 'KOOOOOoOOoOOK', '.KKKKKKKKKKOK', '..........KK.'],
    palette: { K: OUTLINE, O: '#f4a261', o: '#e76f51' },
  },
  butterfly0: { rows: ['PP.PP', 'PPKPP', '.PKP.'], palette: { P: '#ffafcc', K: OUTLINE } },
  butterfly1: { rows: ['.....', '.PKP.', 'PPKPP'], palette: { P: '#ffafcc', K: OUTLINE } },
  chair: {
    rows: ['K......', 'KD.....', 'KD.....', 'KDDDDDK', 'KKKKKKK', 'K.....K', 'KK...KK'],
    palette: { K: OUTLINE, D: '#c68b59' },
  },
  bulb: { rows: ['.K.', 'WWW', '.W.'], palette: { K: OUTLINE, W: '#ffffff' } },
  dot: { rows: ['W'], palette: { W: '#ffffff' } },
  drop: { rows: ['W', 'W', 'W'], palette: { W: '#ffffff' } },
  leaf: { rows: ['.W', 'WW'], palette: { W: '#ffffff' } },
};

/** Paints every sprite into a texture, once, at the given scale. */
export function paintSprites(scene: Phaser.Scene, scale = 3): void {
  for (const [key, sprite] of Object.entries(SPRITES)) {
    if (scene.textures.exists(key)) {
      continue;
    }
    const width = Math.max(...sprite.rows.map((row) => row.length));
    const canvas = scene.textures.createCanvas(key, width * scale, sprite.rows.length * scale)!;
    const context = canvas.getContext();
    sprite.rows.forEach((row, y) => {
      [...row].forEach((pixel, x) => {
        const color = sprite.palette[pixel];
        if (pixel !== '.' && color) {
          context.fillStyle = color;
          context.fillRect(x * scale, y * scale, scale, scale);
        }
      });
    });
    canvas.refresh();
  }
}
