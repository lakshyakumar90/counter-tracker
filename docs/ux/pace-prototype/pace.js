const $ = id => document.getElementById(id);
const sports = { Running: "run", Cycling: "cycle", Jogging: "jog", Walking: "walk" };
const glyphs = {
  home:'M3 11 12 3l9 8v9H3z M9 20v-6h6v6', map:'M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3z M9 3v15m6-12v15',
  record:'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z',
  plan:'M4 6h16v15H4z M4 10h16M8 3v6m8-6v6m-8 5h2m4 0h2m-8 3h2',
  you:'M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8z M4 21a8 7 0 0 1 16 0',
  run:'M14 4a1.5 1.5 0 1 0 3 0 1.5 1.5 0 1 0-3 0z M5 11l4-3 4 1 3 4h4m-7-4-3 6-5 5m5-5 5 1 2 5',
  jog:'M13 4a1.5 1.5 0 1 0 3 0 1.5 1.5 0 1 0-3 0z M7 12l3-4 4 1 2 4h3m-5-4-3 6-4 5m4-5 5 2 1 4',
  walk:'M12 4a1.5 1.5 0 1 0 3 0 1.5 1.5 0 1 0-3 0z M7 14l3-4 3-2 3 5 3 1m-6-6-1 8-4 5m4-5 4 5',
  cycle:'M6 12a4 4 0 1 0 0 8 4 4 0 0 0 0-8zm12 0a4 4 0 1 0 0 8 4 4 0 0 0 0-8z M6 16l4-6 4 6h-8m4-6h6l2 6m-3-9h3',
  gps:'M12 3v4m0 10v4M3 12h4m10 0h4M12 6a6 6 0 1 0 0 12 6 6 0 0 0 0-12z',
  arrow:'M20 12H4m7-7-7 7 7 7', more:'M5 12h.1m6.9 0h.1m6.9 0h.1',
  lock:'M6 10h12v11H6zm2 0V7a4 4 0 0 1 8 0v3m-4 4v3',
  signal:'M4 17h2m2-4h2m2-4h2m2-4h2', down:'M12 3v12m-5-5 5 5 5-5M5 20h14',
  chart:'M3 4v16h18M6 15l4-5 4 2 6-7', layers:'m3 9 9-5 9 5-9 5z M3 13l9 5 9-5m-18 4 9 5 9-5',
  compass:'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zm4 5-2 6-6 2 2-6z',
  search:'M10 4a6 6 0 1 0 0 12 6 6 0 0 0 0-12zm5 11 5 5', pause:'M9 5v14m6-14v14',
  play:'m8 5 11 7-11 7z', check:'m4 12 5 5L20 6', medal:'M7 3h10l-3 7h-4z M12 10a5 5 0 1 0 0 10 5 5 0 1 0 0-10z'
};
const icon = name => `<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${glyphs[name]}"/></svg>`;
const button = (label, go, type = "") => `<button class="${type}" data-go="${go}">${label}</button>`;
const action = (label, key, type = "") => `<button class="${type}" data-action="${key}">${label}</button>`;
const iconAction = (label, name, key) => `<button class="icon-button" aria-label="${label}" data-action="${key}">${icon(name)}</button>`;
const chip = (label, name = "", type = "") => `<span class="chip ${type}">${name ? icon(name) : ""}${label}</span>`;
const metric = (value, unit, label, hero = false) => `<div class="metric ${hero ? "hero" : ""}"><small>${label}</small><b>${value}${unit ? `<em>${unit}</em>` : ""}</b></div>`;
const panel = (title, content) => `<section class="panel"><h3>${title}</h3>${content}</section>`;
const section = (title, content) => `<section class="section"><h3>${title}</h3>${content}</section>`;
const pad = content => `<div class="pad">${content}</div>`;
const actions = (...controls) => `<div class="actions">${controls.join("")}</div>`;
const field = (label, type = "text", value = "") => `<label class="field">${label}<input type="${type}" value="${value}"></label>`;
const illustration = name => `<div class="illustration">${icon(name)}</div>`;
const sportStyle = name => `--route:var(--${sports[name]})`;
const route = [[72,212],[108,184],[132,205],[172,171],[175,120],[222,93],[249,109],[287,64],[327,76],[350,44]];
const routeLine = route.map(point => point.join(",")).join(" ");
const activeStates = ["GPS acquiring","GPS weak","Ready","Recording","Paused","GPS lost","Full map","Full metrics"];
const stateNames = ["Default","Loading","Empty","Offline","Error","Permission denied","Partial data","Long list"];
const nav = [["Today","Home","home"],["Maps","Maps","map"],["Sport picker","Record","record"],["Plan","Plan","plan"],["You","You","you"]];
const flows = {
  "First run":"Welcome", "Record → save → detail":"Sport picker", "Download a map":"Maps",
  "Create and start a plan":"Plan", "Restore after reinstall":"Restore"
};
const ui = {page:"Activity detail", state:"Default", sport:"Running", day:19, range:"Week", mapMode:false, scrub:0, blocks:["Warm-up","Repeat × 4","Interval","Cool-down"], progress:42, saved:false, plan:false, backup:false, sheet:"", history:[]};

function map({thumbnail=false, full=false, recording=false, limited=false, gap=false, detail=false, region=false, offRoute=false, preflight=false, gps="GPS good"} = {}) {
  const point = route[ui.scrub] ?? route[0];
  const roads = Array.from({length:12}, (_,i) => `<path d="M${i*36-95} 300 ${i*36+55} -30 M-30 ${i*30-85} Q200 ${i*30-148} 440 ${i*30-30}"/>`).join("");
  const routeDrawing = gap
    ? `<polyline points="${route.slice(0,6).join(" ")}" fill="none" stroke="var(--route)" stroke-width="6"/><polyline points="${route.slice(5).join(" ")}" fill="none" stroke="var(--warn)" stroke-width="3" stroke-dasharray="5 6"/>`
    : `<polyline points="${routeLine}" fill="none" stroke="var(--bg)" stroke-width="9"/><polyline points="${routeLine}" fill="none" stroke="var(--route)" stroke-width="6"/>`;
  const art = `<svg class="map" ${detail && !thumbnail ? "data-route-scrub" : ""} style="${sportStyle(ui.sport)}" viewBox="0 0 410 265" preserveAspectRatio="${thumbnail ? "xMidYMid meet" : "xMidYMid slice"}" role="img" aria-label="${region ? "Illustrative map region" : gap ? "Illustrative route with a dashed GPS gap" : "Illustrative Riverside route"}">
    <rect width="410" height="265" fill="var(--land)"/>
    ${thumbnail || limited ? "" : `<path fill="var(--park)" d="M0 0h145l33 54-29 40-58 17L0 72z M230 139l62-24 55 30 63 18v102H279z M280 0h130v44l-80-14z"/><path d="M190-25c-51 77 73 83 35 150s-82 76-44 151" fill="none" stroke="var(--water)" stroke-width="43"/><g fill="none" stroke="var(--road)" opacity=".7" stroke-width="1.1">${roads}</g><path d="M-20 200 108 150l67-11 104-23 153-43M30 0l41 104 33 58 8 103" fill="none" stroke="var(--road)" stroke-width="3"/><g font-size="9" fill="var(--muted)"><text x="42" y="58">Riverside Park</text><text x="268" y="195">Canal Road</text><text x="240" y="126" transform="rotate(-62 240 126)">Canal</text></g>`}
    ${limited ? `<path d="${Array.from({length:16},(_,i)=>`M${i*33-235} 265l265-265`).join(" ")}" stroke="var(--road)" opacity=".42"/>` : ""}
    <g stroke-linejoin="round" stroke-linecap="round">${region ? `<rect x="73" y="48" width="255" height="161" rx="8" fill="var(--accent)" fill-opacity=".15" stroke="var(--accent)" stroke-width="2" stroke-dasharray="8 5"/>` : preflight ? "" : routeDrawing}</g>
    ${detail ? `<circle cx="72" cy="212" r="7" fill="var(--success)" stroke="var(--bg)" stroke-width="3"/><rect x="344" y="38" width="12" height="12" rx="2" fill="var(--ink)" stroke="var(--bg)" stroke-width="2"/><circle cx="249" cy="109" r="10" fill="var(--accent)" stroke="var(--bg)" stroke-width="2"/><path d="m246 105 6 0-3 6z" fill="var(--on-accent)"/><g fill="var(--ink)" font-size="9"><text x="122" y="222">1 km</text><text x="179" y="138">3 km</text><text x="289" y="62">6 km</text></g><circle id="route-dot" cx="${point[0]}" cy="${point[1]}" r="5" fill="var(--ink)" stroke="var(--bg)" stroke-width="2"/>` : ""}
    ${recording ? `<circle cx="287" cy="64" r="25" fill="var(--route)" opacity=".21"/><path d="m287 64 10-27 14 14Z" fill="var(--route)" opacity=".55"/><circle cx="287" cy="64" r="6" fill="var(--route)" stroke="white" stroke-width="2"/>` : ""}
  </svg>`;
  if (thumbnail) return `<span class="thumb">${art}</span>`;
  return `<figure class="mapwrap" ${full ? 'style="height:clamp(360px,65vh,610px)"' : ""}>${art}
    ${limited ? chip("Map not downloaded","map") : offRoute ? chip("Off route · 80 m","compass","warn") : gap ? chip("GPS lost","signal","warn") : recording ? chip(gps,"gps",gps==="GPS good" ? "good" : "warn") : ""}
    <div class="map-tools">${region ? iconAction("Layers","layers","layers") + iconAction("Compass","compass","compass") : ""}${iconAction("Recenter map","gps","recenter")}</div></figure>`;
}

function plot(kind, extended = false) {
  const elevation = kind.includes("Elevation");
  const forecast = kind.includes("Forecast");
  const points = elevation ? [74,62,65,49,33,42,25,34,47,64] : [66,52,60,35,41,49,29,41,22,33];
  const xs = points.map((y,i) => [18+i*32,y]);
  const trace = values => values.map((point,i) => `${i ? "L" : "M"}${point.join(" ")}`).join(" ");
  const path = trace(xs);
  const band = trace(xs.map(([x,y])=>[x,y-10]))+" "+trace(xs.map(([x,y])=>[x,y+10]).reverse()).replace("M","L")+"Z";
  const unit = elevation ? "m" : forecast ? "min" : "min/km";
  const ticks = elevation ? ["60","30","0"] : forecast ? ["35","38","41"] : ["4:30","5:00","5:30"];
  return `<div class="plot">
    <svg viewBox="0 0 338 121" class="chart" role="img" aria-label="${kind}. ${elevation ? "Highest point 52 metres" : forecast ? "Illustrative range 35 to 39 minutes" : "Faster pace appears higher"}">
      <g stroke="var(--rule)" stroke-width="1"><path d="M18 25h288M18 52h288M18 79h288"/></g>
      ${elevation || forecast ? `<path d="${forecast ? band : path+" L306 83H18Z"}" fill="var(--accent)" opacity=".19"/>` : ""}
      <path d="${path}" fill="none" stroke="var(--accent)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
      <line class="scrub-line" x1="${xs[ui.scrub][0]}" x2="${xs[ui.scrub][0]}" y1="16" y2="84" stroke="var(--ink)" stroke-dasharray="3 4"/>
      <g fill="var(--muted)" font-size="9"><text x="309" y="28">${ticks[0]}</text><text x="309" y="55">${ticks[1]}</text><text x="309" y="82">${ticks[2]}</text><text x="18" y="105">0</text><text x="145" y="105">3.6 km</text><text x="275" y="105">7.2 km</text><text x="299" y="13">${unit}</text></g>
    </svg>
    <label><span class="offscreen">Scrub ${kind} by distance</span><input type="range" min="0" max="9" value="${ui.scrub}" data-scrub="${kind}"></label>
    <output class="scrub-value">${(ui.scrub*.8).toFixed(1)} km</output>
    ${extended ? "" : button("Full chart","Chart view")}</div>`;
}

function week() {
  const values = [4,0,6.4,3.8,0,7.2,3.4];
  return `<div class="week" role="img" aria-label="24.8 kilometres this week. Monday 4, Wednesday 6.4, Thursday 3.8, Saturday 7.2, Sunday 3.4."><!-- Seven days -->${values.map((value,i)=>`<div><i ${value === 0 ? "data-zero" : ""} style="height:${Math.max(4,value*13)}px"></i>${["M","T","W","T","F","S","S"][i]}</div>`).join("")}</div>`;
}
function ring(percent, value, label) {
  return `<div class="ring" role="img" aria-label="${label}"><svg viewBox="0 0 120 120" aria-hidden="true"><circle cx="60" cy="60" r="50" fill="none" stroke="var(--raised)" stroke-width="9"/><circle cx="60" cy="60" r="50" fill="none" stroke="var(--accent)" stroke-width="9" pathLength="100" stroke-dasharray="${percent} 100" stroke-linecap="round" transform="rotate(-90 60 60)"/></svg><strong>${value}</strong></div>`;
}
const sportButtons = grid => `<div class="${grid ? "sport-grid" : "quick"}">${Object.entries(sports).map(([name,key])=>`<button style="${sportStyle(name)}" data-sport="${name}" ${grid ? `aria-pressed="${ui.sport===name}"` : 'data-quick="true"'}>${icon(key)}<strong>${name}</strong></button>`).join("")}</div>`;
const activity = (title = ui.savedTitle || "Riverside loop", distance = "7.20 km", time = "35:42", pace = "4:58/km") => `<button class="row" data-go="Activity detail">${map({thumbnail:true})}<span><strong>${title}</strong><br><small>${distance} · ${time} · ${pace}</small></span></button>`;
const splitRows = () => `<div aria-label="Splits">${[72,83,79,100,85,73,81].map((value,i)=>`<div class="split"><span>${i+1}</span><i style="width:${value}%;opacity:${i===3 ? 1 : ".55"}"></i><span>${["5:11","4:56","5:02","4:43","4:53","5:10","4:59"][i]}${i===3 ? '<span class="offscreen"> fastest</span>' : ""}</span></div>`).join("")}</div>`;
const details = () => `<div class="record-layout">${map({full:ui.mapMode,detail:true,limited:ui.state==="Offline"})}
  ${pad(`<div class="subhead"><div><h3>${ui.savedTitle || "Riverside loop"}</h3><small>${icon(sports[ui.sport])} ${ui.sport} · 19 Nov · 06:42</small></div>${action(ui.mapMode ? "Show stats" : "Expand map","map-mode")}</div>
    ${ui.mapMode ? "" : `<div class="metrics six">${metric("7.20","km","Distance")}${metric("35:42","","Moving time")}${metric("4:58","/km","Avg pace")}${metric("58","m","Elevation")}${metric("4:43","/km","Best split")}${metric("—","","Heart rate")}</div>
    ${section("Pace",plot("Pace"))}${section("Elevation",plot("Elevation"))}${section("Splits · min/km",splitRows())}
    ${chip(ui.state==="Partial data" ? "Part of route unavailable" : "GPS good","gps",ui.state==="Partial data" ? "warn" : "good")}${section("Notes","<small>No notes</small>")}`}`)}
  </div>`;
function record(phase) {
  const ready = ["Ready","GPS acquiring","GPS weak"].includes(phase), paused = phase === "Paused", lost = phase === "GPS lost";
  const gps = phase==="GPS acquiring" ? phase : phase==="GPS weak" || (ready && ui.state==="Partial data") ? "GPS weak" : "GPS good";
  const metrics = `<div class="record-controls">${metric(ready ? "0:00" : "24:18","","Elapsed time",true)}
    ${ready ? "" : `<div class="metrics">${metric("4.86","km","Distance")}${ui.sport==="Cycling" ? metric("12.0","km/h","Speed") : metric("5:00","/km","Pace")}</div>${chip("Last split · 4:56","chart")}`}
    ${ready ? actions(gps==="GPS good" ? button("Start","Recording","primary") : button("Check GPS","Ready","primary"),action("Options","record-options")) :
      paused ? actions(button("Resume","Recording","primary rec-pause"),action("Hold to finish","hold-finish")) :
      actions(button(icon("pause")+" Pause","Paused","primary rec-pause"),button(phase==="Full metrics" ? "Map" : phase==="Full map" ? "Metrics" : "Expand map",phase==="Full metrics" ? "Recording" : phase==="Full map" ? "Full metrics" : "Full map"))}
  </div>`;
  return `<div class="record-layout ${paused ? "is-paused" : ""}">${phase==="Full metrics" ? "" : map({full:phase==="Full map",recording:true,gap:lost,limited:ui.state==="Offline",preflight:ready,gps})}
    <div>${paused ? pad(chip("Paused","pause")) : ""}${lost ? pad(chip("GPS lost · gaps won’t count toward distance","signal","warn")) : ""}
    ${ui.state==="Partial data" && ready ? pad(chip("GPS weak · waiting for a better fix","gps","warn")) : ""}${metrics}</div></div>`;
}

function calendar() {
  return `<div class="calendar">${["M","T","W","T","F","S","S"].map(day=>`<small>${day}</small>`).join("")}${Array.from({length:36},(_,i)=>i<6 ? "<span></span>" : `<button data-day="${i-5}" aria-label="November ${i-5}${[8,12,19,24].includes(i-5) ? ", planned workout" : ""}" aria-pressed="${ui.day===i-5}" ${[8,12,19,24].includes(i-5) ? "data-event" : ""} style="--route:var(--${i%2 ? "run" : "cycle"})">${i-5}</button>`).join("")}</div>`;
}
const timeline = () => `<div class="blocks" role="img" aria-label="Warm-up, four repeat intervals, cool-down">${[20,40,18,40,18,40,18,40,20].map((h,i)=>`<span style="--h:${h}px;opacity:${i%2 ? 1 : .55}"></span>`).join("")}</div>`;
const stack = () => `<div class="stack" role="img" aria-label="Running 60 percent, cycling 23 percent, jogging 11 percent and walking 6 percent">${Object.values(sports).map((key,i)=>`<span style="--route:var(--${key});--w:${[60,23,11,6][i]}"></span>`).join("")}</div>`;
const inputs = (label, next, password = true) => field("Email","email") + (password ? field("Password","password") : "") + button(label,next,"primary");
const componentSamples = {
  "Metric tile":()=>metric("7.20","km","Distance"),
  "Hero metric":()=>metric("24:18","","Elapsed time",true),
  "Stat delta":()=>`<div class="row"><strong>Avg pace</strong><span>4:58 /km</span>${chip("↓ 0:12 faster","chart","good")}</div><small>↑ Slower · — Steady · Unavailable</small>`,
  "Sport chip and tile":()=>chip(ui.sport,sports[ui.sport])+sportButtons(true),
  "Segmented control":()=>`<div class="compact-actions">${action("Map","map-mode","primary")}${action("Stats","map-mode")}</div>`,
  "Bottom navigation":()=>`<div class="actions">${nav.map(([target,label,glyph])=>button(icon(glyph)+label,target,"navitem")).join("")}</div>`,
  "Bottom sheet":()=>action("Preview recording options","record-options"),
  "Map controls":()=>map({region:true}),
  "Progress ring":()=>ring(62,"62%","24.8 of 40 kilometres"),
  "Goal card":()=>panel("Weekly distance",ring(62,"62%","24.8 of 40 kilometres")),
  "Personal record card":()=>panel("5 km personal best",chip("24:10","medal")),
  "Activity card":()=>activity(),
  "Split row":()=>splitRows(),
  "Chart with scrub":()=>plot("Pace"),
  "Calendar heatmap":()=>`<div class="heat" role="img" aria-label="Two active days out of four"><span data-on></span><span></span><span data-on></span><span></span></div>`,
  "Workout interval":()=>chip("Repeat × 4 · 2 min")+timeline(),
  "Empty state":()=>illustration("chart")+"<small>Your progress will appear here.</small>"+button("Start an activity","Sport picker","primary"),
  "Skeleton":()=>'<div class="skeleton"></div><div class="skeleton"></div>',
  "Permission card":()=>panel("Location access",chip("Permission required","gps")+action("Open settings","map-settings")),
  "Offline chip":()=>chip("Offline","signal"),
  "GPS indicator":()=>chip("GPS acquiring","gps")+chip("GPS good","gps","good")+chip("GPS weak","gps","warn"),
  "Undo snackbar":()=>action("Show snackbar","sample-undo")
};

function components() {
  return pad(`<label class="field">Component<select id="component">${options(Object.keys(componentSamples))}</select></label>
    <label class="field">Component state<select id="component-state">${options(["Default","Pressed","Disabled","Loading","Error"])}</select></label>
    <div id="specimen"></div>`);
}

function refreshSpecimen() {
  if (!$("specimen")) return;
  $("component").value=ui.component || "Hero metric";
  $("component-state").value=ui.componentState || "Default";
  const state=$("component-state").value;
  const name=$("component").value;
  let content=componentSamples[name]();
  if (state==="Loading") content='<div class="skeleton"></div><div class="skeleton"></div>';
  if (state==="Error") content=chip("Unavailable","signal","warn")+action("Retry","sample-retry");
  $("specimen").innerHTML=`<div class="panel" data-spec-state="${state}" ${state==="Disabled" ? 'aria-disabled="true" inert' : ""}>${content}</div>`;
  if (state==="Disabled") $("specimen").insertAdjacentHTML("beforeend",'<small>Disabled specimen</small>');
}
const screens = {
  "Today": () => pad(`<div class="subhead">${metric("24.8","km","This week",true)}${chip(ui.state==="Offline" ? "Offline" : "On this phone","lock")}</div>${week()}
    ${section("Quick start",sportButtons(false))}
    ${section("Next workout",panel("Easy run",`<small>Today · 30 min</small>${button("Start","Ready","primary")}`))}
    ${section("Last activity",activity())}
    ${section("Weekly goal",ring(62,"62%","24.8 of 40 kilometres") + '<small>24.8 / 40 km</small>')}
    ${section("Pace trend",plot("Pace"))}`),
  "Sport picker": () => pad(sportButtons(true)+button("Continue","Ready","primary")),
  ...Object.fromEntries(activeStates.map(phase=>[phase,()=>record(phase)])),
  "Finish and save": () => pad(`${map()}<div class="metrics">${metric("7.20","km","Distance")}${metric("35:42","","Elapsed time")}</div>
    ${field("Activity title","text","Riverside loop")}<label class="field">Sport<select id="save-sport">${Object.keys(sports).map(name=>`<option ${name===ui.sport ? "selected" : ""}>${name}</option>`).join("")}</select></label>
    ${actions(action("Save activity","save","primary"),action("Discard","discard","danger"))}`),
  "Recovery": () => pad(panel("Unfinished activity",`${activity("Recovered route","4.86 km","24:18","5:00/km")}
    ${actions(button("Resume","Recording","primary"),button("Review & save","Finish and save"),action("Discard","discard","danger"))}`)),
  "Activity detail": details,
  "Chart view": () => pad(plot(ui.chart || "Pace",true)),
  "History": () => pad(`<div class="compact-actions">${action("All sports","filter-sports")}${action("Date range","dates")}${iconAction("Search history","search","search")}</div>
    ${ui.sheet==="Search" ? field("Search activities") : ""}<h3>November 2026</h3>
    ${activity()}${activity("Canal tempo","6.40 km","32:07","5:01/km")}${activity("Park cooldown","3.80 km","22:20","5:53/km")}
    <h3>October 2026</h3>${activity("Bridge loop","5.15 km","27:01","5:15/km")}`),
  "Maps": () => `${map({full:true,region:true,limited:ui.state==="Offline"})}${pad(actions(button("Downloaded regions","Regions","primary"),button("Saved routes","Saved routes"),button("Search areas","Choose region")))}`,
  "Regions": () => pad(panel("Downloaded regions",ui.regionDeleted ? "<small>No downloaded regions</small>" : `<div class="row">${map({thumbnail:true,region:true})}<strong>Riverside</strong><small>42 MB</small>${action("Delete","delete-region","danger")}</div>`) + button("Download a map","Choose region","primary")),
  "Choose region": () => `${map({region:true,full:true})}${pad(field("Area","search","Riverside") + metric("42","MB","Estimated download") + '<progress value="42" max="100" aria-label="42 MB estimated size"></progress>' + actions(action("Adjust coverage","coverage"),button("Download","Map download","primary")))}`,
  "Map download": () => pad(panel(ui.progress===100 ? "Map ready" : "Downloading Riverside",metric(String(ui.progress),"%","Download",true)+`<progress value="${ui.progress}" max="100" aria-label="Map download progress"></progress>`+
    (ui.progress===100 ? button("Open maps","Maps","primary") : actions(action(ui.downloadPaused ? "Resume" : "Pause","download-pause"),action("Cancel","cancel-download"),action("Simulate completion","download-done","primary"))))),
  "Saved routes": () => pad(`<div class="row">${map({thumbnail:true})}<strong>Riverside loop</strong><small>7.2 km · 58 m</small></div>${actions(button("Follow","Follow route","primary"),button("Edit","Route editor"),button("Import GPX","GPX preview"))}`),
  "Route editor": () => `${map({detail:true})}${pad(`<div class="compact-actions">${action("Move waypoint up","waypoint")}${action("Move waypoint down","waypoint")}</div>${plot("Elevation",true)}${button("Save route","Saved routes","primary")}`)}`,
  "GPX preview": () => `${map({detail:true})}${pad(`<div class="metrics">${metric("7.20","km","Distance")}${metric("58","m","Elevation")}</div>${button("Save route","Saved routes","primary")}`)}`,
  "Follow route": () => `${map({full:true,detail:true,recording:true,offRoute:true,limited:ui.state==="Offline"})}${pad(button("Start recording","Recording","primary"))}`,
  "You": () => pad(panel("On this phone",chip("Local only","lock")) + ["Insights","History","Personal records","Goals","Settings"].map(name=>button(name,name)).join("")),
  "Insights": () => pad(`<div class="compact-actions">${["Week","Month","Year"].map(name=>`<button data-range="${name}" aria-pressed="${ui.range===name}" class="${ui.range===name ? "primary" : ""}">${name}</button>`).join("")}${action(ui.sport,"filter-sports")}</div>
    ${metric({Week:"24.8",Month:"86.2",Year:"632"}[ui.range],"km",ui.range+" distance",true)}${week()}
    ${section("Pace trend",plot("Pace"))}${section("Frequency",`<div class="heat" role="img" aria-label="8 active days in the last 28 days">${Array.from({length:28},(_,i)=>`<span ${[0,3,7,9,14,16,21,26].includes(i) ? "data-on" : ""}></span>`).join("")}</div>`)}
    ${section("Sport breakdown",stack() + '<small>Run 60% · Cycle 23% · Jog 11% · Walk 6%</small>')}
    ${actions(button("Personal records","Personal records"),button("Goals","Goals"))}`),
  "Personal records": () => pad(illustration("medal") + metric("24:10","","Fastest 5 km",true) + activity()),
  "Goals": () => pad(ring(62,"62%","24.8 of 40 kilometres") + metric("24.8","/ 40 km","Weekly distance") + action("Edit target","target")),
  "Plan": () => pad(`<h3>November 2026</h3>${calendar()}${section(`Thu ${ui.day} Nov`,panel(ui.plan ? "Interval run" : "Easy run",`<small>${ui.plan ? "32" : "30"} min</small>${actions(button("Start","Ready","primary"),button("Edit","Workout editor"))}`))}
    ${actions(button("Week agenda","Week agenda"),button("Create workout","Workout editor"),button("Templates","Templates"))}`),
  "Week agenda": () => pad(["Mon 16","Thu 19","Sat 21"].map((day,i)=>section(day,i===1 ? button("Easy run · 30 min","Workout editor") : "<small>Rest day</small>")).join("")),
  "Workout editor": () => pad(field("Workout name","text","Interval run") + metric("32","min","Total duration",true)+timeline()+ui.blocks.map((item,i)=>`<div class="row"><strong>${item}</strong><button data-move="${i}" ${i===0 ? "disabled" : ""} aria-label="Move ${item} up">Up</button></div>`).join("")+field("Workout date","date","2026-11-19")+action("Schedule workout","schedule","primary")),
  "Templates": () => pad(["Easy run","Hill repeats","Tempo ride"].map((title,i)=>panel(title,timeline()+button(`Use · ${[30,32,45][i]} min`,"Workout editor"))).join("")),
  "Exercise library": () => pad(["Bodyweight squat","Wall push-up","Mobility"].map(title=>panel(title,illustration("walk")+button("Add exercise","Workout builder"))).join("")),
  "Workout builder": () => pad(metric("12","min","Duration",true)+timeline()+ui.blocks.map((item,i)=>`<div class="row"><strong>${item}</strong><button data-move="${i}" ${i===0 ? "disabled" : ""}>Move up</button></div>`).join("")+actions(button("Add exercise","Exercise library"),button("Start workout","Workout player","primary"))),
  "Workout player": () => pad(`<h3>${ui.exercise || "Bodyweight squat"}</h3>${ring(70,ui.playerPaused ? "0:28" : "0:24","24 seconds remaining")}<small>Next · Wall push-up</small>${actions(action(ui.playerPaused ? "Resume" : "Pause","player-pause","primary"),action("Skip","player-skip"))}${button("Finish workout","Workout complete")}`),
  "Workout complete": () => pad(illustration("check")+metric("12:00","","Time",true)+metric("3","","Sets")+button("Done","Today","primary")),
  "Forecast": () => pad(metric("35–39","min","Estimated 7 km",true)+plot("Forecast",true)+"<small>Illustrative estimate · conditions may change it.</small>"),
  "Workout picker": () => pad(sportButtons(true)+field("Minutes","number","30")+ `<label class="field">Goal<select><option>Easy day</option><option>Build endurance</option></select></label>`+button("See workouts","Recommendations","primary")),
  "Recommendations": () => pad(panel("Easy run",timeline()+metric("30","min","Duration")+chip("Easy day after hard week")+button("Review workout","Workout editor","primary"))),
  "Welcome": () => pad(illustration("record")+"<h3>Train on your terms</h3><small>Record and review your activities offline.</small>"+button("Use without an account","Choose sports","primary")+actions(button("Sign in","Sign in"),button("Restore backup","Restore"),button("Skip","Today"))),
  "Choose sports": () => pad(sportButtons(true)+actions(button("Continue","Units and theme","primary"),button("Skip","Today"))),
  "Units and theme": () => pad(illustration("you")+`<label class="field">Units<select><option>Kilometres</option><option>Miles</option></select></label><label class="field">Theme<select id="local-theme"><option>Dark</option><option>Light</option><option>High contrast</option></select></label>`+actions(button("Done","Today","primary"),button("Skip","Today"))),
  "Sign in": () => pad(illustration("lock")+inputs("Sign in",ui.restoring ? "Backup preview" : "Account")+actions(button("Register","Register"),button("Reset password","Reset password"),button("Use locally","Today"))),
  "Register": () => pad(illustration("lock")+inputs("Create account","Verify email")),
  "Verify email": () => pad(illustration("check")+field("Verification code","text")+button("Verify","Account","primary")),
  "Reset password": () => pad(illustration("lock")+inputs("Send reset link","Reset sent",false)),
  "Reset sent": () => pad(illustration("check")+"<h3>Check your email</h3>"+button("Back to sign in","Sign in","primary")),
  "Account": () => pad(panel("Account",chip(ui.backup ? "Signed in" : "Local only","lock"))+actions(button("Sign in","Sign in"),button("Backup & sync","Sync status"),button("Restore","Restore"))),
  "Restore": () => pad(illustration("lock")+"<h3>Restore a private backup</h3><small>Sign in to see available backups.</small>"+action("Sign in to continue","restore-sign-in","primary")+button("Use locally","Today")),
  "Backup preview": () => pad(illustration("down")+metric("18","","Activities",true)+"<small>Backup · 18 Nov 2026 · 42 MB</small>"+action("Merge backup","merge","primary")),
  "Restore complete": () => pad(illustration("check")+metric("18","","Activities restored",true)+button("See history","History","primary")),
  "Settings": () => pad(["Privacy","Sync status","Storage","Account","Units and theme","Exercise library","Workout picker","Forecast","AI"].map(name=>button(name,name)).join("")),
  "Privacy": () => pad(illustration("lock")+section("Stays on your phone","<small>Activities and precise routes</small>")+section("Backed up to your account",'<label><input type="checkbox"> Enable private backup</label>')+section("Sent for AI only if you opt in",'<label><input type="checkbox"> Allow AI access</label>')),
  "Sync status": () => pad(metric("2","","Queued activities",true)+chip(ui.backup ? "Signed in" : "Offline","signal")+"<small>Last sync · 18 Nov, 20:42</small>"+action("Sync now","sync","primary")),
  "Storage": () => pad(metric("64","MB","On this phone",true)+stack()+"<small>Maps 42 MB · Activities 16 MB · Cache 6 MB</small>"+button("Manage maps","Regions")),
  "AI": () => pad(illustration("lock")+"<h3>AI is off</h3><small>Workout data stays private.</small>"+button("Open privacy","Privacy")),
  "Athlete Intelligence": () => pad(panel("A lighter day fits your week",`<div class="metrics">${metric("24.8","km","This week")}${metric("3","","Runs")}${metric("1","","Rest day")}</div>${button("See data","Insights")}`)+button("Review workout","AI workout")),
  "AI workout": () => pad(panel("Easy run · 30 min",timeline()+actions(button("Edit","Workout editor"),action("Schedule","schedule","primary"),button("Reject","AI")))),
  "Components":components
};

const sheetOptions = {
  "record-options":["Recording options","Target","Route","Auto-pause"],
  "filter-sports":["Filter by sport",...Object.keys(sports)],
  dates:["Date range","This week","This month","This year"],
  discard:["Discard this activity?","Keep activity","Discard activity"],
  coverage:["Download coverage","Smaller area","Larger area"],
  "delete-region":["Delete Riverside?","Keep map","Delete map"],
  "cancel-download":["Cancel download?","Keep downloading","Cancel download"],
  layers:["Map layers","Standard","Trail emphasis"],
  target:["Weekly distance","Change target"],
  overflow:["Activity actions","Edit activity","Export GPX","Delete activity"],
  "hold-finish":["Finish recording?","Keep recording","Confirm finish"],
  "map-settings":["Location access","Back to recording"]
};
const labels = {"Sport picker":"Choose a sport",Ready:"Record",Recording:"Recording","Full map":"Recording","Full metrics":"Recording","Activity detail":"Activity","Chart view":"Chart"};
const setup = ["Welcome","Choose sports","Units and theme","Sign in","Register","Verify email","Reset password","Reset sent","Restore","Backup preview","Restore complete"];
const dataScreens = new Set(["Today","Activity detail","History","Maps","Regions","Saved routes","Insights","Personal records","Goals","Plan","Week agenda","Exercise library","Recommendations","Forecast","Sync status","Storage","Athlete Intelligence"]);
const hiddenNavigation = [...activeStates,...setup,"Finish and save","Recovery","Workout player"];
const options = values => values.map(value=>`<option>${value}</option>`).join("");

function statePreview() {
  const {page,state}=ui;
  if (state==="Permission denied") return pad(panel("Location access",illustration("gps")+"<small>Allow location to record a route.</small>"+action("Open settings","map-settings","primary")));
  if (!dataScreens.has(page)) return "";
  if (state==="Loading") return pad('<div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div><div class="offscreen" role="status">Loading</div>');
  if (state==="Empty") return pad(illustration(page==="Maps" ? "map" : "chart")+ `<h3>No ${page==="History" || page==="Today" ? "activities" : "data"} yet</h3><small>Your progress will appear here.</small>`+button("Start an activity","Sport picker","primary"));
  if (state==="Error") return pad(panel("Couldn’t open "+(labels[page] || page).toLowerCase(),"<small>Your saved data is still on this phone.</small>"+action("Retry","retry","primary")));
  if (state==="Partial data" && !["Activity detail","Maps"].includes(page)) return pad('<h3>More activity needed</h3><div style="opacity:.55">'+plot("Pace",true)+'</div><small>Record again to see a trend.</small>');
  if (state==="Long list" && page==="History") return pad('<h3>Saved activities</h3>'+ Array.from({length:24},(_,i)=>activity(["Riverside loop","Canal tempo","Park cooldown"][i%3])).join("")+action("Load older activities","more-history"));
  return "";
}

function render(focus=false) {
  const page=ui.page;
  const active = nav.find(([target])=>target===page)?.[0] || (activeStates.includes(page) ? "Sport picker" : ["History","Insights","Personal records","Goals","Settings","Privacy","Sync status","Storage","Account"].includes(page) ? "You" : "");
  $("toolbar").innerHTML=(ui.history.length ? iconAction("Back","arrow","back") : "")+`<h2>${labels[page] || page}</h2>`+(page==="Activity detail" ? iconAction("Activity actions","more","overflow") : ui.state==="Offline" ? chip("Offline","signal") : "");
  $("screen").innerHTML=statePreview() || screens[page]();
  refreshSpecimen();
  $("navigation").innerHTML=hiddenNavigation.includes(page) ? "" : nav.map(([target,label,glyph])=>`<button class="navitem" data-go="${target}" ${active===target ? 'aria-current="page"' : ""}>${icon(glyph)}${label}</button>`).join("");
  $("screens").value=page;
  $("states").value=ui.state;
  if (focus) { $("screen").scrollTop=0; $("screen").focus({preventScroll:true}); }
}

function go(page, resetState=false) {
  if (!screens[page]) return;
  $("notice")?.remove();
  if (ui.page!==page) ui.history.push(ui.page);
  ui.page=page;
  ui.sheet="";
  if (resetState) ui.state="Default";
  render(true);
}

function notice(message, undo=false) {
  const current=$("notice");
  if (current) current.remove();
  $("device").insertAdjacentHTML("beforeend",`<div id="notice" class="notice"><span role="status">${message}</span>${undo ? action("Undo","undo") : ""}${action("Dismiss","dismiss")}</div>`);
  const next=$("notice");
  if (!undo) setTimeout(()=>next.remove(),3500);
}

function openSheet(key) {
  $("sheet")?.remove();
  const [title,...choices]=sheetOptions[key];
  $("device").insertAdjacentHTML("beforeend",`<dialog id="sheet" aria-labelledby="sheet-title"><h3 id="sheet-title">${title}</h3><div class="sheet-options">${choices.map((choice,i)=>`<button data-sheet="${key}" data-choice="${i}">${choice}</button>`).join("")}<button data-action="close-sheet">Close</button></div></dialog>`);
  $("sheet").showModal();
}

function chooseSheet(key,index) {
  $("sheet").close();
  if (key==="hold-finish" && index===1) return go("Finish and save");
  if (key==="discard" && index===1) { go("Today"); notice("Activity discarded",true); return; }
  if (key==="delete-region" && index===1) { ui.regionDeleted=true; render(); notice("Map deleted",true); return; }
  if (key==="cancel-download" && index===1) { ui.progress=0; return go("Regions"); }
  if (key==="filter-sports") { ui.sport=Object.keys(sports)[index]; render(); return; }
  if (key==="map-settings") { ui.state="Default"; return go("Ready"); }
  if (key==="overflow" && index===0) return go("Finish and save");
  if (key==="overflow" && index===2) return openSheet("discard");
  notice(sheetOptions[key][index+1]);
}

const handlers = {
  back:()=>{ui.page=ui.history.pop() || "Today";ui.sheet="";render(true);},
  save:()=>{
    ui.sport=$("save-sport").value;
    const title=document.createElement("span");
    title.textContent=$("screen").querySelector("input").value.trim();
    ui.savedTitle=title.innerHTML;
    go("Activity detail");notice("Activity saved");
  },
  retry:()=>{ui.state="Default";render(true);},
  "map-mode":()=>{ui.mapMode=!ui.mapMode;render();},
  "download-pause":()=>{ui.downloadPaused=!ui.downloadPaused;render();},
  "download-done":()=>{ui.progress=100;ui.downloadPaused=false;render();},
  schedule:()=>{ui.plan=true;go("Plan");notice("Workout scheduled");},
  "player-pause":()=>{ui.playerPaused=!ui.playerPaused;render();},
  "player-skip":()=>{ui.exercise="Wall push-up";render();},
  "restore-sign-in":()=>{ui.restoring=true;go("Sign in");},
  merge:()=>{ui.backup=true;ui.restoring=false;go("Restore complete");},
  sync:()=>notice(ui.backup ? "Sync complete" : "Sign in to sync"),
  "more-history":()=>notice("You’re up to date"),
  "sample-undo":()=>notice("Activity removed",true),
  "sample-retry":()=>{ui.componentState="Default";refreshSpecimen();},
  dismiss:()=>$("notice")?.remove(),
  undo:()=>{$("notice")?.remove();ui.regionDeleted=false;render();},
  search:()=>{ui.sheet=ui.sheet==="Search" ? "" : "Search";render();$("screen").querySelector("input")?.focus();},
  recenter:()=>{ui.scrub=0;render();notice("Map centered");},
  compass:()=>notice("North up"),
  waypoint:()=>notice("Waypoint selected")
};

$("screens").innerHTML=options(Object.keys(screens));
$("states").innerHTML=options(stateNames);
$("flows").innerHTML=options(["Choose a flow",...Object.keys(flows)]);
$("screens").onchange=event=>go(event.target.value,true);
$("flows").onchange=event=>{ui.restoring=false;ui.progress=42;if(flows[event.target.value])go(flows[event.target.value],true);};
$("states").onchange=event=>{ui.state=event.target.value;render(true);};
$("themes").onchange=event=>{$("device").dataset.theme=event.target.value;};
$("devices").onchange=event=>{$("device").dataset.size=event.target.value;};

$("screen").addEventListener("change",event=>{
  if (event.target.id==="component") ui.component=event.target.value;
  else if (event.target.id==="component-state") ui.componentState=event.target.value;
  else return;
  refreshSpecimen();
});

document.addEventListener("click",event=>{
  const control=event.target.closest("button");
  if (!control) {
    const map=event.target.closest("[data-route-scrub]");
    if (!map) return;
    const position=new DOMPoint(event.clientX,event.clientY).matrixTransform(map.getScreenCTM().inverse());
    const point=route.reduce((best,[x,y],index)=> {
      const distance=(x-position.x)**2+(y-position.y)**2;
      return distance<best.distance ? {distance,index} : best;
    },{distance:Infinity,index:0});
    updateScrub(point.index);
    return;
  }
  const {go:target,action:key,sheet,choice,sport,quick,range,day,move}=control.dataset;
  if (sheet) return chooseSheet(sheet,Number(choice));
  if (sport) {ui.sport=sport;return quick ? go("Ready") : render();}
  if (range) {ui.range=range;return render();}
  if (day) {ui.day=Number(day);return render();}
  if (move) {
    const index=Number(move);
    [ui.blocks[index-1],ui.blocks[index]]=[ui.blocks[index],ui.blocks[index-1]];
    return render();
  }
  if (target) {
    if (target==="Chart view") ui.chart=control.closest(".plot").querySelector("input").dataset.scrub;
    if (target==="Map download") ui.progress=42;
    return go(target);
  }
  if (key==="close-sheet") return $("sheet").close();
  if (sheetOptions[key]) return openSheet(key);
  handlers[key]?.();
});

let finishTimer;
$("screen").addEventListener("pointerdown",event=>{
  if (!event.target.closest('[data-action="hold-finish"]')) return;
  finishTimer=setTimeout(()=>{go("Finish and save");},650);
});
for (const type of ["pointerup","pointercancel","pointerleave"]) $("screen").addEventListener(type,()=>clearTimeout(finishTimer));

function updateScrub(index) {
  ui.scrub=index;
  const [x,y]=route[ui.scrub];
  $("route-dot")?.setAttribute("cx",x);
  $("route-dot")?.setAttribute("cy",y);
  for (const plot of $("screen").querySelectorAll(".plot")) {
    const line=plot.querySelector(".scrub-line");
    line.setAttribute("x1",18+ui.scrub*32);
    line.setAttribute("x2",18+ui.scrub*32);
    plot.querySelector("input").value=ui.scrub;
    plot.querySelector("output").value=`${(ui.scrub*.8).toFixed(1)} km`;
  }
}

$("screen").addEventListener("input",event=>{
  if (event.target.id==="local-theme") {
    $("device").dataset.theme=event.target.value;
    $("themes").value=event.target.value;
  }
  if (event.target.dataset.scrub) updateScrub(Number(event.target.value));
});

$("device").dataset.theme="Dark";
$("device").dataset.size="Compact";
const preview = new URLSearchParams(location.search).get("stage");
if (preview && Object.hasOwn(screens,preview)) {
  document.body.classList.add("stage-view");
  ui.page=preview;
}
render();
