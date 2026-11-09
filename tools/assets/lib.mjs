// Small 4x4 matrix / vector helpers (column-major, like glTF) used by the Haven asset pipeline.
export const I4 = () => new Float32Array([1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]);
export function mul(a, b) { // a * b
  const o = new Float32Array(16);
  for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) {
    let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
    o[c * 4 + r] = s;
  }
  return o;
}
export const T = (x, y, z) => { const m = I4(); m[12] = x; m[13] = y; m[14] = z; return m; };
export const pos = (m) => [m[12], m[13], m[14]];
export const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
export const len = (a) => Math.hypot(a[0], a[1], a[2]);
export const norm = (a) => { const l = len(a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
export const cross = (a, b) => [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]];
export const dot = (a, b) => a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
export function xformPoint(m, p) { return [m[0]*p[0]+m[4]*p[1]+m[8]*p[2]+m[12], m[1]*p[0]+m[5]*p[1]+m[9]*p[2]+m[13], m[2]*p[0]+m[6]*p[1]+m[10]*p[2]+m[14]]; }
export function xformDir(m, p) { return [m[0]*p[0]+m[4]*p[1]+m[8]*p[2], m[1]*p[0]+m[5]*p[1]+m[9]*p[2], m[2]*p[0]+m[6]*p[1]+m[10]*p[2]]; }
/** Rotation (as a 4x4) taking unit vector a onto unit vector b (Rodrigues). */
export function rotFromTo(a, b) {
  const v = cross(a, b), c = dot(a, b), s = len(v);
  const m = I4();
  if (s < 1e-9) { return m; }
  const k = norm(v), t = Math.atan2(s, c), ct = Math.cos(t), st = Math.sin(t), u = 1 - ct;
  const [x, y, z] = k;
  m[0] = ct + x*x*u;     m[4] = x*y*u - z*st; m[8]  = x*z*u + y*st;
  m[1] = y*x*u + z*st;   m[5] = ct + y*y*u;   m[9]  = y*z*u - x*st;
  m[2] = z*x*u - y*st;   m[6] = z*y*u + x*st; m[10] = ct + z*z*u;
  return m;
}
/** Rotation about the point p. */
export const about = (p, R) => mul(mul(T(p[0], p[1], p[2]), R), T(-p[0], -p[1], -p[2]));
