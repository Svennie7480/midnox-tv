'use strict';
/* Gezeichnete Straßenszenen für bestehende Fragen (Gefahrenlehre & Verhalten) */
const IMG = (id, street) => { const q = QDATA.find(x => x.id === 'h-' + id); if (q) q.img = { street: Object.assign({ seed: id.length * 13 + id.charCodeAt(id.length - 1) }, street) }; };
const ME = { t: 'car', x: 'r', y: 330, me: true, move: 40 };
IMG('g01', { items: [{ t: 'car', x: 'pr', y: 70, color: 'weiss' }, { t: 'car', x: 'pr', y: 160, color: 'rot' }, { t: 'ball', x: 262, y: 116, trail: true }, ME] });
IMG('g02', { items: [{ t: 'stop', x: 312, y: 120 }, { t: 'bus', x: 'pr', y: 150, color: '#f59e0b', warn: true }, { t: 'kid', x: 316, y: 170, color: '#ef4444', rot: -90 }, { t: 'kid', x: 322, y: 196, color: '#22c55e' }, { t: 'kid', x: 252, y: 98, color: '#8b5cf6', rot: -90 }, ME] });
IMG('g11', { items: [{ t: 'ped', x: 282, y: 140, color: '#64748b', hair: '#e5e7eb' }, ME] });
IMG('g12', { items: [{ t: 'car', x: 'pr', y: 210, color: 'gruen' }, { t: 'kid', x: 300, y: 150, color: '#f97316', rot: -90 }, ME] });
IMG('g31', { items: [{ t: 'bike', x: 236, y: 140, color: '#0ea5e9' }, { t: 'bike', x: 236, y: 186, color: '#e11d48' }, { t: 'car', x: 'l', y: 50, rot: 180, color: 'rot' }, ME] });
IMG('g37', { items: [{ t: 'car', x: 'pr', y: 60, color: 'schwarz' }, { t: 'car', x: 'pr', y: 150, color: 'weiss', door: 'left' }, { t: 'car', x: 'pr', y: 240, color: 'gruen' }, ME] });
IMG('g48', { items: [{ t: 'zebra', y: 140 }, { t: 'ped', x: 304, y: 124, color: '#e11d48', rot: -90 }, ME] });
IMG('g51', { items: [{ t: 'bike', x: 310, y: 150, color: '#22c55e', rot: -90 }, { t: 'car', x: 'pr', y: 230, color: 'lila' }, ME] });
IMG('g53', { items: [{ t: 'bike', x: 240, y: 170, color: '#f97316', rot: -12 }, { t: 'car', x: 'l', y: 70, rot: 180, color: 'weiss' }, ME] });
IMG('g55', { items: [{ t: 'car', x: 'pr', y: 60, color: 'weiss' }, { t: 'car', x: 'pr', y: 200, color: 'schwarz' }, { t: 'car', x: 'r', y: 160, color: 'rot', blink: 'right', brake: true }, ME] });
IMG('v57', { items: [{ t: 'stop', x: 88, y: 200 }, { t: 'bus', x: 'pl', y: 170, rot: 180, color: '#f59e0b', warn: true }, { t: 'kid', x: 86, y: 140, color: '#22c55e' }, { t: 'kid', x: 152, y: 120, color: '#ef4444', rot: 90 }, ME] });
IMG('v39', { items: [{ t: 'car', x: 'r', y: 150, color: 'weiss' }, { t: 'car', x: 'r', y: 330, color: 'weiss', blue: true, label: 'P' }, { t: 'car', x: 'r', y: 240, me: true }] });
IMG('v48', { items: [{ t: 'stop', x: 312, y: 110 }, { t: 'bus', x: 'pr', y: 150, color: '#facc15' }, { t: 'ped', x: 300, y: 196, color: '#0ea5e9', rot: -90 }, ME] });
IMG('v55', { items: [{ t: 'ped', x: 302, y: 150, color: '#1f2937', rot: -90, hair: '#d1d5db', cane: true }, ME] });
IMG('kb09', { items: [{ t: 'truck', x: 'r', y: 170, color: '#e2e8f0' }, { t: 'car', x: 'l', y: 40, rot: 180, color: 'rot' }, { t: 'car', x: 'l', y: 290, me: true, move: 30 }] });
