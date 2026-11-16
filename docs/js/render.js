/*
 * Rendering: kleine Java2D-ähnliche Grafik-Schicht über Canvas 2D,
 * UI-Hilfen, Welt-Renderer und HUD.
 */
'use strict';
(function () {
  const CH = window.CH;
  const { rgb, Config, Color, Vis, Tile } = CH;
  const W = Config.LOGICAL_WIDTH, H = Config.LOGICAL_HEIGHT;
  const SANS = '"Segoe UI", Inter, Roboto, "Helvetica Neue", Arial, sans-serif';
  const MONO = 'Consolas, "SF Mono", Menlo, "DejaVu Sans Mono", monospace';

  /**
   * Java2D-artige Zeichenfläche. Farbe, Strichstärke und Schrift bleiben – wie in
   * Java – auch nach dem Zurücksetzen der Transformation erhalten.
   */
  class G {
    constructor(ctx) {
      this.ctx = ctx;
      this.style = '#000';
      this.lineWidth = 1;
      this.lineCap = 'butt';
      this.lineJoin = 'miter';
      this.dash = null;
      this.font = `12px ${SANS}`;
      this._appliedFont = null;
    }
    setColor(c) { this.style = c.css; }
    setPaint(p) { this.style = p; }
    setStroke(width, cap, join, dash) {
      this.lineWidth = width;
      this.lineCap = cap || 'butt';
      this.lineJoin = join || 'miter';
      this.dash = dash || null;
    }
    setFont(size, bold, mono) { this.font = `${bold ? 'bold ' : ''}${size}px ${mono ? MONO : SANS}`; }
    applyFill() { this.ctx.fillStyle = this.style; }
    applyStroke() {
      const c = this.ctx;
      c.strokeStyle = this.style;
      c.lineWidth = this.lineWidth;
      c.lineCap = this.lineCap;
      c.lineJoin = this.lineJoin;
      c.setLineDash(this.dash || []);
    }
    applyFont() {
      if (this._appliedFont !== this.font || this.ctx.font !== this.font) {
        this.ctx.font = this.font;
        this._appliedFont = this.font;
      }
    }
    stringWidth(s) { this.applyFont(); return this.ctx.measureText(s).width; }
    ascent() { this.applyFont(); const m = this.ctx.measureText('Hg'); return m.actualBoundingBoxAscent || 10; }

    fillRect(x, y, w, h) { this.applyFill(); this.ctx.fillRect(x, y, w, h); }
    drawRect(x, y, w, h) { this.applyStroke(); this.ctx.strokeRect(x + 0.5, y + 0.5, w, h); }
    ellipsePath(x, y, w, h) {
      const c = this.ctx;
      c.beginPath();
      c.ellipse(x + w / 2, y + h / 2, Math.abs(w / 2), Math.abs(h / 2), 0, 0, Math.PI * 2);
    }
    fillOval(x, y, w, h) { if (w <= 0 || h <= 0) return; this.applyFill(); this.ellipsePath(x, y, w, h); this.ctx.fill(); }
    drawOval(x, y, w, h) { if (w <= 0 || h <= 0) return; this.applyStroke(); this.ellipsePath(x, y, w, h); this.ctx.stroke(); }
    roundRectPath(x, y, w, h, arc) {
      const r = Math.max(0, Math.min(arc / 2, w / 2, h / 2));
      const c = this.ctx;
      c.beginPath();
      c.moveTo(x + r, y);
      c.lineTo(x + w - r, y);
      c.arcTo(x + w, y, x + w, y + r, r);
      c.lineTo(x + w, y + h - r);
      c.arcTo(x + w, y + h, x + w - r, y + h, r);
      c.lineTo(x + r, y + h);
      c.arcTo(x, y + h, x, y + h - r, r);
      c.lineTo(x, y + r);
      c.arcTo(x, y, x + r, y, r);
      c.closePath();
    }
    fillRoundRect(x, y, w, h, arc) { if (w <= 0 || h <= 0) return; this.applyFill(); this.roundRectPath(x, y, w, h, arc); this.ctx.fill(); }
    drawRoundRect(x, y, w, h, arc) { this.applyStroke(); this.roundRectPath(x + 0.5, y + 0.5, w, h, arc); this.ctx.stroke(); }
    polyPath(xs, ys) {
      const c = this.ctx;
      c.beginPath();
      c.moveTo(xs[0], ys[0]);
      for (let i = 1; i < xs.length; i++) c.lineTo(xs[i], ys[i]);
      c.closePath();
    }
    fillPolygon(xs, ys) { this.applyFill(); this.polyPath(xs, ys); this.ctx.fill(); }
    drawPolygon(xs, ys) { this.applyStroke(); this.polyPath(xs, ys); this.ctx.stroke(); }
    drawLine(x1, y1, x2, y2) {
      this.applyStroke();
      const c = this.ctx;
      c.beginPath(); c.moveTo(x1, y1); c.lineTo(x2, y2); c.stroke();
    }
    /** Wie Graphics.drawArc: Winkel in Grad, gegen den Uhrzeigersinn ab 3 Uhr. */
    drawArc(x, y, w, h, startDeg, extentDeg) {
      this.applyStroke();
      const c = this.ctx;
      const a0 = -startDeg * Math.PI / 180;
      const a1 = -(startDeg + extentDeg) * Math.PI / 180;
      c.beginPath();
      c.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, a0, a1, extentDeg > 0);
      c.stroke();
    }
    drawString(s, x, y) { this.applyFont(); this.applyFill(); this.ctx.fillText(s, x, y); }
    rotate(theta, cx, cy) {
      const c = this.ctx;
      c.translate(cx, cy); c.rotate(theta); c.translate(-cx, -cy);
    }
    getTransform() { return this.ctx.getTransform(); }
    setTransform(m) { this.ctx.setTransform(m); }
    setAlpha(a) { this.ctx.globalAlpha = a; }
    linearGradient(x0, y0, c0, x1, y1, c1) {
      const gr = this.ctx.createLinearGradient(x0, y0, x1, y1);
      gr.addColorStop(0, c0.css); gr.addColorStop(1, c1.css);
      return gr;
    }
    radialGradient(cx, cy, r, stops) {
      const gr = this.ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
      for (const [o, col] of stops) gr.addColorStop(o, col.css);
      return gr;
    }
  }
  CH.G = G;

  // -------------------------------------------------------------------------
  const UiHelper = {
    isHovered(r, input) { return input.mouseX >= r.x && input.mouseX < r.x + r.w && input.mouseY >= r.y && input.mouseY < r.y + r.h; },
    isClicked(r, input) { return this.isHovered(r, input) && input.wasMousePressed(CH.Input.BUTTON1); },
    drawButton(g, r, text, hovered, accent) {
      g.setColor(hovered ? rgb(36, 44, 72) : rgb(20, 24, 40));
      g.fillRoundRect(r.x, r.y, r.w, r.h, 10);
      g.setColor(hovered ? accent : rgb(75, 92, 130));
      g.setStroke(hovered ? 2.2 : 1.5);
      g.drawRoundRect(r.x, r.y, r.w, r.h, 10);
      g.setFont(15, true);
      const tw = g.stringWidth(text);
      g.setColor(hovered ? Color.WHITE : rgb(218, 226, 245));
      g.drawString(text, r.x + (r.w - tw) / 2, r.y + r.h / 2 + 5);
    },
    drawCentered(g, text, cx, y, size, bold, color, mono) {
      g.setFont(size, bold, mono);
      g.setColor(color);
      g.drawString(text, cx - g.stringWidth(text) / 2, y);
    },
    wrapText(g, text, maxWidth) {
      const lines = [];
      if (!text) return lines;
      let cur = '';
      for (const w of text.split(/\s+/)) {
        if (!cur) cur = w;
        else if (g.stringWidth(cur + ' ' + w) <= maxWidth) cur += ' ' + w;
        else { lines.push(cur); cur = w; }
      }
      if (cur) lines.push(cur);
      return lines;
    },
  };
  CH.UiHelper = UiHelper;
  CH.rect = (x, y, w, h) => ({ x, y, w, h });

  // -------------------------------------------------------------------------
  // Welt-Renderer
  // -------------------------------------------------------------------------
  const BG = rgb(10, 11, 18);
  const PALETTE = {
    1: { floor: rgb(34, 34, 44), top: rgb(62, 62, 78), front: rgb(42, 42, 56) },
    2: { floor: rgb(33, 38, 52), top: rgb(56, 66, 90), front: rgb(38, 46, 64) },
    3: { floor: rgb(42, 26, 44), top: rgb(76, 42, 72), front: rgb(48, 26, 48) },
  };
  const FLOOR_SHADE = {};
  function floorColor(base, odd) {
    const key = base.css + odd;
    if (!FLOOR_SHADE[key]) {
      const s = odd ? -4 : 0;
      FLOOR_SHADE[key] = rgb(Math.max(0, base.r + s), Math.max(0, base.g + s), Math.max(0, base.b + s));
    }
    return FLOOR_SHADE[key];
  }
  const C = {
    wallEdge: rgb(255, 255, 255, 18), floorEdge: rgb(0, 0, 0, 35), pillarShadow: rgb(18, 18, 26, 130),
    fogExplored: rgb(6, 7, 14, 155), spikeBase: rgb(20, 20, 28), spikeRed: rgb(220, 75, 75), spikeEdge: rgb(235, 240, 250),
    spikeWarn: rgb(255, 165, 50, 145), spikeHole: rgb(45, 45, 58),
  };
  const fogCache = [];
  for (let a = 0; a <= 180; a++) fogCache.push(rgb(6, 7, 14, a));

  class WorldRenderer {
    render(g, session, mouseX, mouseY, time) {
      g.setColor(BG);
      g.fillRect(0, 0, W, H);
      const map = session.map, cam = session.camera;
      const camX = cam.getX(), camY = cam.getY();
      const ts = Config.TILE_SIZE;
      const pal = PALETTE[session.currentFloor] || PALETTE[1];
      const minTx = Math.max(0, Math.floor(camX / ts) - 1);
      const maxTx = Math.min(map.cols - 1, Math.ceil((camX + W) / ts) + 1);
      const minTy = Math.max(0, Math.floor(camY / ts) - 1);
      const maxTy = Math.min(map.rows - 1, Math.ceil((camY + H) / ts) + 1);

      for (let ty = minTy; ty <= maxTy; ty++) {
        for (let tx = minTx; tx <= maxTx; tx++) {
          if (map.getVisibility(tx, ty) === Vis.UNSEEN) continue;
          const tile = map.getTile(tx, ty);
          if (tile === Tile.VOID) continue;
          const sx = Math.round(tx * ts - camX), sy = Math.round(ty * ts - camY);
          if (tile === Tile.WALL) {
            g.setColor(pal.top); g.fillRect(sx, sy, ts, ts - 10);
            g.setColor(pal.front); g.fillRect(sx, sy + ts - 10, ts, 10);
            g.setColor(C.wallEdge); g.setStroke(1); g.drawRect(sx, sy, ts - 1, ts - 1);
          } else if (tile === Tile.PILLAR) {
            this.floorTile(g, sx, sy, ts, tx, ty, pal.floor);
            g.setColor(C.pillarShadow); g.fillOval(sx + 6, sy + ts - 12, ts - 12, 10);
            g.setColor(pal.front); g.fillRoundRect(sx + 8, sy + 6, ts - 16, ts - 12, 8);
            g.setColor(pal.top); g.fillRoundRect(sx + 8, sy + 2, ts - 16, ts - 18, 8);
          } else {
            this.floorTile(g, sx, sy, ts, tx, ty, pal.floor);
            if (tile === Tile.SPIKE_TRAP) this.spikeTrap(g, sx, sy, ts, session.trapTimer);
          }
        }
      }

      for (const r of map.rooms) {
        if (!r.discovered) continue;
        const rc = r.centerWorld();
        const rx = rc.x - camX, ry = rc.y - camY;
        if (rx < -200 || rx > W + 200 || ry < -200 || ry > H + 200) continue;
        if (r.type === 'BOSS') {
          g.setColor(rgb(220, 48, 82, 42)); g.setStroke(2.5);
          g.drawOval(Math.trunc(rx - 95), Math.trunc(ry - 95), 190, 190);
          g.drawOval(Math.trunc(rx - 65), Math.trunc(ry - 65), 130, 130);
        } else if (r.type === 'TREASURE') {
          g.setColor(rgb(255, 196, 62, 32)); g.fillRoundRect(rx - 65, ry - 45, 130, 90, 14);
        } else if (r.type === 'SHRINE') {
          g.setColor(rgb(80, 235, 165, 30)); g.setStroke(1); g.drawOval(rx - 72, ry - 72, 144, 144);
        }
      }

      for (const t of map.torches) {
        if (map.getVisibility(map.worldToTile(t.x), map.worldToTile(t.y)) !== Vis.VISIBLE) continue;
        const sx = t.x - camX, sy = t.y - camY;
        const rad = 58 * (1 + 0.08 * Math.sin(time * 11 + t.x));
        g.setPaint(g.radialGradient(sx, sy, rad, [[0, rgb(255, 160, 55, 90)], [0.5, rgb(255, 110, 30, 35)], [1, rgb(255, 80, 0, 0)]]));
        g.fillOval(sx - rad, sy - rad, rad * 2, rad * 2);
        g.setColor(rgb(255, 220, 110)); g.fillOval(sx - 3, sy - 3, 6, 6);
      }

      for (const hz of session.hazardZones) {
        const sx = hz.x - camX, sy = hz.y - camY, p = hz.progress();
        g.setColor(hz.color.withAlpha(0.18 + 0.14 * p)); g.fillOval(sx - hz.radius, sy - hz.radius, hz.radius * 2, hz.radius * 2);
        g.setColor(hz.color.withAlpha(0.85)); g.setStroke(2.2); g.drawOval(sx - hz.radius, sy - hz.radius, hz.radius * 2, hz.radius * 2);
        const ir = hz.radius * p;
        g.setColor(hz.color.withAlpha(0.42)); g.fillOval(sx - ir, sy - ir, ir * 2, ir * 2);
      }

      for (const prop of session.props) {
        if (map.getVisibility(map.worldToTile(prop.x), map.worldToTile(prop.y)) === Vis.UNSEEN) continue;
        this.prop(g, prop, camX, camY, session.portalUnlocked);
      }

      for (const pk of session.pickups) {
        if (map.getVisibility(map.worldToTile(pk.x), map.worldToTile(pk.y)) !== Vis.VISIBLE) continue;
        this.pickup(g, pk, camX, camY);
      }

      for (const e of session.enemies) {
        if (!e.active || !e.telegraphing) continue;
        g.setColor(e.telegraphColor.withAlpha(0.72));
        g.setStroke(2.2, 'round', 'round', [8, 6]);
        g.drawLine(e.telegraphX1 - camX, e.telegraphY1 - camY, e.telegraphX2 - camX, e.telegraphY2 - camY);
      }
      g.setStroke(1);

      for (const e of session.enemies) {
        if (!e.active) continue;
        if (map.getVisibility(map.worldToTile(e.x), map.worldToTile(e.y)) !== Vis.VISIBLE) continue;
        this.enemy(g, e, camX, camY, time);
      }

      this.player(g, session.player, camX, camY, time);

      for (const p of session.projectiles) {
        if (!p.active) continue;
        if (map.getVisibility(map.worldToTile(p.x), map.worldToTile(p.y)) !== Vis.VISIBLE && !p.fromPlayer) continue;
        this.projectile(g, p, camX, camY);
      }

      this.particles(g, session.particles, camX, camY);

      for (let ty = minTy; ty <= maxTy; ty++) {
        for (let tx = minTx; tx <= maxTx; tx++) {
          const vis = map.getVisibility(tx, ty);
          if (vis === Vis.EXPLORED) {
            g.setColor(C.fogExplored);
            g.fillRect(Math.round(tx * ts - camX), Math.round(ty * ts - camY), ts, ts);
          } else if (vis === Vis.VISIBLE) {
            const light = map.getLightLevel(tx, ty);
            if (light < 0.95) {
              const a = CH.clamp(Math.round((1 - light) * 115), 0, 180);
              g.setColor(fogCache[a]);
              g.fillRect(Math.round(tx * ts - camX), Math.round(ty * ts - camY), ts, ts);
            }
          }
        }
      }

      for (const ft of session.floatingTexts) {
        const sx = ft.x - camX, sy = ft.y - camY;
        g.setFont(Math.round(ft.fontSize), true);
        const tw = g.stringWidth(ft.text);
        const a = ft.alpha();
        g.setColor(rgb(0, 0, 0, Math.trunc(a * 190)));
        g.drawString(ft.text, sx - tw / 2 + 1, sy + 1);
        g.setColor(ft.color.withAlpha(a));
        g.drawString(ft.text, sx - tw / 2, sy);
      }

      this.crosshair(g, mouseX, mouseY, session.player.heroClass.accentColor);
    }

    floorTile(g, sx, sy, ts, tx, ty, base) {
      g.setColor(floorColor(base, (tx + ty) % 2 !== 0));
      g.fillRect(sx, sy, ts, ts);
      g.setColor(C.floorEdge); g.setStroke(1);
      g.drawRect(sx, sy, ts - 1, ts - 1);
    }

    spikeTrap(g, sx, sy, ts, timer) {
      g.setColor(C.spikeBase);
      g.fillRect(sx + 6, sy + 6, ts - 12, ts - 12);
      if (timer >= 2.1) {
        g.setStroke(1);
        for (let row = 0; row < 2; row++) {
          for (let col = 0; col < 2; col++) {
            const bx = sx + 13 + col * 16, by = sy + 18 + row * 14;
            const xs = [bx - 5, bx, bx + 5], ys = [by, by - 12, by];
            g.setColor(C.spikeRed); g.fillPolygon(xs, ys);
            g.setColor(C.spikeEdge); g.drawPolygon(xs, ys);
          }
        }
      } else if (timer >= 1.7) {
        g.setColor(C.spikeWarn); g.fillRect(sx + 8, sy + 8, ts - 16, ts - 16);
      } else {
        g.setColor(C.spikeHole);
        g.fillOval(sx + 11, sy + 11, 6, 6); g.fillOval(sx + ts - 17, sy + 11, 6, 6);
        g.fillOval(sx + 11, sy + ts - 17, 6, 6); g.fillOval(sx + ts - 17, sy + ts - 17, 6, 6);
      }
    }

    prop(g, prop, camX, camY, unlocked) {
      const sx = prop.x - camX, sy = prop.y - camY;
      const pulse = 0.5 + 0.5 * Math.sin(prop.pulseTime * 4);
      switch (prop.type) {
        case 'CHEST':
          g.setColor(rgb(0, 0, 0, 120)); g.fillOval(sx - 16, sy + 6, 32, 12);
          g.setColor(prop.used ? rgb(82, 62, 46) : rgb(148, 92, 42)); g.fillRoundRect(sx - 14, sy - 11, 28, 22, 5);
          g.setColor(prop.used ? rgb(110, 100, 85) : rgb(255, 204, 68)); g.setStroke(2); g.drawRoundRect(sx - 14, sy - 11, 28, 22, 5);
          if (!prop.used) g.fillRect(sx - 3, sy - 3, 6, 7);
          break;
        case 'HEALING_SHRINE': {
          const c = prop.used ? rgb(80, 95, 90) : rgb(80, 245, 155);
          if (!prop.used) { g.setColor(c.withAlpha(0.16 + 0.12 * pulse)); g.fillOval(sx - 28, sy - 28, 56, 56); }
          g.setColor(rgb(70, 82, 88)); g.fillRoundRect(sx - 12, sy - 4, 24, 16, 4);
          g.setColor(c); g.fillPolygon([sx, sx + 9, sx, sx - 9], [sy - 22, sy - 8, sy + 2, sy - 8]);
          break;
        }
        case 'RELIC_ALTAR': {
          const c = prop.used ? rgb(90, 82, 105) : rgb(188, 108, 255);
          if (!prop.used) { g.setColor(c.withAlpha(0.18 + 0.12 * pulse)); g.fillOval(sx - 28, sy - 28, 56, 56); }
          g.setColor(rgb(62, 56, 82)); g.fillRoundRect(sx - 13, sy - 6, 26, 18, 4);
          g.setColor(c); g.fillOval(sx - 8, sy - 22 + Math.sin(prop.pulseTime * 3.2) * 3, 16, 16);
          break;
        }
        case 'EXIT_PORTAL': {
          const ring = unlocked ? rgb(72, 238, 255) : rgb(220, 68, 86);
          g.setColor(ring.withAlpha(0.22 + 0.12 * pulse)); g.fillOval(sx - 34, sy - 34, 68, 68);
          g.setColor(ring); g.setStroke(3.2); g.drawOval(sx - 24, sy - 24, 48, 48);
          if (unlocked) {
            const old = g.getTransform();
            g.rotate(prop.pulseTime * 2.2, sx, sy);
            g.setColor(rgb(200, 252, 255, 190)); g.drawRect(sx - 13, sy - 13, 26, 26);
            g.setTransform(old);
          }
          break;
        }
      }
    }

    pickup(g, pk, camX, camY) {
      const sx = pk.x - camX, sy = pk.y - camY + Math.sin(pk.animTime * 5) * 2.5;
      g.setStroke(1);
      switch (pk.kind) {
        case 'XP_GEM':
          g.setColor(rgb(90, 240, 255, 80)); g.fillOval(sx - 9, sy - 9, 18, 18);
          g.setColor(rgb(108, 232, 255)); g.fillPolygon([sx, sx + 6, sx, sx - 6], [sy - 7, sy, sy + 7, sy]);
          break;
        case 'GOLD_COIN':
          g.setColor(rgb(255, 205, 55)); g.fillOval(sx - 6, sy - 6, 12, 12);
          g.setColor(rgb(255, 245, 165)); g.drawOval(sx - 6, sy - 6, 12, 12);
          break;
        case 'HEALTH_POTION':
          g.setColor(rgb(245, 72, 92)); g.fillOval(sx - 7, sy - 6, 14, 14);
          g.setColor(Color.WHITE); g.fillRect(sx - 1, sy - 3, 2, 8); g.fillRect(sx - 4, sy, 8, 2);
          break;
        default:
          g.setColor(rgb(88, 178, 255)); g.fillOval(sx - 7, sy - 7, 14, 14);
          g.setColor(rgb(205, 238, 255)); g.drawOval(sx - 7, sy - 7, 14, 14);
      }
    }

    enemy(g, e, camX, camY, time) {
      const sx = e.x - camX, sy = e.y - camY, r = e.radius;
      g.setColor(rgb(0, 0, 0, 115)); g.fillOval(sx - r * 0.9, sy + r * 0.45, r * 1.8, r * 0.7);
      const body = e.hitFlashTimer > 0 ? Color.WHITE : e.type.color;
      switch (e.type.id) {
        case 'SLIME': case 'ELDER_SLIME': {
          const sq = 1 + 0.10 * Math.sin(e.animTime * 6.5);
          const w = r * 2 * sq, h = r * 1.8 / sq;
          g.setColor(body); g.fillOval(sx - w / 2, sy - h / 2, w, h);
          g.setColor(rgb(255, 255, 255, 95)); g.fillOval(sx - w * 0.25, sy - h * 0.3, w * 0.25, h * 0.2);
          g.setColor(Color.BLACK); g.fillOval(sx - 5, sy - 3, 3, 4); g.fillOval(sx + 2, sy - 3, 3, 4);
          break;
        }
        case 'SKELETON_ARCHER': {
          g.setColor(body); g.fillOval(sx - r * 0.8, sy - r * 0.8, r * 1.6, r * 1.6);
          g.setColor(rgb(65, 58, 72)); g.setStroke(1); g.drawOval(sx - r * 0.8, sy - r * 0.8, r * 1.6, r * 1.6);
          const old = g.getTransform();
          g.rotate(e.facingAngle, sx, sy);
          g.setColor(rgb(180, 120, 62)); g.setStroke(2.6);
          g.drawArc(sx - 2, sy - 12, 16, 24, -75, 150);
          g.setTransform(old);
          break;
        }
        case 'BERSERKER':
          g.setColor(body); g.fillRoundRect(sx - r, sy - r, r * 2, r * 2, 10);
          g.setColor(rgb(255, 210, 120));
          g.fillPolygon([sx - r, sx - r - 6, sx - r + 4], [sy - 4, sy - 14, sy - 10]);
          g.fillPolygon([sx + r, sx + r + 6, sx + r - 4], [sy - 4, sy - 14, sy - 10]);
          break;
        case 'NECROMANCER':
          g.setColor(body); g.fillPolygon([sx, sx + r, sx - r], [sy - r - 4, sy + r, sy + r]);
          g.setColor(rgb(230, 180, 255)); g.fillOval(sx - 4, sy - r + 2, 8, 8);
          break;
        default: {
          const old = g.getTransform();
          g.rotate(time * 1.4, sx, sy);
          g.setColor(e.bossPhase === 2 ? rgb(255, 90, 60, 165) : rgb(255, 65, 130, 140)); g.setStroke(3);
          g.drawRect(sx - r - 6, sy - r - 6, (r + 6) * 2, (r + 6) * 2);
          g.setTransform(old);
          g.setPaint(g.linearGradient(sx - r, sy - r, body, sx + r, sy + r, rgb(115, 18, 52)));
          g.fillOval(sx - r, sy - r, r * 2, r * 2);
          g.setColor(rgb(255, 228, 120)); g.setStroke(2.5); g.drawOval(sx - r, sy - r, r * 2, r * 2);
          g.fillOval(sx - 8, sy - 5, 5, 5); g.fillOval(sx + 3, sy - 5, 5, 5);
        }
      }
      if (e.hasStatus('SLOW')) { g.setColor(rgb(120, 235, 255, 180)); g.setStroke(2); g.drawOval(sx - r - 3, sy - r - 3, (r + 3) * 2, (r + 3) * 2); }
      if (e.hasStatus('BURN')) { g.setColor(rgb(255, 145, 40)); g.fillOval(sx - 4, sy - r - 12, 8, 8); }
      if (e.hasStatus('POISON')) { g.setColor(rgb(110, 235, 95)); g.fillOval(sx + 6, sy - r - 12, 7, 7); }
      if (e.hasStatus('STUN')) {
        g.setColor(rgb(255, 230, 80));
        g.fillOval(sx - 8 + Math.cos(time * 9) * 6, sy - r - 10, 5, 5);
        g.fillOval(sx + 4 - Math.cos(time * 9) * 6, sy - r - 10, 5, 5);
      }
      if (!e.type.isBoss && e.hp < e.maxHp) {
        const bw = Math.max(26, r * 2.1), bx = sx - bw / 2, by = sy - r - 9;
        g.setColor(rgb(0, 0, 0, 180)); g.fillRect(bx - 1, by - 1, bw + 2, 6);
        g.setColor(rgb(235, 68, 78)); g.fillRect(bx, by, bw * Math.max(0, e.hp / e.maxHp), 4);
      }
    }

    player(g, p, camX, camY, time) {
      const sx = p.x - camX, sy = p.y - camY, r = p.radius;
      g.setColor(rgb(0, 0, 0, 130)); g.fillOval(sx - r, sy + r * 0.45, r * 2, r * 0.75);
      if (p.shield > 0.5) { g.setColor(rgb(95, 200, 255, 95)); g.setStroke(2.2); g.drawOval(sx - r - 5, sy - r - 5, (r + 5) * 2, (r + 5) * 2); }
      const old = g.getTransform();
      g.rotate(p.facingAngle, sx, sy);
      g.setColor(p.hitFlashTimer > 0 ? Color.WHITE : p.heroClass.primaryColor); g.fillOval(sx - r, sy - r, r * 2, r * 2);
      g.setColor(p.heroClass.accentColor); g.setStroke(2.2); g.drawOval(sx - r, sy - r, r * 2, r * 2);
      if (p.heroClass.id === 'VANGUARD') {
        g.setColor(rgb(210, 232, 255)); g.fillRoundRect(sx + r - 2, sy - 3, 20, 6, 3);
        g.setColor(rgb(100, 185, 255)); g.fillRoundRect(sx + 4, sy + r - 3, 12, 6, 3);
      } else if (p.heroClass.id === 'ARCANIST') {
        g.setColor(rgb(150, 100, 65)); g.fillRect(sx + 4, sy - 2, 18, 4);
        g.setColor(rgb(120, 245, 255)); g.fillOval(sx + 19, sy - 5, 10, 10);
      } else {
        g.setColor(rgb(210, 255, 160));
        g.fillPolygon([sx + r, sx + r + 14, sx + r], [sy - 7, sy - 4, sy - 1]);
        g.fillPolygon([sx + r, sx + r + 14, sx + r], [sy + 1, sy + 4, sy + 7]);
      }
      g.setTransform(old);
      if (p.isInvulnerable()) { g.setColor(rgb(255, 255, 255, 100)); g.setStroke(1.5); g.drawOval(sx - r - 2, sy - r - 2, (r + 2) * 2, (r + 2) * 2); }
      if (p.getUpgradeCount('FROST_AURA') > 0) {
        const pr = 108 + Math.sin(time * 4) * 5;
        g.setColor(rgb(120, 225, 255, 42)); g.setStroke(1.6); g.drawOval(sx - pr, sy - pr, pr * 2, pr * 2);
      }
    }

    projectile(g, p, camX, camY) {
      const sx = p.x - camX, sy = p.y - camY;
      g.setColor(p.color.withAlpha(0.32)); g.fillOval(sx - p.radius * 1.8, sy - p.radius * 1.8, p.radius * 3.6, p.radius * 3.6);
      const old = g.getTransform();
      g.rotate(Math.atan2(p.vy, p.vx), sx, sy);
      g.setColor(p.color); g.fillOval(sx - p.radius * 1.4, sy - p.radius * 0.75, p.radius * 2.8, p.radius * 1.5);
      g.setColor(Color.WHITE); g.fillOval(sx - p.radius * 0.5, sy - p.radius * 0.4, p.radius * 1.2, p.radius * 0.8);
      g.setTransform(old);
    }

    particles(g, ps, camX, camY) {
      for (const ai of ps.afterimages) {
        g.setColor(ai.color.withAlpha(Math.max(0, ai.life / ai.maxLife) * 0.45));
        g.fillOval(ai.x - camX - ai.radius, ai.y - camY - ai.radius, ai.radius * 2, ai.radius * 2);
      }
      for (const sw of ps.shockwaves) {
        const prog = 1 - sw.life / sw.maxLife;
        const r = sw.maxRadius * (0.2 + 0.8 * prog);
        g.setColor(sw.color.withAlpha((1 - prog) * 0.85));
        g.setStroke(4 * (1 - prog) + 1);
        g.drawOval(sw.x - camX - r, sw.y - camY - r, r * 2, r * 2);
      }
      for (const sl of ps.slashes) {
        const a = Math.max(0, sl.life / sl.maxLife), r = sl.radius;
        const start = -sl.angleRad * 180 / Math.PI - sl.arcDeg / 2;
        g.setColor(sl.color.withAlpha(a * 0.55)); g.setStroke(14, 'round', 'round');
        g.drawArc(sl.x - camX - r, sl.y - camY - r, r * 2, r * 2, start, sl.arcDeg);
        g.setColor(Color.WHITE.withAlpha(a * 0.9)); g.setStroke(4, 'round', 'round');
        g.drawArc(sl.x - camX - r, sl.y - camY - r, r * 2, r * 2, start, sl.arcDeg);
      }
      for (const lb of ps.bolts) {
        const a = Math.max(0, lb.life / lb.maxLife);
        for (const [col, w] of [[lb.color.withAlpha(a), 3.2], [Color.WHITE.withAlpha(a), 1.3]]) {
          g.setColor(col); g.setStroke(w);
          for (let i = 0; i < lb.points.length - 1; i++) {
            const p0 = lb.points[i], p1 = lb.points[i + 1];
            g.drawLine(p0.x - camX, p0.y - camY, p1.x - camX, p1.y - camY);
          }
        }
      }
      const ctx = g.ctx;
      for (const p of ps.particles) {
        const prog = 1 - CH.clamp(p.life / p.maxLife, 0, 1);
        const alpha = 1 - prog;
        const s = p.size * (1 - prog * 0.5);
        const sx = p.x - camX, sy = p.y - camY;
        if (sx < -20 || sy < -20 || sx > W + 20 || sy > H + 20) continue;
        ctx.globalAlpha = alpha;
        ctx.fillStyle = p.color.css;
        if (p.glow) {
          ctx.globalAlpha = alpha * 0.3;
          ctx.beginPath(); ctx.arc(sx, sy, s * 1.4, 0, Math.PI * 2); ctx.fill();
          ctx.globalAlpha = alpha;
        }
        ctx.beginPath(); ctx.arc(sx, sy, s * 0.5, 0, Math.PI * 2); ctx.fill();
      }
      ctx.globalAlpha = 1;
    }

    crosshair(g, mx, my, accent) {
      g.setColor(accent.withAlpha(0.85)); g.setStroke(1.8);
      g.drawOval(mx - 8, my - 8, 16, 16);
      g.drawLine(mx - 13, my, mx - 5, my); g.drawLine(mx + 5, my, mx + 13, my);
      g.drawLine(mx, my - 13, mx, my - 5); g.drawLine(mx, my + 5, mx, my + 13);
    }
  }
  CH.WorldRenderer = WorldRenderer;

  // -------------------------------------------------------------------------
  // HUD
  // -------------------------------------------------------------------------
  const PANEL_BG = rgb(12, 14, 24, 210), PANEL_BORDER = rgb(75, 92, 130, 180);
  const I = () => CH.I18n;

  class HudRenderer {
    render(g, session, showFullMap, muted) {
      const p = session.player;
      this.topLeft(g, p);
      this.topCenter(g, session);
      this.minimap(g, session);
      this.skillDock(g, p, muted);
      this.relics(g, p);
      this.interactPrompt(g, session);
      this.toast(g, session);
      if (showFullMap) this.fullMap(g, session);
    }

    bar(g, x, y, w, h, ratio, bg, fg, text) {
      const c = CH.clamp(ratio, 0, 1);
      g.setColor(bg); g.fillRoundRect(x, y, w, h, 6);
      if (c > 0) { g.setColor(fg); g.fillRoundRect(x, y, Math.round(w * c), h, 6); }
      g.setColor(rgb(255, 255, 255, 45)); g.setStroke(1); g.drawRoundRect(x, y, w, h, 6);
      if (h >= 12 && text != null) {
        g.setFont(h >= 16 ? 11 : 10, true); g.setColor(Color.WHITE);
        g.drawString(text, x + 8, y + h - 3);
      }
    }

    topLeft(g, p) {
      g.setColor(PANEL_BG); g.fillRoundRect(16, 16, 286, 128, 12);
      g.setColor(PANEL_BORDER); g.setStroke(1.5); g.drawRoundRect(16, 16, 286, 128, 12);
      g.setFont(15, true); g.setColor(p.heroClass.accentColor); g.drawString(p.heroClass.displayName(), 28, 38);
      g.setColor(rgb(255, 218, 92)); g.drawString(I().t('hud.level') + ' ' + p.level, 232, 38);
      this.bar(g, 28, 48, 260, 18, p.hp / Math.max(1, p.maxHp), rgb(55, 22, 28), rgb(228, 62, 78),
        `HP  ${Math.ceil(p.hp)} / ${Math.ceil(p.maxHp)}`);
      const hasShield = p.maxShield > 0 || p.shield > 0;
      if (hasShield) {
        this.bar(g, 28, 70, 260, 12, p.shield / Math.max(1, Math.max(p.maxShield, p.shield)), rgb(20, 35, 56), rgb(78, 185, 255),
          `SHD ${Math.ceil(p.shield)}`);
      }
      this.bar(g, 28, hasShield ? 87 : 74, 260, 10, p.xp / Math.max(1, p.xpToNext), rgb(22, 28, 42), rgb(92, 232, 255),
        `XP ${p.xp} / ${p.xpToNext}`);
      g.setFont(13, true);
      g.setColor(rgb(255, 212, 70)); g.drawString(I().t('hud.gold') + ': ' + p.gold, 28, 128);
      g.setColor(rgb(215, 225, 245)); g.drawString(I().t('hud.score') + ': ' + p.score, 128, 128);
      if (p.combo >= 2) { g.setColor(rgb(255, 135, 72)); g.drawString(p.combo + 'x', 262, 128); }
    }

    topCenter(g, s) {
      const label = `${I().t('hud.floor')} ${s.currentFloor} / ${Config.MAX_FLOORS}  •  ${I().t('hud.seed')}: ${s.seed}  •  ${CH.formatTime(s.runDurationSeconds)}`;
      g.setFont(13, true);
      const bw = g.stringWidth(label) + 32, bx = (W - bw) / 2;
      g.setColor(PANEL_BG); g.fillRoundRect(bx, 14, bw, 30, 10);
      g.setColor(PANEL_BORDER); g.setStroke(1.5); g.drawRoundRect(bx, 14, bw, 30, 10);
      g.setColor(rgb(225, 232, 248)); g.drawString(label, bx + 16, 34);

      const boss = s.getActiveBoss();
      if (boss) {
        const w = 460, x = (W - w) / 2, y = 54;
        g.setColor(PANEL_BG); g.fillRoundRect(x - 12, y - 4, w + 24, 46, 10);
        g.setColor(rgb(255, 72, 105, 190)); g.drawRoundRect(x - 12, y - 4, w + 24, 46, 10);
        const title = I().t('hud.boss_name') + (boss.bossPhase === 2 ? '  [' + I().t('hud.boss_phase2') + ']' : '');
        g.setFont(13, true); g.setColor(rgb(255, 190, 200)); g.drawString(title, x, y + 13);
        this.bar(g, x, y + 18, w, 16, boss.hp / boss.maxHp, rgb(45, 14, 22), rgb(238, 48, 86),
          `${Math.ceil(boss.hp)} / ${Math.ceil(boss.maxHp)}`);
      }
    }

    minimap(g, s) {
      const mw = 196, mh = 140, mx = W - mw - 16, my = 16;
      g.setColor(PANEL_BG); g.fillRoundRect(mx, my, mw, mh, 12);
      g.setColor(PANEL_BORDER); g.setStroke(1.5); g.drawRoundRect(mx, my, mw, mh, 12);
      this.mapCells(g, s, mx + 10, my + 10, mw - 20, mh - 28, false);
      g.setFont(11, false); g.setColor(rgb(165, 178, 204));
      g.drawString('[TAB] Map   •   [ESC] Pause', mx + 14, my + mh - 7);
    }

    fullMap(g, s) {
      const mw = 740, mh = 520, mx = (W - mw) / 2, my = (H - mh) / 2;
      g.setColor(rgb(8, 10, 18, 235)); g.fillRoundRect(mx, my, mw, mh, 16);
      g.setColor(rgb(110, 145, 210)); g.setStroke(2); g.drawRoundRect(mx, my, mw, mh, 16);
      this.mapCells(g, s, mx + 24, my + 24, mw - 48, mh - 64, true);
      g.setFont(13, true); g.setColor(rgb(200, 212, 235));
      g.drawString(I().t('hud.map_legend'), mx + 30, my + mh - 16);
    }

    mapCells(g, s, x, y, w, h, detailed) {
      const map = s.map;
      const scale = Math.min(w / map.cols, h / map.rows);
      const ox = x + Math.trunc((w - map.cols * scale) / 2), oy = y + Math.trunc((h - map.rows * scale) / 2);
      const cell = Math.max(2, Math.ceil(scale));
      const visC = rgb(112, 132, 175), expC = rgb(62, 72, 98);
      for (let r = 0; r < map.rows; r++) {
        for (let c = 0; c < map.cols; c++) {
          const v = map.getVisibility(c, r);
          if (v === Vis.UNSEEN || map.isSolid(c, r)) continue;
          g.setColor(v === Vis.VISIBLE ? visC : expC);
          g.fillRect(ox + Math.trunc(c * scale), oy + Math.trunc(r * scale), cell, cell);
        }
      }
      const MARK = { START: rgb(80, 170, 255), TREASURE: rgb(255, 204, 65), SHRINE: rgb(85, 235, 145), BOSS: rgb(255, 72, 96) };
      for (const room of map.rooms) {
        if (!room.discovered || !MARK[room.type]) continue;
        const sz = detailed ? 9 : 5;
        g.setColor(MARK[room.type]);
        g.fillOval(ox + Math.trunc(room.centerTileX() * scale) - Math.trunc(sz / 2), oy + Math.trunc(room.centerTileY() * scale) - Math.trunc(sz / 2), sz, sz);
      }
      const p = s.player, ps = detailed ? 9 : 5;
      g.setColor(Color.WHITE);
      g.fillOval(ox + Math.trunc(p.x / Config.TILE_SIZE * scale) - Math.trunc(ps / 2), oy + Math.trunc(p.y / Config.TILE_SIZE * scale) - Math.trunc(ps / 2), ps, ps);
    }

    skillDock(g, p, muted) {
      const size = 54, gap = 12, total = size * 4 + gap * 3, sx = (W - total) / 2, y = H - size - 18;
      const cds = p.getCooldownScale();
      this.slot(g, sx, y, size, 'LMB', 'ATK', p.attackTimer, p.getEffectiveAttackCooldown(), p.heroClass.accentColor);
      this.slot(g, sx + (size + gap), y, size, 'SPC', 'DASH', p.dashCooldownTimer, p.getDashCooldownMax(), rgb(110, 225, 255));
      this.slot(g, sx + (size + gap) * 2, y, size, 'Q', 'SKILL', p.skillQTimer, p.heroClass.skillQCooldown * cds, p.heroClass.primaryColor);
      this.slot(g, sx + (size + gap) * 3, y, size, 'E', 'ULT', p.skillETimer, p.heroClass.skillECooldown * cds, rgb(255, 195, 75));
      if (muted) { g.setFont(12, true); g.setColor(rgb(255, 120, 120)); g.drawString(I().t('hud.muted'), W - 120, H - 22); }
    }

    slot(g, x, y, size, key, label, cd, maxCd, accent) {
      g.setColor(PANEL_BG); g.fillRoundRect(x, y, size, size, 10);
      if (cd > 0.01 && maxCd > 0.01) {
        const fh = Math.round(size * Math.min(1, cd / maxCd));
        g.setColor(rgb(15, 18, 28, 210)); g.fillRoundRect(x, y + size - fh, size, fh, 8);
        g.setColor(rgb(90, 100, 125));
      } else g.setColor(accent);
      g.setStroke(2); g.drawRoundRect(x, y, size, size, 10);
      g.setFont(11, true); g.setColor(rgb(195, 208, 232)); g.drawString(key, x + 6, y + 14);
      g.setFont(12, true); g.setColor(cd > 0.01 ? rgb(255, 180, 110) : Color.WHITE);
      const t = cd > 0.01 ? cd.toFixed(1) : label;
      g.drawString(t, x + (size - g.stringWidth(t)) / 2, y + 36);
    }

    relics(g, p) {
      if (p.upgradeStacks.size === 0) return;
      const x = 16, y = 160;
      g.setFont(11, true); g.setColor(rgb(175, 192, 220)); g.drawString(I().t('hud.upgrades') + ':', x + 2, y - 4);
      let row = 0;
      for (const [id, n] of p.upgradeStacks) {
        if (row >= 9) break;
        const u = CH.UpgradePool.find(id);
        if (!u) continue;
        const iy = y + row * 26;
        g.setColor(PANEL_BG); g.fillRoundRect(x, iy, 170, 22, 6);
        g.setColor(u.rarity.color); g.setStroke(1.5); g.drawRoundRect(x, iy, 170, 22, 6);
        g.drawString(u.name() + ' x' + n, x + 8, iy + 15);
        row++;
      }
    }

    interactPrompt(g, s) {
      const prop = s.getNearestInteractableProp();
      if (!prop) return;
      let msg = null;
      switch (prop.type) {
        case 'CHEST': msg = prop.used ? null : I().t('hud.interact_chest'); break;
        case 'HEALING_SHRINE': msg = prop.used ? null : I().t('hud.interact_shrine'); break;
        case 'RELIC_ALTAR': msg = prop.used ? null : I().t('hud.interact_altar'); break;
        case 'EXIT_PORTAL': msg = s.portalUnlocked ? I().t('hud.interact_portal') : I().t('hud.portal_locked'); break;
      }
      if (!msg) return;
      g.setFont(14, true);
      const w = g.stringWidth(msg) + 28, x = (W - w) / 2, y = H - 110;
      g.setColor(rgb(14, 18, 30, 230)); g.fillRoundRect(x, y, w, 30, 10);
      g.setColor(rgb(100, 235, 255)); g.setStroke(1.5); g.drawRoundRect(x, y, w, 30, 10);
      g.setColor(Color.WHITE); g.drawString(msg, x + 14, y + 20);
    }

    toast(g, s) {
      const t = s.getCurrentToast();
      if (!t) return;
      const w = 310, h = 56, x = (W - w) / 2, y = 108;
      g.setColor(rgb(18, 22, 36, 240)); g.fillRoundRect(x, y, w, h, 12);
      g.setColor(rgb(255, 205, 65)); g.setStroke(2); g.drawRoundRect(x, y, w, h, 12);
      g.setFont(11, true); g.drawString(I().t('hud.achievement'), x + 14, y + 18);
      g.setFont(14, true); g.setColor(Color.WHITE); g.drawString(t.achievement.title(), x + 14, y + 36);
      g.setFont(11, false); g.setColor(rgb(190, 200, 220)); g.drawString(t.achievement.description(), x + 14, y + 50);
    }
  }
  CH.HudRenderer = HudRenderer;
})();
