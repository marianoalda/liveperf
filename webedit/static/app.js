(() => {
  const state = {
    files: [],
    catalog: [],
    fileName: null,
    library: null,
    dirty: false,
    mode: "tree", // tree | labels
    sel: { g: null, s: null, song: null },
    labelSel: null, // v3 name
    labelDraft: "",
    labelPicking: false,
    labelCreating: false,
    labelCreateDraft: "",
    labelCreatePicking: false,
    instrumentFilter: "",
    labelInstrFilter: "",
    labelCreateFilter: "",
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
    return { groups: [], instrumentLabels: {} };
  }

  function ensureShape(lib) {
    if (!lib || typeof lib !== "object") return emptyLibrary();
    if (!Array.isArray(lib.groups)) lib.groups = [];
    if (!lib.instrumentLabels || typeof lib.instrumentLabels !== "object") {
      lib.instrumentLabels = {};
    }
    const normalizeInstr = (raw) => {
      if (raw && typeof raw === "object") {
        const name = raw.name || raw.instrument || "";
        const label = (raw.label || "").trim();
        if (name && label) lib.instrumentLabels[name] = label;
        return name;
      }
      return String(raw || "");
    };
    lib.groups.forEach((g) => {
      if (!Array.isArray(g.setlists)) g.setlists = [];
      g.setlists.forEach((sl) => {
        if (!Array.isArray(sl.songs)) sl.songs = [];
        sl.songs.forEach((song) => {
          if (!Array.isArray(song.instruments)) song.instruments = [];
          song.instruments = song.instruments.map(normalizeInstr).filter(Boolean);
          if (song.instrumentNotes == null) song.instrumentNotes = "";
          if (song.key == null) song.key = "";
        });
      });
    });
    return lib;
  }

  function displayLabel(v3Name) {
    const custom = (state.library.instrumentLabels[v3Name] || "").trim();
    return custom || `(?) ${v3Name}`;
  }

  function setLabel(v3Name, label) {
    const trimmed = (label || "").trim();
    if (!state.library.instrumentLabels) state.library.instrumentLabels = {};
    if (trimmed) state.library.instrumentLabels[v3Name] = trimmed;
    else delete state.library.instrumentLabels[v3Name];
    markDirty();
  }

  function pcOf(name) {
    const s = state.catalog.find((x) => x.name === name);
    return s ? s.program : null;
  }

  function labelEntries() {
    const labels = state.library?.instrumentLabels || {};
    return Object.entries(labels)
      .map(([v3Name, label]) => {
        const trimmed = (label || "").trim();
        const program = pcOf(v3Name);
        return trimmed && program != null
          ? { v3Name, label: trimmed, program }
          : null;
      })
      .filter(Boolean)
      .sort((a, b) => a.label.localeCompare(b.label, "es", { sensitivity: "base" }));
  }

  function forEachSong(fn) {
    (state.library.groups || []).forEach((g) => {
      (g.setlists || []).forEach((sl) => {
        (sl.songs || []).forEach((song) => fn(song));
      });
    });
  }

  /** Remap all song instruments from old V3 name to new; move label. */
  function remapLabeledInstrument(oldV3Name, newV3Name, newLabel) {
    const label = (newLabel || "").trim();
    if (!state.library.instrumentLabels) state.library.instrumentLabels = {};
    delete state.library.instrumentLabels[oldV3Name];
    if (label) state.library.instrumentLabels[newV3Name] = label;
    else delete state.library.instrumentLabels[newV3Name];

    forEachSong((song) => {
      if (!Array.isArray(song.instruments)) return;
      const next = [];
      const seen = new Set();
      song.instruments.forEach((name) => {
        const n = name === oldV3Name ? newV3Name : name;
        if (!seen.has(n)) {
          seen.add(n);
          next.push(n);
        }
      });
      song.instruments = next;
    });
    markDirty();
  }

  function renderInstrumentPicker(opts) {
    const {
      filterId,
      pickerId,
      filterValue,
      excludeNames = new Set(),
      emptyOk = true,
    } = opts;
    const q = (filterValue || "").trim();
    const matches = state.catalog.filter((sound) => {
      if (!emptyOk && excludeNames.has(sound.name)) return false;
      if (excludeNames.has(sound.name)) return false;
      if (!q) return true;
      return (
        sound.name.toLowerCase().includes(q.toLowerCase()) ||
        String(sound.program) === q
      );
    });
    return `
      <div class="field">
        <label>Filtrar por nombre (sin distinguir mayúsculas)</label>
        <input type="text" id="${filterId}" value="${escapeAttr(filterValue || "")}" placeholder="piano, organ, pad…" />
      </div>
      <p class="hint">${matches.length} coincidencias · desplaza la lista</p>
      <div class="picker" id="${pickerId}">
        ${matches
          .map(
            (s) => `
          <div class="item" data-name="${escapeAttr(s.name)}" data-program="${s.program}">
            ${escapeHtml(s.name)}<span class="pc">PC ${s.program}</span>
          </div>`
          )
          .join("")}
      </div>
    `;
  }

  function setMode(mode) {
    state.mode = mode;
    state.labelPicking = false;
    $("mode-tree").classList.toggle("active", mode === "tree");
    $("mode-labels").classList.toggle("active", mode === "labels");
    const toolbar = $("tree-toolbar");
    const title = $("tree-title");
    if (mode === "labels") {
      title.textContent = "Etiquetas";
      toolbar.style.display = "none";
    } else {
      title.textContent = "Grupos";
      toolbar.style.display = "";
    }
    renderAll();
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
    state.labelSel = null;
    state.labelDraft = "";
    state.labelPicking = false;
    state.labelCreating = false;
    state.labelCreateDraft = "";
    state.labelCreatePicking = false;
    state.instrumentFilter = "";
    state.labelInstrFilter = "";
    state.labelCreateFilter = "";
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

    if (state.mode === "labels") {
      const addLi = document.createElement("li");
      addLi.className = state.labelCreating ? "active" : "";
      addLi.innerHTML = `<span class="label">${state.labelCreating ? "Creando…" : "+ Crear etiqueta"}<span class="sub">Literal + instrumento V3</span></span>`;
      addLi.onclick = () => {
        state.labelCreating = !state.labelCreating;
        state.labelCreatePicking = false;
        state.labelCreateDraft = "";
        state.labelCreateFilter = "";
        if (state.labelCreating) {
          state.labelSel = null;
          state.labelPicking = false;
        }
        renderAll();
      };
      ul.appendChild(addLi);

      const entries = labelEntries();
      if (!entries.length && !state.labelCreating) {
        const empty = document.createElement("li");
        empty.className = "empty-li";
        empty.innerHTML = `<span class="label">Sin etiquetas<span class="sub">Crea una o asígnalas en canciones</span></span>`;
        ul.appendChild(empty);
        return;
      }
      entries.forEach((entry) => {
        const li = document.createElement("li");
        if (state.labelSel === entry.v3Name && !state.labelCreating) li.classList.add("active");
        li.innerHTML = `<span class="label">${escapeHtml(entry.label)}<span class="sub">PC ${entry.program} · ${escapeHtml(entry.v3Name)}</span></span>`;
        li.onclick = () => {
          state.labelSel = entry.v3Name;
          state.labelDraft = entry.label;
          state.labelPicking = false;
          state.labelInstrFilter = "";
          state.labelCreating = false;
          state.labelCreatePicking = false;
          renderAll();
        };
        ul.appendChild(li);
      });
      return;
    }

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
    if (state.mode === "labels") {
      panel.innerHTML = renderLabelsEditor();
      bindLabelsEditor();
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
            <span class="label">${escapeHtml(sl.name || "(sin nombre)")}<span class="sub">${(sl.songs || []).length} canciones</span></span>
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
            <span class="label">${i + 1}. ${escapeHtml(song.title || "(sin título)")}<span class="sub">${escapeHtml(songMeta(song))}</span></span>
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
    const pcOf = (name) => {
      const s = state.catalog.find((x) => x.name === name);
      return s ? s.program : "?";
    };
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
      <div class="section-title">Instrumentos V3 + etiqueta propia (botón)</div>
      <p class="hint">La etiqueta propia es la que se ve en el móvil. Sin ella aparece «(?)» + nombre V3.</p>
      <div id="ed-instr-list">
        ${(song.instruments || [])
          .map((name, i) => {
            const custom = (state.library.instrumentLabels[name] || "").trim();
            return `
          <div class="instr-card" data-i="${i}">
            <div class="instr-head">
              <strong>${escapeHtml(displayLabel(name))}</strong>
              <span class="pc">PC ${pcOf(name)}</span>
              <button type="button" class="danger small" data-rm="${i}" title="Quitar">×</button>
            </div>
            <div class="field">
              <label>Nombre V3</label>
              <input type="text" value="${escapeAttr(name)}" disabled />
            </div>
            <div class="field">
              <label>Etiqueta propia (botón)</label>
              <input type="text" class="instr-label" data-name="${escapeAttr(name)}"
                value="${escapeAttr(custom)}" placeholder="ej. Piano, Órgano…" />
            </div>
          </div>`;
          })
          .join("") || `<span class="hint">Ninguno aún</span>`}
      </div>
      <div class="field" style="margin-top:0.75rem">
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
    document.querySelectorAll("#ed-instr-list [data-rm]").forEach((btn) => {
      btn.onclick = () => {
        const i = Number(btn.dataset.rm);
        song.instruments.splice(i, 1);
        markDirty();
        renderDetail();
      };
    });
    document.querySelectorAll("#ed-instr-list .instr-label").forEach((input) => {
      input.onchange = () => setLabel(input.dataset.name, input.value);
      input.onblur = () => {
        setLabel(input.dataset.name, input.value);
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

  function renderLabelsEditor() {
    if (state.labelCreating) {
      return `
        <div class="crumb">Etiquetas › Nueva</div>
        <p class="hint">Compatible con asignar etiqueta al añadir instrumento en una canción (mismo mapa instrumentLabels).</p>
        <div class="field">
          <label>Literal de la etiqueta (botón)</label>
          <input type="text" id="lab-create-draft" value="${escapeAttr(state.labelCreateDraft)}" placeholder="ej. Piano, Órgano…" />
        </div>
        <div class="toolbar">
          <button type="button" id="lab-create-pick">
            ${state.labelCreatePicking ? "Ocultar catálogo" : "Elegir instrumento V3"}
          </button>
          <button type="button" id="lab-create-cancel">Cancelar</button>
        </div>
        ${
          state.labelCreatePicking
            ? `<p class="hint">Elige el sonido V3. Si ya tenía etiqueta, se sustituye.</p>
               ${renderInstrumentPicker({
                 filterId: "lab-create-filter",
                 pickerId: "lab-create-picker",
                 filterValue: state.labelCreateFilter,
                 excludeNames: new Set(),
               })}`
            : ""
        }
      `;
    }

    const entries = labelEntries();
    if (!entries.length) {
      return `<p class="empty">No hay etiquetas. Usa «+ Crear etiqueta» o asígnalas en una canción.</p>`;
    }
    const entry = entries.find((e) => e.v3Name === state.labelSel);
    if (!entry) {
      return `<p class="empty">Selecciona una etiqueta en la columna central o crea una nueva.</p>`;
    }
    const draft = state.labelDraft;
    return `
      <div class="crumb">Etiquetas › ${escapeHtml(entry.label)}</div>
      <p class="hint">Cambia el texto del botón o remapea la etiqueta a otro sonido V3 (se actualiza en todas las canciones).</p>
      <div class="field">
        <label>Literal de la etiqueta (botón)</label>
        <input type="text" id="lab-draft" value="${escapeAttr(draft)}" />
      </div>
      <p><strong>Programa actual:</strong> PC ${entry.program}</p>
      <p><strong>Instrumento V3:</strong> ${escapeHtml(entry.v3Name)}</p>
      <div class="toolbar">
        <button type="button" id="lab-save-literal" class="primary">Guardar literal</button>
        <button type="button" id="lab-toggle-pick">
          ${state.labelPicking ? "Ocultar catálogo" : "Cambiar instrumento V3"}
        </button>
        <button type="button" id="lab-delete" class="danger">Eliminar etiqueta</button>
      </div>
      <p class="hint">Eliminar quita solo la etiqueta propia; el instrumento sigue en las canciones.</p>
      ${
        state.labelPicking
          ? `<p class="hint">Elige el nuevo sonido. Todas las canciones con «${escapeHtml(entry.v3Name)}» pasarán al nuevo instrumento.</p>
             ${renderInstrumentPicker({
               filterId: "lab-instr-filter",
               pickerId: "lab-instr-picker",
               filterValue: state.labelInstrFilter,
               excludeNames: new Set(),
             })}`
          : ""
      }
    `;
  }

  function bindLabelsEditor() {
    if (state.labelCreating) {
      const draft = $("lab-create-draft");
      $("lab-create-cancel").onclick = () => {
        state.labelCreating = false;
        state.labelCreatePicking = false;
        state.labelCreateDraft = "";
        renderAll();
      };
      $("lab-create-pick").onclick = () => {
        state.labelCreateDraft = draft?.value || "";
        if (!state.labelCreateDraft.trim()) {
          setStatus("Escribe el literal de la etiqueta primero", "err");
          return;
        }
        state.labelCreatePicking = !state.labelCreatePicking;
        renderAll();
      };
      const filter = $("lab-create-filter");
      if (filter) {
        filter.oninput = () => {
          state.labelCreateDraft = $("lab-create-draft")?.value ?? state.labelCreateDraft;
          state.labelCreateFilter = filter.value;
          const pos = filter.selectionStart;
          renderAll();
          const again = $("lab-create-filter");
          if (again) {
            again.focus();
            again.setSelectionRange(pos, pos);
          }
        };
      }
      document.querySelectorAll("#lab-create-picker .item").forEach((el) => {
        el.onclick = () => {
          const name = el.dataset.name;
          const text = ($("lab-create-draft")?.value || state.labelCreateDraft).trim();
          if (!name || !text) return;
          setLabel(name, text);
          state.labelSel = name;
          state.labelDraft = text;
          state.labelCreating = false;
          state.labelCreatePicking = false;
          state.labelCreateDraft = "";
          state.labelCreateFilter = "";
          setStatus("Etiqueta creada (recuerda Guardar el fichero)", "ok");
          renderAll();
        };
      });
      return;
    }

    const saveLit = $("lab-save-literal");
    if (!saveLit) return;
    const entryName = state.labelSel;
    saveLit.onclick = () => {
      const text = $("lab-draft").value.trim();
      if (!text || !entryName) return;
      setLabel(entryName, text);
      state.labelDraft = text;
      setStatus("Literal actualizado (recuerda Guardar el fichero)", "ok");
      renderAll();
    };
    $("lab-toggle-pick").onclick = () => {
      state.labelDraft = $("lab-draft").value;
      state.labelPicking = !state.labelPicking;
      renderAll();
    };
    $("lab-delete").onclick = () => {
      if (!entryName) return;
      const label = state.library.instrumentLabels[entryName] || entryName;
      if (!confirm(`¿Eliminar la etiqueta «${label}»? El instrumento V3 seguirá en las canciones.`)) {
        return;
      }
      setLabel(entryName, "");
      state.labelSel = null;
      state.labelDraft = "";
      state.labelPicking = false;
      setStatus("Etiqueta eliminada (recuerda Guardar el fichero)", "ok");
      renderAll();
    };
    const filter = $("lab-instr-filter");
    if (filter) {
      filter.oninput = () => {
        state.labelDraft = $("lab-draft")?.value ?? state.labelDraft;
        state.labelInstrFilter = filter.value;
        const pos = filter.selectionStart;
        renderAll();
        const again = $("lab-instr-filter");
        if (again) {
          again.focus();
          again.setSelectionRange(pos, pos);
        }
      };
    }
    document.querySelectorAll("#lab-instr-picker .item").forEach((el) => {
      el.onclick = () => {
        const newName = el.dataset.name;
        const draft = ($("lab-draft")?.value || state.labelDraft).trim();
        if (!entryName || !newName) return;
        remapLabeledInstrument(entryName, newName, draft || state.labelDraft);
        state.labelSel = newName;
        state.labelDraft = draft || state.labelDraft;
        state.labelPicking = false;
        state.labelInstrFilter = "";
        setStatus("Instrumento remapeado en todas las canciones (recuerda Guardar)", "ok");
        renderAll();
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
  $("mode-tree").onclick = () => setMode("tree");
  $("mode-labels").onclick = () => setMode("labels");
  $("btn-add-group").onclick = () => {
    if (!state.library) {
      setStatus("Abre o crea un fichero primero", "err");
      return;
    }
    if (state.mode !== "tree") setMode("tree");
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
