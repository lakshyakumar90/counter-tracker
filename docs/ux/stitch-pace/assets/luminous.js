/* Stitch-specific screens and frame composition. All example readings are synthetic. */
const frame = document.body.dataset;
const footer = (...buttons) => `<div class="task-footer">${buttons.join("")}</div>`;
const check = (label, checked = false) => `<label class="check"><input type="checkbox" ${checked ? "checked" : ""}>${label}</label>`;
const localAction = (label, target, primary = false) => button(label, target, primary ? "primary" : "");
const back = () => action("Back","back");
const message = (glyph, heading, copy, ...choices) =>
  pad(illustration(glyph)+`<h3>${heading}</h3><small>${copy}</small>`)+footer(...choices);
const smallList = (items) => items.map(([glyph, label, target]) =>
  button(`<span class="chip">${icon(glyph)}</span><strong>${label}</strong>`,target,"row")).join("");
const heatmap = () => `<div class="heat" role="img" aria-label="12 active days over the last six weeks">${Array.from({length:42},(_,i)=>
  `<span ${[1,3,5,9,12,14,15,18,22,28,33,39].includes(i) ? 'data-on style="opacity:'+(.42+i%4*.18)+'"' : ""}></span>`).join("")}</div>`;
const intervalRows = () => [["Warm-up","6 min"],["Repeat × 4","16 min"],["Easy","4 min"],["Cool-down","6 min"]]
  .map(([label,value])=>`<div class="row"><strong>${label}</strong><small>${value}</small></div>`).join("");
const accountRows = [
  ["you","Profile","Profile"],["lock","Privacy","Privacy review"],["layers","Appearance and units","Units and theme"],
  ["signal","Notifications","Notifications"],["down","Storage","Storage"],["lock","Backup","Sync status"],["gps","Help","Help"]
];
const baseMap = map;
map = options => {
  const markup = baseMap(options).replace('stroke="var(--bg)" stroke-width="9"', 'stroke="var(--casing,var(--bg))" stroke-width="9"');
  return options?.thumbnail ? markup : markup.replace("</figure>",'<small class="attribution">Illustrative map</small></figure>');
};

function calendarMonth() {
  return pad(`<div class="subhead"><h3>November 2026</h3>${icon("plan")}</div>${calendar()}
    ${section("Thu 19 Nov",panel("Easy run",'<small>30 min · Running</small>'+actions(button("Start","Ready","primary"),button("Week view","Week agenda"))))}`)
    +footer(button("Add workout","Workout editor","primary"));
}

function liveSummary() {
  return `<div class="two-pane"><div>${map({detail:true})}</div>${pad(`
    <div class="metrics">${metric("7.20","km","Distance")}${metric("35:42","","Moving time")}</div>
    ${section("Splits · min/km",splitRows())}${chip("On this phone","lock")}`)} </div>`
    +footer(button("View activity","Activity detail","primary"));
}

Object.assign(screens,{
  "Launch":()=>message("record","Pace","Your private training log.",button("Continue","Welcome","primary")),
  "Theme":()=>pad(illustration("layers")+'<h3>Choose your theme</h3>'+
    '<label class="field">Theme<select id="local-theme"><option>Light</option><option>Dark</option><option>High contrast</option></select></label>')
    +footer(button("Continue","Auth hub","primary")),
  "Auth hub":()=>message("lock","Use Pace your way","Your activities can stay on this phone.",
    button("Use without an account","Today","primary"),button("Sign in","Sign in"),button("Create account","Register")),
  "Link data":()=>message("lock","Back up local activities","Choose whether to back up activities on this phone.",
    button("Review backup","Backup preview","primary"),button("Keep on this phone","Today")),
  "Today":()=>pad(`<section class="panel hero-card"><div>${metric("24.8","km","This week",true)}<small>15.2 km to your goal</small></div>
    ${ring(62,"62%","24.8 of 40 kilometres")}</section><div class="two-pane">
    <div>${section("Quick start",sportButtons(false))}</div><div>${section("Recent activity",activity()+button("History","History"))}</div></div>
    ${section("Next workout",panel("Easy run",'<small>Today · 30 min</small>'+button("Start","Ready","primary")))}`),
  "Week strip":()=>pad(metric("24.8","km","This week",true)+week()+section("Goal progress",ring(62,"62%","24.8 of 40 kilometres")))+footer(button("See insights","Insights","primary")),
  "Location":()=>message("gps","Record your route","Allow location while you record.",
    button("Allow","Ready","primary"),button("Not now","Today")),
  "Finish confirmation":()=>message("check","Finish this activity?","Your route and stats will be saved on this phone.",
    button("Confirm finish","Finish and save","primary"),button("Keep recording","Recording")),
  "Saved":()=>message("check","Activity saved","Your route and stats are on this phone.",
    button("View activity","Activity detail","primary"),button("Home","Today")),
  "Summary":liveSummary,
  "History filters":()=>pad(field("Search activities","search")+
    section("Sport",sportButtons(true))+section("Date range",field("From","date")+field("To","date"))
    +'<label class="field">Sort by<select><option>Newest</option><option>Distance</option><option>Duration</option></select></label>')
    +footer(button("Apply filters","History","primary"),button("Clear","History")),
  "Edit activity":()=>pad(field("Title","text","Riverside loop")+section("Sport",sportButtons(true))+field("Notes"))
    +footer(button("Save changes","Activity detail","primary"),back()),
  "Delete activity":()=>message("lock","Delete Riverside loop?","Removes the route and stats from this phone. This example has backup off.",
    button("Delete activity","History","danger"),button("Keep activity","Activity detail")),
  "Splits":()=>pad(metric("4:43","/km","Fastest split",true)+section("Splits · min/km",splitRows()))
    +footer(button("View route","Route detail","primary")),
  "Route detail":()=>`<div class="two-pane"><div>${map({detail:true})}</div>${pad(section("Pace over distance",plot("Pace",true)))}</div>`,
  "Saved route detail":()=>`${map({detail:true})}${pad(`<div class="metrics">${metric("7.20","km","Distance")}${metric("58","m","Elevation")}</div>${plot("Elevation",true)}`)}`
    +footer(button("Follow route","Follow route","primary"),button("Edit route","Route editor")),
  "Distance trend":()=>pad(metric("24.8","km","This week",true)+week()+section("Compare",chip("Week")+chip("Month")+chip("Year")))
    +footer(button("View activities","History","primary")),
  "Pace speed":()=>pad(metric("4:58","/km","Average pace",true)+plot("Pace",true)+smallList([["chart","Cycling speed","Insights"]])),
  "Elevation detail":()=>pad(metric("58","m","Total ascent",true)+plot("Elevation",true))+footer(button("View route","Route detail","primary")),
  "Heatmap":()=>pad(metric("12","","Active days",true)+section("Last six weeks",heatmap())+activity())
    +footer(button("View history","History","primary")),
  "Edit goal":()=>pad(ring(62,"62%","24.8 of 40 kilometres")+field("Weekly distance · km","number","40"))
    +footer(button("Save goal","Goals","primary"),back()),
  "Plan":calendarMonth,
  "Template detail":()=>pad(metric("32","min","Duration",true)+timeline()+section("Workout blocks",intervalRows()))
    +footer(button("Schedule","Schedule","primary"),button("Edit blocks","Workout editor")),
  "Schedule":()=>pad(chip("Hill repeats","plan")+field("Date","date","2026-11-19")+field("Time","time","07:00")+check("Remind me"))
    +footer(button("Schedule workout","Plan","primary"),back()),
  "Exercise detail":()=>pad(`<img class="exercise-art" src="../assets/exercise-squat.svg" alt="Bodyweight squat start and end positions">`+
    metric("12","","Repetitions",true)+section("Form","<small>Sit back, then stand tall.</small>"))
    +footer(button("Add to workout","Workout builder","primary")),
  "Workout ready":()=>pad(metric("12","min","Workout duration",true)+timeline()+section("First exercise",chip("Bodyweight squat","walk"))+check("Play interval cues"))
    +footer(button("Start workout","Workout player","primary")),
  "Settings":()=>pad(smallList(accountRows)),
  "Profile":()=>pad(illustration("you")+chip("On this phone","lock")+field("Name","text","Alex"))
    +footer(button("Save profile","Today","primary"),button("Settings","Settings")),
  "Privacy review":()=>pad(smallList([["gps","Location access","Location"],["lock","Private backup","Sync status"],
    ["chart","AI access","AI consent"],["down","Export data","Export data"],["lock","Delete account","Delete account"]])),
  "Notifications":()=>pad(illustration("signal")+check("Workout reminders",true)+check("Milestone alerts")+check("Interval cues",true))
    +footer(button("Done","Settings","primary")),
  "Export data":()=>message("down","Export your data","Create a file with your activities, routes and settings.",
    button("Create export","Privacy review","primary"),back()),
  "Delete account":()=>message("lock","Delete your account?","Removes the cloud account and backup. Choose whether to remove local data too.",
    button("Review deletion","Privacy review","danger"),button("Cancel","Settings")),
  "AI consent":()=>pad(illustration("lock")+chip("AI is off","lock")+
    section("Data shared if you turn it on","<small>Weekly distance, moving time, activity counts and pace summaries.</small>")+
    section("Stays on your phone","<small>GPS tracks and personal notes.</small>"))
    +footer(button("Turn on AI","Athlete Intelligence","primary"),button("Keep AI off","Settings")),
  "AI detail":()=>pad(illustration("chart")+section("A lighter day fits your week",
    `<div class="metrics">${metric("24.8","km","Weekly distance")}${metric("3","","Runs")}</div><small>Based on your last seven days.</small>`))
    +footer(button("Review suggested workout","AI workout","primary"),button("See local data","Insights")),
  "Help":()=>pad(smallList([["gps","GPS and sensors","Location"],["signal","Battery settings","Battery"],["lock","Crash reports","Crash reports"]])),
  "Battery":()=>message("signal","Allow background recording","If recordings stop with the screen off, check Android battery settings.",
    button("Open settings","Help","primary"),button("Not now","Ready")),
  "Crash reports":()=>message("lock","Share crash reports?","Reports help fix crashes. They exclude GPS routes and workout notes.",
    button("Allow reports","Settings","primary"),button("Not now","Settings")),
  "State sheet":()=>pad(`<label class="field">Surface<select id="state-surface">${options(Object.keys(screens).filter(name=>!["Components","State sheet"].includes(name)))}</select></label><div id="state-samples"></div>`)
});

function showStateSamples(surface) {
  const savedPage=ui.page, savedState=ui.state;
  ui.page=surface;
  const variants=["Loading","Empty","Offline","Error","Partial data","Permission denied"].map(state=>{
    ui.state=state;
    const sample=statePreview() || (state==="Offline" ?
      chip("Offline ready","signal")+"<small>Saved data is available on this phone.</small>" :
      chip("Some data unavailable","signal","warn")+"<small>Keep using your saved data.</small>");
    return panel(state,sample);
  });
  ui.page=savedPage;
  ui.state=savedState;
  $("state-samples").innerHTML=variants.join("");
}

const layouts = ["Today","Maps","Plan","Insights"];
const recordingPages = ["Sport picker","Ready","Recording","Full metrics","Full map","Paused","GPS lost"];
const baseRender = render;
render = function(focus=false) {
  baseRender(focus);
  $("frame-actions")?.remove();
  const onTop = layouts.includes(ui.page);
  const live = ["Recording","Full metrics","Full map","Paused","GPS lost"].includes(ui.page);
  const title = ui.page===frame.view && frame.label ? frame.label : labels[ui.page] || ui.page;
  if (onTop) {
    $("toolbar").innerHTML=`<h2 class="wordmark">Pace</h2><span class="chip top-status">${ui.state==="Offline" ? "Offline ready" : "Backup off"}</span>
      <button class="avatar" data-go="Profile" aria-label="Open profile">A</button>`;
    if (ui.state==="Default" && ui.page==="Maps") {
      $("screen").innerHTML=`<div class="two-pane"><div>${map({full:true,region:true})}</div>${pad(section("Offline maps",
        smallList([["down","Download a region","Choose region"],["map","Downloaded maps","Regions"],["plan","Saved routes","Saved routes"]])))}</div>`;
    }
  } else {
    $("toolbar").innerHTML=`${live ? "" : iconAction("Back","arrow","back")}<h2>${title}</h2>${
      live && ui.page!=="Paused" ? '<span class="chip"><span class="recdot"></span>24:18</span>' :
      ui.page==="Activity detail" ? iconAction("Activity actions","more","overflow") : ""}`;
  }
  if (ui.page==="Components") {
    $("screen").innerHTML=pad(Object.entries(componentSamples).map(([name,sample])=>
      panel(name,sample()+`<div class="spec-variants" aria-label="Interaction states">
        <button disabled>Disabled</button><span class="chip pressed">Pressed</span><span class="chip warn">Unavailable</span>
        <span class="skeleton" aria-label="Loading"></span>
      </div>`)).join(""));
  }
  if (ui.page==="State sheet") {
    $("state-surface").value="Activity detail";
    showStateSamples("Activity detail");
  }
  $("navigation").querySelector('[data-go="You"]')?.setAttribute("data-go","Insights");
  const last=$("navigation").lastElementChild;
  if (last) last.innerHTML=icon("chart")+"Insights";
  if (!onTop && !recordingPages.includes(ui.page) && ui.page!=="History" &&
      !(frame.size==="Tablet" && ui.page==="Activity detail")) $("navigation").innerHTML="";
  const taskActions=$("screen").querySelector(".task-footer") ||
    (recordingPages.includes(ui.page) ? $("screen").querySelector(".record-controls .actions") : null);
  if (taskActions) {
    taskActions.id="frame-actions";
    $("screen").after(taskActions);
  }
};
$("screen").addEventListener("change",event=>{
  if (event.target.id==="state-surface") showStateSamples(event.target.value);
});
let hold;
$("device").addEventListener("pointerdown",event=>{
  if (event.target.closest('#frame-actions [data-action="hold-finish"]')) {
    hold=setTimeout(()=>go("Finish confirmation"),650);
  }
});
for (const type of ["pointerup","pointercancel"]) $("device").addEventListener(type,()=>clearTimeout(hold));
$("device").addEventListener("click",event=>{
  if (event.detail && event.target.closest('#frame-actions [data-action="hold-finish"]')) {
    event.stopPropagation();
  }
});
$("screens").innerHTML=options(Object.keys(screens));
Object.keys(screens).forEach(name=>dataScreens.add(name));
$("device").dataset.theme=frame.theme || "Light";
$("device").dataset.size=frame.size || "Compact";
ui.page=frame.view || "Today";
ui.sport=frame.sport || "Running";
ui.state=frame.state || "Default";
ui.history=ui.page==="Today" ? [] : ["Today"];
render();
