let token = sessionStorage.getItem("awp-private-review-token") || "";
let queue = "cases";
let selected = null;
const byId = (id) => document.getElementById(id);
const feedback = (text) => { byId("feedback").textContent = text; };

async function api(path, body) {
  const response = await fetch(path, { method: body ? "POST" : "GET", cache: "no-store",
    headers: { Authorization: `Bearer ${token}`, ...(body ? { "Content-Type": "application/json" } : {}) },
    ...(body ? { body: JSON.stringify(body) } : {}) });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || "request refused");
  return result;
}

function clearRow() {
  selected = null;
  byId("rowTitle").textContent = "Select a row";
  byId("rowStatus").textContent = "";
  byId("source").textContent = "";
  byId("caseForm").hidden = true;
  byId("metadataForm").hidden = true;
}

async function refresh() {
  const state = await api("/api/summary");
  byId("identity").textContent = `${state.identity} (${state.role})`;
  const q = state.summary.quality, m = state.summary.migration;
  byId("progress").textContent = `Quality: ${q.pending}/${q.total} pending, ${q.eligible} eligible, gate ${q.gate}. Metadata: ${m.quarantined}/${m.total} quarantined, gate ${m.gate}. Live reads unchanged.`;
  const rows = (await api(`/api/${queue}`)).rows;
  byId("queueTitle").textContent = queue === "cases" ? "Quality cases" : "Unlinked metadata";
  const container = byId("rows");
  container.replaceChildren();
  for (const row of rows) {
    const button = document.createElement("button");
    button.className = `row${selected && selected.id === (row["case-id"] || row["legacy-id"]) ? " selected" : ""}`;
    const id = row["case-id"] || row["legacy-id"];
    button.textContent = queue === "cases" ? `${id.slice(0, 10)} · ${row.branch} · ${row["review-status"]} · privacy ${row["privacy-status"]}`
      : `${id.slice(0, 10)} · ${row.disposition}`;
    button.addEventListener("click", () => showRow(id));
    container.append(button);
  }
  if (selected) await showRow(selected.id);
}

async function showRow(id) {
  selected = await api(`/api/${queue}/${encodeURIComponent(id)}`);
  const state = selected.state;
  byId("rowTitle").textContent = `${queue === "cases" ? "Case" : "Metadata"} ${id}`;
  byId("rowStatus").textContent = `Revision ${state.revision} · ${queue === "cases" ? `${state["review-status"]}, privacy ${state["privacy-status"]}, ${state.split}` : state.disposition}`;
  byId("source").textContent = JSON.stringify(selected.source, null, 2);
  byId("caseForm").hidden = queue !== "cases";
  byId("metadataForm").hidden = queue !== "metadata";
  if (queue === "cases") {
    byId("familyId").value = state["family-id"] || selected.draft?.familyId || "";
    byId("split").value = state.split === "unassigned" ? selected.draft?.split || "development" : state.split;
    byId("expected").value = JSON.stringify(selected.draft?.expected ?? [], null, 2);
    byId("note").value = selected.draft?.note || "";
  } else {
    byId("reason").value = "";
    byId("evidence").value = "";
    byId("sourceId").value = "";
  }
  feedback("");
}

async function submit(body) {
  if (!selected) { feedback("Select a row first."); return; }
  try {
    await api("/api/action", { id: selected.id, revision: selected.state.revision,
      actionId: crypto.randomUUID(), ...body });
    feedback("Decision recorded. The queue will refresh.");
    await refresh();
  } catch (error) { feedback(error.message); }
}

function expected() {
  const value = JSON.parse(byId("expected").value);
  if (!Array.isArray(value)) throw new Error("Expected items must be a JSON array.");
  return value;
}

byId("unlock").addEventListener("click", async () => {
  token = byId("token").value.trim();
  try {
    await refresh();
    sessionStorage.setItem("awp-private-review-token", token);
    byId("login").hidden = true;
    byId("workbench").hidden = false;
    byId("token").value = "";
  } catch { feedback("Access refused."); }
});
byId("lock").addEventListener("click", () => { sessionStorage.removeItem("awp-private-review-token"); token = ""; clearRow(); byId("workbench").hidden = true; byId("login").hidden = false; });
byId("refresh").addEventListener("click", () => refresh().catch(() => feedback("Refresh unavailable.")));
for (const [id, target] of [["casesTab", "cases"], ["metadataTab", "metadata"]]) {
  byId(id).addEventListener("click", () => { queue = target; clearRow(); refresh().catch(() => feedback("Queue unavailable.")); });
}
byId("saveFamily").addEventListener("click", () => submit({kind:"family", familyId:byId("familyId").value.trim(), split:byId("split").value}));
byId("saveDraft").addEventListener("click", () => { try { submit({kind:"draft", draft:{expected:expected(),note:byId("note").value,familyId:byId("familyId").value.trim(),split:byId("split").value}}); } catch (error) { feedback(error.message); } });
byId("saveReview").addEventListener("click", () => { try { submit({kind:"review",expected:expected(),note:byId("note").value,decision:byId("reviewDecision").value,reviewerRole:byId("reviewDecision").value === "adjudicated" ? "adjudicator" : "independent"}); } catch (error) { feedback(error.message); } });
byId("privacyPass").addEventListener("click", () => submit({kind:"privacy",decision:"passed"}));
byId("privacyFail").addEventListener("click", () => submit({kind:"privacy",decision:"failed"}));
byId("saveDisposition").addEventListener("click", () => submit({kind:"disposition",disposition:byId("disposition").value,reason:byId("reason").value,evidence:byId("evidence").value,sourceId:byId("sourceId").value.trim()}));
if (token) { refresh().then(() => { byId("login").hidden = true; byId("workbench").hidden = false; }).catch(() => { token = ""; sessionStorage.removeItem("awp-private-review-token"); }); }
