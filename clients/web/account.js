"use strict";

window.MoonCatAccount = (() => {
  const native = window.MoonCatAccountNative;
  let snapshot = null;
  let account = null;
  let feedback = "";
  let confirmation = null;
  let pending = false;
  let panel;
  let request = 0;
  const listeners = new Set();
  const knownTitles = new Map();

  function send(action, input = {}) {
    native?.action(action, JSON.stringify(input));
  }

  function node(tag, text, props) {
    const result = document.createElement(tag);
    if (text) result.textContent = text;
    for (const [key, value] of Object.entries(props || {}))
      result.setAttribute(key, value);
    return result;
  }

  function button(label, click, primary = false) {
    const result = node("button", label, {
      type: "button",
      class: primary ? "button primary" : "button",
    });
    result.addEventListener("click", click);
    return result;
  }

  function showStatus(value) {
    feedback = value;
    const status = panel?.querySelector("[data-account-status]");
    if (status) status.textContent = feedback;
  }

  function publish(value) {
    snapshot = value;
    for (const callback of listeners) callback(value);
  }

  function progressRows(rows) {
    return (Array.isArray(rows) ? rows : []).filter((row) => {
      return /^tt\d+$/.test(row.content_id) && row.position > 0 &&
        row.duration > 0 && row.position / row.duration < 0.97;
    });
  }

  async function readSnapshot(event) {
    const generation = ++request;
    const list = {};
    for (const row of event.library || []) {
      if (!/^tt\d+$/.test(row.content_id)) continue;
      const item = MoonCat.title({ ...row, id: row.content_id, type: row.content_type, releaseInfo: row.release_info });
      list[item.id] = item;
      knownTitles.set(item.id, item);
    }
    const rows = progressRows(event.progress);
    const progress = {};
    for (const row of rows) {
      const item = knownTitles.get(row.content_id) || MoonCat.title({
        id: row.content_id, type: row.content_type,
        name: row.content_type === "series" ? "Series" : "Movie",
      });
      progress[row.video_id] = { item, position: row.position / 1000, duration: row.duration / 1000,
        label: row.season == null ? "" : `S${row.season} E${row.episode}`,
        episode: row.content_type === "series" ? row.video_id : "", updated: row.last_watched };
    }
    publish({ list, progress });
    const hydrate = [...new Map(Object.values(progress).slice(0, 8).filter(
      ({item}) => !knownTitles.has(item.id)).map(({item}) => [item.id, item])).values()];
    if (!hydrate.length) return;
    await Promise.all(hydrate.map(async (item) => {
      try {
        const result = await MoonCat.providerJson(MoonCat.pathFor(MoonCat.cinemeta, "meta", item.type, item.id));
        if (result.meta) knownTitles.set(item.id, MoonCat.title({...result.meta, id: item.id, type: item.type}));
      } catch { /* Progress remains available without catalogue artwork. */ }
    }));
    if (generation !== request) return;
    for (const entry of Object.values(progress)) entry.item = knownTitles.get(entry.item.id) || entry.item;
    publish({ list, progress });
  }

  window.__mooncatAccountEvent = (event) => {
    switch (event.event) {
      case "guest":
        ++request;
        account = null;
        pending = false;
        confirmation = null;
        publish(null);
        draw();
        break;
      case "account": {
        const profileChanged = account?.profileId !== event.profileId;
        account = event;
        pending = false;
        if (profileChanged) {
          ++request;
          publish({ list: {}, progress: {} });
        }
        draw();
        break;
      }
      case "pending":
        pending = true;
        feedback = "Approve the browser sign-in request. Your code is " + event.code + ".";
        draw();
        break;
      case "snapshot":
        if (event.profileId === account?.profileId) {
          readSnapshot(event);
          showStatus("");
        }
        break;
      case "confirmTv":
        confirmation = event;
        feedback = "";
        draw();
        break;
      case "tvApproved":
        confirmation = null;
        feedback = "TV sign-in approved.";
        draw();
        break;
      case "error":
        pending = false;
        showStatus(event.message);
        draw();
        break;
    }
  };

  function draw() {
    if (!panel) return;
    const state = node("p", feedback, { class: "muted", role: "status", "data-account-status": "" });
    panel.replaceChildren(node("h2", "Account"));
    if (account) {
      panel.append(node("p", account.email || "Signed in", { class: "metadata" }));
      if (account.profiles?.length) {
        const profile = node("select", "", { "aria-label": "Sync profile" });
        for (const entry of account.profiles) profile.append(node("option", entry.name, { value: entry.id }));
        profile.value = account.profileId;
        profile.addEventListener("change", () => {
          showStatus("Switching profile…");
          send("switchProfile", { profileId: Number(profile.value) });
        });
        const field = node("label", "Sync profile", { class: "field" });
        field.append(profile);
        panel.append(field);
      } else {
        panel.append(node("p", "Choose an account profile without a PIN to sync on this phone.", { class: "muted" }));
      }

      const actions = node("div", "", { class: "actions" });
      actions.append(button("Sync now", refresh), button("Sign out", () => send("signOut")));
      panel.append(actions);
      if (confirmation) {
        const candidate = confirmation;
        panel.append(node("h3", "Approve TV sign-in"),
          node("p", `Only approve if ${candidate.device} shows code ${candidate.code}.`, { class: "muted" }),
          button("Approve TV sign-in", () => {
            send("approveTv", { value: candidate.code });
            showStatus("Approving…");
          }, true),
          button("Cancel", () => { confirmation = null; draw(); }));
      } else {
        const form = node("form", "", { class: "settings-form" });
        const input = node("input", "", { type: "text", placeholder: "ABC123", required: "",
          minlength: 6, maxlength: 8, autocomplete: "off", autocapitalize: "characters", "aria-label": "TV sign-in code" });
        const field = node("label", "TV sign-in code", { class: "field" });
        field.append(input);
        form.append(field, node("button", "Check TV code", { type: "submit", class: "button" }));
        form.addEventListener("submit", (event) => {
          event.preventDefault();
          send("previewTv", { value: input.value });
          showStatus("Checking TV code…");
        });
        panel.append(form);
      }
    } else if (pending) {
      panel.append(button("Cancel sign-in", () => { pending = false; feedback = ""; send("cancel"); }));
    } else {
      panel.append(node("p", "Sign in with the same account on TV and phone to sync My List and Continue Watching.", { class: "muted" }));
      panel.append(button("Sign in", () => {
        showStatus("Opening account sign-in…");
        send("signIn");
      }, true));
      const custom = node("details");
      const form = node("form", "", { class: "settings-form" });
      const field = node("label", "HTTPS backend address", { class: "field" });
      const input = node("input", "", { type: "url", placeholder: "https://your-server.example", required: "", autocomplete: "off" });
      field.append(input);
      form.append(field, node("button", "Sign in on this server", { type: "submit", class: "button" }));
      form.addEventListener("submit", (event) => {
        event.preventDefault();
        showStatus("Opening account sign-in…");
        send("signIn", { backend: input.value });
      });
      custom.append(node("summary", "Use a custom server"), form);
      panel.append(custom);
    }
    panel.append(state);
  }

  function render() {
    if (!native) return null;
    panel = node("section", "", { class: "stack", "aria-label": "MoonCat account" });
    draw();
    return panel;
  }

  function refresh() {
    if (account) send("refresh");
  }

  function onSnapshot(callback) {
    if (!native) return;
    listeners.add(callback);
    if (snapshot) callback(snapshot);
  }

  function scanCode(raw) {
    try {
      const value = new URL(raw);
      if (value.protocol !== "https:" || !value.pathname.endsWith("/link") || !value.searchParams.has("code")) return false;
      send("previewTv", { value: raw });
      return true;
    } catch {
      return false;
    }
  }

  function setSaved(item, saved) {
    if (!account?.profileId) return;
    knownTitles.set(item.id, item);
    const { videos, ...title } = item;
    send("saveTitle", { profileId: account.profileId, item: title, saved });
  }

  function record(media, position, duration, complete) {
    if (!account?.profileId || !media?.item || !Number.isFinite(position) || !Number.isFinite(duration)) return;
    const [contentId, season, episode] = media.videoId.split(":");
    knownTitles.set(media.item.id, media.item);
    send("saveProgress", { profileId: account.profileId, contentId, videoId: media.videoId, season: Number(season),
      episode: Number(episode), type: media.item.type, position: Math.round(position * 1000),
      duration: Math.round(duration * 1000), complete: !!complete });
  }

  function start() {
    send("restore");
    document.addEventListener("visibilitychange", () => { if (!document.hidden) refresh(); });
    if (native) setInterval(() => { if (!document.hidden) refresh(); }, 45_000);
  }

  return { render, scanCode, setSaved, record, refresh, onSnapshot, start };
})();
