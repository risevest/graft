import { registerPlugin } from '@capacitor/core';

import type { GraftPlugin } from './definitions';

const Graft = registerPlugin<GraftPlugin>('Graft', {
  web: () => import('./web').then(m => new m.GraftWeb()),
});

export * from './definitions';
export { releaseIdentity } from './identity';
export { Graft };
