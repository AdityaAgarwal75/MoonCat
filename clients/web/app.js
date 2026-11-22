"use strict";

const main = document.querySelector("main");
const nav = document.querySelector("#nav");
const notice = document.querySelector("#notice");
const android = window.MoonCatAndroid;
const artwork = new Map();
const studioData = new Map();
const sourceRequests = new Map();
const sections = {
  home: ["Home", "m3 10 9-7 9 7v10h-6v-6H9v6H3z"],
  search: ["Search", "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zm5 12 4 4"],
  studios: ["Studios", "M3 5h18v14H3z M3 9h18 M7 5v4 M14 5v4"],
  news: ["Live", "M3 5h18v13H3z M8 21h8 M10 9l5 3-5 3z"],
  library: ["My List", "M6 3h12v18l-6-4-6 4z"],
  settings: ["Settings", "M4 7h16m-16 5h16m-16 5h16 M8 5v4m8 1v4m-6 1v4"],
};

const settings = { addons: [], preferred: "hi", list: {}, progress: {} };
let catalogues;
let version = 0;
let lastRoute = "";
let routeHistory = [];
let noticeTimer;
let playerCleanup;
let remoteSave = Promise.resolve();
const positionByRoute = new Map();

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(props)) {
    if (key === "class") node.className = value;
    else if (key === "click") node.addEventListener("click", value);
    else if (value !== null && value !== undefined && value !== false)
      node.setAttribute(key, value === true ? "" : String(value));
  }
  for (const child of children.flat())
    if (child !== null && child !== undefined) node.append(child);
  return node;
}

function icon(path) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("aria-hidden", "true");
  const line = document.createElementNS(svg.namespaceURI, "path");
  for (const [key, value] of Object.entries({
    d: path,
    fill: "none",
    stroke: "currentColor",
    "stroke-width": 1.65,
    "stroke-linecap": "round",
    "stroke-linejoin": "round",
  }))
    line.setAttribute(key, value);
  svg.append(line);
  return svg;
}

function button(label, click, primary = false) {
  return el(
    "button",
    { class: primary ? "button primary" : "button", type: "button", click },
    label,
  );
}

function heading(title, description) {
  return el(
    "header",
    { class: "page-head" },
    el("h1", {}, title),
    description && el("p", { class: "muted" }, description),
  );
}

function status(text, error = false) {
  return el(
    "p",
    { class: error ? "status error" : "status", role: "status" },
    text,
  );
}

function message(text) {
  notice.textContent = text;
  notice.hidden = false;
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => {
    notice.hidden = true;
  }, 3300);
}

function persist() {
  const json = JSON.stringify(settings);
  if (android) {
    try {
      localStorage.setItem("mooncat-v3-client", json);
    } catch {
      message("MoonCat could not save your changes.");
    }
  } else {
    remoteSave = remoteSave
      .catch(() => {})
      .then(() => MoonCat.rpc("save", JSON.parse(json)));
    remoteSave.catch(() => message("MoonCat could not save your changes."));
  }
}

function navigate(route, { replace = false } = {}) {
  const hash = "#/" + route;
  if (location.hash === hash) return;
  if (replace) {
    history.replaceState(null, "", hash);
    renderPage();
  } else {
    routeHistory.push(lastRoute);
    location.hash = hash;
  }
}

function back() {
  if (routeHistory.length) {
    routeHistory.pop();
    history.back();
    return true;
  }
  if (lastRoute !== "home") {
    navigate("home", { replace: true });
    return true;
  }
  return false;
}

window.mooncatBack = back;

function currentRoute() {
  return location.hash.startsWith("#/") ? location.hash.slice(2) : "home";
}

function renderNav(active) {
  for (const [id, item] of Object.entries(sections)) {
    if (nav.querySelector(`[data-route="${id}"]`)) continue;
    const link = el(
      "a",
      { class: "nav-link", href: "#/" + id, "data-route": id },
      icon(item[1]),
      el("span", {}, item[0]),
      id === "search" ? el("kbd", { "aria-hidden": "true" }, "Ctrl K") : null,
    );
    link.addEventListener("click", (event) => {
      event.preventDefault();
      navigate(id);
    });
    nav.append(link);
  }
  for (const link of nav.children) {
    if (link.dataset.route === active)
      link.setAttribute("aria-current", "page");
    else link.removeAttribute("aria-current");
  }
}

function art(url, className, label = "", lazy = true) {
  if (!url) return null;
  const img = el("img", {
    src: url,
    class: className,
    alt: label,
    loading: lazy ? "lazy" : "eager",
    decoding: "async",
    referrerpolicy: "no-referrer",
  });
  img.addEventListener("error", () => img.remove(), { once: true });
  return img;
}

function rememberTitle(item) {
  artwork.set(item.id, item);
  return item;
}

function openDetail(item, episode = "") {
  rememberTitle(item);
  navigate(
    `detail/${item.type}/${item.id}${episode ? "/" + encodeURIComponent(episode) : ""}`,
  );
}

function posterCard(item, resume) {
  const image = el(
    "span",
    { class: "card-image" },
    el("span", {}, item.name),
    art(item.poster),
  );
  if (resume?.duration > 0)
    image.append(
      el("progress", {
        class: "progress",
        value: resume.position,
        max: resume.duration,
        "aria-label": "Playback progress",
      }),
    );
  return el(
    "button",
    {
      class: "card",
      type: "button",
      "aria-label": item.name,
      click: () => openDetail(item, resume?.episode),
    },
    image,
    el("span", { class: "card-name" }, item.name),
    el(
      "span",
      { class: "card-note" },
      resume?.label ||
        item.year ||
        (item.type === "series" ? "Series" : "Movie"),
    ),
  );
}

function logoCard(item, label, click, news = false) {
  return el(
    "button",
    { class: "card logo-card" + (news ? " news" : ""), type: "button", click },
    el("span", { class: "card-image" }, item.name, art(item.logo)),
    el("span", { class: "card-name" }, item.name),
    el("span", { class: "card-note" }, label),
  );
}

function rail(name, cards) {
  if (!cards.length) return null;
  const row = el("div", { class: "rail" }, cards);
  const scroll = (sign) =>
    row.scrollBy({
      left: sign * Math.max(150, row.clientWidth * 0.7),
      behavior: matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "instant"
        : "smooth",
    });
  return el(
    "section",
    {},
    el(
      "header",
      { class: "section-head" },
      el("h2", {}, name),
      el(
        "div",
        { class: "row-controls" },
        el(
          "button",
          {
            type: "button",
            "aria-label": "Scroll " + name + " left",
            click: () => scroll(-1),
          },
          "‹",
        ),
        el(
          "button",
          {
            type: "button",
            "aria-label": "Scroll " + name + " right",
            click: () => scroll(1),
          },
          "›",
        ),
      ),
    ),
    row,
  );
}

function hero(item, actions, detail = false) {
  const info = [
    item.type === "series" ? "Series" : "Movie",
    item.year,
    ...item.genres.slice(0, 2),
  ]
    .filter(Boolean)
    .join(" · ");
  return el(
    "section",
    { class: "hero" },
    art(item.background || item.poster, "hero-media", "", false),
    el(
      "div",
      { class: "hero-copy" },
      el("p", { class: "metadata" }, info),
      el(
        "h1",
        { class: detail ? "" : "hero-title" },
        item.name,
      ),
      item.description && el("p", {}, item.description),
      el("div", { class: "actions" }, actions),
    ),
  );
}

async function getStudio(id) {
  if (!catalogues.studios.some((item) => item.id === id))
    throw new Error("Studio unavailable.");
  if (!studioData.has(id)) {
    const result = fetch(`studios/${id}.json`)
      .then((response) => {
        if (!response.ok) throw new Error("Studio catalogue unavailable.");
        return response.json();
      })
      .then((items) => items.map(MoonCat.title))
      .catch((error) => {
        studioData.delete(id);
        throw error;
      });
    studioData.set(id, result);
  }
  return studioData.get(id);
}

function renderContinue() {
  return rail(
    "Continue watching",
    Object.values(settings.progress)
      .sort((a, b) => b.updated - a.updated)
      .slice(0, 14)
      .map((entry) => posterCard(entry.item, entry)),
  );
}

async function home(token) {
  const content = el("div", {}, status("Loading featured titles…"));
  main.append(content);
  const studioCards = rail(
    "Studios & collections",
    catalogues.studios.map((item) =>
      logoCard(item, `${item.count.toLocaleString()} titles`, () =>
        navigate("studio/" + item.id),
      ),
    ),
  );
  if (studioCards) main.append(studioCards);
  const requests = ["movie", "series"].map((type) =>
    MoonCat.providerJson(
      MoonCat.pathFor(MoonCat.cinemeta, "catalog", type, "top"),
    ).then(MoonCat.titles),
  );
  const [movies, series, fallback] = await Promise.allSettled([
    ...requests,
    getStudio("marvel"),
  ]);
  if (token !== version) return;
  const filmList = movies.value || [];
  const seriesList = series.value || [];
  const sample = fallback.value || [];
  const featured =
    filmList.find((item) => item.background) ||
    filmList[0] ||
    seriesList[0] ||
    sample[0];
  content.replaceChildren();
  if (featured)
    content.append(
      hero(featured, [
        button("View details", () => openDetail(featured), true),
      ]),
    );
  const playing = renderContinue();
  if (playing) content.append(playing);
  for (const [name, items] of [
    ["Popular movies", filmList],
    ["Popular series", seriesList],
    [
      "From the collections",
      !filmList.length && !seriesList.length ? sample : [],
    ],
  ]) {
    const row = rail(
      name,
      items.slice(0, 18).map((item) => posterCard(item)),
    );
    if (row) content.append(row);
  }
  if (!featured)
    content.append(
      status(
        "The online catalogue is unavailable. Browse Studios to keep exploring.",
        true,
      ),
    );
}

function studios() {
  main.append(heading("Studios & collections"));
  main.append(
    el(
      "div",
      { class: "grid wide" },
      catalogues.studios.map((item) =>
        logoCard(item, `${item.count.toLocaleString()} titles`, () =>
          navigate("studio/" + item.id),
        ),
      ),
    ),
  );
}

function filterButtons(options, selected, action) {
  return el(
    "div",
    { class: "filters", "aria-label": "Filters" },
    options.map((option) =>
      el(
        "button",
        {
          class: "chip",
          "aria-pressed": option === selected ? "true" : "false",
          type: "button",
          click: () => action(option),
        },
        option,
      ),
    ),
  );
}

function paginated(items, target) {
  const grid = el("div", { class: "grid" });
  let offset = 0;
  const more = button("Show more", () => appendPage(true));
  function appendPage(interactive) {
    const cards = items
      .slice(offset, offset + 48)
      .map((item) => posterCard(item));
    grid.append(...cards);
    offset += cards.length;
    more.hidden = offset >= items.length;
    if (interactive && cards[0]) cards[0].focus({ preventScroll: true });
  }
  appendPage(false);
  target.replaceChildren(
    items.length ? grid : status("No titles match these filters."),
    more,
  );
}

async function studio(id, token) {
  const active = catalogues.studios.find((item) => item.id === id);
  if (!active) throw new Error("Studio unavailable.");
  main.append(
    heading(active.name),
    button("‹ All studios", () => navigate("studios")),
  );
  const filters = el("div", { class: "episodes" });
  const results = el("div", { class: "episodes" }, status("Loading titles…"));
  main.append(filters, results);
  const items = await getStudio(id);
  if (token !== version) return;
  const input = el("input", {
    type: "search",
    placeholder: "Filter this collection",
    "aria-label": "Filter titles",
  });
  const row = el("div");
  filters.append(el("div", { class: "field" }, input), row);
  let selected = "All";
  function update() {
    row.replaceChildren(
      filterButtons(["All", "Movies", "Series"], selected, (value) => {
        selected = value;
        update();
        row.querySelector('[aria-pressed="true"]').focus();
      }),
    );
    const query = input.value.trim().toLowerCase();
    paginated(
      items.filter(
        (item) =>
          (!query || item.name.toLowerCase().includes(query)) &&
          (selected === "All" ||
            (selected === "Series") === (item.type === "series")),
      ),
      results,
    );
  }
  input.addEventListener("input", () => {
    const query = input.value.trim().toLowerCase();
    paginated(
      items.filter(
        (item) =>
          item.name.toLowerCase().includes(query) &&
          (selected === "All" ||
            (selected === "Series") === (item.type === "series")),
      ),
      results,
    );
  });
  update();
}

async function search(token, initial) {
  const field = el("input", {
    id: "search-query",
    type: "search",
    placeholder: "Search movies and series",
    "aria-label": "Search movies and series",
    maxlength: 120,
    autocomplete: "off",
  });
  const results = el("div");
  const form = el(
    "form",
    { class: "search-form" },
    field,
    el("button", { class: "button primary", type: "submit" }, "Search"),
  );
  let request = 0;
  async function find() {
    const query = field.value.trim();
    const id = ++request;
    if (query.length < 2) {
      results.replaceChildren(status("Enter at least two characters."));
      return;
    }
    history.replaceState(null, "", "#/search/" + encodeURIComponent(query));
    lastRoute = currentRoute();
    results.replaceChildren(status("Searching movies and series…"));
    const remote = ["movie", "series"].map((type) =>
      MoonCat.providerJson(
        `${MoonCat.cinemeta}/catalog/${type}/top/search=${encodeURIComponent(query)}.json`,
      ).then(MoonCat.titles),
    );
    const [movies, series, ...local] = await Promise.allSettled([
      ...remote,
      ...catalogues.studios.map((item) => getStudio(item.id)),
    ]);
    if (token !== version || id !== request) return;
    const found = new Map();
    for (const item of [
      ...(movies.value || []),
      ...(series.value || []),
      ...local
        .flatMap((item) => item.value || [])
        .filter((item) => item.name.toLowerCase().includes(query.toLowerCase()))
        .slice(0, 80),
    ]) {
      if (!found.has(item.id)) found.set(item.id, item);
    }
    paginated([...found.values()], results);
    if (
      !found.size &&
      movies.status === "rejected" &&
      series.status === "rejected"
    ) {
      results.replaceChildren(
        status(
          "The online search is unavailable. No matches in the included collections.",
          true,
        ),
      );
    }
  }
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    find();
  });
  main.append(heading("Search"), form, results);
  field.value = initial;
  if (initial) await find();
  else field.focus({ preventScroll: true });
}

function news() {
  const channels = catalogues.channels.filter((item) =>
    ["Hindi", "English"].includes(item.language),
  );
  main.append(heading("Live news"));
  const filters = el("div");
  const grid = el("div", { class: "grid wide episodes" });
  main.append(filters, grid);
  function select(language, focus = false) {
    filters.replaceChildren(
      filterButtons(["All", "Hindi", "English"], language, (value) =>
        select(value, true),
      ),
    );
    if (focus) filters.querySelector('[aria-pressed="true"]').focus();
    grid.replaceChildren(
      ...channels
        .filter(
          (channel) => language === "All" || channel.language === language,
        )
        .map((channel) =>
          logoCard(
            channel,
            `${channel.language} · Live`,
            () =>
              play(
                { item: { name: channel.name }, live: true, label: "Live" },
                [
                  {
                    ...channel,
                    provider: channel.name,
                    hls: true,
                    mime: "application/x-mpegURL",
                  },
                ],
              ),
            true,
          ),
        ),
    );
  }
  select("All");
}

function library() {
  const watching = renderContinue();
  main.append(heading("My List"));
  if (watching) main.append(watching);
  const all = Object.values(settings.list);
  main.append(
    el(
      "section",
      {},
      el("header", { class: "section-head" }, el("h2", {}, "Saved titles")),
      all.length
        ? el(
            "div",
            { class: "grid" },
            all.map((item) => posterCard(item)),
          )
        : status("Save movies and series from their detail pages."),
    ),
  );
}

function storedTitle(item) {
  const { videos, ...display } = item;
  return { ...display, videos: [] };
}

function addToList(item, control) {
  if (settings.list[item.id]) {
    delete settings.list[item.id];
    message("Removed from My List.");
  } else {
    settings.list[item.id] = storedTitle(item);
    message("Saved to My List.");
  }
  control.textContent = settings.list[item.id] ? "✓ In My List" : "+ My List";
  control.setAttribute(
    "aria-pressed",
    settings.list[item.id] ? "true" : "false",
  );
  persist();
}

function renderSettings() {
  main.append(heading("Settings"));
  const label = el(
    "label",
    { class: "field", for: "language" },
    "Preferred audio",
  );
  const select = el(
    "select",
    { id: "language" },
    el("option", { value: "hi" }, "Hindi"),
    el("option", { value: "en" }, "English"),
  );
  select.value = settings.preferred;
  select.addEventListener("change", () => {
    settings.preferred = select.value;
    persist();
  });
  label.append(select);

  const saved = el("div", { class: "stack" });
  function showAddons() {
    saved.replaceChildren(
      ...settings.addons.map((addon) =>
        el(
          "div",
          { class: "line-item plain" },
          el("strong", {}, addon.name),
          button("Remove", () => {
            settings.addons = settings.addons.filter(
              (item) => item.url !== addon.url,
            );
            sourceRequests.clear();
            persist();
            showAddons();
          }),
        ),
      ),
    );
  }
  showAddons();

  const field = el("input", {
    id: "addon",
    type: "url",
    required: true,
    autocomplete: "off",
    spellcheck: "false",
    placeholder: "https://provider.example/manifest.json",
  });
  const submit = el(
    "button",
    { type: "submit", class: "button primary" },
    "Add provider",
  );
  const outcome = el("p", { role: "status", class: "muted" });
  const form = el(
    "form",
    { class: "settings-form" },
    el(
      "label",
      { class: "field", for: "addon" },
      "Stremio-compatible manifest URL",
      field,
    ),
    el("div", { class: "actions" }, submit),
    outcome,
  );
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const url = MoonCat.safeUrl(
      field.value.trim().replace(/^stremio:/i, "https:"),
    );
    if (
      !url ||
      !new URL(url).pathname.endsWith("/manifest.json") ||
      new URL(url).search ||
      new URL(url).hash
    ) {
      outcome.textContent = "Enter an HTTPS address ending in /manifest.json.";
      return;
    }
    submit.disabled = true;
    outcome.textContent = "Checking provider…";
    try {
      const manifest = await MoonCat.providerJson(url);
      const resource =
        Array.isArray(manifest.resources) &&
        manifest.resources.find(
          (value) => value === "stream" || value?.name === "stream",
        );
      if (!resource)
        throw new Error(
          "This provider does not advertise movie or series streams.",
        );
      const name = String(manifest.name || "Stream provider").slice(0, 100);
      if (settings.addons.some((item) => item.url === url))
        throw new Error("This provider is already installed.");
      if (settings.addons.length >= 12)
        throw new Error("Remove a provider before adding another.");
      settings.addons.push({
        name,
        url,
        types:
          (resource?.types || manifest.types || []).filter?.(
            (type) => type === "movie" || type === "series",
          ) || [],
      });
      sourceRequests.clear();
      persist();
      field.value = "";
      outcome.textContent = name + " added.";
      showAddons();
    } catch (error) {
      outcome.textContent = error.message;
    } finally {
      submit.disabled = false;
    }
  });
  main.append(
    el(
      "section",
      { class: "stack" },
      label,
      el("h2", {}, "Playback providers"),
      el(
        "p",
        { class: "muted" },
        "Movie and series playback requires a provider that returns direct HTTPS streams. Add the manifest from your provider or media server.",
      ),
      saved,
      form,
    ),
  );
}

async function detail(type, id, episodeId, token) {
  if (!/^tt\d+$/.test(id) || !["movie", "series"].includes(type))
    throw new Error("Title unavailable.");
  const saved = artwork.get(id) || settings.list[id];
  main.append(status("Loading title…"));
  const item = (await MoonCat.providerJson(
    MoonCat.pathFor(MoonCat.cinemeta, "meta", type, id),
  )
    .then((value) =>
      value.meta ? MoonCat.title({ ...value.meta, id, type }) : null,
    )
    .catch(() => null)) ||
    saved || { id, type, name: id, genres: [], videos: [] };
  if (token !== version) return;
  rememberTitle(item);
  const list = button(settings.list[id] ? "✓ In My List" : "+ My List", () =>
    addToList(item, list),
  );
  list.setAttribute("aria-pressed", settings.list[id] ? "true" : "false");
  const actions = [list];
  const sources = el("section", { class: "stack episodes" });
  const directMedia = { item, videoId: id, label: "" };
  if (type === "movie") {
    actions.unshift(
      button(
        settings.progress[id] ? "Resume" : "Play",
        () => selectSources(directMedia, sources, true),
        true,
      ),
      button("Sources", () => selectSources(directMedia, sources, false)),
    );
  } else if (item.videos.length) {
    const entry =
      item.videos.find((video) => video.id === episodeId) ||
      item.videos.find((video) => settings.progress[video.id]) ||
      item.videos.find((video) => Number(video.season) > 0) ||
      item.videos[0];
    actions.unshift(
      button(
        settings.progress[entry.id]
          ? "Resume " + episodeLabel(entry)
          : "Play " + episodeLabel(entry),
        () => selectSources(episodeMedia(item, entry), sources, true),
        true,
      ),
    );
  }
  main.replaceChildren(
    el("div", { class: "detail-top" }, button("‹ Back", back)),
    hero(item, actions, true),
    sources,
  );
  main.classList.add("detail");
  if (type === "series") main.append(episodes(item, sources, episodeId));
}

function episodeLabel(video) {
  return `S${video.season} E${video.episode}`;
}

function episodeMedia(item, video) {
  return {
    item,
    videoId: video.id,
    label: episodeLabel(video) + (video.title ? ` · ${video.title}` : ""),
    episode: video.id,
  };
}

function episodes(item, sources, episodeId) {
  const valid = item.videos.filter(
    (video) =>
      typeof video.id === "string" &&
      Number.isFinite(Number(video.season)) &&
      Number.isFinite(Number(video.episode)),
  );
  if (!valid.length)
    return status("Episode data is unavailable for this series.", true);
  const seasons = [...new Set(valid.map((video) => Number(video.season)))].sort(
    (a, b) => (a || Infinity) - (b || Infinity),
  );
  const choose = el(
    "select",
    { id: "season", "aria-label": "Season" },
    seasons.map((season) =>
      el("option", { value: season }, season ? "Season " + season : "Specials"),
    ),
  );
  const list = el("div", { class: "stack" });
  const defaultSeason =
    valid.find((video) => video.id === episodeId)?.season ?? seasons[0];
  choose.value = String(defaultSeason);
  function show() {
    const buttons = valid
      .filter((video) => Number(video.season) === Number(choose.value))
      .sort((a, b) => Number(a.episode) - Number(b.episode))
      .map((video) => {
        const media = episodeMedia(item, video);
        return el(
          "button",
          {
            class: "line-item episode",
            type: "button",
            click: () => selectSources(media, sources, false),
          },
          art(MoonCat.safeUrl(video.thumbnail)) ||
            el(
              "span",
              { class: "episode-placeholder", "aria-hidden": "true" },
              "▶",
            ),
          el(
            "span",
            {},
            el("strong", {}, video.title || "Episode " + video.episode),
            el(
              "span",
              { class: "muted" },
              episodeLabel(video) +
                (settings.progress[video.id] ? " · Resume available" : ""),
            ),
          ),
        );
      });
    list.replaceChildren(...buttons);
  }
  choose.addEventListener("change", show);
  show();
  return el(
    "section",
    { class: "stack episodes" },
    el("h2", {}, "Episodes"),
    el("label", { class: "field", for: "season" }, "Season", choose),
    list,
  );
}

async function selectSources(media, target, autoplay) {
  const token = version;
  const selection = Symbol();
  target.selection = selection;
  const title = el(
    "h2",
    { tabindex: "-1" },
    "Sources" + (media.label ? " · " + media.label : ""),
  );
  target.replaceChildren(title, status("Finding available streams…"));
  title.focus();
  target.scrollIntoView({ block: "nearest" });
  if (!settings.addons.length) {
    target.replaceChildren(
      title,
      status("Add a playback provider to find streams for this title."),
      el(
        "div",
        {},
        button("Open playback settings", () => navigate("settings"), true),
      ),
    );
    title.focus();
    return;
  }

  const key = [
    settings.preferred,
    media.videoId,
    ...settings.addons.map((addon) => addon.url),
  ].join("|");
  if (!sourceRequests.has(key)) {
    const providers = settings.addons.filter(
      (addon) => !addon.types.length || addon.types.includes(media.item.type),
    );
    const lookup = Promise.allSettled(
      providers.map(async (provider) => {
        const result = await MoonCat.providerJson(
          MoonCat.pathFor(
            provider.url.slice(0, -"/manifest.json".length),
            "stream",
            media.item.type,
            media.videoId,
          ),
        );
        const entries = Array.isArray(result.streams)
          ? result.streams.filter((entry) => entry && typeof entry === "object")
          : [];
        return {
          entries: entries.map((entry) => ({ entry, provider: provider.name })),
          torrentOnly: entries.some((entry) => entry.infoHash && !entry.url),
        };
      }),
    );
    sourceRequests.set(key, lookup);
    if (sourceRequests.size > 25)
      sourceRequests.delete(sourceRequests.keys().next().value);
    setTimeout(() => {
      if (sourceRequests.get(key) === lookup) sourceRequests.delete(key);
    }, 120000);
  }

  const all = await sourceRequests.get(key);
  if (token !== version || target.selection !== selection) return;
  const successful = all
    .filter((item) => item.status === "fulfilled")
    .map((item) => item.value);
  const streams = MoonCat.orderedSources(
    successful.map((item) => item.entries),
    settings.preferred,
  );
  if (!streams.length) {
    const explanation = successful.some((item) => item.torrentOnly)
      ? "This provider returned torrent hashes. Use a provider configuration that returns direct HTTPS streams."
      : all.length && !successful.length
        ? "The playback providers are unavailable. Try again."
        : "No compatible streams were returned for this title.";
    const retry = button("Retry", () => {
      sourceRequests.delete(key);
      selectSources(media, target, autoplay);
    });
    target.replaceChildren(
      title,
      status(explanation, true),
      el(
        "div",
        { class: "actions" },
        retry,
        button("Playback settings", () => navigate("settings")),
      ),
    );
    title.focus();
    return;
  }
  const playable = streams.filter((stream) => stream.url);
  if (autoplay && playable.length) {
    play(media, playable.slice(0, 5));
    return;
  }
  const rows = streams.map((stream) =>
    el(
      "button",
      {
        class: "line-item",
        type: "button",
        click: () =>
          stream.url ? play(media, [stream]) : openExternal(stream.external),
      },
      el("strong", {}, stream.name),
      el(
        "span",
        { class: "muted" },
        [stream.provider, stream.quality, stream.url ? "" : "Opens in browser"]
          .filter(Boolean)
          .join(" · "),
      ),
    ),
  );
  target.replaceChildren(title, ...rows);
  title.focus();
}

function openExternal(url) {
  if (android) android.openExternal(url);
  else
    MoonCat.rpc("external", { url }).catch(() =>
      message("MoonCat could not open your browser."),
    );
}

function record(media, position, duration, complete = false) {
  if (!media || media.live || !media.videoId) return;
  if (complete || (duration > 0 && position / duration > 0.97)) {
    delete settings.progress[media.videoId];
  } else {
    if (
      !Number.isFinite(position) ||
      !Number.isFinite(duration) ||
      position < 3 ||
      duration <= 0
    )
      return;
    settings.progress[media.videoId] = {
      item: storedTitle(media.item),
      position,
      duration,
      episode: media.episode || "",
      label: media.label,
      updated: Date.now(),
    };
  }
  const progress = Object.entries(settings.progress)
    .sort((a, b) => b[1].updated - a[1].updated)
    .slice(0, 40);
  settings.progress = Object.fromEntries(progress);
  persist();
}

let nativePlayback;
window.__mooncatProgress = (token, position, duration, complete) => {
  if (nativePlayback?.token === token)
    record(nativePlayback.media, position, duration, complete);
};

function play(media, streams) {
  const resume = settings.progress[media.videoId]?.position || 0;
  if (android) {
    const token = String(Date.now()) + Math.random().toString(36).slice(2);
    nativePlayback = { token, media };
    android.play(
      JSON.stringify({
        token,
        title: media.item.name,
        label: media.label,
        live: !!media.live,
        streams,
        resume,
        language: settings.preferred,
      }),
    );
    return;
  }
  // Closing playback restores the exact browse location without adding a fake page to history.
  playerCleanup?.();
  const restore = window.scrollY;
  const focus = document.activeElement;
  const video = el("video", {
    controls: true,
    playsinline: true,
    tabindex: "0",
    "aria-label": media.item.name,
  });
  const label = el("p", { class: "player-status", role: "status" });
  const close = button("‹ Back", () => playerCleanup?.());
  const fullscreen = button("Fullscreen", () => {
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    else
      video.requestFullscreen?.().catch(() => {
        shell.classList.toggle("theater");
        fullscreen.textContent = shell.classList.contains("theater")
          ? "Exit theater view"
          : "Fullscreen";
      });
  });
  const fallback = button("Open in browser", () =>
    openExternal(streams[index].url),
  );
  fallback.hidden = true;
  const trackControls = el("div", { class: "actions" });
  const shell = el(
    "main",
    { class: "player-view" },
    el("div", { class: "player-top" }, close, el("h2", {}, media.item.name)),
    video,
    label,
    el("div", { class: "actions" }, fullscreen, fallback),
    trackControls,
  );
  let index = 0;
  let hls;
  let timeout;
  let serial = 0;
  let ready = false;
  let failedGeneration = -1;
  let lastPosition = resume;
  let updateTime = 0;
  let ended = false;
  const reset = () => {
    clearTimeout(timeout);
    if (hls) hls.destroy();
    hls = null;
    video.removeAttribute("src");
    video.load();
  };
  function failed(generation) {
    if (generation !== serial || failedGeneration === generation) return;
    failedGeneration = generation;
    if (index + 1 < streams.length && !ready) {
      index += 1;
      load();
      return;
    }
    clearTimeout(timeout);
    fallback.hidden = !streams[index]?.url;
    label.textContent =
      "Playback was interrupted or this format is unavailable on Windows.";
  }
  function addTrackPicker(name, options, selected, change, allowOff) {
    if (!options.length || (options.length === 1 && !allowOff)) return;
    const labels = options.map((track, i) =>
      el("option", { value: i }, track.name || track.lang || `Track ${i + 1}`),
    );
    if (allowOff) labels.unshift(el("option", { value: -1 }, "Off"));
    const select = el("select", { "aria-label": name }, labels);
    select.value = String(selected);
    select.addEventListener("change", () => change(Number(select.value)));
    trackControls.append(el("label", { class: "track-picker" }, name, select));
  }
  function showTracks(instance, selectPreferred = false) {
    if (instance !== hls) return;
    trackControls.replaceChildren();
    if (selectPreferred) {
      const preferred =
        settings.preferred === "en"
          ? /^(en|eng|english)\b/i
          : /^(hi|hin|hindi)\b/i;
      const matching = instance.audioTracks.findIndex((track) =>
        preferred.test(track.lang || track.name || ""),
      );
      if (matching >= 0) instance.audioTrack = matching;
    }
    addTrackPicker(
      "Audio",
      instance.audioTracks,
      instance.audioTrack,
      (index) => {
        instance.audioTrack = index;
      },
      false,
    );
    addTrackPicker(
      "Subtitles",
      instance.subtitleTracks,
      instance.subtitleTrack,
      (index) => {
        instance.subtitleTrack = index;
      },
      true,
    );
  }
  async function load() {
    const generation = ++serial;
    reset();
    trackControls.replaceChildren();
    ready = false;
    const stream = streams[index];
    label.textContent = `Connecting to ${stream.provider}…`;
    try {
      const result = await MoonCat.rpc("play", {
        url: stream.url,
        headers: stream.headers || {},
        hls: !!stream.hls,
      });
      if (generation !== serial) return;
      if (result.hls && window.Hls?.isSupported()) {
        const instance = new window.Hls({
          maxBufferLength: 22,
          backBufferLength: 15,
          capLevelToPlayerSize: true,
        });
        hls = instance;
        instance.on(window.Hls.Events.ERROR, (_event, data) => {
          if (data.fatal) failed(generation);
        });
        instance.on(window.Hls.Events.MANIFEST_PARSED, () => {
          const cap = instance.levels.findLastIndex(
            (level) => level.height <= 1080,
          );
          if (cap >= 0 && cap < instance.levels.length - 1)
            instance.autoLevelCapping = cap;
          showTracks(instance, true);
          video.play().catch(() => {});
        });
        instance.on(window.Hls.Events.AUDIO_TRACKS_UPDATED, () =>
          showTracks(instance, true),
        );
        instance.on(window.Hls.Events.SUBTITLE_TRACKS_UPDATED, () =>
          showTracks(instance),
        );
        instance.loadSource(result.path);
        instance.attachMedia(video);
      } else {
        video.src = result.path;
        video.play().catch(() => {});
      }
      timeout = setTimeout(() => failed(generation), 18000);
    } catch {
      failed(generation);
    }
  }
  video.addEventListener("loadedmetadata", () => {
    if (
      !media.live &&
      lastPosition > 0 &&
      Number.isFinite(video.duration) &&
      video.duration > lastPosition
    )
      video.currentTime = lastPosition;
  });
  video.addEventListener("playing", () => {
    ready = true;
    clearTimeout(timeout);
    label.textContent = `${media.label ? media.label + " · " : ""}${streams[index].provider}`;
  });
  video.addEventListener("error", () => {
    if (video.currentSrc) failed(serial);
  });
  video.addEventListener("timeupdate", () => {
    if (!video.currentTime) return;
    lastPosition = video.currentTime;
    if (Date.now() - updateTime > 10000) {
      updateTime = Date.now();
      record(media, video.currentTime, video.duration);
    }
  });
  video.addEventListener("ended", () => {
    ended = true;
    record(media, video.currentTime, video.duration, true);
  });
  function controls(event) {
    if (event.key === "Escape" && !document.fullscreenElement) {
      event.preventDefault();
      playerCleanup?.();
      return;
    }
    if (event.target !== video) return;
    if (
      ["ArrowLeft", "ArrowRight"].includes(event.key) &&
      !media.live &&
      Number.isFinite(video.duration)
    ) {
      event.preventDefault();
      video.currentTime = Math.max(
        0,
        Math.min(
          video.duration,
          video.currentTime + (event.key === "ArrowRight" ? 10 : -10),
        ),
      );
    }
    if (event.key.toLowerCase() === "f") fullscreen.click();
  }
  document.addEventListener("keydown", controls);
  main.hidden = true;
  main.after(shell);
  close.focus();
  playerCleanup = () => {
    record(media, lastPosition, video.duration, ended);
    serial += 1;
    reset();
    document.removeEventListener("keydown", controls);
    shell.remove();
    main.hidden = false;
    window.scrollTo(0, restore);
    focus?.focus({ preventScroll: true });
    playerCleanup = null;
  };
  load();
}

function decode(value) {
  try {
    return decodeURIComponent(value || "");
  } catch {
    return "";
  }
}

async function renderPage() {
  if (!catalogues) return;
  const route = currentRoute();
  if (lastRoute === route && version) return;
  playerCleanup?.();
  if (lastRoute) positionByRoute.set(lastRoute, window.scrollY);
  const [page, first, second, third] = route.split("/");
  const token = ++version;
  lastRoute = route;
  const tab = page === "studio" ? "studios" : page === "detail" ? "" : page;
  renderNav(tab);
  main.replaceChildren();
  main.className = "";
  main.setAttribute("aria-busy", "true");
  window.scrollTo(0, 0);
  if (token > 1) main.focus({ preventScroll: true });
  try {
    if (page === "detail") await detail(first, second, decode(third), token);
    else if (page === "studio") await studio(first, token);
    else if (page === "studios") studios();
    else if (page === "search") await search(token, decode(first));
    else if (page === "news") news();
    else if (page === "library") library();
    else if (page === "settings") renderSettings();
    else await home(token);
  } catch (error) {
    if (token === version)
      main.replaceChildren(
        status(error.message || "MoonCat could not open this page.", true),
        button("Go home", () => navigate("home")),
      );
  } finally {
    if (token === version) {
      main.setAttribute("aria-busy", "false");
      if (page !== "search" && positionByRoute.has(route))
        window.scrollTo(0, positionByRoute.get(route));
    }
  }
}

async function start() {
  try {
    const requests = [
      fetch("catalogues.json").then((response) => response.json()),
      android
        ? Promise.resolve().then(() =>
            JSON.parse(localStorage.getItem("mooncat-v3-client") || "{}"),
          )
        : MoonCat.rpc("state", {}),
    ];
    const [data, stored] = await Promise.allSettled(requests);
    if (data.status === "rejected") throw data.reason;
    catalogues = data.value;
    if (
      stored.status === "fulfilled" &&
      stored.value &&
      typeof stored.value === "object"
    ) {
      for (const key of Object.keys(settings))
        if (
          typeof stored.value[key] === typeof settings[key] &&
          stored.value[key] !== null
        )
          settings[key] = stored.value[key];
    }
    document
      .querySelector(".top-search")
      .addEventListener("click", () => navigate("search"));
    document.querySelector(".brand").addEventListener("click", (event) => {
      event.preventDefault();
      navigate("home");
    });
    document.querySelector(".skip").addEventListener("click", (event) => {
      event.preventDefault();
      main.focus();
    });
    window.addEventListener("hashchange", renderPage);
    window.addEventListener("keydown", (event) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        if (currentRoute().startsWith("search"))
          document.querySelector("#search-query")?.focus();
        else navigate("search");
      }
      if (!playerCleanup && event.altKey && event.key === "ArrowLeft") {
        event.preventDefault();
        back();
      }
    });
    if (!location.hash.startsWith("#/"))
      history.replaceState(null, "", "#/home");
    await renderPage();
  } catch {
    main.replaceChildren(
      status("MoonCat could not load its packaged catalogues.", true),
    );
    main.setAttribute("aria-busy", "false");
  }
}

start();
