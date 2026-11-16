/*
 * Welt: Tile-Typen, Räume, Dungeon-Karte (Kollision, Sichtlinie, Fog-of-War),
 * prozeduraler Generator (BSP + Prim-MST + Doppel-BFS) und A*-Pfadfindung.
 */
'use strict';
(function () {
  const CH = window.CH;
  const TS = CH.Config.TILE_SIZE;

  // Tile-Typen als kleine Ganzzahlen für schnelle Arrays
  const Tile = Object.freeze({ VOID: 0, WALL: 1, FLOOR: 2, CORRIDOR: 3, SPIKE_TRAP: 4, PILLAR: 5 });
  const SOLID = [true, true, false, false, false, true];
  const BLOCKS_LIGHT = [true, true, false, false, false, true];
  CH.Tile = Tile;

  const Vis = Object.freeze({ UNSEEN: 0, EXPLORED: 1, VISIBLE: 2 });
  CH.Vis = Vis;

  class Room {
    constructor(id, tileX, tileY, width, height, type) {
      this.id = id;
      this.tileX = tileX; this.tileY = tileY;
      this.width = width; this.height = height;
      this.type = type; // 'START' | 'COMBAT' | 'TREASURE' | 'SHRINE' | 'BOSS'
      this.discovered = false;
      this.cleared = false;
      this.neighbors = [];
    }
    centerTileX() { return this.tileX + Math.trunc(this.width / 2); }
    centerTileY() { return this.tileY + Math.trunc(this.height / 2); }
    centerWorld() { return new CH.Vec((this.centerTileX() + 0.5) * TS, (this.centerTileY() + 0.5) * TS); }
    containsTile(tx, ty) {
      return tx >= this.tileX && tx < this.tileX + this.width && ty >= this.tileY && ty < this.tileY + this.height;
    }
  }
  CH.Room = Room;

  // -------------------------------------------------------------------------
  class DungeonMap {
    constructor(cols, rows, floorNumber, seed, tiles, rooms) {
      this.cols = cols; this.rows = rows;
      this.floorNumber = floorNumber; this.seed = seed;
      this.tiles = tiles; // Uint8Array(rows*cols)
      this.rooms = rooms.slice();
      this.visibility = new Uint8Array(rows * cols);
      this.lightLevel = new Float32Array(rows * cols);
      this.torches = [];
      this.startRoom = null;
      this.bossRoom = null;
    }

    inBounds(tx, ty) { return tx >= 0 && tx < this.cols && ty >= 0 && ty < this.rows; }
    getTile(tx, ty) { return this.inBounds(tx, ty) ? this.tiles[ty * this.cols + tx] : Tile.VOID; }
    isSolid(tx, ty) { return SOLID[this.getTile(tx, ty)]; }
    isWalkable(tx, ty) { return !this.isSolid(tx, ty); }
    blocksLight(tx, ty) { return BLOCKS_LIGHT[this.getTile(tx, ty)]; }
    getVisibility(tx, ty) { return this.inBounds(tx, ty) ? this.visibility[ty * this.cols + tx] : Vis.UNSEEN; }
    getLightLevel(tx, ty) { return this.inBounds(tx, ty) ? this.lightLevel[ty * this.cols + tx] : 0; }
    worldToTile(w) { return Math.floor(w / TS); }
    tileCenterWorld(tx, ty) { return new CH.Vec((tx + 0.5) * TS, (ty + 0.5) * TS); }

    getRoomAt(wx, wy) {
      const tx = this.worldToTile(wx), ty = this.worldToTile(wy);
      for (const r of this.rooms) if (r.containsTile(tx, ty)) return r;
      return null;
    }

    moveWithCollision(x, y, dx, dy, radius) {
      const nextX = x + dx;
      if (this.canMoveTo(x, y, nextX, y, radius)) x = nextX;
      const nextY = y + dy;
      if (this.canMoveTo(x, y, x, nextY, radius)) y = nextY;
      return new CH.Vec(x, y);
    }

    /** Ziel frei, oder ein bereits feststeckendes Objekt verringert seine Überlappung. */
    canMoveTo(fx, fy, tx, ty, radius) {
      if (!this.collidesCircleWithWalls(tx, ty, radius)) return true;
      const before = this.wallPenetration(fx, fy, radius);
      return before > 0 && this.wallPenetration(tx, ty, radius) < before;
    }

    wallPenetration(cx, cy, radius) {
      let worst = 0;
      const minTx = this.worldToTile(cx - radius), maxTx = this.worldToTile(cx + radius);
      const minTy = this.worldToTile(cy - radius), maxTy = this.worldToTile(cy + radius);
      for (let ty = minTy; ty <= maxTy; ty++) {
        for (let tx = minTx; tx <= maxTx; tx++) {
          if (this.isSolid(tx, ty)) {
            const l = tx * TS, t = ty * TS;
            const qx = Math.max(l, Math.min(cx, l + TS));
            const qy = Math.max(t, Math.min(cy, t + TS));
            worst = Math.max(worst, radius - Math.hypot(cx - qx, cy - qy));
          }
        }
      }
      return worst;
    }

    collidesCircleWithWalls(cx, cy, radius) {
      const minTx = this.worldToTile(cx - radius), maxTx = this.worldToTile(cx + radius);
      const minTy = this.worldToTile(cy - radius), maxTy = this.worldToTile(cy + radius);
      const r2 = radius * radius;
      for (let ty = minTy; ty <= maxTy; ty++) {
        for (let tx = minTx; tx <= maxTx; tx++) {
          if (this.isSolid(tx, ty)) {
            const l = tx * TS, t = ty * TS;
            const qx = Math.max(l, Math.min(cx, l + TS));
            const qy = Math.max(t, Math.min(cy, t + TS));
            const ddx = cx - qx, ddy = cy - qy;
            if (ddx * ddx + ddy * ddy < r2) return true;
          }
        }
      }
      return false;
    }

    hasLineOfSight(x0, y0, x1, y1) {
      return this.hasTileLineOfSight(this.worldToTile(x0), this.worldToTile(y0), this.worldToTile(x1), this.worldToTile(y1));
    }

    /** Bresenham-Linie über das Tile-Gitter. */
    hasTileLineOfSight(x0, y0, x1, y1) {
      const dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
      const sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
      let err = dx - dy;
      let cx = x0, cy = y0;
      for (;;) {
        if (!this.inBounds(cx, cy)) return false;
        if ((cx !== x0 || cy !== y0) && (cx !== x1 || cy !== y1) && BLOCKS_LIGHT[this.tiles[cy * this.cols + cx]]) return false;
        if (cx === x1 && cy === y1) return true;
        const e2 = 2 * err;
        if (e2 > -dy) { err -= dy; cx += sx; }
        if (e2 < dx) { err += dx; cy += sy; }
      }
    }

    /** Fog-of-War per Recursive Shadowcasting (8 Oktanten) mit Lichtabfall. */
    updateFogOfWar(px, py, radiusTiles) {
      const vis = this.visibility, light = this.lightLevel;
      for (let i = 0; i < vis.length; i++) {
        if (vis[i] === Vis.VISIBLE) vis[i] = Vis.EXPLORED;
        light[i] = vis[i] === Vis.EXPLORED ? 0.25 : 0.0;
      }
      const ox = this.worldToTile(px), oy = this.worldToTile(py);
      if (this.inBounds(ox, oy)) {
        vis[oy * this.cols + ox] = Vis.VISIBLE;
        light[oy * this.cols + ox] = 1.0;
      }
      for (let oct = 0; oct < 8; oct++) this.castLight(ox, oy, radiusTiles, 1, 1.0, 0.0, oct);

      for (const room of this.rooms) {
        if (!room.discovered) {
          const cx = room.centerTileX(), cy = room.centerTileY();
          if (this.inBounds(cx, cy) && vis[cy * this.cols + cx] === Vis.VISIBLE) room.discovered = true;
        }
      }
    }

    castLight(ox, oy, radius, row, startSlope, endSlope, octant) {
      if (startSlope < endSlope) return;
      let nextStartSlope = startSlope;
      for (let i = row; i <= radius; i++) {
        let blocked = false;
        for (let dx = -i, dy = -i; dx <= 0; dx++) {
          const lSlope = (dx - 0.5) / (dy + 0.5);
          const rSlope = (dx + 0.5) / (dy - 0.5);
          if (startSlope < rSlope) continue;
          if (endSlope > lSlope) break;

          let mx, my;
          switch (octant) {
            case 0: mx = dx; my = dy; break;
            case 1: mx = dy; my = dx; break;
            case 2: mx = -dy; my = dx; break;
            case 3: mx = -dx; my = dy; break;
            case 4: mx = -dx; my = -dy; break;
            case 5: mx = -dy; my = -dx; break;
            case 6: mx = dy; my = -dx; break;
            default: mx = dx; my = -dy; break;
          }
          const ax = ox + mx, ay = oy + my;
          const distSq = dx * dx + dy * dy;
          const inb = this.inBounds(ax, ay);
          if (inb && distSq <= radius * radius) {
            const idx = ay * this.cols + ax;
            this.visibility[idx] = Vis.VISIBLE;
            const falloff = 1.0 - Math.sqrt(distSq) / (radius + 0.5);
            this.lightLevel[idx] = Math.max(this.lightLevel[idx], 0.35 + 0.65 * falloff);
          }
          const opaque = !inb || BLOCKS_LIGHT[this.tiles[ay * this.cols + ax]];
          if (blocked) {
            if (opaque) {
              nextStartSlope = rSlope;
            } else {
              blocked = false;
              startSlope = nextStartSlope;
            }
          } else if (opaque && i < radius) {
            blocked = true;
            this.castLight(ox, oy, radius, i + 1, startSlope, lSlope, octant);
            nextStartSlope = rSlope;
          }
        }
        if (blocked) break;
      }
    }
  }
  CH.DungeonMap = DungeonMap;

  // -------------------------------------------------------------------------
  // Generator
  // -------------------------------------------------------------------------
  const MIN_LEAF = 15, MAX_LEAF = 24;

  /**
   * Iterationsreihenfolge eines java.util.HashSet<Integer> nachbilden
   * (Bucket = Wert & (Kapazität-1), innerhalb eines Buckets Einfügereihenfolge),
   * damit die MST-Kanten bei Gleichstand exakt wie in Java gewählt werden.
   */
  function javaHashSetOrder(insertionOrder) {
    let cap = 16;
    while (insertionOrder.length > cap * 0.75) cap *= 2;
    const mask = cap - 1;
    return insertionOrder
      .map((v, i) => ({ v, i }))
      .sort((a, b) => ((a.v & mask) - (b.v & mask)) || (a.i - b.i))
      .map(o => o.v);
  }

  class DungeonGenerator {
    generate(floorNumber, seed) {
      const rng = new CH.SeededRandom(CH.Long.xor(seed, CH.Long.mul(floorNumber, 0x9E3779B97F4A7C15n)));
      const cols = CH.Config.BASE_MAP_COLS + (floorNumber - 1) * 8;
      const rows = CH.Config.BASE_MAP_ROWS + (floorNumber - 1) * 6;
      const grid = new Uint8Array(rows * cols); // alles VOID
      const G = { cols, rows, get: (x, y) => grid[y * cols + x], set: (x, y, t) => { grid[y * cols + x] = t; } };

      const root = { x: 2, y: 2, w: cols - 4, h: rows - 4, left: null, right: null };
      this.splitRecursive(root, rng, 0);

      let rooms = [];
      this.collectRooms(root, rooms, rng);
      if (rooms.length < 4) {
        rooms = [
          new Room(0, 4, 4, 12, 10, 'COMBAT'),
          new Room(1, cols - 18, 4, 12, 10, 'COMBAT'),
          new Room(2, 4, rows - 16, 12, 10, 'COMBAT'),
          new Room(3, cols - 18, rows - 16, 12, 10, 'COMBAT'),
        ];
      }
      for (const r of rooms) {
        for (let ty = r.tileY; ty < r.tileY + r.height; ty++)
          for (let tx = r.tileX; tx < r.tileX + r.width; tx++) G.set(tx, ty, Tile.FLOOR);
      }

      const edges = this.connectRooms(rooms, rng);
      for (const e of edges) {
        const ra = rooms[e.a], rb = rooms[e.b];
        ra.neighbors.push(rb.id);
        rb.neighbors.push(ra.id);
        this.carveCorridor(G, ra.centerTileX(), ra.centerTileY(), rb.centerTileX(), rb.centerTileY(), rng);
      }

      const far0 = this.findFarthest(rooms, 0);
      const opp = this.findFarthest(rooms, far0);
      const startRoom = rooms[far0], bossRoom = rooms[opp];
      startRoom.type = 'START'; startRoom.cleared = true; startRoom.discovered = true;
      bossRoom.type = 'BOSS';

      const middle = rooms.filter(r => r !== startRoom && r !== bossRoom);
      if (middle.length >= 2) {
        const t = middle.splice(rng.nextInt(middle.length), 1)[0];
        t.type = 'TREASURE'; t.cleared = true;
      }
      if (middle.length >= 2) {
        const s = middle.splice(rng.nextInt(middle.length), 1)[0];
        s.type = 'SHRINE'; s.cleared = true;
      }

      this.buildWalls(G);
      this.decorate(G, rooms, rng);

      const map = new DungeonMap(cols, rows, floorNumber, seed, grid, rooms);
      map.startRoom = startRoom;
      map.bossRoom = bossRoom;
      this.placeTorches(map, rooms, rng);
      return map;
    }

    splitRecursive(node, rng, depth) {
      if (depth >= 5) return;
      const canH = node.h >= MIN_LEAF * 2, canV = node.w >= MIN_LEAF * 2;
      if (!canH && !canV) return;
      if (node.w <= MAX_LEAF && node.h <= MAX_LEAF && rng.chance(0.25)) return;
      let horiz;
      if (canH && canV) {
        if (node.w > node.h * 1.25) horiz = false;
        else if (node.h > node.w * 1.25) horiz = true;
        else horiz = rng.nextBoolean();
      } else {
        horiz = canH;
      }
      if (horiz) {
        const sy = rng.nextInt(MIN_LEAF, node.h - MIN_LEAF);
        node.left = { x: node.x, y: node.y, w: node.w, h: sy, left: null, right: null };
        node.right = { x: node.x, y: node.y + sy, w: node.w, h: node.h - sy, left: null, right: null };
      } else {
        const sx = rng.nextInt(MIN_LEAF, node.w - MIN_LEAF);
        node.left = { x: node.x, y: node.y, w: sx, h: node.h, left: null, right: null };
        node.right = { x: node.x + sx, y: node.y, w: node.w - sx, h: node.h, left: null, right: null };
      }
      this.splitRecursive(node.left, rng, depth + 1);
      this.splitRecursive(node.right, rng, depth + 1);
    }

    collectRooms(node, rooms, rng) {
      if (!node) return;
      if (!node.left && !node.right) {
        const maxW = Math.max(7, node.w - 4), maxH = Math.max(7, node.h - 4);
        const rw = rng.nextInt(Math.min(8, maxW), Math.min(15, maxW));
        const rh = rng.nextInt(Math.min(8, maxH), Math.min(13, maxH));
        const rx = node.x + 2 + rng.nextInt(Math.max(1, node.w - rw - 3));
        const ry = node.y + 2 + rng.nextInt(Math.max(1, node.h - rh - 3));
        rooms.push(new Room(rooms.length, rx, ry, rw, rh, 'COMBAT'));
      } else {
        this.collectRooms(node.left, rooms, rng);
        this.collectRooms(node.right, rooms, rng);
      }
    }

    connectRooms(rooms, rng) {
      const n = rooms.length;
      const visitedOrder = [0];
      const visited = new Set([0]);
      const mst = [];
      const used = new Set();
      const dist = (a, b) => {
        const dx = rooms[a].centerTileX() - rooms[b].centerTileX();
        const dy = rooms[a].centerTileY() - rooms[b].centerTileY();
        return Math.sqrt(dx * dx + dy * dy);
      };
      const key = (a, b) => Math.min(a, b) * 4096 + Math.max(a, b);

      while (visited.size < n) {
        let best = null;
        for (const u of javaHashSetOrder(visitedOrder)) {
          for (let v = 0; v < n; v++) {
            if (visited.has(v)) continue;
            const d = dist(u, v);
            if (best === null || d < best.w) best = { a: u, b: v, w: d };
          }
        }
        if (!best) break;
        mst.push(best);
        visited.add(best.b);
        visitedOrder.push(best.b);
        used.add(key(best.a, best.b));
      }

      const cands = [];
      for (let i = 0; i < n; i++)
        for (let j = i + 1; j < n; j++)
          if (!used.has(key(i, j))) cands.push({ a: i, b: j, w: dist(i, j) });
      cands.sort((p, q) => p.w - q.w); // stabil wie Javas TimSort
      const target = Math.max(1, Math.trunc(n / 4));
      let added = 0;
      for (const c of cands) {
        if (added >= target) break;
        if (rng.chance(0.75)) { mst.push(c); added++; }
      }
      return mst;
    }

    carveCorridor(G, x0, y0, x1, y1, rng) {
      if (rng.nextBoolean()) {
        this.carveH(G, x0, x1, y0);
        this.carveV(G, y0, y1, x1);
      } else {
        this.carveV(G, y0, y1, x0);
        this.carveH(G, x0, x1, y1);
      }
    }

    carveH(G, x0, x1, y) {
      for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
        for (let dy = -1; dy <= 1; dy++) {
          const ny = y + dy;
          if (ny > 0 && ny < G.rows - 1 && x > 0 && x < G.cols - 1 && G.get(x, ny) === Tile.VOID) G.set(x, ny, Tile.CORRIDOR);
        }
      }
    }

    carveV(G, y0, y1, x) {
      for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
        for (let dx = -1; dx <= 1; dx++) {
          const nx = x + dx;
          if (y > 0 && y < G.rows - 1 && nx > 0 && nx < G.cols - 1 && G.get(nx, y) === Tile.VOID) G.set(nx, y, Tile.CORRIDOR);
        }
      }
    }

    findFarthest(rooms, start) {
      const dist = new Map([[start, 0]]);
      const q = [start];
      let far = start, maxD = 0;
      while (q.length) {
        const cur = q.shift();
        const d = dist.get(cur);
        if (d > maxD) { maxD = d; far = cur; }
        for (const nx of rooms[cur].neighbors) {
          if (!dist.has(nx)) { dist.set(nx, d + 1); q.push(nx); }
        }
      }
      return far;
    }

    buildWalls(G) {
      for (let y = 0; y < G.rows; y++) {
        for (let x = 0; x < G.cols; x++) {
          if (G.get(x, y) !== Tile.VOID) continue;
          let near = false;
          for (let dy = -1; dy <= 1 && !near; dy++) {
            for (let dx = -1; dx <= 1; dx++) {
              const nx = x + dx, ny = y + dy;
              if (nx >= 0 && nx < G.cols && ny >= 0 && ny < G.rows) {
                const t = G.get(nx, ny);
                if (t === Tile.FLOOR || t === Tile.CORRIDOR) { near = true; break; }
              }
            }
          }
          if (near) G.set(x, y, Tile.WALL);
        }
      }
    }

    decorate(G, rooms, rng) {
      for (const r of rooms) {
        if (r.type === 'COMBAT' && r.width >= 10 && r.height >= 9) {
          if (rng.chance(0.65)) {
            const px1 = r.tileX + 2, px2 = r.tileX + r.width - 3;
            const py1 = r.tileY + 2, py2 = r.tileY + r.height - 3;
            if (this.innerSafe(r, px1, py1)) G.set(px1, py1, Tile.PILLAR);
            if (this.innerSafe(r, px2, py1)) G.set(px2, py1, Tile.PILLAR);
            if (this.innerSafe(r, px1, py2)) G.set(px1, py2, Tile.PILLAR);
            if (this.innerSafe(r, px2, py2)) G.set(px2, py2, Tile.PILLAR);
          }
          if (rng.chance(0.55)) {
            const traps = rng.nextInt(1, 3);
            for (let i = 0; i < traps; i++) {
              const tx = rng.nextInt(r.tileX + 3, r.tileX + r.width - 4);
              const ty = rng.nextInt(r.tileY + 3, r.tileY + r.height - 4);
              if (G.get(tx, ty) === Tile.FLOOR && (tx !== r.centerTileX() || ty !== r.centerTileY())) G.set(tx, ty, Tile.SPIKE_TRAP);
            }
          }
        } else if (r.type === 'BOSS' && r.width >= 11 && r.height >= 10) {
          G.set(r.tileX + 2, r.tileY + 2, Tile.PILLAR);
          G.set(r.tileX + r.width - 3, r.tileY + 2, Tile.PILLAR);
          G.set(r.tileX + 2, r.tileY + r.height - 3, Tile.PILLAR);
          G.set(r.tileX + r.width - 3, r.tileY + r.height - 3, Tile.PILLAR);
        }
      }
    }

    innerSafe(r, tx, ty) {
      return tx > r.tileX + 1 && tx < r.tileX + r.width - 2 && ty > r.tileY + 1 && ty < r.tileY + r.height - 2
        && (tx !== r.centerTileX() || ty !== r.centerTileY());
    }

    placeTorches(map, rooms, rng) {
      for (const r of rooms) {
        const corners = [
          [r.tileX + 1, r.tileY + 1], [r.tileX + r.width - 2, r.tileY + 1],
          [r.tileX + 1, r.tileY + r.height - 2], [r.tileX + r.width - 2, r.tileY + r.height - 2],
        ];
        for (const c of corners) {
          if (map.isWalkable(c[0], c[1]) && rng.chance(0.75)) map.torches.push(map.tileCenterWorld(c[0], c[1]));
        }
      }
    }
  }
  CH.DungeonGenerator = DungeonGenerator;

  // -------------------------------------------------------------------------
  // A*-Pfadfindung (Oktil-Heuristik, kein Corner-Cutting, Knotenlimit)
  // -------------------------------------------------------------------------
  const DIRS = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]];
  const MAX_EXPANDED = 1800;

  /** Kleiner binärer Min-Heap. Bei gleichem f gewinnt der früher eingefügte Knoten. */
  class MinHeap {
    constructor() { this.a = []; this.counter = 0; }
    get size() { return this.a.length; }
    less(i, j) { const x = this.a[i], y = this.a[j]; return x.f < y.f || (x.f === y.f && x.n < y.n); }
    push(node) {
      node.n = this.counter++;
      const a = this.a; a.push(node);
      let i = a.length - 1;
      while (i > 0) {
        const p = (i - 1) >> 1;
        if (!this.less(i, p)) break;
        [a[i], a[p]] = [a[p], a[i]]; i = p;
      }
    }
    pop() {
      const a = this.a; const top = a[0]; const last = a.pop();
      if (a.length) {
        a[0] = last;
        let i = 0;
        for (;;) {
          const l = 2 * i + 1, r = l + 1; let m = i;
          if (l < a.length && this.less(l, m)) m = l;
          if (r < a.length && this.less(r, m)) m = r;
          if (m === i) break;
          [a[i], a[m]] = [a[m], a[i]]; i = m;
        }
      }
      return top;
    }
  }

  function octile(x0, y0, x1, y1) {
    const dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
    return (dx + dy) + (1.41421356 - 2.0) * Math.min(dx, dy);
  }

  class AStarPathfinder {
    findPath(map, start, goal) {
      const sx = map.worldToTile(start.x), sy = map.worldToTile(start.y);
      const gx = map.worldToTile(goal.x), gy = map.worldToTile(goal.y);
      if (!map.inBounds(sx, sy) || !map.inBounds(gx, gy) || !map.isWalkable(gx, gy)) return [];
      if (sx === gx && sy === gy) return [goal];

      const cols = map.cols;
      const keyOf = (x, y) => y * cols + x;
      const bestG = new Map(), parent = new Map(), closed = new Set();
      const open = new MinHeap();
      const startKey = keyOf(sx, sy), goalKey = keyOf(gx, gy);
      bestG.set(startKey, 0);
      open.push({ x: sx, y: sy, g: 0, f: octile(sx, sy, gx, gy) });

      let expanded = 0;
      while (open.size > 0 && expanded < MAX_EXPANDED) {
        const cur = open.pop();
        const ck = keyOf(cur.x, cur.y);
        if (closed.has(ck)) continue;
        if (ck === goalKey) {
          const path = [];
          let k = goalKey;
          while (k !== startKey) {
            path.push(map.tileCenterWorld(k % cols, Math.floor(k / cols)));
            const p = parent.get(k);
            if (p === undefined) break;
            k = p;
          }
          return path.reverse();
        }
        closed.add(ck);
        expanded++;
        for (const d of DIRS) {
          const nx = cur.x + d[0], ny = cur.y + d[1];
          if (!map.inBounds(nx, ny) || !map.isWalkable(nx, ny)) continue;
          if (d[0] !== 0 && d[1] !== 0) {
            if (!map.isWalkable(cur.x + d[0], cur.y) || !map.isWalkable(cur.x, cur.y + d[1])) continue;
          }
          const nk = keyOf(nx, ny);
          if (closed.has(nk)) continue;
          const tg = cur.g + ((d[0] !== 0 && d[1] !== 0) ? 1.41421356 : 1.0);
          const old = bestG.has(nk) ? bestG.get(nk) : Infinity;
          if (tg < old) {
            bestG.set(nk, tg);
            parent.set(nk, ck);
            open.push({ x: nx, y: ny, g: tg, f: tg + octile(nx, ny, gx, gy) });
          }
        }
      }
      return [];
    }
  }
  CH.AStarPathfinder = AStarPathfinder;
})();
