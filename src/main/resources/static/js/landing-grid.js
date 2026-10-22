const canvas = document.querySelector(".landing-grid-canvas");

if (canvas instanceof HTMLCanvasElement) {
  const context = canvas.getContext("2d");

  if (context) {
    const cellSize = 55;
    const dotSpacing = 28;
    const influenceRadius = 260;
    const maxWarp = 24;
    const ripples = [];
    const mouse = { x: -9999, y: -9999 };
    const target = { x: -9999, y: -9999 };
    let width = 0;
    let height = 0;
    let frame = 0;
    let lastFrame = 0;
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const hoverAvailable = window.matchMedia("(hover: hover) and (pointer: fine)");

    const lerp = (start, end, amount) => start + (end - start) * amount;
    const rgba = (start, end, amount) => {
      const red = Math.round(lerp(start[0], end[0], amount));
      const green = Math.round(lerp(start[1], end[1], amount));
      const blue = Math.round(lerp(start[2], end[2], amount));
      const alpha = lerp(start[3], end[3], amount).toFixed(3);
      return `rgba(${red},${green},${blue},${alpha})`;
    };

    function resize() {
      const scale = Math.min(window.devicePixelRatio || 1, 1.5);
      width = window.innerWidth * 1.05;
      height = window.innerHeight;
      canvas.width = Math.round(width * scale);
      canvas.height = Math.round(height * scale);
      context.setTransform(scale, 0, 0, scale, 0, 0);
      draw(performance.now());
    }

    function pointAt(x, y, col, row, cols, rows) {
      const pinX = Math.min(col / 1.5, (cols - 1 - col) / 1.5, 1);
      const pinY = Math.min(row / 1.5, (rows - 1 - row) / 1.5, 1);
      const pin = Math.max(0, pinX * pinX * pinY * pinY);
      const dx = x - mouse.x;
      const dy = y - mouse.y;
      const distance = Math.hypot(dx, dy);
      const proximity = Math.max(0, 1 - distance / influenceRadius) * pin;
      let rippleX = 0;
      let rippleY = 0;

      for (const ripple of ripples) {
        const rx = x - ripple.x;
        const ry = y - ripple.y;
        const radius = Math.hypot(rx, ry);
        const difference = radius - ripple.radius;
        const waveWidth = 55;
        if (Math.abs(difference) < waveWidth) {
          const strength = (1 - Math.abs(difference) / waveWidth) * ripple.opacity * 18 * pin;
          const angle = Math.atan2(ry, rx);
          const direction = difference < 0 ? 1 : -1;
          rippleX += Math.cos(angle) * strength * direction;
          rippleY += Math.sin(angle) * strength * direction;
        }
      }

      if (distance < influenceRadius && distance > 0 && pin > 0) {
        const ratio = distance / influenceRadius;
        const easing = ratio < 0.01 ? 0 : (1 - ratio) ** 2 * Math.min(1, distance / 60);
        const warp = easing * maxWarp * pin;
        return {
          x: x - (dx / distance) * warp + rippleX,
          y: y - (dy / distance) * warp + rippleY,
          proximity,
        };
      }

      return { x: x + rippleX, y: y + rippleY, proximity };
    }

    function draw(now) {
      context.clearRect(0, 0, width, height);
      context.fillStyle = "#fff";
      context.fillRect(0, 0, width, height);

      context.fillStyle = "rgba(15,23,42,0.045)";
      for (let x = dotSpacing / 2; x < width; x += dotSpacing) {
        for (let y = dotSpacing / 2; y < height; y += dotSpacing) {
          context.beginPath();
          context.arc(x, y, 0.7, 0, Math.PI * 2);
          context.fill();
        }
      }

      for (let index = ripples.length - 1; index >= 0; index -= 1) {
        const ripple = ripples[index];
        const age = (now - ripple.born) / 1000;
        ripple.radius = age * 400;
        ripple.opacity = Math.max(0, 1 - age * 1.2);
        if (ripple.opacity <= 0) ripples.splice(index, 1);
      }

      const cols = Math.max(2, Math.ceil(width / cellSize)) + 1;
      const rows = Math.max(2, Math.ceil(height / cellSize)) + 1;
      const cellWidth = width / (cols - 1);
      const cellHeight = height / (rows - 1);
      const points = Array.from({ length: rows }, (_, row) =>
        Array.from({ length: cols }, (_, col) =>
          pointAt(col * cellWidth, row * cellHeight, col, row, cols, rows),
        ),
      );
      const activeLine = [37, 99, 235, 0.78];
      const baseLine = [15, 23, 42, 0.08];

      function segment(rowA, colA, rowB, colB) {
        const first = points[rowA][colA];
        const second = points[rowB][colB];
        const average = (first.proximity + second.proximity) / 2;
        const eased = average * average * (3 - 2 * average);
        context.beginPath();
        context.moveTo(first.x, first.y);
        context.lineTo(second.x, second.y);
        context.strokeStyle = rgba(baseLine, activeLine, eased);
        context.lineWidth = lerp(0.8, 1.5, eased);
        context.stroke();
      }

      context.lineCap = "butt";
      for (let row = 0; row < rows; row += 1) {
        for (let col = 0; col < cols - 1; col += 1) segment(row, col, row, col + 1);
      }
      for (let col = 0; col < cols; col += 1) {
        for (let row = 0; row < rows - 1; row += 1) segment(row, col, row + 1, col);
      }

      for (let row = 0; row < rows; row += 1) {
        for (let col = 0; col < cols; col += 1) {
          const point = points[row][col];
          const amount = point.proximity;
          const eased = amount * amount * (3 - 2 * amount);
          const radius = lerp(1.8, 3.2, eased);
          if (eased > 0.3) {
            const glowRadius = radius + lerp(0, 6, (eased - 0.3) / 0.7);
            const glow = context.createRadialGradient(point.x, point.y, radius * 0.5, point.x, point.y, glowRadius);
            glow.addColorStop(0, `rgba(37,99,235,${(eased * 0.22).toFixed(3)})`);
            glow.addColorStop(1, "rgba(37,99,235,0)");
            context.beginPath();
            context.arc(point.x, point.y, glowRadius, 0, Math.PI * 2);
            context.fillStyle = glow;
            context.fill();
          }
          context.beginPath();
          context.arc(point.x, point.y, radius, 0, Math.PI * 2);
          context.fillStyle = rgba([15, 23, 42, 0.18], [37, 99, 235, 0.95], eased);
          context.fill();
        }
      }

      for (const ripple of ripples) {
        context.beginPath();
        context.arc(ripple.x, ripple.y, Math.max(0, ripple.radius), 0, Math.PI * 2);
        context.strokeStyle = `rgba(37,99,235,${(ripple.opacity * 0.22).toFixed(3)})`;
        context.lineWidth = 1.5;
        context.stroke();
      }
    }

    function animate(now) {
      frame = 0;
      if (document.hidden) {
        lastFrame = 0;
        return;
      }
      if (reduceMotion.matches || document.documentElement.hasAttribute("data-reduce-motion")) {
        lastFrame = 0;
        return;
      }
      if (now - lastFrame >= 1000 / 30) {
        mouse.x = lerp(mouse.x, target.x, 0.08);
        mouse.y = lerp(mouse.y, target.y, 0.08);
        if (Math.hypot(target.x - mouse.x, target.y - mouse.y) < 0.5) {
          mouse.x = target.x;
          mouse.y = target.y;
        }
        draw(now);
        lastFrame = now;
      }
      if (ripples.length || Math.hypot(target.x - mouse.x, target.y - mouse.y) >= 0.5) {
        frame = window.requestAnimationFrame(animate);
      } else {
        lastFrame = 0;
      }
    }

    function startAnimation() {
      const reduced = reduceMotion.matches || document.documentElement.hasAttribute("data-reduce-motion");
      const mouseIsMoving = Math.hypot(target.x - mouse.x, target.y - mouse.y) >= 0.5;
      if (!reduced && frame === 0 && !document.hidden && (ripples.length || mouseIsMoving)) {
        frame = window.requestAnimationFrame(animate);
      }
    }

    function stopAnimation() {
      if (frame) window.cancelAnimationFrame(frame);
      frame = 0;
    }

    function onMotionPreferenceChange() {
      if (reduceMotion.matches || document.documentElement.hasAttribute("data-reduce-motion")) {
        stopAnimation();
        mouse.x = -9999;
        mouse.y = -9999;
        target.x = -9999;
        target.y = -9999;
        ripples.length = 0;
        draw(performance.now());
      }
    }

    function onPointerMove(event) {
      if (!hoverAvailable.matches || reduceMotion.matches || document.documentElement.hasAttribute("data-reduce-motion")) return;
      target.x = event.clientX + window.innerWidth * 0.05;
      target.y = event.clientY;
      if (mouse.x === -9999) {
        mouse.x = target.x;
        mouse.y = target.y;
        draw(performance.now());
        return;
      }
      startAnimation();
    }

    function onClick(event) {
      if (reduceMotion.matches || document.documentElement.hasAttribute("data-reduce-motion")) return;
      ripples.push({
        x: event.clientX + window.innerWidth * 0.05,
        y: event.clientY,
        radius: 0,
        opacity: 1,
        born: performance.now(),
      });
      startAnimation();
    }

    function onPointerLeave() {
      mouse.x = -9999;
      mouse.y = -9999;
      target.x = -9999;
      target.y = -9999;
      draw(performance.now());
      startAnimation();
    }

    function onVisibilityChange() {
      if (document.hidden) stopAnimation();
      else startAnimation();
    }

    resize();
    window.addEventListener("resize", resize);
    window.addEventListener("pointermove", onPointerMove, { passive: true });
    window.addEventListener("click", onClick, { passive: true });
    window.addEventListener("pointerleave", onPointerLeave, { passive: true });
    document.addEventListener("visibilitychange", onVisibilityChange);
    reduceMotion.addEventListener("change", onMotionPreferenceChange);
    new MutationObserver(onMotionPreferenceChange).observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["data-reduce-motion"],
    });
    window.addEventListener("pagehide", stopAnimation, { once: true });
  }
}

if ("IntersectionObserver" in window) {
  const movingSections = document.querySelectorAll(".landing-preview, .landing-faq");
  const animationVisibility = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      entry.target.toggleAttribute("data-motion-offscreen", !entry.isIntersecting);
    }
  }, { rootMargin: "80px 0px" });

  movingSections.forEach((section) => animationVisibility.observe(section));
}
