"use strict";

const byId = (id) => document.getElementById(id);
const all = (selector, scope = document) => [...scope.querySelectorAll(selector)];
const on = (target, eventName, handler) => {
  if (target) target.addEventListener(eventName, handler);
};

const state = {
  activeRole: null,
  userName: "",
  userEmail: "",
  requestNumber: 25,
  workOrderNumber: 18,
  personnelNumber: 3,
  totalRequests: 24,
  pendingRequests: 8
};

const roleConfiguration = {
  requester: {
    label: "Requester",
    defaultView: "requester-overview",
    navigation: [
      { view: "requester-overview", icon: "▦", label: "Overview" },
      { view: "requester-new", icon: "+", label: "New Request" },
      { view: "requester-records", icon: "▤", label: "My Requests" }
    ]
  },
  administrator: {
    label: "Service Administrator",
    defaultView: "administrator-overview",
    navigation: [
      { view: "administrator-overview", icon: "▦", label: "Overview" },
      { view: "administrator-review", icon: "✓", label: "Review Queue" },
      { view: "administrator-work-orders", icon: "⚒", label: "Work Orders" },
      { view: "administrator-access", icon: "◆", label: "User Access" }
    ]
  },
  approver: {
    label: "Department Head / Authorized Approver",
    defaultView: "approver-overview",
    navigation: [
      { view: "approver-overview", icon: "▦", label: "Overview" },
      { view: "approver-queue", icon: "✓", label: "Approval Queue" }
    ]
  },
  technician: {
    label: "Service Personnel / Technician",
    defaultView: "technician-overview",
    navigation: [
      { view: "technician-overview", icon: "▦", label: "Overview" },
      { view: "technician-assigned", icon: "⚒", label: "Assigned Work" },
      { view: "technician-update", icon: "↻", label: "Progress Update" }
    ]
  }
};

function installLogoFallback(image, label) {
  const replaceImage = () => {
    if (!image.isConnected) return;
    const fallback = document.createElement("span");
    fallback.className = "logo-fallback";
    fallback.setAttribute("aria-label", image.alt || label);
    fallback.textContent = label;
    image.replaceWith(fallback);
  };
  image.addEventListener("error", replaceImage, { once: true });
  if (image.complete && image.naturalWidth === 0) replaceImage();
}

all('img[src$="wildcatslogo.png"]').forEach((img) => installLogoFallback(img, "WC"));
all('img[src$="htclogo.png"]').forEach((img) => installLogoFallback(img, "HTC"));

function showToast(message) {
  const region = byId("toastRegion");
  if (!region) return;
  const toast = document.createElement("div");
  const icon = document.createElement("span");
  const text = document.createElement("p");
  toast.className = "toast";
  icon.textContent = "✓";
  text.textContent = message;
  toast.append(icon, text);
  region.appendChild(toast);
  window.setTimeout(() => toast.remove(), 4200);
}

function badgeClass(value) {
  const status = String(value).toLowerCase();
  if (status.includes("high")) return "badge-high";
  if (status.includes("medium")) return "badge-medium";
  if (status.includes("low")) return "badge-low";
  if (status.includes("progress")) return "badge-progress";
  if (status.includes("assign")) return "badge-assigned";
  if (status.includes("pending") || status.includes("awaiting") || status.includes("inactive")) return "badge-pending";
  if (status.includes("reject") || status.includes("duplicate")) return "badge-rejected";
  if (status.includes("complete") || status.includes("active") || status.includes("approve")) return "badge-completed";
  return "badge-review";
}

function createBadge(value) {
  const badge = document.createElement("span");
  badge.className = `badge ${badgeClass(value)}`;
  badge.textContent = value;
  return badge;
}

function createRecordRow(values, badgeColumns = []) {
  const row = document.createElement("tr");
  row.className = "new-record";
  values.forEach((value, index) => {
    const cell = document.createElement("td");
    if (index === 0) {
      const strong = document.createElement("strong");
      strong.textContent = value;
      cell.appendChild(strong);
    } else if (badgeColumns.includes(index)) {
      cell.appendChild(createBadge(value));
    } else {
      cell.textContent = value;
    }
    row.appendChild(cell);
  });
  return row;
}

function updateRecordCount(tableBodyId, countId) {
  const body = byId(tableBodyId);
  const label = byId(countId);
  if (!body || !label) return;
  const count = body.rows.length;
  label.textContent = `${count} ${count === 1 ? "Record" : "Records"}`;
}

function initials(name) {
  return String(name).trim().split(/\s+/).slice(0, 2).map((word) => word.charAt(0).toUpperCase()).join("") || "GU";
}

function recommendCategory(description) {
  const text = description.toLowerCase();
  if (/internet|wifi|wi-fi|network|router|connection/.test(text)) return "Internet & Network";
  if (/computer|laptop|printer|software|monitor|keyboard/.test(text)) return "IT & Computer";
  if (/light|outlet|power|electrical|electric|wiring|switch/.test(text)) return "Electrical";
  if (/projector|aircon|air conditioner|fan|equipment|machine/.test(text)) return "Equipment";
  if (/door|window|chair|desk|ceiling|classroom|building/.test(text)) return "Facilities";
  return "Maintenance";
}

function recommendPriority(description) {
  const text = description.toLowerCase();
  if (/fire|smoke|spark|shock|danger|flood|emergency|exposed wire|unavailable|several|multiple|entire|all users/.test(text)) return "High";
  if (/minor|loose|occasionally|intermittent|cosmetic/.test(text)) return "Low";
  return "Medium";
}

function formatDate(dateValue) {
  const date = new Date(`${dateValue}T00:00:00`);
  return new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric" }).format(date);
}

function viewTitle(viewId) {
  if (!state.activeRole || !roleConfiguration[state.activeRole]) return "Workspace";
  const item = roleConfiguration[state.activeRole].navigation.find((entry) => entry.view === viewId);
  return item ? item.label : "Workspace";
}

function buildWorkspaceNavigation(role) {
  const navigation = byId("workspaceNavigation");
  if (!navigation) return;
  navigation.replaceChildren();
  roleConfiguration[role].navigation.forEach((item) => {
    const button = document.createElement("button");
    const icon = document.createElement("span");
    const label = document.createElement("strong");
    button.type = "button";
    button.className = "workspace-nav-button";
    button.dataset.workspaceView = item.view;
    icon.textContent = item.icon;
    label.textContent = item.label;
    button.append(icon, label);
    navigation.appendChild(button);
  });
}

function openWorkspaceView(viewId) {
  if (!state.activeRole) return;
  const activeInterface = document.querySelector(`.role-interface[data-role="${state.activeRole}"]`);
  if (!activeInterface) return;

  all(".workspace-view", activeInterface).forEach((view) => {
    view.hidden = view.dataset.view !== viewId;
  });

  all(".workspace-nav-button", byId("workspaceNavigation") || document).forEach((button) => {
    button.classList.toggle("active", button.dataset.workspaceView === viewId);
  });

  const title = viewTitle(viewId);
  if (byId("workspacePageTitle")) byId("workspacePageTitle").textContent = title;
  if (byId("workspaceBreadcrumb")) byId("workspaceBreadcrumb").textContent = `Service Portal / ${roleConfiguration[state.activeRole].label} / ${title}`;
}

function activateRoleWorkspace(role, name, email) {
  if (!roleConfiguration[role]) {
    showToast("The selected role is not configured.");
    return;
  }

  state.activeRole = role;
  state.userName = name;
  state.userEmail = email;

  all(".role-interface").forEach((roleInterface) => {
    roleInterface.hidden = roleInterface.dataset.role !== role;
  });

  buildWorkspaceNavigation(role);
  if (byId("workspaceUserName")) byId("workspaceUserName").textContent = name;
  if (byId("workspaceUserEmail")) byId("workspaceUserEmail").textContent = email;
  if (byId("workspaceAvatar")) byId("workspaceAvatar").textContent = initials(name);
  if (byId("workspaceRoleName")) byId("workspaceRoleName").textContent = roleConfiguration[role].label;
  all("[data-user-name]").forEach((element) => { element.textContent = name; });

  if (role === "requester") {
    if (byId("requesterName")) byId("requesterName").value = name;
    if (byId("requesterEmail")) byId("requesterEmail").value = email;
  }

  if (byId("workspace")) byId("workspace").hidden = false;
  if (byId("workspaceNavLink")) byId("workspaceNavLink").hidden = false;
  if (byId("signInButton")) byId("signInButton").textContent = "Switch Account";

  openWorkspaceView(roleConfiguration[role].defaultView);
  byId("workspace")?.scrollIntoView({ behavior: "smooth", block: "start" });
}

function openLoginModal() {
  const modal = byId("loginModal");
  if (!modal) return;
  modal.hidden = false;
  document.body.classList.add("modal-open");
  window.setTimeout(() => byId("loginName")?.focus(), 30);
}

function closeLoginModal() {
  const modal = byId("loginModal");
  if (!modal) return;
  modal.hidden = true;
  document.body.classList.remove("modal-open");
}

/* NAVIGATION */
on(byId("menuButton"), "click", () => {
  const nav = byId("mainNav");
  if (!nav) return;
  const isOpen = nav.classList.toggle("open");
  byId("menuButton")?.setAttribute("aria-expanded", String(isOpen));
});

all(".main-nav a").forEach((link) => on(link, "click", () => {
  byId("mainNav")?.classList.remove("open");
  byId("menuButton")?.setAttribute("aria-expanded", "false");
}));

on(byId("workspaceNavigation"), "click", (event) => {
  const button = event.target.closest("[data-workspace-view]");
  if (button) openWorkspaceView(button.dataset.workspaceView);
});

/* SIGN IN / SIGN OUT */
on(byId("signInButton"), "click", openLoginModal);
on(byId("heroAccessButton"), "click", openLoginModal);
all("[data-close-modal]").forEach((control) => on(control, "click", closeLoginModal));

on(document, "keydown", (event) => {
  if (event.key === "Escape" && byId("loginModal") && !byId("loginModal").hidden) closeLoginModal();
});

on(byId("loginForm"), "submit", (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  if (!form.reportValidity()) return;

  const name = byId("loginName").value.trim();
  const email = byId("loginEmail").value.trim();
  const role = byId("loginRole").value;

  closeLoginModal();
  activateRoleWorkspace(role, name, email);
  showToast(`Signed in to the ${roleConfiguration[role].label} workspace.`);
  form.reset();
});

on(byId("signOutButton"), "click", () => {
  state.activeRole = null;
  state.userName = "";
  state.userEmail = "";
  all(".role-interface").forEach((roleInterface) => { roleInterface.hidden = true; });
  if (byId("workspace")) byId("workspace").hidden = true;
  if (byId("workspaceNavLink")) byId("workspaceNavLink").hidden = true;
  if (byId("signInButton")) byId("signInButton").textContent = "Sign In";
  byId("home")?.scrollIntoView({ behavior: "smooth", block: "start" });
  showToast("You have signed out of the role workspace.");
});

/* REQUESTER */
on(byId("requestDescription"), "input", (event) => {
  if (byId("requestCharacterCount")) byId("requestCharacterCount").textContent = event.target.value.length;
});

on(byId("serviceRequestForm"), "reset", () => {
  window.setTimeout(() => {
    if (byId("requestCharacterCount")) byId("requestCharacterCount").textContent = "0";
    if (state.activeRole === "requester") {
      if (byId("requesterName")) byId("requesterName").value = state.userName;
      if (byId("requesterEmail")) byId("requesterEmail").value = state.userEmail;
    }
  }, 0);
});

function createAdminReviewRow(requestId, description, category, priority) {
  const row = createRecordRow([requestId, description, category, priority], [3]);
  const statusCell = document.createElement("td");
  const actionCell = document.createElement("td");
  const actions = document.createElement("div");
  const forward = document.createElement("button");
  const duplicate = document.createElement("button");
  statusCell.dataset.status = "";
  statusCell.appendChild(createBadge("Under Review"));
  actions.className = "row-actions";
  forward.type = "button";
  forward.dataset.adminAction = "forward";
  forward.textContent = "Forward";
  duplicate.type = "button";
  duplicate.dataset.adminAction = "duplicate";
  duplicate.className = "danger";
  duplicate.textContent = "Duplicate";
  actions.append(forward, duplicate);
  actionCell.appendChild(actions);
  row.append(statusCell, actionCell);
  return row;
}

function createApprovalRow(requestId, description, category, priority) {
  const row = createRecordRow([requestId, description, category, priority], [3]);
  const statusCell = document.createElement("td");
  const actionCell = document.createElement("td");
  const actions = document.createElement("div");
  const approve = document.createElement("button");
  const reject = document.createElement("button");
  statusCell.dataset.status = "";
  statusCell.appendChild(createBadge("Awaiting Approval"));
  actions.className = "row-actions";
  approve.type = "button";
  approve.dataset.approvalAction = "approve";
  approve.textContent = "Approve";
  reject.type = "button";
  reject.dataset.approvalAction = "reject";
  reject.className = "danger";
  reject.textContent = "Reject";
  actions.append(approve, reject);
  actionCell.appendChild(actions);
  row.append(statusCell, actionCell);
  return row;
}

on(byId("serviceRequestForm"), "submit", (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  if (!form.reportValidity()) return;

  const description = byId("requestDescription").value.trim();
  const selectedCategory = byId("requestCategory").value;
  const category = selectedCategory === "AI Recommendation" ? recommendCategory(description) : selectedCategory;
  const priority = recommendPriority(description);
  const location = byId("requestLocation").value.trim();

  state.requestNumber += 1;
  state.totalRequests += 1;
  state.pendingRequests += 1;
  const requestId = `SR-2026-${String(state.requestNumber).padStart(4, "0")}`;

  byId("serviceRequestTableBody")?.prepend(createRecordRow([requestId, description, category, location, priority, "Under Review"], [4, 5]));
  byId("adminReviewTableBody")?.prepend(createAdminReviewRow(requestId, description, category, priority));

  updateRecordCount("serviceRequestTableBody", "requestRecordCount");
  updateRecordCount("adminReviewTableBody", "adminReviewCount");
  if (byId("requesterTotalMetric")) byId("requesterTotalMetric").textContent = String(byId("serviceRequestTableBody").rows.length).padStart(2, "0");
  if (byId("totalRequestMetric")) byId("totalRequestMetric").textContent = String(state.totalRequests).padStart(2, "0");
  if (byId("pendingRequestMetric")) byId("pendingRequestMetric").textContent = String(state.pendingRequests).padStart(2, "0");

  form.reset();
  openWorkspaceView("requester-records");
  showToast(`${requestId} submitted. AI recommends ${category} with ${priority} priority.`);
});

/* ADMINISTRATOR */
on(byId("adminReviewTableBody"), "click", (event) => {
  const button = event.target.closest("[data-admin-action]");
  if (!button || button.disabled) return;
  const row = button.closest("tr");
  const requestId = row.querySelector("strong")?.textContent || "Request";
  const statusCell = row.querySelector("[data-status]");

  if (button.dataset.adminAction === "forward") {
    statusCell?.replaceChildren(createBadge("Awaiting Approval"));
    const cells = row.cells;
    const description = cells[1].textContent.trim();
    const category = cells[2].textContent.trim();
    const priority = cells[3].textContent.trim();
    const exists = all("tr", byId("approvalTableBody")).some((approvalRow) => approvalRow.querySelector("strong")?.textContent === requestId);
    if (!exists) {
      byId("approvalTableBody")?.prepend(createApprovalRow(requestId, description, category, priority));
      updateRecordCount("approvalTableBody", "approvalRecordCount");
    }
    showToast(`${requestId} was forwarded to the Department Head / Authorized Approver.`);
  } else {
    statusCell?.replaceChildren(createBadge("Duplicate"));
    showToast(`${requestId} was marked as a possible duplicate.`);
  }
  all("button", row).forEach((action) => { action.disabled = true; });
});

const scheduleInput = byId("workScheduleInput");
if (scheduleInput) {
  const now = new Date();
  scheduleInput.min = [now.getFullYear(), String(now.getMonth() + 1).padStart(2, "0"), String(now.getDate()).padStart(2, "0")].join("-");
}

on(byId("workOrderEntryForm"), "submit", (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  if (!form.reportValidity()) return;
  state.workOrderNumber += 1;
  const workOrderId = `WO-2026-${String(state.workOrderNumber).padStart(4, "0")}`;
  const requestId = byId("relatedRequestId").value.trim().toUpperCase();
  const team = byId("assignedTeam").value;
  const schedule = formatDate(byId("workScheduleInput").value);
  const materials = byId("workMaterials").value.trim() || "To be determined";
  const status = byId("workStatusInput").value;
  const instructions = byId("workInstructions").value.trim();

  byId("workOrderTableBody")?.prepend(createRecordRow([workOrderId, requestId, team, schedule, materials, status], [5]));

  const technicianRow = createRecordRow([workOrderId, requestId, instructions, schedule, "Medium"], [4]);
  const statusCell = document.createElement("td");
  statusCell.dataset.status = "";
  statusCell.appendChild(createBadge(status));
  technicianRow.appendChild(statusCell);
  byId("technicianWorkTableBody")?.prepend(technicianRow);

  updateRecordCount("workOrderTableBody", "workOrderRecordCount");
  updateRecordCount("technicianWorkTableBody", "technicianWorkCount");
  form.reset();
  showToast(`${workOrderId} was created and routed to ${team}.`);
});

const accessCheckboxes = all('input[name="accessRights"]');
accessCheckboxes.forEach((checkbox) => on(checkbox, "change", () => {
  if (byId("permissionError")) byId("permissionError").textContent = "";
}));

on(byId("personnelAccessForm"), "reset", () => {
  if (byId("permissionError")) byId("permissionError").textContent = "";
});

on(byId("personnelAccessForm"), "submit", (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const selectedRights = accessCheckboxes.filter((checkbox) => checkbox.checked).map((checkbox) => checkbox.value);
  if (selectedRights.length === 0) {
    byId("permissionError").textContent = "Select at least one access right.";
    accessCheckboxes[0]?.focus();
    return;
  }
  if (!form.reportValidity()) return;

  state.personnelNumber += 1;
  const personnelId = `SP-${String(state.personnelNumber).padStart(3, "0")}`;
  byId("personnelTableBody")?.prepend(createRecordRow([
    personnelId,
    byId("personnelName").value.trim(),
    byId("personnelDepartment").value,
    byId("personnelRole").value,
    selectedRights.join(", "),
    byId("personnelStatus").value
  ], [5]));
  updateRecordCount("personnelTableBody", "personnelRecordCount");
  form.reset();
  showToast(`${personnelId} was added with ${selectedRights.join(", ")} access.`);
});

/* APPROVER */
on(byId("approvalTableBody"), "click", (event) => {
  const button = event.target.closest("[data-approval-action]");
  if (!button || button.disabled) return;
  const row = button.closest("tr");
  const requestId = row.querySelector("strong")?.textContent || "Request";
  const status = button.dataset.approvalAction === "approve" ? "Approved" : "Rejected";
  row.querySelector("[data-status]")?.replaceChildren(createBadge(status));
  all("button", row).forEach((action) => { action.disabled = true; });
  showToast(`${requestId} was ${status.toLowerCase()} by the authorized approver.`);
});

/* TECHNICIAN */
on(byId("progressUpdateForm"), "submit", (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  if (!form.reportValidity()) return;

  const input = byId("progressWorkOrderId");
  const workOrderId = input.value.trim().toUpperCase();
  const matchingRow = all("tr", byId("technicianWorkTableBody") || document).find((row) => row.querySelector("td:first-child")?.textContent.trim() === workOrderId);

  if (!matchingRow) {
    input.setCustomValidity("This work order is not assigned to the current service queue.");
    input.reportValidity();
    return;
  }

  input.setCustomValidity("");
  const status = byId("progressStatusInput").value;
  matchingRow.querySelector("[data-status]")?.replaceChildren(createBadge(status));
  form.reset();
  openWorkspaceView("technician-assigned");
  showToast(`${workOrderId} was updated to ${status}.`);
});

on(byId("progressWorkOrderId"), "input", (event) => event.target.setCustomValidity(""));

/* DATE */
if (byId("workspaceDate")) {
  byId("workspaceDate").textContent = new Intl.DateTimeFormat("en-US", { month: "long", day: "numeric", year: "numeric" }).format(new Date());
}
