"use strict";

// Lifecycle operations share the existing session, validators and HTC interface.
let workspaceLoadGeneration = 0;

async function apiRequest(url, method = "GET", payload = null) {
  const headers = { Accept: "application/json" };
  if (method !== "GET") {
    const csrf = await getCsrfToken();
    headers[csrf.headerName] = csrf.token;
    if (!(payload instanceof FormData)) headers["Content-Type"] = "application/json";
  }
  const response = await fetch(url, {
    method, headers, credentials: "same-origin", cache: "no-store",
    ...(payload == null ? {} : { body: payload instanceof FormData ? payload : JSON.stringify(payload) })
  });
  const data = await readJsonResponse(response);
  if (!response.ok || !data.success) {
    if (response.status === 401 && state.activeRole) clearAuthenticatedWorkspace();
    throw new Error(data.message || `Unable to complete the request (${response.status}).`);
  }
  return data;
}

function clearPrivateRecords() {
  workspaceLoadGeneration++;
  for (const key of ["serviceRequests", "adminReviewRequests", "serviceRequestDrafts", "workOrders", "personnel", "approvals", "approvalHistory", "technicianHistory", "notifications", "accountReviews", "auditEntries", "technicians"]) state[key] = [];
  state.dashboard = null;
  state.currentDraftId = null;
  state.confirmAction = null;
  for (const id of ["detailsModalContent", "notificationList", "requesterDraftList", "accountReviewList", "auditLogList"]) byId(id)?.replaceChildren();
  all("[data-report-content]").forEach(node => node.replaceChildren());
  all(".modal").forEach(modal => { modal.hidden = true; });
  document.body.classList.remove("modal-open");
}

function mapWorkOrder(record) {
  return { ...record, databaseId: record.workOrderId, id: record.workOrderNumber,
    requestDatabaseId: record.requestId, requestId: record.requestNumber,
    assignedPersonnel: record.assignedPersonnelName || "Unassigned",
    serviceUnit: record.departmentName || "", workDescription: record.workDescription || record.description || record.title || "",
    completionDate: record.completedAt || "", updatedAt: record.updatedAt || record.createdAt,
    actionTaken: record.actionTaken || "", materials: record.materialsUsed || "", remarks: record.remarks || ""
  };
}

async function loadWorkspaceData() {
  if (!state.activeRole) return;
  const generation = ++workspaceLoadGeneration;
  const role = state.activeRole;
  const notice = byId("workspaceLoadStatus");
  notice.textContent = "Loading your workspace…";
  notice.className = "workspace-load-status";
  byId("workspaceRefresh").disabled = true;
  const jobs = [
    ["Dashboard", API_ENDPOINTS.dashboard, data => { state.dashboard = data.data; }],
    ["Notifications", API_ENDPOINTS.notifications, data => { state.notifications = data.notifications || []; }]
  ];
  if (role === "requester") {
    jobs.push(["Requests", API_ENDPOINTS.serviceRequests + "?limit=100", data => { state.serviceRequests = (data.requests || []).map(mapServiceRequestFromApi); }]);
    jobs.push(["Drafts", API_ENDPOINTS.serviceRequestDrafts, data => { state.serviceRequestDrafts = data.drafts || []; }]);
  } else {
    jobs.push(["Requests", API_ENDPOINTS.requestDetails, data => { state.serviceRequests = (data.requests || []).map(mapServiceRequestFromApi); }]);
    jobs.push(["Work orders", API_ENDPOINTS.workOrders, data => {
      state.workOrders = (data.workOrders || []).map(mapWorkOrder);
      state.technicians = data.technicians || [];
      state.technicianHistory = (data.progress || []).map(record => {
        const order = state.workOrders.find(item => item.databaseId === record.workOrderId);
        return { ...record, workOrderId: order?.id || record.workOrderId, requestId: order?.requestId || "",
          actionTaken: record.actionTaken || record.remarks || record.updateType, completionDate: record.recordedAt };
      });
    }]);
  }
  if (role === "administrator") {
    jobs.push(["Review queue", API_ENDPOINTS.serviceRequestReviews + "?limit=100", data => { state.adminReviewRequests = (data.reviewItems || []).map(mapAdministratorReviewFromApi); }]);
    jobs.push(["Personnel", API_ENDPOINTS.personnel, data => { state.personnel = (data.personnel || []).map(mapPersonnelFromApi); }]);
    jobs.push(["Account reviews", API_ENDPOINTS.accountReviews, data => { state.accountReviews = data.reviews || []; }]);
    jobs.push(["Audit logs", API_ENDPOINTS.auditLogs, data => { state.auditEntries = data.entries || []; }]);
  }
  if (role === "approver" || role === "administrator") {
    jobs.push(["Approvals", API_ENDPOINTS.approvals, data => {
      const records = (data.approvals || []).map(item => ({ ...item, requestDatabaseId: item.requestId, requestId: item.requestNumber }));
      state.approvals = records;
      state.approvalHistory = records.filter(item => item.decision !== "Pending");
    }]);
  }
  const outcomes = await Promise.allSettled(jobs.map(async ([label, url, apply]) => {
    const data = await apiRequest(url);
    if (generation === workspaceLoadGeneration) apply(data);
    return label;
  }));
  if (generation !== workspaceLoadGeneration) return;
  const errors = outcomes.flatMap((result, index) => result.status === "rejected" ? [`${jobs[index][0]}: ${result.reason.message}`] : []);
  notice.textContent = errors.length ? errors.join(" ") : "Workspace up to date.";
  notice.classList.toggle("is-error", errors.length > 0);
  byId("workspaceRefresh").disabled = false;
  renderAll();
  populatePersonnelAccessManagement();
  populateWorkReferences();
}

async function runWorkspaceAction(action, message, form = null) {
  if (form) setSubmitting(form, true);
  try {
    await action();
    showToast(message, "success");
    await loadWorkspaceData();
    return true;
  } catch (error) {
    showToast(error.message, "error");
    return false;
  } finally {
    if (form) setSubmitting(form, false);
  }
}

function option(value, label = value) {
  const node = createElement("option", "", label);
  node.value = value;
  return node;
}

function populateWorkReferences() {
  const technician = byId("assignedTechnician");
  const previous = technician.value;
  technician.replaceChildren(option("", "Select technician"), ...state.technicians.map(person => option(person.personnelId, person.name)));
  technician.value = previous;
  const departments = [...departmentNamesById.entries()].map(([id, name]) => option(name, name));
  for (const id of ["serviceUnit", "progressServiceUnit"]) {
    const select = byId(id), value = select.value;
    select.replaceChildren(option("", "Assigned service department"), ...departments.map(node => node.cloneNode(true)));
    select.value = value;
  }
  const requests = byId("approvedRequestChoices");
  requests.replaceChildren(...state.serviceRequests.filter(item => item.status === "Approved").map(item => option(item.id, `${item.id} · ${item.title}`)));
}

async function saveWorkOrder(form) {
  const orderNumber = byId("workOrderId").value.trim();
  const order = findWorkOrder(orderNumber);
  const assignedPersonnelId = Number(byId("assignedTechnician").value) || null;
  const remarks = byId("technicianRemarks").value.trim();
  if (orderNumber && !order) return showToast("Select an existing work order from the table, or clear the form to create one.", "error");
  if (order) {
    const action = byId("workOrderAction").value;
    if (action === "assign" && !assignedPersonnelId) return showToast("Choose an active technician.", "error");
    if (remarks.length < 3) return showToast("Add remarks explaining this assignment or verification.", "error");
    if (await runWorkspaceAction(() => apiRequest(API_ENDPOINTS.workOrders, "PUT", { workOrderId: order.databaseId, action, assignedPersonnelId, remarks }), "Work order updated.", form)) form.reset();
    return;
  }
  const request = findRequest(byId("workOrderRequestId").value.trim());
  const workDescription = byId("workDescription").value.trim();
  if (!request || request.status !== "Approved") return showToast("Choose an approved service request.", "error");
  if (workDescription.length < 10) return showToast("Describe the work in at least 10 characters.", "error");
  const targetDate = byId("workOrderTargetDate").value || null;
  if (await runWorkspaceAction(() => apiRequest(API_ENDPOINTS.workOrders, "POST", { requestId: request.databaseId, assignedPersonnelId, workDescription, targetDate }), "Work order created.", form)) form.reset();
}

async function saveWorkProgress(form) {
  const order = findWorkOrder(byId("progressWorkOrderId").value.trim());
  if (!order) return showToast("Select an assigned work order using its Update button.", "error");
  const action = byId("progressAction").value;
  const remarks = byId("progressRemarks").value.trim();
  const actionTaken = byId("progressActionTaken").value.trim();
  if (Math.max(remarks.length, actionTaken.length) < 3) return showToast("Describe the action taken or add progress remarks.", "error");
  if (action === "complete" && actionTaken.length < 5) return showToast("Enter resolution details before completing this work.", "error");
  const evidence = byId("progressEvidence");
  if (!validateAttachment(evidence)) return;
  setSubmitting(form, true);
  try {
    // Upload first so a failed upload never silently discards resolution evidence.
    if (evidence.files?.[0]) {
      const body = new FormData();
      body.append("requestId", order.requestDatabaseId);
      body.append("attachment", evidence.files[0]);
      await apiRequest(API_ENDPOINTS.serviceRequestAttachments, "POST", body);
      evidence.value = "";
    }
    await apiRequest(API_ENDPOINTS.workOrders, "PUT", {
      workOrderId: order.databaseId, action, remarks: remarks || actionTaken,
      actionTaken, materialsUsed: byId("progressMaterials").value.trim(),
      progressPercentage: Number(byId("progressPercentage").value)
    });
    showToast("Work progress recorded.", "success");
    form.reset();
    await loadWorkspaceData();
  } catch (error) { showToast(error.message, "error"); }
  finally { setSubmitting(form, false); }
}

function renderPersistentNotifications() {
  const list = byId("notificationList"), count = byId("notificationCount");
  if (!list || !count) return;
  const unread = state.notifications.filter(item => !item.isRead).length;
  count.textContent = unread;
  count.hidden = unread === 0;
  byId("notificationButton").setAttribute("aria-label", `Notifications, ${unread} unread`);
  list.replaceChildren();
  if (!state.notifications.length) list.append(createElement("p", "notification-empty", "No notifications yet."));
  state.notifications.forEach(notification => {
    const item = createElement("article", `notification-item${notification.isRead ? " is-read" : ""}`);
    item.append(createElement("strong", "", notification.title), createElement("p", "", notification.message), createElement("small", "", formatDate(notification.createdAt)));
    if (!notification.isRead) {
      const button = createElement("button", "button button-small button-secondary", "Mark as read");
      button.type = "button";
      on(button, "click", async () => {
        button.disabled = true;
        try {
          await apiRequest(API_ENDPOINTS.notifications, "PUT", { notificationId: notification.notificationId });
          notification.isRead = true;
          renderPersistentNotifications();
        } catch (error) { showToast(error.message, "error"); button.disabled = false; }
      });
      item.append(button);
    }
    list.append(item);
  });
}

function detailField(label, value, full = false) { return { label, value, full }; }

async function showRequestTimeline(request) {
  if (!request?.databaseId) return showToast("This request has no valid identifier.", "error");
  showDetails(request.id, "Service Request", [detailField("Status", "Loading details…")]);
  try {
    const [data, attachmentResult] = await Promise.all([
      apiRequest(`${API_ENDPOINTS.requestDetails}?requestId=${encodeURIComponent(request.databaseId)}`),
      apiRequest(`${API_ENDPOINTS.serviceRequestAttachments}?requestId=${encodeURIComponent(request.databaseId)}`).then(data => ({ data }), error => ({ error }))
    ]);
    const record = mapServiceRequestFromApi(data.request || request);
    const fields = [detailField("Title", record.title, true), detailField("Requester", record.requesterName), detailField("Department", record.department),
      detailField("Category", record.category), detailField("Priority", record.priority), detailField("Status", record.status), detailField("Location", record.location),
      detailField("Submitted", formatDate(record.createdAt || record.dateReported)), detailField("Description", record.description, true)];
    for (const item of data.aiRecommendations || []) fields.push(detailField("AI recommendation", [item.analysisStatus, item.recommendedCategoryName || item.categoryName, item.recommendedPriority, item.analysisMessage || item.reasoning, item.recommendationMethod].filter(Boolean).join(" · "), true));
    if (!(data.aiRecommendations || []).length) fields.push(detailField("AI analysis", "No completed recommendation is available. Human review remains required.", true));
    if (attachmentResult.error) fields.push(detailField("Attachments", attachmentResult.error.message, true));
    else for (const attachment of attachmentResult.data.attachments || []) fields.push({ label: "Attachment / Evidence", value: `${attachment.originalFileName} (${formatAttachmentSize(attachment.fileSizeBytes)})`, full: true,
      linkUrl: `${API_ENDPOINTS.serviceRequestAttachments}?attachmentId=${encodeURIComponent(Number(attachment.attachmentId))}&download=1`, linkLabel: "Download" });
    for (const item of data.history || []) fields.push(detailField("Request history", [item.changedAt || item.createdAt, `${item.oldStatus || item.previousStatus || ""} → ${item.newStatus || item.status || ""}`, item.changedByName || item.actorName, item.remarks].filter(Boolean).join(" · "), true));
    for (const item of data.approvals || []) fields.push(detailField("Approval", [item.decision, item.approverName, item.decisionDate || item.decidedAt, item.remarks].filter(Boolean).join(" · "), true));
    for (const item of data.workOrders || []) fields.push(detailField("Work order", [item.workOrderNumber, item.status, item.assignedPersonnelName, item.targetDate ? `Target: ${item.targetDate}` : ""].filter(Boolean).join(" · "), true));
    for (const item of data.progress || []) fields.push(detailField("Work progress", [item.recordedAt, item.status || item.newStatus, item.updateType, item.actionTaken, item.remarks, item.materialsUsed].filter(Boolean).join(" · "), true));
    showDetails(record.id, "Request Details & History", fields);
    if (state.activeRole === "administrator") {
      const actions = createElement("div", "detail-item full row-actions");
      for (const [action, label] of [["note", "Add administrative note"], ...(record.status === "Submitted" ? [["review", "Begin review"]] : [])]) {
        const button = createElement("button", "button button-secondary", label); button.type = "button";
        on(button, "click", () => openConfirm({ title: label, message: record.id, requireRemarks: true, onConfirm: remarks => runWorkspaceAction(() => apiRequest(API_ENDPOINTS.requestDetails, "PUT", { requestId: record.databaseId, action, remarks }), "Request updated.") }));
        actions.append(button);
      }
      byId("detailsModalContent").append(actions);
    }
    if (["requester", "administrator"].includes(state.activeRole) && ["Submitted", "Under Review", "Awaiting Approval", "Approved"].includes(record.status) && !(data.workOrders || []).length) {
      const button = createElement("button", "button button-danger", "Cancel request"); button.type = "button";
      on(button, "click", () => openConfirm({ title: "Cancel this request?", message: record.id, requireRemarks: true, type: "danger", onConfirm: remarks => runWorkspaceAction(() => apiRequest(API_ENDPOINTS.requestDetails, "PUT", { requestId: record.databaseId, action: "cancel", remarks }), "Request cancelled.") }));
      byId("detailsModalContent").append(button);
    }
  } catch (error) {
    byId("detailsModalContent").replaceChildren(createElement("p", "is-error", error.message));
  }
}

function renderOperationalViews() {
  const dashboard = state.dashboard;
  if (dashboard) {
    const values = { requesterTotalMetric: dashboard.totalRequests, requesterPendingMetric: dashboard.pendingRequests,
      requesterProgressMetric: dashboard.inProgressRequests, requesterCompletedMetric: dashboard.completedRequests,
      adminQueueMetric: dashboard.pendingRequests, adminApprovalMetric: (dashboard.byStatus || []).find(item => item.label === "Awaiting Approval")?.count || 0,
      adminHighMetric: (dashboard.byPriority || []).filter(item => ["High", "Urgent"].includes(item.label)).reduce((sum, item) => sum + Number(item.count), 0),
      adminWorkOrderMetric: (dashboard.workOrderStatus || []).reduce((sum, item) => sum + Number(item.count), 0) };
    Object.entries(values).forEach(([id, value]) => { if (byId(id)) byId(id).textContent = value ?? "—"; });
    for (const container of all("[data-report-content]")) {
      container.replaceChildren();
      const metrics = createElement("div", "report-summary");
      metrics.append(createElement("p", "", `${dashboard.totalRequests ?? 0} requests · ${dashboard.pendingRequests ?? 0} pending · ${dashboard.completedRequests ?? 0} completed`));
      metrics.append(createElement("p", "", dashboard.averageResolutionHours == null ? "No completed requests for a resolution-time average." : `Average resolution: ${Number(dashboard.averageResolutionHours).toFixed(1)} hours`));
      container.append(metrics);
      for (const [key, label] of [["byStatus", "Request status"], ["byCategory", "Service categories"], ["byDepartment", "Departments"], ["byPriority", "Priorities"], ["workOrderStatus", "Work orders"], ["technicianWorkload", "Technician workload"], ["aiStatus", "AI analysis"], ["monthlyVolume", "Monthly request volume"], ["frequentLocations", "Frequent locations"]]) {
        const group = createElement("section", "report-group"); group.append(createElement("h3", "", label));
        const records = dashboard[key] || [];
        if (!records.length) group.append(createElement("p", "helper-text", "No records available."));
        for (const record of records) {
          const row = createElement("div", "report-row");
          row.append(createElement("span", "", record.label || "Unspecified"), createElement("strong", "", record.count ?? 0));
          group.append(row);
        }
        container.append(group);
      }
    }
  }
  renderAccountReviews();
  const audit = byId("auditLogList");
  audit.replaceChildren();
  for (const item of state.auditEntries) {
    const row = createElement("article", "compact-item");
    row.append(createElement("strong", "", `${item.action} · ${item.actorName || "System"}`), createElement("span", "", `${item.summary || `${item.entityType || ""} ${item.entityId || ""}`} · ${formatDate(item.createdAt)}`));
    audit.append(row);
  }
  if (!state.auditEntries.length) audit.append(createElement("p", "helper-text", "No audit records loaded."));
  for (const id of ["workOrderTableBody", "technicianWorkTableBody", "approvalTableBody", "approvalHistoryTableBody", "personnelTableBody", "technicianHistoryTableBody"]) {
    const body = byId(id);
    if (!body?.children.length) {
      const row = document.createElement("tr"), cell = createElement("td", "table-empty", "No records available.");
      cell.colSpan = body.closest("table").querySelectorAll("thead th").length;
      row.append(cell); body.append(row);
    }
  }
}

function renderAccountReviews() {
  const container = byId("accountReviewList"); container.replaceChildren();
  if (!state.accountReviews.length) container.append(createElement("p", "helper-text", "No account registrations awaiting approval."));
  for (const review of state.accountReviews) {
    const row = createElement("article", "account-review-item");
    row.append(createElement("strong", "", `${review.firstName} ${review.lastName}`), createElement("p", "", `${review.email} · ${review.requestedDepartmentName} · ${review.requestedRoleName}`));
    for (const decision of ["Approved", "Rejected"]) {
      const button = createElement("button", `button button-small ${decision === "Approved" ? "button-primary" : "button-secondary"}`, decision === "Approved" ? "Approve account" : "Reject account");
      button.type = "button";
      on(button, "click", () => openConfirm({ title: `${decision === "Approved" ? "Approve" : "Reject"} this account?`, message: review.email, requireRemarks: true,
        onConfirm: remarks => runWorkspaceAction(() => apiRequest(API_ENDPOINTS.accountReviews, "POST", { accountReviewId: review.accountReviewId, decision, remarks }), "Account review recorded.") }));
      row.append(button);
    }
    container.append(row);
  }
}

function populatePersonnelProfile(person) {
  byId("personnelAccountStatus").value = ["Active", "Inactive"].includes(person.status) ? person.status : "";
  byId("personnelContact").value = person.contactNumber || "";
}

function personnelProfileChanges() {
  return { ...(byId("personnelAccountStatus").value ? { accountStatus: byId("personnelAccountStatus").value } : {}), contactNumber: byId("personnelContact").value.trim() };
}

function initializeOperationalInterface() {
  for (const [role, configuration] of Object.entries(roleConfiguration)) {
    configuration.navigation.push({ view: `${role}-reports`, icon: "▤", label: "Insights & Reports" });
    const section = createElement("section", "workspace-view");
    section.dataset.view = `${role}-reports`; section.hidden = true;
    const card = createElement("div", "panel-card");
    card.append(createElement("h3", "", "Service insights"), createElement("p", "helper-text", "Statistics cover the records available to your account."));
    const download = createElement("button", "button button-secondary", "Download report CSV"); download.type = "button";
    on(download, "click", downloadReport); card.append(download);
    const content = createElement("div", "reports-grid"); content.dataset.reportContent = "";
    content.append(createElement("p", "helper-text", "Sign in to load your report.")); card.append(content); section.append(card);
    document.querySelector(`.role-interface[data-role="${role}"]`).append(section);
  }
  roleConfiguration.administrator.navigation.push({ view: "administrator-audit", icon: "▤", label: "Audit Logs" });
  on(byId("workspaceRefresh"), "click", loadWorkspaceData);
  on(byId("markAllNotificationsRead"), "click", () => runWorkspaceAction(() => apiRequest(API_ENDPOINTS.notifications, "PUT", { all: true }), "Notifications marked as read."));
  on(document, "keydown", event => {
    if (event.key !== "Tab") return;
    const modal = all(".modal").filter(item => !item.hidden).at(-1);
    if (!modal) return;
    const controls = all('button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]', modal).filter(node => node.getClientRects().length);
    const first = controls[0], last = controls.at(-1);
    if (!first) { event.preventDefault(); return; }
    if (event.shiftKey && (document.activeElement === first || !modal.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && (document.activeElement === last || !modal.contains(document.activeElement))) { event.preventDefault(); first.focus(); }
  });
  on(byId("personnelSearch"), "input", filterPersonnelTable);
  on(byId("personnelStatusFilter"), "change", filterPersonnelTable);
  on(byId("workOrderSearch"), "input", filterWorkOrders);
  on(byId("workOrderStatusFilter"), "change", filterWorkOrders);
  on(byId("adminStatusFilter"), "change", renderAdminReviewTable);
  on(byId("adminSort"), "change", renderAdminReviewTable);
  on(byId("workOrderRequestId"), "change", () => {
    const request = findRequest(byId("workOrderRequestId").value.trim());
    if (request) { byId("workDescription").value = request.description || request.title; byId("serviceUnit").value = request.routedDepartmentName || request.department || ""; }
  });
}

function filterPersonnelTable() {
  const query = normalizeText(byId("personnelSearch").value), status = byId("personnelStatusFilter").value;
  all("#personnelTableBody tr").forEach(row => { row.hidden = !normalizeText(row.textContent).includes(query) || (status && row.lastElementChild?.textContent !== status); });
}

function filterWorkOrders() {
  const query = normalizeText(byId("workOrderSearch").value), status = byId("workOrderStatusFilter").value;
  all("#workOrderTableBody tr").forEach(row => { row.hidden = !normalizeText(row.textContent).includes(query) || (status && row.children[4]?.textContent !== status); });
}

async function downloadReport() {
  try {
    const response = await apiRequest(API_ENDPOINTS.reports);
    const data = response.data;
    const rows = [["Measure", "Group", "Value"]];
    for (const [key, value] of Object.entries(data || {})) {
      if (Array.isArray(value)) for (const item of value) { if (Object.hasOwn(item, "count")) rows.push([key, item.label, item.count]); }
      else rows.push([key, "", value ?? ""]);
    }
    const cell = value => `"${String(value ?? "").replace(/^[=+@-]/, "'$&").replace(/"/g, '""')}"`;
    const blob = new Blob(["\uFEFF" + rows.map(row => row.map(cell).join(",")).join("\r\n")], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob), link = document.createElement("a");
    link.href = url; link.download = `htc-service-report-${todayInputValue()}.csv`; link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (error) { showToast(error.message, "error"); }
}
