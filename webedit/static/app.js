(() => {
  const state = {
    files: [],
    catalog: [],
    fileName: null,
    library: null,
    dirty: false,
    sel: { g: null, s: null, song: null },
    instrumentFilter: "",
  };

  const $ = (id) => document.getElementById(id);
  const statusEl = $("status");
  const saveBtn = $("btn-save");

  function setStatus(text, kind = "") {
    statusEl.textContent = text;
    statusEl.className = "status" + (kind ? ` ${kind}` : "");
  }

  function markDirty(v = true) {
    state.dirty = v;
    saveBtn.disabled = !state.fileName || !v;
    if (v) setStatus("Cambios sin guardar", "dirty");
  }

  async function api(path, opts = {}) {
    const res = await fetch(path, {
      headers: { "Content-Type": "application/json", ...(opts.headers || {}) },
      ...opts,
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || res.statusText);
    return data;
  }

  function emptyLibrary() {
    return { groups: [] };
  }

  function ensureShape(lib) {
    if (!lib || typeof lib !== "object") return emptyLibrary();
    if (!Array.isArray(lib.groups)) lib.groups = [];
    lib.groups.forEach((g) => {
      if (!Array.isArray(g.setlists)) g.setlists = [];
      g.setlists.forEach((sl) => {
        if (!Array.isArray(sl.songs)) sl.songs = [];
        sl.songs.forEach((song) => {
          if (!Array.isArray(song.instruments)) song.instruments = [];
          if (song.instrumentNotes == null) song.instrumentNotes = "";
          if (song.key == null) song.key = "";
        });
      });
    });
    return lib;
  }

  async function loadCatalog() {
    const data = await api("/api/catalog");
    state.catalog = data.sounds || [];
  }

  async function refreshFiles() {
    const data = await api("/api/files");
    state.files = data.files || [];
    renderFiles();
  }

  async function openFile(name) {
    if (state.dirty && !confirm("Hay cambios sin guardar. ¿Descartar?")) return;
    const data = await api(`/api/files/${encodeURIComponent(name)}`);
    state.fileName = name;
    state.library = ensureShape(data.library);
    state.sel = { g: null, s: null, song: null };
    state.instrumentFilter = "";
    markDirty(false);
    setStatus(`Abierto: ${name}`, "ok");
    renderAll();
  }

  async function saveFile() {
    if (!state.fileName || !state.library) return;
    await api(`/api/files/${encodeURIComponent(state.fileName)}`, {
      method: "PUT",
      body: JSON.stringify({ library: state.library }),
    });
    markDirty(false);
    setStatus(`Guardado: ${state.fileName}`, "ok");
    await refreshFiles();
  }

  async function createFile() {
    let name = $("new-file").value.trim();
    if (!name) name = "library.json";
    if (!name.endsWith(".json")) name += ".json";
    try {
      await api("/api/files", {
        method: "POST",
        body: JSON.stringify({ name, library: emptyLibrary() }),
      });
      $("new-file").value = "";
      await refreshFiles();
      await openFile(name);
    } catch (e) {
      setStatus(e.message, "err");
    }
  }

  async function deleteFile(name, ev) {
    ev.stopPropagation();
    if (!confirm(`¿Borrar ${name}?`)) return;
    await api(`/api/files/${encodeURIComponent(name)}`, { method: "DELETE" });
    if (state.fileName === name) {
      state.fileName = null;
      state.library = null;
      markDirty(false);
    }
    await refreshFiles();
    renderAll();
  }

  function renderFiles() {
    const ul = $("file-list");
    ul.innerHTML = "";
    state.files.forEach((f) => {
      const li = document.createElement("li");
      if (f.name === state.fileName) li.classList.add("active");
      li.innerHTML = `<span class="label">${escapeHtml(f.name)}<span class="sub">${f.size} B</span></span>`;
      const del = document.createElement("button");
      del.className = "danger small";
      del.textContent = "×";
      del.title = "Borrar";
      del.onclick = (ev) => deleteFile(f.name, ev).catch((e) => setStatus(e.message, "err"));
      li.appendChild(del);
      li.onclick = () => openFile(f.name).catch((e) => setStatus(e.message, "err"));
      ul.appendChild(li);
    });
  }

  function renderGroups() {
    const ul = $("group-list");
    ul.innerHTML = "";
    if (!state.library) return;
    state.library.groups.forEach((g, gi) => {
      const li = document.createElement("li");
      if (state.sel.g === gi) li.classList.add("active");
      const n = (g.setlists || []).length;
      li.innerHTML = `<span class="label">${escapeHtml(g.name || "(sin nombre)")}<span class="sub">${n} setlists</span></span>`;
      const del = document.createElement("button");
      del.className = "danger small";
      del.textContent = "×";
      del.onclick = (ev) => {
        ev.stopPropagation();
        if (!confirm(`¿Eliminar grupo «${g.name}»?`)) return;
        state.library.groups.splice(gi, 1);
        if (state.sel.g === gi) state.sel = { g: null, s: null, song: null };
        else if (state.sel.g > gi) state.sel.g -= 1;
        markDirty();
        renderAll();
      };
      li.appendChild(del);
      li.onclick = () => {
        state.sel = { g: gi, s: null, song: null };
        renderAll();
      };
      ul.appendChild(li);
    });
  }

  function currentGroup() {
    return state.library && state.sel.g != null ? state.library.groups[state.sel.g] : null;
  }
  function currentSetlist() {
    const g = currentGroup();
    return g && state.sel.s != null ? g.setlists[state.sel.s] : null;
  }
  function currentSong() {
    const sl = currentSetlist();
    return sl && state.sel.song != null ? sl.songs[state.sel.song] : null;
  }

  function renderDetail() {
    const panel = $("panel-detail");
    if (!state.library) {
      panel.innerHTML = `<p class="empty">Abre o crea un fichero JSON para editar.</p>`;
      return;
    }
    const g = currentGroup();
    if (g == null) {
      panel.innerHTML = `<p class="empty">Selecciona un grupo o crea uno nuevo.</p>`;
      return;
    }
    if (state.sel.s == null) {
      panel.innerHTML = renderGroupEditor(g);
      bindGroupEditor(g);
      return;
    }
    const sl = currentSetlist();
    if (!sl) {
      panel.innerHTML = `<p class="empty">Setlist no encontrada.</p>`;
      return;
    }
    if (state.sel.song == null) {
      panel.innerHTML = renderSetlistEditor(g, sl);
      bindSetlistEditor(g, sl);
      return;
    }
    const song = currentSong();
    if (!song) {
      panel.innerHTML = `<p class="empty">Canción no encontrada.</p>`;
      return;
    }
    panel.innerHTML = renderSongEditor(g, sl, song);
    bindSongEditor(song);
  }

  function renderGroupEditor(g) {
    const setlists = g.setlists || [];
    return `
      <div class="crumb">${escapeHtml(g.name || "Grupo")}</div>
      <div class="field">
        <label>Nombre del grupo</label>
        <input type="text" id="ed-group-name" value="${escapeAttr(g.name || "")}" />
      </div>
      <div class="toolbar">
        <button type="button" id="ed-group-save-name">Guardar nombre</button>
      </div>
      <div class="section-title">Setlists de este grupo</div>
      <p class="hint">Se crean ya asignadas al grupo. Pulsa una para definir canciones.</p>
      <div class="row">
        <input type="text" id="ed-new-setlist" placeholder="Nueva setlist" />
        <button type="button" id="ed-add-setlist">Crear</button>
      </div>
      <ul class="list" id="ed-setlist-list">
        ${setlists
          .map(
            (sl, i) => `
          <li data-i="${i}">
            <span class="label">${escapeHtml(sl.name || "(sin nombre)")}
span class="sub">${(sl.songs || []).length} canciones</span></span>
            <button type="button" class="danger small" data-del="${i}">×</button>
          </li>`
          )
          .join("")}
      </ul>
    `;
  }

  function bindGroupEditor(g) {
    $("ed-group-save-name").onclick = () => {
      g.name = $("ed-group-name").value.trim() || g.name;
      markDirty();
      renderAll();
    };
    $("ed-add-setlist").onclick = () => {
      const name = $("ed-new-setlist").value.trim();
      if (!name) return;
      if (!g.setlists) g.setlists = [];
      g.setlists.push({ name, songs: [] });
      state.sel.s = g.setlists.length - 1;
      state.sel.song = null;
      markDirty();
      renderAll();
    };
    document.querySelectorAll("#ed-setlist-list li").forEach((li) => {
      const i = Number(li.dataset.i);
      li.onclick = (ev) => {
        if (ev.target.dataset.del != null) return;
        state.sel.s = i;
        state.sel.song = null;
        renderAll();
      };
      const del = li.querySelector("[data-del]");
      if (del) {
        del.onclick = (ev) => {
          ev.stopPropagation();
          if (!confirm("¿Quitar setlist del grupo?")) return;
          g.setlists.splice(i, 1);
          state.sel.s = null;
          state.sel.song = null;
          markDirty();
          renderAll();
        };
      }
    });
  }

  function renderSetlistEditor(g, sl) {
    const songs = sl.songs || [];
    return `
      <div class="crumb">${escapeHtml(g.name)} › ${escapeHtml(sl.name || "Setlist")}</div>
      <div class="toolbar">
        <button type="button" class="ghost" id="ed-back-group">← Grupo</button>
      </div>
      <div class="field">
        <label>Nombre de la setlist</label>
        <input type="text" id="ed-setlist-name" value="${escapeAttr(sl.name || "")}" />
      </div>
      <div class="toolbar">
        <button type="button" id="ed-setlist-save-name">Guardar nombre</button>
      </div>
      <div class="section-title">Canciones (orden = directo)</div>
      <p class="hint">Al crear una canción se abre para completar BPM, tonalidad e instrumentos.</p>
      <div class="row">
        <input type="text" id="ed-new-song" placeholder="Nueva canción" />
        <button type="button" id="ed-add-song">Crear</button>
      </div>
      <ul class="list" id="ed-song-list">
        ${songs
          .map(
            (song, i) => `
          <li data-i="${i}">
            <span class="label">${i + 1}. ${escapeHtml(song.title || "(sin título)")}
span class="sub">${songMeta(song)}</span></span>
            <button type="button" class="small" data-up="${i}">↑</button>
            <button type="button" class="small" data-down="${i}">↓</button>
            <button type="button" class="danger small" data-del="${i}">×</button>
          </li>`
          )
          .join("")}
      </ul>
    `;
  }

  function songMeta(song) {
    const parts = [];
    if (song.bpm != null && song.bpm !== "") parts.push(`${song.bpm} BPM`);
    if (song.key) parts.push(song.key);
    const n = (song.instruments || []).length;
    if (n) parts.push(`${n} instr.`);
    return parts.join(" · ") || "tocar para completar";
  }

  function bindSetlistEditor(g, sl) {
    $("ed-back-group").onclick = () => {
      state.sel.s = null;
      state.sel.song = null;
      renderAll();
    };
    $("ed-setlist-save-name").onclick = () => {
      sl.name = $("ed-setlist-name").value.trim() || sl.name;
      markDirty();
      renderAll();
    };
    $("ed-add-song").onclick = () => {
      const title = $("ed-new-song").value.trim();
      if (!title) return;
      if (!sl.songs) sl.songs = [];
      sl.songs.push({
        title,
        bpm: null,
        key: "",
        instrumentNotes: "",
        instruments: [],
      });
      state.sel.song = sl.songs.length - 1;
      markDirty();
      renderAll();
    };
    document.querySelectorAll("#ed-song-list li").forEach((li) => {
      const i = Number(li.dataset.i);
      li.onclick = (ev) => {
        if (ev.target.closest("button")) return;
        state.sel.song = i;
        renderAll();
      };
      li.querySelector("[data-up]")?.addEventListener("click", (ev) => {
        ev.stopPropagation();
        if (i === 0) return;
        const tmp = sl.songs[i - 1];
        sl.songs[i - 1] = sl.songs[i];
        sl.songs[i] = tmp;
        markDirty();
        renderAll();
      });
      li.querySelector("[data-down]")?.addEventListener("click", (ev) => {
        ev.stopPropagation();
        if (i >= sl.songs.length - 1) return;
        const tmp = sl.songs[i + 1];
        sl.songs[i + 1] = sl.songs[i];
        sl.songs[i] = tmp;
        markDirty();
        renderAll();
      });
      li.querySelector("[data-del]")?.addEventListener("click", (ev) => {
        ev.stopPropagation();
        if (!confirm("¿Quitar canción de la setlist?")) return;
        sl.songs.splice(i, 1);
        state.sel.song = null;
        markDirty();
        renderAll();
      });
    });
  }

  function renderSongEditor(g, sl, song) {
    const selected = new Set(song.instruments || []);
    const q = state.instrumentFilter.trim();
    const matches = state.catalog.filter((sound) => {
      if (selected.has(sound.name)) return false;
      if (!q) return true;
      return (
        sound.name.toLowerCase().includes(q.toLowerCase()) ||
        String(sound.program) === q
      );
    });
    return `
      <div class="crumb">${escapeHtml(g.name)} › ${escapeHtml(sl.name)} › ${escapeHtml(song.title || "Canción")}</div>
      <div class="toolbar">
        <button type="button" class="ghost" id="ed-back-setlist">← Setlist</button>
      </div>
      <div class="form-grid">
        <div class="field full">
          <label>Título</label>
          <input type="text" id="ed-song-title" value="${escapeAttr(song.title || "")}" />
        </div>
        <div class="field">
          <label>BPM</label>
          <input type="number" id="ed-song-bpm" min="0" value="${song.bpm ?? ""}" />
        </div>
        <div class="field">
          <label>Tonalidad</label>
          <input type="text" id="ed-song-key" value="${escapeAttr(song.key || "")}" />
        </div>
        <div class="field full">
          <label>Cambios de instrumento (notas)</label>
          <textarea id="ed-song-notes">${escapeHtml(song.instrumentNotes || "")}</textarea>
        </div>
      </div>
      <div class="toolbar">
        <button type="button" id="ed-song-save" class="primary">Guardar datos de la canción</button>
      </div>
      <div class="section-title">Instrumentos V3 (por nombre)</div>
      <div class="chip-row" id="ed-instr-chips">
        ${(song.instruments || [])
          .map(
            (name, i) => `
          <span class="chip">${escapeHtml(name)}
            <button type="button" data-rm="${i}" title="Quitar">×</button>
          </span>`
          )
          .join("") || `<span class="hint">Ninguno aún</span>`}
      </div>
      <div class="field">
        <label>Filtrar por nombre (sin distinguir mayúsculas)</label>
        <input type="text" id="ed-instr-filter" value="${escapeAttr(state.instrumentFilter)}" placeholder="piano, organ, pad…" />
      </div>
      <p class="hint">${matches.length} coincidencias · desplaza la lista</p>
      <div class="picker" id="ed-instr-picker">
        ${matches
          .map(
            (s) => `
          <div class="item" data-name="${escapeAttr(s.name)}">
            ${escapeHtml(s.name)}<span class="pc">PC ${s.program}</span>
          </div>`
          )
          .join("")}
      </div>
    `;
  }

  function bindSongEditor(song) {
    $("ed-back-setlist").onclick = () => {
      state.sel.song = null;
      state.instrumentFilter = "";
      renderAll();
    };
    $("ed-song-save").onclick = () => {
      song.title = $("ed-song-title").value.trim() || song.title;
      const bpmRaw = $("ed-song-bpm").value.trim();
      song.bpm = bpmRaw === "" ? null : Number(bpmRaw);
      song.key = $("ed-song-key").value;
      song.instrumentNotes = $("ed-song-notes").value;
      markDirty();
      setStatus("Datos de canción actualizados (recuerda Guardar el fichero)", "ok");
      renderAll();
    };
    document.querySelectorAll("#ed-instr-chips [data-rm]").forEach((btn) => {
      btn.onclick = () => {
        const i = Number(btn.dataset.rm);
        song.instruments.splice(i, 1);
        markDirty();
        renderDetail();
      };
    });
    const filter = $("ed-instr-filter");
    filter.oninput = () => {
      state.instrumentFilter = filter.value;
      const pos = filter.selectionStart;
      renderDetail();
      const again = $("ed-instr-filter");
      if (again) {
        again.focus();
        again.setSelectionRange(pos, pos);
      }
    };
    document.querySelectorAll("#ed-instr-picker .item").forEach((el) => {
      el.onclick = () => {
        const name = el.dataset.name;
        if (!song.instruments) song.instruments = [];
        if (!song.instruments.includes(name)) {
          song.instruments.push(name);
          markDirty();
          renderDetail();
        }
      };
    });
  }

  function renderAll() {
    renderFiles();
    renderGroups();
    renderDetail();
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }
  function escapeAttr(s) {
    return escapeHtml(s).replace(/'/g, "&#39;");
  }

  $("btn-save").onclick = () => saveFile().catch((e) => setStatus(e.message, "err"));
  $("btn-new").onclick = () => createFile();
  $("btn-add-group").onclick = () => {
    if (!state.library) {
      setStatus("Abre o crea un fichero primero", "err");
      return;
    }
    const name = $("new-group").value.trim();
    if (!name) return;
    state.library.groups.push({ name, setlists: [] });
    $("new-group").value = "";
    state.sel = { g: state.library.groups.length - 1, s: null, song: null };
    markDirty();
    renderAll();
  };

  window.addEventListener("keydown", (ev) => {
    if ((ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === "s") {
      ev.preventDefault();
      if (!saveBtn.disabled) saveFile().catch((e) => setStatus(e.message, "err"));
    }
  });

  (async () => {
    try {
      await loadCatalog();
      await refreshFiles();
      if (state.files.some((f) => f.name === "library.json")) {
        await openFile("library.json");
      } else if (state.files.length) {
        await openFile(state.files[0].name);
      } else {
        renderAll();
        setStatus("Crea un fichero en libraries/", "ok");
      }
    } catch (e) {
      setStatus(e.message, "err");
    }
  })();
})();
