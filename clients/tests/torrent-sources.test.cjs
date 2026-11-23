const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.join(__dirname, "../web/core.js"), "utf8");
const hash = "abcdef0123456789abcdef0123456789abcdef01";
const torrentio = {
  provider: "Torrentio",
  entry: {
    name: "Torrentio 1080p",
    title: "Public domain sample\n👤 18 💾 1.2 GB",
    infoHash: hash.toUpperCase(),
    fileIdx: 0,
    behaviorHints: { filename: "episode-one.mkv" },
    sources: ["tracker:udp://tracker.example:6969/announce", "dht:" + hash],
  },
};

function resolve(groups, mobile) {
  const context = vm.createContext({ window: mobile ? { MoonCatAndroid: {} } : {}, URL, groups });
  vm.runInContext(source + '\nglobalThis.result = MoonCat.orderedSources(groups, "hi")', context);
  return JSON.parse(JSON.stringify(context.result));
}

const separateFile = { ...torrentio, entry: { ...torrentio.entry, fileIdx: 3 } };
const sourceList = resolve([[torrentio, separateFile, torrentio]], true);
assert.equal(sourceList.length, 2);
assert.deepEqual(sourceList[0].torrent, {
  infoHash: hash,
  fileIndex: 0,
  filename: "episode-one.mkv",
  trackers: ["udp://tracker.example:6969/announce"],
});
assert.equal(sourceList[1].torrent.fileIndex, 3);
assert.equal(resolve([[torrentio]], false).length, 0);

const invalid = (infoHash, name = torrentio.entry.name, fileIdx = 0) =>
  ({ ...torrentio, entry: { ...torrentio.entry, infoHash, name, fileIdx } });
assert.equal(resolve([[invalid("junk"), invalid(hash, "2160p"), invalid(hash, "1080p", -1)]], true).length, 0);
assert.equal(resolve([[{ ...torrentio, entry: { ...torrentio.entry, title: "7 GB" } }]], true).length, 0);

const direct = { provider: "Direct", entry: { url: "https://example.org/video.mp4", name: "Hindi 1080p" } };
assert.equal(resolve([[torrentio, direct]], true)[0].url, direct.entry.url);
assert.equal(resolve([[direct]], false).length, 1);
console.log("Torrent routes preserve file indices, remove duplicates, and leave Windows on HTTPS.");
