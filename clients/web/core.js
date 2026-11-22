"use strict";

const MoonCat = (() => {
  const cinemeta = "https://v3-cinemeta.strem.io";
  const calls = new Map();
  const pending = new Map();
  let callId = 0;
  const safeHeader =
    /^(accept|authorization|origin|referer|user-agent|x-[a-z0-9-]+)$/i;

  window.__mooncatResult = (id, payload, error) => {
    const callback = pending.get(id);
    if (!callback) return;
    pending.delete(id);
    clearTimeout(callback.timer);
    if (error) callback.reject(new Error(error));
    else callback.resolve(payload);
  };

  function nativeRequest(url) {
    return new Promise((resolve, reject) => {
      const id = ++callId;
      const timer = setTimeout(() => {
        pending.delete(id);
        reject(new Error("The provider timed out."));
      }, 18000);
      pending.set(id, { resolve, reject, timer });
      window.MoonCatAndroid.request(id, url);
    });
  }

  function safeUrl(value) {
    if (typeof value !== "string" || value.length > 8192) return "";
    try {
      const url = new URL(value);
      if (url.protocol !== "https:" || url.username || url.password) return "";
      return url.href;
    } catch {
      return "";
    }
  }

  function poster(id, asset = "poster") {
    return /^tt\d+$/.test(id)
      ? `https://images.metahub.space/${asset}/medium/${id}/img`
      : "";
  }

  async function rpc(path, data) {
    const response = await fetch(`api/${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
      cache: "no-store",
    });
    if (!response.ok)
      throw new Error((await response.text()).trim() || "The request failed.");
    return response.json();
  }

  function providerJson(url, ttl = 120000) {
    const key = safeUrl(url);
    if (!key) return Promise.reject(new Error("Use a valid HTTPS URL."));
    const cached = calls.get(key);
    if (cached && cached.until > Date.now()) return cached.promise;
    const promise = (
      window.MoonCatAndroid ? nativeRequest(key) : rpc("json", { url: key })
    ).catch((error) => {
      calls.delete(key);
      throw error;
    });
    if (calls.size >= 120) calls.delete(calls.keys().next().value);
    calls.set(key, { until: Date.now() + ttl, promise });
    return promise;
  }

  function title(item) {
    const id = item.id || item.imdb || item.imdb_id || "";
    const type = item.type === "series" ? "series" : "movie";
    return {
      id,
      type,
      name: item.name || item.title || "Untitled",
      poster: safeUrl(item.poster) || poster(id),
      background: safeUrl(item.background) || poster(id, "background"),
      year:
        item.releaseInfo || (/^\d{4}$/.test(item.year || "") ? item.year : ""),
      description: item.description || "",
      genres: Array.isArray(item.genres) ? item.genres.slice(0, 4) : [],
      videos: Array.isArray(item.videos) ? item.videos : [],
    };
  }

  function titles(payload) {
    return (Array.isArray(payload.metas) ? payload.metas : [])
      .filter(
        (item) =>
          item && /^tt\d+$/.test(item.id || item.imdb || item.imdb_id || ""),
      )
      .map(title);
  }

  function pathFor(base, resource, type, id) {
    return `${base}/${resource}/${encodeURIComponent(type)}/${encodeURIComponent(id)}.json`;
  }

  function orderedSources(groups, preferred) {
    const unique = new Set();
    return groups
      .flat()
      .filter(Boolean)
      .flatMap(({ entry, provider }) => {
        const url = safeUrl(entry.url);
        const external = safeUrl(entry.externalUrl);
        const labels = [
          entry.name,
          entry.title,
          entry.description,
          entry.behaviorHints?.filename,
        ]
          .filter(Boolean)
          .join(" ");
        const size = /(\d+(?:\.\d+)?)\s*(GiB|GB|MiB|MB)\b/i.exec(labels);
        const sizeUnits = { gib: 2 ** 30, gb: 1e9, mib: 2 ** 20, mb: 1e6 };
        const bytes =
          Number(entry.behaviorHints?.videoSize) ||
          (size ? Number(size[1]) * sizeUnits[size[2].toLowerCase()] : 0);
        if (
          /\b(?:2160p|2880p|4320p|[48][ ._-]?k|uhd)\b/i.test(labels) ||
          bytes > 6e9
        )
          return [];
        if (!url && !external) return [];
        const key = url || external;
        const headers = Object.fromEntries(
          Object.entries(
            entry.behaviorHints?.proxyHeaders?.request || {},
          ).filter(
            ([name, value]) =>
              safeHeader.test(name) &&
              typeof value === "string" &&
              value.length < 2048 &&
              !/[\r\n]/.test(value),
          ),
        );
        const signature = key + JSON.stringify(Object.entries(headers).sort());
        if (unique.has(signature)) return [];
        unique.add(signature);
        const hindi = /\b(?:hindi|hin)\b|हिंदी|हिन्दी/i.test(labels);
        const english = /\b(?:english|eng)\b/i.test(labels);
        const language = (preferred === "hi" ? hindi : english)
          ? 0
          : hindi || english
            ? 1
            : 2;
        const resolution = /\b1080p\b/i.test(labels)
          ? 0
          : /\b720p\b/i.test(labels)
            ? 1
            : 2;
        const seeds =
          Number(
            /(?:👤|👥|🌱|\bseeders?\s*[:=]?\s*)\s*([\d,]+)/i
              .exec(labels)?.[1]
              ?.replace(/,/g, ""),
          ) || 0;
        return [
          {
            url,
            external,
            provider,
            headers,
            name:
              entry.title || entry.name || entry.description || "Direct stream",
            quality:
              resolution === 0 ? "1080p" : resolution === 1 ? "720p" : "",
            language,
            resolution,
            seeds,
            hls: /\.m3u8(?:$|[?#])/i.test(url),
            mime: "",
          },
        ];
      })
      .sort(
        (a, b) =>
          Number(!a.url) - Number(!b.url) ||
          a.language - b.language ||
          a.resolution - b.resolution ||
          b.seeds - a.seeds,
      );
  }

  return {
    cinemeta,
    orderedSources,
    pathFor,
    poster,
    providerJson,
    rpc,
    safeUrl,
    title,
    titles,
  };
})();
