import { mkdir, readFile, readdir, writeFile, copyFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { pathToFileURL, fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const prototype = join(root, "..", "pace-prototype");
const assets = join(root, "assets");
const escape = value => value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll('"', "&quot;");
const rows = (await readFile(join(root, "screens.tsv"), "utf8")).trim().split(/\r?\n/);
const screens = rows.map(row => {
  const [id, slug, title, view, sport = "Running"] = row.split("\t");
  return { id, slug, title, view, sport };
});

const frames = screens.map(screen => ({ ...screen, theme: "Light", size: "Compact" }));
for (const screen of screens) {
  if (["011", "019", "025", "028"].includes(screen.id)) frames.push({ ...screen, theme: "Dark", size: "Compact" });
  if (["011", "028", "033"].includes(screen.id)) frames.push({ ...screen, theme: "Light", size: "Tablet" });
  if (["015", "019", "020", "021"].includes(screen.id)) frames.push({ ...screen, theme: "Light", size: "Landscape" });
}
frames.push(
  { id: "", slug: "component_sheet", title: "Component sheet", view: "Components", sport: "Running", theme: "Light", size: "Compact" },
  { id: "", slug: "state_sheet", title: "State sheet", view: "State sheet", sport: "Running", theme: "Light", size: "Compact" },
  { id: "", slug: "state_gps_lost", title: "GPS lost", view: "GPS lost", sport: "Running", theme: "Light", size: "Compact" },
  ...["Loading","Empty","Offline","Error","Partial data","Permission denied"].map(state => ({
    id:"", slug:"state_"+state.toLowerCase().replaceAll(" ","_"), title:"Activity detail", view:"Activity detail", sport:"Running",
    theme:"Light", size:"Compact", state
  })),
);

for (const name of ["pace.css", "pace.js"]) await copyFile(join(prototype, name), join(assets, name));

function filename(frame) {
  const branch = frame.size === "Compact" ? frame.theme.toLowerCase() : frame.size.toLowerCase();
  return frame.id ? `ui_${frame.id}_${frame.slug}_${branch}` : frame.slug;
}

function html(frame) {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${escape(frame.title)} · Pace</title>
  <link rel="stylesheet" href="../assets/pace.css">
  <link rel="stylesheet" href="../assets/luminous.css">
</head>
<body class="stage-view" data-view="${escape(frame.view)}" data-label="${escape(frame.title)}" data-theme="${frame.theme}" data-size="${frame.size}" data-sport="${frame.sport}" data-state="${frame.state || "Default"}">
  <main class="studio">
    <div hidden>
      <select id="screens" aria-label="Screen"></select><select id="flows" aria-label="Flow"></select>
      <select id="themes" aria-label="Theme"><option>Light</option><option>Dark</option><option>High contrast</option></select>
      <select id="devices" aria-label="Device"><option>Compact</option><option>Tablet</option><option>Landscape</option></select>
      <select id="states" aria-label="Data state"></select>
    </div>
    <section id="device" class="device" aria-label="Pace design prototype">
      <header id="toolbar"></header>
      <article id="screen" tabindex="-1"></article>
      <nav id="navigation" aria-label="Pace sections"></nav>
    </section>
  </main>
  <script src="../assets/pace.js"></script>
  <script src="../assets/luminous.js"></script>
</body>
</html>
`;
}

await Promise.all(frames.map(async frame => {
  frame.directory=join(root,filename(frame));
  await mkdir(frame.directory,{recursive:true});
  await writeFile(join(frame.directory,"code.html"),html(frame));
}));

const link = frame => `<li><a href="${filename(frame)}/code.html">${frame.id ? frame.id+" · " : ""}${escape(frame.title)}${frame.theme==="Dark" ? " · Dark" : frame.size==="Compact" ? "" : " · "+frame.size}${frame.state ? " · "+frame.state : ""}</a> <a href="${filename(frame)}/screen.png">PNG</a></li>`;
await writeFile(join(root,"index.html"),`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Pace · Screen library</title>
<style>@font-face{font-family:Outfit;src:url(assets/Outfit.ttf)}body{font:17px Outfit,sans-serif;background:#f8f7fc;color:#1e1135;margin:32px auto;padding:0 20px;max-width:860px}a{color:#630ed4}li{padding:5px 0}li a:last-child{margin-left:8px;font-size:.75em}ul{columns:2;gap:32px}@media(max-width:600px){ul{columns:1}}</style>
<h1>Pace screen library</h1><p>81 Android design concepts. Example routes and readings are illustrative.</p>
<p><a href="luminous_lavender_pace/DESIGN.md">Design system and Compose handoff</a></p>
<h2>Light handset</h2><ul>${frames.slice(0,81).map(link).join("")}</ul>
<h2>Theme, device and state variants</h2><ul>${frames.slice(81).map(link).join("")}</ul></html>`);

if (process.env.PACE_PLAYWRIGHT) {
  const { chromium } = await import(pathToFileURL(process.env.PACE_PLAYWRIGHT));
  const browser = await chromium.launch({ executablePath: process.env.PACE_CHROME, headless:true });
  const errors = [];
  const run = async () => {
    const page = await browser.newPage({ reducedMotion:"reduce",deviceScaleFactor:1 });
    page.on("pageerror",error=>errors.push(error.message));
    for (;;) {
      const frame = frames.shift();
      if (!frame) break;
      if (process.env.PACE_FRAME && !filename(frame).includes(process.env.PACE_FRAME)) continue;
      const [width,height] = frame.size==="Tablet" ? [800,1000] : frame.size==="Landscape" ? [800,420] : [390,844];
      await page.setViewportSize({width,height});
      await page.goto(pathToFileURL(join(frame.directory,"code.html")).href);
      await page.evaluate(()=>document.fonts.ready);
      const result = await page.locator("#screen").evaluate(element => ({
        content:element.textContent.trim(),
        overflow:element.scrollWidth>element.clientWidth+2
      }));
      if (!result.content || result.overflow) errors.push(`${filename(frame)}: ${result.content ? "horizontal overflow" : "empty"}`);
      await page.screenshot({path:join(frame.directory,"screen.png"),fullPage:["Components","State sheet"].includes(frame.view)});
    }
    await page.close();
  };
  try {
    await Promise.all(Array.from({length:5},run));
    const page=await browser.newPage({viewport:{width:390,height:844}});
    const open = directory => page.goto(pathToFileURL(join(root,directory,"code.html")).href);
    await open("ui_028_activity_detail_light");
    await page.locator("input[data-scrub]").first().fill("4");
    if (await page.locator("#route-dot").getAttribute("cx") !== "175") errors.push("Activity detail chart cursor");
    await open("ui_021_paused_light");
    const hold=page.getByRole("button",{name:"Hold to finish",exact:true});
    await hold.click({delay:700});
    if (!(await page.getByRole("heading",{name:"Finish this activity?"}).isVisible())) errors.push("Hold to finish");
    await open("state_sheet");
    await page.selectOption("#state-surface","History");
    if (!(await page.getByRole("heading",{name:"No activities yet"}).isVisible())) errors.push("History state sheet");
    await page.close();
  }
  finally { await browser.close(); }
  if (errors.length) throw new Error(errors.join("\n"));
  const captures = await readdir(root,{withFileTypes:true});
  console.log(`Checked and captured ${captures.filter(entry=>entry.isDirectory() && /^(ui_|state_|component_sheet)/.test(entry.name)).length} frames.`);
}
