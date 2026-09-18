from pathlib import Path
import re

path = Path('src/main/webapp/js/script.js')
s = path.read_text(encoding='utf-8')

def replace_function(name, code):
    global s
    match = re.search(r'^(?:async )?function ' + re.escape(name) + r'\(', s, re.M)
    if not match:
        raise RuntimeError(name)
    end = re.search(r'^(?:(?:async )?function |(?:const|let) [A-Za-z])', s[match.end():], re.M)
    if not end:
        raise RuntimeError('end ' + name)
    boundary = match.end() + end.start()
    s = s[:match.start()] + code.strip() + '\n\n' + s[boundary:]

s = s.replace('console.info("HTC Service Portal JS version: 2026.09.13-auth3");', '')
s = s.replace('  departments: "api/departments",', '''  approvals: "api/approvals",
  workOrders: "api/work-orders",
  requestDetails: "api/request-details",
  notifications: "api/notifications",
  dashboard: "api/dashboard",
  reports: "api/reports",
  auditLogs: "api/audit-logs",
  accountReviews: "api/account-reviews",
  departments: "api/departments",''')
s = s.replace('  notifications: []\n', '  notifications: [], dashboard: null, accountReviews: [], auditEntries: [], technicians: []\n')
for name in ['recommendCategory', 'recommendPriority', 'importantWords', 'findPossibleDuplicate']:
    replace_function(name, '')
replace_function('mapServiceRequestFromApi', '''function mapServiceRequestFromApi(record) {
  return {
    ...record,
    databaseId: record.requestId,
    id: record.requestNumber || `Request #${record.requestId}`,
    requesterName: record.requesterName || state.userName || "",
    requesterEmail: record.requesterEmail || state.userEmail || "",
    department: record.requesterDepartmentName || record.departmentName || state.currentAccount?.department || "",
    category: record.finalCategoryName || record.category || record.requestedCategoryName || "Pending classification",
    requestedCategory: record.requestedCategoryName || "System recommendation",
    preferredPriority: record.preferredPriority || "",
    priority: record.finalPriority || record.priority || record.preferredPriority || "",
    status: record.currentStatus || record.status || "Submitted",
    dateReported: record.dateReported || record.createdAt,
    aiCategory: record.aiCategory || "Pending analysis",
    aiPriority: record.aiPriority || "Pending analysis",
    duplicateCheck: record.duplicateCheck || "Review request details for analysis"
  };
}''')
replace_function('renderPublicMetrics', '''function renderPublicMetrics() {
  ["publicTotalMetric", "publicPendingMetric", "publicProgressMetric", "publicCompletedMetric"].forEach(id => {
    byId(id).textContent = "—";
  });
}''')
replace_function('renderPublicActivity', '''function renderPublicActivity() {
  byId("publicRecentActivity")?.replaceChildren(createElement("p", "activity-empty", "Sign in to see the requests and service activity available to your account."));
}''')
s = s.replace('  return state.adminReviewRequests;', '''  return state.serviceRequests.length ? state.serviceRequests.map(request => ({
    ...request, ...state.adminReviewRequests.find(item => item.databaseId === request.databaseId)
  })) : state.adminReviewRequests;''', 1)
# Capture immutable session changes before any private data is rendered.
s = s.replace('  state.activeRole =\n    role;', '  clearPrivateRecords();\n  state.activeRole =\n    role;', 1)
s = s.replace('  state.currentAccount = null;', '  state.currentAccount = null;\n  clearPrivateRecords();', 1)
s = s.replace('  renderNotifications();\n}', '  renderNotifications();\n  renderOperationalViews();\n}', 1)
s = s.replace('  const records = state.serviceRequests;', '  const records = state.serviceRequests;', 1)
replace_function('roleNotifications', '''function roleNotifications() {
  return state.activeRole ? state.notifications : [];
}''')
replace_function('renderNotifications', '''function renderNotifications() {
  renderPersistentNotifications();
}''')
replace_function('handleWorkOrderSubmit', '''async function handleWorkOrderSubmit(event) {
  event.preventDefault();
  await saveWorkOrder(event.currentTarget);
}''')
replace_function('handleProgressUpdateSubmit', '''async function handleProgressUpdateSubmit(event) {
  event.preventDefault();
  await saveWorkProgress(event.currentTarget);
}''')
replace_function('decideApproval', '''async function decideApproval(requestId, decision, remarks = "") {
  const approval = state.approvals.find(item => item.requestId === requestId && item.decision === "Pending");
  if (!approval) return showToast("This approval is no longer pending. Refresh the workspace.", "error");
  await runWorkspaceAction(() => apiRequest(API_ENDPOINTS.approvals, "POST", {
    approvalId: approval.approvalId, decision, remarks
  }), "Approval decision recorded.");
}''')
s = s.replace('      onConfirm:\n        () =>\n          decideApproval(', '      requireRemarks: true,\n      onConfirm:\n        (remarks) =>\n          decideApproval(', 1)
s = s.replace('            "Approved"\n          )', '            "Approved", remarks\n          )', 1)
replace_function('showRequestDetails', '''async function showRequestDetails(request) {
  await showRequestTimeline(request);
}''')
replace_function('showWorkOrderDetails', '''async function showWorkOrderDetails(workOrder) {
  const request = findRequest(workOrder.requestId) || { databaseId: workOrder.requestDatabaseId, id: workOrder.requestId };
  await showRequestTimeline(request);
}''')
s = s.replace('    showToast(\n      `${request.id} is currently ${request.status}.`,\n      "info"\n    );', '    showRequestDetails(request);', 1)
replace_function('populateWorkOrderForm', '''function populateWorkOrderForm(workOrder) {
  byId("workOrderId").value = workOrder.id;
  byId("workOrderRequestId").value = workOrder.requestId;
  byId("assignedTechnician").value = workOrder.assignedPersonnelId || "";
  byId("serviceUnit").value = workOrder.serviceUnit;
  byId("workDescription").value = workOrder.workDescription;
  byId("workStatus").value = workOrder.status;
  byId("workOrderTargetDate").value = workOrder.targetDate?.slice(0, 10) || "";
  byId("workOrderAction").value = "assign";
  byId("technicianRemarks").value = "";
}''')
replace_function('populateTechnicianUpdateForm', '''function populateTechnicianUpdateForm(workOrder) {
  byId("progressWorkOrderId").value = workOrder.id;
  byId("progressRequestId").value = workOrder.requestId;
  byId("progressTechnician").value = workOrder.assignedPersonnel || state.userName;
  byId("progressServiceUnit").value = workOrder.serviceUnit;
  byId("progressWorkDescription").value = workOrder.workDescription;
  byId("progressStatusInput").value = workOrder.status;
  byId("progressPercentage").value = workOrder.progressPercentage || 0;
  byId("progressAction").value = workOrder.status === "Assigned" ? "acknowledge" : "progress";
  byId("progressActionTaken").value = "";
  byId("progressRemarks").value = "";
}''')
# Keep authentication and validation flows, replace role-specific data initialization.
start = s.index('    if (account.role === "administrator")', s.index('async function restoreServerSession'))
end = s.index('\n  } catch (error)', start)
s = s[:start] + '    await loadWorkspaceData();\n' + s[end:]
start = s.index('    if (account.role === "requester")', s.index('async function handleLoginSubmit'))
end = s.index('    showToast(', s.index('    if (account.role === "administrator")', start))
# Find whole admin block using next signed-in message, preserving the success toast.
end = s.rfind('    showToast(', start, s.index('      `Signed in to the', start))
s = s[:start] + '    await loadWorkspaceData();\n\n' + s[end:]
s = s.replace('  initializeEvents();', '  initializeOperationalInterface();\n  initializeEvents();', 1)
s = s.replace('''    if (state.activeRole === "requester") {
      await loadRequesterServiceRequests();
      await loadRequesterServiceRequestDrafts();
      renderAll();
    }
''', '')
# CSRF on both explicit and error-cleanup logout.
s = s.replace('    const response = await fetch(API_ENDPOINTS.logout, {', '    const csrf = await getCsrfToken();\n    const response = await fetch(API_ENDPOINTS.logout, {', 1)
s = s.replace('''      headers: {
        Accept: "application/json"
      },
      cache: "no-store"
    });

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success) {
      throw new Error(data.message || "The server did not complete logout.");''', '''      headers: {
        Accept: "application/json", [csrf.headerName]: csrf.token
      },
      cache: "no-store"
    });

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success) {
      throw new Error(data.message || "The server did not complete logout.");''')
replace_function('invalidateServerSessionSilently', '''async function invalidateServerSessionSilently() {
  try { await apiRequest(API_ENDPOINTS.logout, "POST", {}); }
  catch { /* Preserve the original authentication error. */ }
}''')
s = s.replace('            roleId\n          })', '            roleId,\n            ...personnelProfileChanges()\n          })', 1)
s = s.replace('  populatePersonnelDepartmentOptions(\n    person.departmentId\n  );', '  populatePersonnelProfile(person);\n  populatePersonnelDepartmentOptions(\n    person.departmentId\n  );', 1)
# Submission helpers must surface retrieval failures instead of silently showing an empty successful view.
s = s.replace('    // Never restore demo/mock data.\n    state.serviceRequests = [];', '    state.serviceRequests = [];\n    throw error;')
s = s.replace('    state.serviceRequestDrafts = [];\n  }\n}', '    state.serviceRequestDrafts = [];\n    throw error;\n  }\n}', 1)
s = s.replace('''  if (!input || input.disabled || input.type''', '''  if (!input || input.disabled || input.readOnly || input.type''', 1)
s = s.replace('"Enter at least 5 meaningful characters explaining the rejection."', '"Enter at least 5 meaningful characters explaining this decision."')
path.write_text(s, encoding='utf-8')
