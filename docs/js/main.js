/*
 * Engine: Fixed-Timestep-Game-Loop (60 Updates/s), Skalierung mit Letterboxing,
 * Eingabe-Anbindung, Vollbild und Audio-Freischaltung.
 */
'use strict';
(function () {
  const CH = window.CH;
  const { Config } = CH;
  const W = Config.LOGICAL_WIDTH, H = Config.LOGICAL_HEIGHT;

  const canvas = document.getElementById('game');
  const ctx2d = canvas.getContext('2d', { alpha: false });
  const g = new CH.G(ctx2d);

  const input = new CH.Input();
  const save = new CH.SaveManager();
  CH.I18n.lang = save.language;
  const sound = new CH.SoundEngine(save.muted);
  const bus = new CH.EventBus();
  bus.subscribe(ev => sound.onEvent(ev));

  function toggleFullscreen() {
    try {
      if (!document.fullscreenElement) (document.documentElement.requestFullscreen || document.documentElement.webkitRequestFullscreen).call(document.documentElement);
      else (document.exitFullscreen || document.webkitExitFullscreen).call(document);
    } catch (e) { /* nicht unterstützt */ }
  }

  const ctx = { eventBus: bus, saveManager: save, sound, toggleFullscreen };
  const sm = new CH.StateManager();
  sm.push(new CH.MenuState(sm, ctx));
  sm.push(new CH.IntroState(sm));

  // Für Tests & Debugging von außen erreichbar
  CH.engine = { sm, ctx, input, save, sound, bus };

  // ---------------------------------------------------------------- Größe
  let dpr = 1, scale = 1, offX = 0, offY = 0, cssW = W, cssH = H;
  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    cssW = window.innerWidth; cssH = window.innerHeight;
    canvas.style.width = cssW + 'px';
    canvas.style.height = cssH + 'px';
    canvas.width = Math.round(cssW * dpr);
    canvas.height = Math.round(cssH * dpr);
    scale = Math.min(cssW / W, cssH / H);
    offX = (cssW - W * scale) / 2;
    offY = (cssH - H * scale) / 2;
    input.setRenderTransform(offX, offY, scale);
  }
  window.addEventListener('resize', resize);
  resize();

  // ---------------------------------------------------------------- Eingabe
  const BLOCK = new Set(['Tab', 'Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'F11', 'Backspace']);
  window.addEventListener('keydown', e => {
    sound.unlock();
    if (BLOCK.has(e.code)) e.preventDefault();
    if (e.repeat && e.code !== 'Backspace') return;
    input.onKeyDown(e);
  });
  window.addEventListener('keyup', e => input.onKeyUp(e));
  const rectOf = () => canvas.getBoundingClientRect();
  canvas.addEventListener('mousemove', e => input.updateMouse(e.clientX, e.clientY, rectOf()));
  canvas.addEventListener('mousedown', e => {
    sound.unlock();
    input.updateMouse(e.clientX, e.clientY, rectOf());
    input.onMouseDown(e.button === 2 ? CH.Input.BUTTON3 : (e.button === 0 ? CH.Input.BUTTON1 : 2));
    e.preventDefault();
  });
  window.addEventListener('mouseup', e => input.onMouseUp(e.button === 2 ? CH.Input.BUTTON3 : (e.button === 0 ? CH.Input.BUTTON1 : 2)));
  canvas.addEventListener('contextmenu', e => e.preventDefault());
  window.addEventListener('blur', () => input.clearAllInputs());
  document.addEventListener('visibilitychange', () => { if (document.hidden) input.clearAllInputs(); });

  // Einfache Touch-Unterstützung für Menüs (Tippen = Klick)
  canvas.addEventListener('touchstart', e => {
    sound.unlock();
    const t = e.changedTouches[0];
    input.updateMouse(t.clientX, t.clientY, rectOf());
    input.onMouseDown(CH.Input.BUTTON1);
    e.preventDefault();
  }, { passive: false });
  canvas.addEventListener('touchend', e => { input.onMouseUp(CH.Input.BUTTON1); e.preventDefault(); }, { passive: false });

  // ---------------------------------------------------------------- Loop
  function render() {
    ctx2d.setTransform(1, 0, 0, 1, 0, 0);
    ctx2d.fillStyle = '#000';
    ctx2d.fillRect(0, 0, canvas.width, canvas.height);
    ctx2d.setTransform(dpr * scale, 0, 0, dpr * scale, dpr * offX, dpr * offY);
    ctx2d.save();
    ctx2d.beginPath();
    ctx2d.rect(0, 0, W, H);
    ctx2d.clip();
    sm.render(g, input);
    const top = sm.peek();
    if (!(top instanceof CH.PlayingState) && !(top instanceof CH.IntroState)) drawPointer(g, input.mouseX, input.mouseY);
    ctx2d.restore();
  }

  function drawPointer(gr, mx, my) {
    const xs = [mx, mx + 14, mx + 6, mx + 3], ys = [my, my + 10, my + 11, my + 17];
    gr.setColor(CH.rgb(115, 225, 255)); gr.fillPolygon(xs, ys);
    gr.setColor(CH.Color.WHITE); gr.setStroke(1.4); gr.drawPolygon(xs, ys);
  }

  let last = performance.now(), acc = 0;
  function frame(now) {
    requestAnimationFrame(frame);
    let ft = (now - last) / 1000;
    last = now;
    if (ft > Config.MAX_FRAME_TIME) ft = Config.MAX_FRAME_TIME;
    acc += ft;
    let steps = 0;
    try {
      while (acc >= Config.FIXED_DT && steps < 8) {
        input.beginFrame();
        sm.update(Config.FIXED_DT, input);
        acc -= Config.FIXED_DT;
        steps++;
      }
    } catch (e) {
      console.error(e);
      acc = 0;
    }
    if (steps === 8) acc = 0;
    try {
      render();
    } catch (e) {
      console.error(e);
    }
  }
  requestAnimationFrame(frame);

  const loader = document.getElementById('loading');
  if (loader) loader.remove();
})();
