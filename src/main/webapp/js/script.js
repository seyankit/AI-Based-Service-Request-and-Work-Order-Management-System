"use strict";

console.info("HTC Service Portal JS version: 2026.09.13-auth3");

/* =========================
   DOM HELPERS
========================= */
const byId = (id) => document.getElementById(id);

const all = (selector, scope = document) => [
  ...scope.querySelectorAll(selector),
];

const on = (target, eventName, handler) => {
  if (target) {
    target.addEventListener(eventName, handler);
  }
};

const API_ENDPOINTS = Object.freeze({
  departments: "api/departments",
  register: "api/register",
  resendVerification: "api/email-verification/resend",
  login: "api/login",
  session: "api/session",
  logout: "api/logout",
  serviceRequests: "api/service-requests",
  serviceRequestDrafts: "api/service-request-drafts",
  csrfToken: "api/csrf-token",
  serviceCategories: "api/service-categories",
  profilePhoto: "api/profile/photo",
  serviceRequestAttachments: "api/service-request-attachments",
  personnel: "api/personnel",
  approvals: "api/approvals",
  workOrders: "api/work-orders",
  workOrderProgress: "api/work-order-progress",
  workOrderHistory: "api/work-order-history",
  auditLogs: "api/audit-logs",
  serviceRequestReviews: "api/service-request-reviews",
  serviceRequestDuplicates: "api/service-request-duplicates",
  aiRecommendationAnalysis: "api/ai-recommendations/analyze",
  notifications: "api/notifications",
});

/* =========================
   APPLICATION STATE / INITIAL DATA
========================= */
const state = {
  activeRole: null,
  userName: "",
  userEmail: "",
  currentAccount: null,
  pendingVerificationEmail: "",

  requestNumber: 0,
  workOrderNumber: 0,
  personnelNumber: 0,

  publicBaseTotal: 0,
  publicBasePending: 0,

  lastFocusedElement: null,
  confirmAction: null,
  confirmRequiresRemarks: false,

  // Real records are loaded from backend APIs.
  serviceRequests: [],
  adminReviewRequests: [],
  serviceRequestDrafts: [],
  currentDraftId: null,
  workOrders: [],
  personnel: [],
  approvals: [],
  approvalHistory: [],
  approverApprovalVersion: 0,
  requestHistoryVersion: 0,
  technicianHistory: [],
  technicianWorkOrders: [],
  technicianWorkOrder: null,
  technicianWorkOrderId: null,
  technicianAction: "",
  technicianSessionVersion: 0,
  technicianQueueVersion: 0,
  technicianDetailVersion: 0,
  technicianLoading: false,
  technicianDetailLoading: false,
  technicianSubmitting: false,
  technicianQueueError: "",
  notifications: [],
  notificationUnreadCount: null,
  notificationsLoading: false,
  pendingAssignmentWorkOrderId: null,
  workOrderTimelineVersion: 0,
  auditEntries: [],
  auditNextCursor: null,
  auditLoading: false,
  auditError: "",
  auditLoadVersion: 0,
  auditFilters: {},
  adminAnalysisRequestId: null,
};

/* =========================
   BACKEND DATA LOADING
========================= */
function mapServiceRequestFromApi(record) {
  return {
    databaseId: record.requestId ?? null,

    id:
      record.requestNumber ||
      (record.requestId != null
        ? `Request #${record.requestId}`
        : "Service Request"),

    requesterName: state.userName || "",
    requesterEmail: state.userEmail || "",

    requesterType: state.currentAccount?.personnelType || "",

    department:
      record.requesterDepartmentName || state.currentAccount?.department || "",

    requestedCategoryId: record.requestedCategoryId ?? null,

    category:
      record.finalCategoryName ||
      record.requestedCategoryName ||
      "Pending Classification",

    requestedCategory: record.requestedCategoryName || "",

    title: record.title || "",

    description: record.description || "",

    location: record.location || "",

    preferredPriority: record.preferredPriority || "",

    priority: record.priority || record.preferredPriority || "",

    status: record.currentStatus || "Submitted",

    dateReported: record.dateReported || "",

    attachmentName: "",

    aiCategory: "",
    aiPriority: "",
    duplicateCheck: "",
  };
}

function mapPersonnelFromApi(record) {
  return {
    id: record.personnelId ?? "",

    personnelId: record.personnelId ?? null,

    name: record.fullName || "Unnamed Personnel",

    email: record.email || "",

    contactNumber: record.contactNumber || "",

    personnelType: record.personnelType || "",

    departmentId: record.departmentId ?? null,

    department: record.departmentName || "Not Available",

    roleId: record.roleId ?? null,

    role: record.roleName || "Not Assigned",

    status: record.accountStatus || "Unknown",

    emailVerifiedAt: record.emailVerifiedAt || null,

    profileImageFileName: record.profileImageFileName || "",
  };
}

function mapAdministratorReviewFromApi(item) {
  const request = item?.request || {};

  const requester = item?.requester || {};

  const requestedCategory = item?.requestedCategory || {};

  const recommendation = item?.aiRecommendation;

  const requesterName = [requester.firstName, requester.lastName]
    .filter(Boolean)
    .join(" ")
    .trim();

  const analysisStatus = String(recommendation?.analysisStatus || "Pending").trim();

  const recommendedCategory =
    recommendation?.recommendedCategoryName || recommendation?.categoryName || "";

  const recommendedPriority = recommendation?.recommendedPriority || "";

  const duplicateCandidates = Array.isArray(recommendation?.duplicateCandidates)
    ? recommendation.duplicateCandidates
      .map((candidate) => ({
        requestId: Number(candidate?.requestId),
        requestNumber: candidate?.requestNumber || "Request",
        title: candidate?.title || "Untitled request",
        category: candidate?.category || "Not specified",
        currentStatus: candidate?.currentStatus || "Unknown",
        similarityScore: Number(candidate?.similarityScore),
        explanation: candidate?.explanation || "",
      }))
      .filter((candidate) =>
        Number.isSafeInteger(candidate.requestId) && candidate.requestId > 0 &&
        Number.isFinite(candidate.similarityScore) && candidate.similarityScore >= 0 &&
        candidate.similarityScore <= 1,
      )
    : [];

  let duplicateCheck = "Awaiting AI analysis";

  if (["unavailable", "failed"].includes(analysisStatus.toLowerCase())) {
    duplicateCheck = "Duplicate analysis unavailable";
  } else if (duplicateCandidates.length) {
    duplicateCheck = `${duplicateCandidates.length} possible duplicate${
      duplicateCandidates.length === 1 ? "" : "s"
    }`;
  } else if (analysisStatus.toLowerCase() === "completed") {
    duplicateCheck = "No likely duplicate identified";
  }

  return {
    databaseId: request.requestId ?? null,

    id:
      request.requestNumber ||
      (request.requestId != null
        ? `Request #${request.requestId}`
        : "Service Request"),

    requesterId: requester.personnelId ?? request.requesterId ?? null,

    requesterName,
    requesterEmail: requester.email || "",

    requesterContactNumber: requester.contactNumber || "",

    requesterType: requester.personnelType || "",

    requesterDepartmentId:
      requester.departmentId ?? request.requesterDepartmentId ?? null,

    department: requester.departmentName || "",

    requestedCategoryId:
      requestedCategory.categoryId ?? request.requestedCategoryId ?? null,

    category: requestedCategory.categoryName || "Not specified",

    requestedCategory: requestedCategory.categoryName || "Not specified",

    title: request.title || "",

    description: request.description || "",

    location: request.location || "",

    date: request.dateReported || "",

    dateReported: request.dateReported || "",

    priority: request.preferredPriority || "Not specified",

    preferredPriority: request.preferredPriority || "Not specified",

    status: request.currentStatus || "Submitted",

    createdAt: request.createdAt || "",

    updatedAt: request.updatedAt || "",

    aiStatus: analysisStatus,

    aiCategory:
      recommendedCategory ||
      (analysisStatus.toLowerCase() === "pending"
        ? "Awaiting AI analysis"
        : "No AI category recommendation"),

    aiPriority:
      recommendedPriority ||
      (analysisStatus.toLowerCase() === "pending"
        ? "Pending"
        : "No AI priority recommendation"),

    duplicateCheck,

    aiAnalysisMessage: recommendation?.analysisMessage || "",

    aiRecommendationMethod: recommendation?.recommendationMethod || "",

    aiCategoryExplanation: recommendation?.categoryExplanation || "",

    aiPriorityExplanation: recommendation?.priorityExplanation || "",

    aiDuplicateExplanation: recommendation?.duplicateExplanation || "",

    aiDuplicateCandidates: duplicateCandidates,

    aiModelName: recommendation?.modelName || "",

    aiModelVersion: recommendation?.modelVersion || "",
  };
}

function mapWorkOrderFromApi(record) {
  return {
    databaseId: record.workOrderId ?? null,
    id: record.workOrderNumber || "Work Order",
    requestDatabaseId: record.requestId ?? null,
    requestId:
      record.requestNumber ||
      (record.requestId != null ? `Request #${record.requestId}` : ""),
    requestTitle: record.requestTitle || "",
    category: record.category || "",
    requestLocation: record.requestLocation || "",
    departmentId: record.departmentId ?? null,
    assignmentId: record.assignmentId ?? null,
    assignmentSequence: record.assignmentSequence ?? null,
    technicianId: record.technicianId ?? null,
    assignedPersonnel:
      record.technicianName || record.assignedPersonnel || "Not assigned",
    technicianEmail: record.technicianEmail || "",
    assignedAt: record.assignedAt || "",
    assignmentAcknowledgedAt: record.assignmentAcknowledgedAt || "",
    assignmentNotes: record.assignmentNotes || "",
    serviceUnit: record.departmentName || "",
    workDescription: record.workDescription || "",
    status: record.status || "Created",
    targetCompletionDate: record.targetCompletionDate || "",
    completionDate: record.completedAt || "",
    completionSummary: record.completionSummary || "",
    createdAt: record.createdAt || "",
    updatedAt: record.updatedAt || "",
  };
}

async function loadAdministratorServiceRequestReviews() {
  try {
    const response = await fetch(
      `${API_ENDPOINTS.serviceRequestReviews}?limit=100`,
      {
        method: "GET",

        headers: {
          Accept: "application/json",
        },

        credentials: "same-origin",

        cache: "no-store",
      },
    );

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success) {
      throw new Error(
        data.message || "Unable to load the Administrator request queue.",
      );
    }

    state.adminReviewRequests = Array.isArray(data.reviewItems)
      ? data.reviewItems.map(mapAdministratorReviewFromApi)
      : [];

    renderAdminMetrics();
    renderAdminReviewTable();
    renderAdminAiSummary();

    return state.adminReviewRequests;
  } catch (error) {
    state.adminReviewRequests = [];

    renderAdminMetrics();
    renderAdminReviewTable();
    renderAdminAiSummary();

    console.error("Unable to load Administrator request queue:", error);

    throw error;
  }
}

async function loadApproverApprovals() {
  if (state.activeRole !== "approver") {
    state.approverApprovalVersion += 1;
    state.approvals = [];
    state.approvalHistory = [];
    return;
  }

  const account = state.currentAccount;
  const requestVersion = ++state.approverApprovalVersion;
  const current = () => state.activeRole === "approver" &&
    state.currentAccount === account && state.approverApprovalVersion === requestVersion;

  try {
    const statuses = ["Pending", "Approved", "Rejected"];
    const responses = await Promise.all(
      statuses.map((status) =>
        fetch(`${API_ENDPOINTS.approvals}?status=${status}`, {
          method: "GET",
          headers: { Accept: "application/json" },
          credentials: "same-origin",
          cache: "no-store",
        }),
      ),
    );
    const results = await Promise.all(
      responses.map((response) => readJsonResponse(response)),
    );

    results.forEach((data, index) => {
      if (!responses[index].ok || !data.success) {
        throw new Error(data.message || "Unable to load approval records.");
      }
    });

    if (!current()) return;
    state.approvals = results[0].approvals || [];
    state.approvalHistory = [
      ...(results[1].approvals || []),
      ...(results[2].approvals || []),
    ].sort(compareApprovalHistoryNewestFirst);

    renderApproverMetrics();
    renderApprovalTable();
    renderApprovalHistory();
  } catch (error) {
    if (!current()) return;
    state.approvals = [];
    state.approvalHistory = [];
    renderApproverMetrics();
    renderApprovalTable();
    renderApprovalHistory();
    console.error("Unable to load approval records:", error);
    throw error;
  }
}

async function loadAdministratorPersonnel() {
  try {
    const response = await fetch(API_ENDPOINTS.personnel, {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      credentials: "same-origin",
      cache: "no-store",
    });

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to load personnel records.");
    }

    state.personnel = Array.isArray(data.personnel)
      ? data.personnel.map(mapPersonnelFromApi)
      : [];

    renderPersonnelTable();
    populatePersonnelAccessManagement();

    return state.personnel;
  } catch (error) {
    state.personnel = [];

    renderPersonnelTable();

    console.error("Personnel retrieval failed:", error);

    throw error;
  }
}

async function loadWorkOrders() {
  if (state.activeRole !== "administrator") {
    state.workOrders = [];
    renderWorkOrderTable();
    return [];
  }

  try {
    const response = await fetch(API_ENDPOINTS.workOrders, {
      method: "GET",
      headers: { Accept: "application/json" },
      credentials: "same-origin",
      cache: "no-store",
    });
    const data = await readJsonResponse(response);
    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to load work orders.");
    }
    state.workOrders = Array.isArray(data.workOrders)
      ? data.workOrders.map(mapWorkOrderFromApi)
      : [];
    renderWorkOrderTable();
    renderAdminMetrics();
    return state.workOrders;
  } catch (error) {
    state.workOrders = [];
    renderWorkOrderTable();
    renderAdminMetrics();
    console.error("Work-order retrieval failed:", error);
    throw error;
  }
}

function mapTechnicianWorkOrderFromApi(record) {
  const databaseId = Number(record?.workOrderId);
  if (!Number.isSafeInteger(databaseId) || databaseId <= 0 ||
      !Array.isArray(record.progressHistory) ||
      record.progressHistory.some((entry) => !entry || !Number.isSafeInteger(Number(entry.progressId)))) {
    throw new Error("The server returned invalid technician work-order data.");
  }
  return {
    ...mapWorkOrderFromApi(record),
    databaseId,
    status: record.status || "",
    requestDescription: record.requestDescription || "",
    departmentName: record.departmentName || "",
    technicianName: record.technicianName || "",
    targetStartDate: record.targetStartDate || "",
    acknowledgedAt: record.acknowledgedAt || "",
    actualStartAt: record.actualStartAt || "",
    completedAt: record.completedAt || "",
    progressHistory: record.progressHistory,
  };
}

function technicianSession() {
  return { account: state.currentAccount, version: state.technicianSessionVersion };
}

function isTechnicianSessionCurrent(session) {
  return state.activeRole === "technician" &&
    state.currentAccount?.roleId === 4 &&
    session.account === state.currentAccount &&
    session.version === state.technicianSessionVersion;
}

function clearTechnicianSelection() {
  state.technicianDetailVersion += 1;
  state.workOrderTimelineVersion += 1;
  state.technicianWorkOrder = null;
  state.technicianWorkOrderId = null;
  state.technicianDetailLoading = false;
  if (byId("detailsModal")?.dataset.technicianWorkOrderId) {
    closeModal("detailsModal");
    byId("detailsModalContent").replaceChildren();
    byId("detailsModalTitle").textContent = "Details";
    delete byId("detailsModal").dataset.technicianWorkOrderId;
  }
  populateTechnicianUpdateForm(null);
}

function clearTechnicianWorkspace() {
  state.technicianSessionVersion += 1;
  state.technicianQueueVersion += 1;
  state.technicianWorkOrders = [];
  state.technicianHistory = [];
  state.technicianLoading = false;
  state.technicianSubmitting = false;
  state.technicianQueueError = "";
  clearTechnicianSelection();
  renderTechnicianWorkspace();
}

function renderTechnicianWorkspace() {
  const records = new Map(state.technicianWorkOrders.map((order) => [order.databaseId, order]));
  if (state.technicianWorkOrder) {
    records.set(state.technicianWorkOrder.databaseId, state.technicianWorkOrder);
  }
  const history = new Map();
  records.forEach((order) => {
    order.progressHistory.forEach((record) => {
      history.set(`${order.databaseId}:${record.progressId}`, {
        ...record, workOrderNumber: order.id, requestNumber: order.requestId,
        workOrderId: order.databaseId,
      });
    });
  });
  state.technicianHistory = [...history.values()].sort((left, right) =>
    (Date.parse(left.recordedAt) || 0) - (Date.parse(right.recordedAt) || 0) ||
    Number(left.progressId) - Number(right.progressId));
  renderTechnicianMetrics();
  renderTechnicianWorkTable();
  renderTechnicianHistory();
  renderTechnicianProgressActions();
}

async function readTechnicianResponse(response) {
  const data = await readJsonResponse(response);
  if (!response.ok || data?.success !== true) {
    const error = new Error(data?.message || "Unable to load or update technician work orders.");
    error.status = response.status;
    throw error;
  }
  return data;
}

function handleTechnicianRequestError(error, notify) {
  if (error.status === 401) {
    clearAuthenticatedWorkspace();
  } else if (error.status === 403 || error.status === 404) {
    const id = state.technicianWorkOrderId;
    state.technicianWorkOrders = state.technicianWorkOrders.filter((order) => order.databaseId !== id);
    clearTechnicianSelection();
    renderTechnicianWorkspace();
  }
  if (notify || error.status === 401) showToast(error.message || "Unable to connect. Refresh and try again.", "error");
}

async function loadTechnicianWorkOrders(notify = true) {
  const session = technicianSession();
  if (!isTechnicianSessionCurrent(session)) return false;
  const requestVersion = ++state.technicianQueueVersion;
  const detailVersion = state.technicianDetailVersion;
  const current = () => isTechnicianSessionCurrent(session) && requestVersion === state.technicianQueueVersion;
  state.technicianLoading = true;
  state.technicianQueueError = "";
  renderTechnicianWorkspace();
  try {
    const response = await fetch(API_ENDPOINTS.workOrderProgress, {
      method: "GET", headers: { Accept: "application/json" },
      credentials: "same-origin", cache: "no-store",
    });
    const data = await readTechnicianResponse(response);
    if (!current()) return false;
    if (!Array.isArray(data.workOrders)) throw new Error("The server returned an invalid technician queue.");
    state.technicianWorkOrders = data.workOrders.map(mapTechnicianWorkOrderFromApi);
    const selected = state.technicianWorkOrder;
    if (selected && detailVersion !== state.technicianDetailVersion) {
      state.technicianWorkOrders = state.technicianWorkOrders.map((order) =>
        order.databaseId === selected.databaseId ? selected : order);
    }
    return true;
  } catch (error) {
    if (!current()) return false;
    state.technicianWorkOrders = [];
    state.technicianQueueError = "Unable to load assignments. Select Refresh to try again.";
    handleTechnicianRequestError(error, notify);
    return false;
  } finally {
    if (current()) {
      state.technicianLoading = false;
      renderTechnicianWorkspace();
    }
  }
}

async function loadTechnicianWorkOrderDetails(workOrderId, notify = true) {
  const session = technicianSession();
  if (!isTechnicianSessionCurrent(session)) return null;
  const id = Number(workOrderId);
  clearTechnicianSelection();
  if (!Number.isSafeInteger(id) || id <= 0) {
    if (notify) showToast("Select a work order with a valid database identifier.", "error");
    renderTechnicianWorkspace();
    return null;
  }
  state.technicianWorkOrderId = id;
  state.technicianDetailLoading = true;
  const requestVersion = state.technicianDetailVersion;
  const current = () => isTechnicianSessionCurrent(session) &&
    requestVersion === state.technicianDetailVersion && state.technicianWorkOrderId === id;
  renderTechnicianProgressActions();
  renderTechnicianHistory();
  try {
    const response = await fetch(`${API_ENDPOINTS.workOrderProgress}?workOrderId=${id}`, {
      method: "GET", headers: { Accept: "application/json" },
      credentials: "same-origin", cache: "no-store",
    });
    const data = await readTechnicianResponse(response);
    if (!current()) return null;
    const order = mapTechnicianWorkOrderFromApi(data.workOrder);
    if (order.databaseId !== id) throw new Error("The server returned a different work order.");
    state.technicianWorkOrder = order;
    state.technicianWorkOrders = state.technicianWorkOrders.map((item) => item.databaseId === id ? order : item);
    populateTechnicianUpdateForm(order);
    return order;
  } catch (error) {
    if (!current()) return null;
    clearTechnicianSelection();
    if (error.status === 403 || error.status === 404) {
      state.technicianWorkOrders = state.technicianWorkOrders.filter((order) => order.databaseId !== id);
    }
    handleTechnicianRequestError(error, notify);
    renderTechnicianWorkspace();
    return null;
  } finally {
    if (current()) {
      state.technicianDetailLoading = false;
      renderTechnicianProgressActions();
      renderTechnicianWorkspace();
    }
  }
}

async function refreshTechnicianWorkspace() {
  const session = technicianSession();
  if (!isTechnicianSessionCurrent(session) || state.technicianSubmitting || state.technicianLoading) return;
  const id = state.technicianWorkOrderId;
  const detailVersion = state.technicianDetailVersion;
  await loadTechnicianWorkOrders();
  if (isTechnicianSessionCurrent(session) && !state.technicianSubmitting && id &&
      detailVersion === state.technicianDetailVersion && id === state.technicianWorkOrderId) {
    await loadTechnicianWorkOrderDetails(id);
  }
}

async function getCsrfToken() {
  const response = await fetch(API_ENDPOINTS.csrfToken, {
    method: "GET",
    headers: {
      Accept: "application/json",
    },
    credentials: "same-origin",
    cache: "no-store",
  });

  const data = await response.json();

  if (!response.ok || !data.success || !data.csrfToken) {
    const error = new Error(data.message || "Unable to obtain the security token.");
    error.status = response.status;
    throw error;
  }

  return {
    headerName: data.headerName || "X-CSRF-Token",

    token: data.csrfToken,
  };
}

async function loadServiceCategories() {
  const select = byId("requestCategory");

  if (!select) return;

  try {
    const response = await fetch(API_ENDPOINTS.serviceCategories, {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      credentials: "same-origin",
      cache: "no-store",
    });

    const data = await response.json();

    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to load service categories.");
    }

    select.replaceChildren();

    const placeholder = document.createElement("option");

    placeholder.value = "";
    placeholder.textContent = "Select category";

    select.appendChild(placeholder);

    const automaticOption = document.createElement("option");

    automaticOption.value = "AUTO";
    automaticOption.textContent = "Use system recommendation";

    select.appendChild(automaticOption);

    if (Array.isArray(data.categories)) {
      data.categories.forEach((category) => {
        const option = document.createElement("option");

        option.value = String(category.categoryId);

        option.textContent = category.categoryName;

        select.appendChild(option);
      });
    }
  } catch (error) {
    console.error("Unable to load service categories:", error);
  }
}

async function loadRequesterServiceRequests() {
  if (state.activeRole !== "requester") {
    state.serviceRequests = [];
    return;
  }

  try {
    const response = await fetch(`${API_ENDPOINTS.serviceRequests}?limit=100`, {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      credentials: "same-origin",
      cache: "no-store",
    });

    const data = await response.json();

    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to load service requests.");
    }

    state.serviceRequests = Array.isArray(data.requests)
      ? data.requests.map(mapServiceRequestFromApi)
      : [];
  } catch (error) {
    console.error("Unable to load service requests:", error);

    // Never restore demo/mock data.
    state.serviceRequests = [];
  }
}

async function loadRequesterServiceRequestDrafts() {
  if (state.activeRole !== "requester") {
    state.serviceRequestDrafts = [];
    return;
  }

  try {
    const response = await fetch(API_ENDPOINTS.serviceRequestDrafts, {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      credentials: "same-origin",
      cache: "no-store",
    });

    const data = await response.json();

    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to load service request drafts.");
    }

    state.serviceRequestDrafts = Array.isArray(data.drafts) ? data.drafts : [];
  } catch (error) {
    console.error("Unable to load service request drafts:", error);

    state.serviceRequestDrafts = [];
  }
}

/* =========================
   ROLE CONFIGURATION
========================= */
const roleConfiguration = {
  requester: {
    label: "Requester",
    defaultView: "requester-overview",

    navigation: [
      {
        view: "requester-overview",
        icon: "\u25A6",
        label: "Overview",
      },

      {
        view: "requester-new",
        icon: "+",
        label: "New Request",
      },

      {
        view: "requester-records",
        icon: "\u25A4",
        label: "My Requests",
      },
    ],
  },

  administrator: {
    label: "Service Administrator / Coordinator",
    defaultView: "administrator-overview",

    navigation: [
      {
        view: "administrator-overview",
        icon: "\u25A6",
        label: "Overview",
      },

      {
        view: "administrator-review",
        icon: "\u2713",
        label: "Request Queue",
      },

      {
        view: "administrator-work-orders",
        icon: "\u2692",
        label: "Work Orders",
      },

      {
        view: "administrator-access",
        icon: "\u25C6",
        label: "Personnel Access",
      },

      {
        view: "administrator-audit",
        icon: "\u25A4",
        label: "Audit Viewer",
      },
    ],
  },

  approver: {
    label: "Department Head / Authorized Approver",
    defaultView: "approver-overview",

    navigation: [
      {
        view: "approver-overview",
        icon: "\u25A6",
        label: "Overview",
      },

      {
        view: "approver-queue",
        icon: "\u2713",
        label: "Approval Queue",
      },

      {
        view: "approver-history",
        icon: "\u25A4",
        label: "Approval History",
      },
    ],
  },

  technician: {
    label: "Service Personnel / Technician",
    defaultView: "technician-overview",

    navigation: [
      {
        view: "technician-overview",
        icon: "\u25A6",
        label: "Overview",
      },

      {
        view: "technician-assigned",
        icon: "\u2692",
        label: "Assigned Work",
      },

      {
        view: "technician-update",
        icon: "\u21BB",
        label: "Update Progress",
      },

      {
        view: "technician-history",
        icon: "\u25A4",
        label: "Work History",
      },
    ],
  },
};

/* =========================
   GENERAL UTILITIES
========================= */
function installLogoFallback(image, label) {
  const replaceImage = () => {
    if (!image.isConnected) {
      return;
    }

    const fallback = document.createElement("span");

    fallback.className = "logo-fallback";

    fallback.setAttribute("aria-label", image.alt || label);

    fallback.textContent = label;

    image.replaceWith(fallback);
  };

  image.addEventListener("error", replaceImage, {
    once: true,
  });

  if (image.complete && image.naturalWidth === 0) {
    replaceImage();
  }
}

function initializeLogoFallbacks() {
  all('img[src$="wildcatslogo.png"]').forEach((img) => {
    installLogoFallback(img, "WC");
  });

  all('img[src$="htclogo.png"]').forEach((img) => {
    installLogoFallback(img, "HTC");
  });
}

function initials(name) {
  return (
    String(name)
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((word) => word.charAt(0).toUpperCase())
      .join("") || "GU"
  );
}

function todayInputValue() {
  const now = new Date();

  return [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, "0"),
    String(now.getDate()).padStart(2, "0"),
  ].join("-");
}

function formatDate(value) {
  if (!value) {
    return "\u2014";
  }

  const date = new Date(`${value}T00:00:00`);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(date);
}

function formatDateTime(value) {
  if (value == null || String(value).trim() === "") return "\u2014";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "\u2014" : date.toLocaleString();
}

function normalizeText(value) {
  return String(value || "")
    .trim()
    .toLowerCase();
}

function createElement(tag, className, text) {
  const element = document.createElement(tag);

  if (className) {
    element.className = className;
  }

  if (text !== undefined) {
    element.textContent = text;
  }

  return element;
}

function findRequest(requestId) {
  return (
    state.serviceRequests.find((request) => request.id === requestId) ||
    state.adminReviewRequests.find((request) => request.id === requestId)
  );
}

function findWorkOrder(workOrderId) {
  return state.workOrders.find((workOrder) => workOrder.id === workOrderId);
}

/* =========================
   TOAST NOTIFICATIONS
========================= */
function showToast(message, type = "success") {
  const region = byId("toastRegion");

  if (!region) {
    return;
  }

  const toast = createElement("div", `toast ${type}`);

  toast.setAttribute("role", type === "error" ? "alert" : "status");

  const iconMap = {
    success: "\u2713",
    warning: "!",
    error: "\u00D7",
    info: "i",
  };

  const icon = createElement("span", "toast-icon", iconMap[type] || "\u2713");

  const text = createElement("p", "", message);

  const close = createElement("button", "", "\u00D7");

  close.type = "button";

  close.setAttribute("aria-label", "Dismiss notification");

  on(close, "click", () => {
    toast.remove();
  });

  toast.append(icon, text, close);

  region.appendChild(toast);

  window.setTimeout(() => {
    toast.remove();
  }, 4600);
}

/* =========================
   BADGES
========================= */
function badgeClass(value) {
  const text = normalizeText(value);

  if (text.includes("urgent")) {
    return "badge-urgent";
  }

  if (text.includes("high")) {
    return "badge-high";
  }

  if (text.includes("medium")) {
    return "badge-medium";
  }

  if (text.includes("low")) {
    return "badge-low";
  }

  if (text.includes("progress")) {
    return "badge-progress";
  }

  if (text.includes("assigned")) {
    return "badge-assigned";
  }

  if (text.includes("hold")) {
    return "badge-hold";
  }

  if (
    text.includes("review") ||
    text.includes("awaiting") ||
    text.includes("pending")
  ) {
    return "badge-review";
  }

  if (text.includes("reject")) {
    return "badge-rejected";
  }

  if (text.includes("duplicate")) {
    return "badge-duplicate";
  }

  if (text.includes("inactive")) {
    return "badge-inactive";
  }

  if (text.includes("complete")) {
    return "badge-completed";
  }

  if (text.includes("approve")) {
    return "badge-approved";
  }

  if (text.includes("active")) {
    return "badge-active";
  }

  return "badge-pending";
}

function createBadge(value) {
  return createElement("span", `badge ${badgeClass(value)}`, value);
}

/* =========================
   MODALS
========================= */
function openModal(modalId, focusSelector) {
  const modal = byId(modalId);

  if (!modal) {
    return;
  }

  state.lastFocusedElement = document.activeElement;

  modal.hidden = false;

  document.body.classList.add("modal-open");

  window.setTimeout(() => {
    const target = focusSelector
      ? modal.querySelector(focusSelector)
      : modal.querySelector("input, select, textarea, button");

    target?.focus();
  }, 20);
}

function closeModal(modalId) {
  const modal = byId(modalId);

  if (!modal) {
    return;
  }

  modal.hidden = true;

  if (modalId === "detailsModal") {
    state.workOrderTimelineVersion += 1;
    state.requestHistoryVersion += 1;
    delete modal.dataset.workOrderTimelineId;
    delete modal.dataset.requestHistoryId;
  }

  if (modalId === "loginModal") {
    clearAuthMessage();
  }

  const anyOpenModal = all(".modal").some((item) => !item.hidden);

  if (!anyOpenModal) {
    document.body.classList.remove("modal-open");
  }

  if (
    state.lastFocusedElement instanceof HTMLElement &&
    state.lastFocusedElement.isConnected
  ) {
    state.lastFocusedElement.focus();
  }
}

function closeTopModal() {
  const openModals = all(".modal").filter((modal) => !modal.hidden);

  const topModal = openModals.at(-1);

  if (topModal) {
    closeModal(topModal.id);
  }
}

function openConfirm({
  title,
  message,
  confirmLabel = "Confirm",
  type = "primary",
  requireRemarks = false,
  onConfirm,
}) {
  byId("confirmTitle").textContent = title;
  byId("confirmMessage").textContent = message;
  byId("confirmActionButton").textContent = confirmLabel;
  byId("confirmActionButton").className =
    `button ${type === "danger" ? "button-danger" : "button-primary"}`;

  state.confirmAction = onConfirm;
  state.confirmRequiresRemarks = requireRemarks;

  const remarksGroup = byId("confirmRemarksGroup");
  const remarks = byId("confirmRemarks");
  const remarksError = byId("confirmRemarksError");
  remarksGroup.hidden = !requireRemarks;
  remarks.value = "";
  remarksError.textContent = "";
  remarks.removeAttribute("aria-invalid");

  openModal(
    "confirmModal",
    requireRemarks ? "#confirmRemarks" : "#confirmActionButton",
  );
}

/* =========================
   AI-ASSISTED REQUEST RULES
========================= */
function recommendCategory(textValue) {
  const text = normalizeText(textValue);

  if (
    /internet|wifi|wi-fi|network|router|connection|switch port|lan/.test(text)
  ) {
    return "Internet & Network";
  }

  if (/computer|laptop|printer|software|monitor|keyboard|mouse/.test(text)) {
    return "IT & Computer";
  }

  if (
    /light|outlet|power|electrical|electric|wiring|switch|spark|shock/.test(
      text,
    )
  ) {
    return "Electrical";
  }

  if (/projector|aircon|air conditioner|fan|equipment|machine/.test(text)) {
    return "Equipment";
  }

  if (/door|window|chair|desk|ceiling|classroom|building|room/.test(text)) {
    return "Facilities";
  }

  return "Maintenance";
}

function recommendPriority(textValue) {
  const text = normalizeText(textValue);

  if (
    /fire|smoke|spark|shock|danger|flood|emergency|exposed wire|injury|hazard/.test(
      text,
    )
  ) {
    return "Urgent";
  }

  if (
    /unavailable|several|multiple|entire|all users|cannot access|not working|no internet/.test(
      text,
    )
  ) {
    return "High";
  }

  if (/minor|loose|occasionally|intermittent|cosmetic/.test(text)) {
    return "Low";
  }

  return "Medium";
}

function importantWords(textValue) {
  const stopWords = new Set([
    "the",
    "and",
    "for",
    "with",
    "that",
    "this",
    "from",
    "room",
    "need",
    "needs",
    "not",
    "are",
    "is",
    "was",
    "were",
    "has",
    "have",
  ]);

  return normalizeText(textValue)
    .replace(/[^a-z0-9\s]/g, " ")
    .split(/\s+/)
    .filter((word) => word.length > 3 && !stopWords.has(word));
}

function findPossibleDuplicate(title, description) {
  const incomingWords = new Set(importantWords(`${title} ${description}`));

  let bestMatch = null;
  let bestScore = 0;

  state.serviceRequests.forEach((request) => {
    const existingWords = new Set(
      importantWords(`${request.title} ${request.description}`),
    );

    const score = [...incomingWords].filter((word) =>
      existingWords.has(word),
    ).length;

    if (score > bestScore) {
      bestScore = score;

      bestMatch = request;
    }
  });

  return bestScore >= 3 && bestMatch
    ? `Possible match: ${bestMatch.id}`
    : "No close match found";
}

/* =========================
   WORKSPACE NAVIGATION
========================= */
function viewTitle(viewId) {
  if (!state.activeRole || !roleConfiguration[state.activeRole]) {
    return "Workspace";
  }

  const item = roleConfiguration[state.activeRole].navigation.find(
    (entry) => entry.view === viewId,
  );

  return item ? item.label : "Workspace";
}

function buildWorkspaceNavigation(role) {
  const navigation = byId("workspaceNavigation");

  navigation.replaceChildren();

  roleConfiguration[role].navigation.forEach((item) => {
    const button = createElement("button", "workspace-nav-button");

    button.type = "button";

    button.dataset.workspaceView = item.view;

    button.append(
      createElement("span", "", item.icon),

      createElement("strong", "", item.label),
    );

    navigation.appendChild(button);
  });
}

function openWorkspaceView(viewId) {
  if (!state.activeRole) {
    return;
  }

  const activeInterface = document.querySelector(
    `.role-interface[data-role="${state.activeRole}"]`,
  );

  if (!activeInterface) {
    return;
  }

  const auditView = activeInterface.querySelector('[data-view="administrator-audit"]');
  if (auditView && !auditView.hidden && viewId !== "administrator-audit") {
    state.auditLoadVersion += 1;
    state.auditLoading = false;
  }

  all(".workspace-view", activeInterface).forEach((view) => {
    view.hidden = view.dataset.view !== viewId;
  });

  all(".workspace-nav-button", byId("workspaceNavigation")).forEach(
    (button) => {
      button.classList.toggle(
        "active",
        button.dataset.workspaceView === viewId,
      );
    },
  );

  const title = viewTitle(viewId);

  byId("workspacePageTitle").textContent = title;

  byId("workspaceBreadcrumb").textContent = `Service Portal / ${
    roleConfiguration[state.activeRole].label
  } / ${title}`;

  document.body.classList.remove("sidebar-open");

  if (viewId === "administrator-audit" && state.activeRole === "administrator") {
    void loadAuditEntries();
  }

  byId("sidebarToggle")?.setAttribute("aria-expanded", "false");

  if (window.matchMedia("(max-width: 980px)").matches) {
    byId("workspace")?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  }
}

function renderWorkspaceAvatar(name, profileImageFileName) {
  const avatar = byId("workspaceAvatar");

  if (!avatar) {
    return;
  }

  avatar.replaceChildren();
  avatar.classList.remove("has-photo");

  const removeButton = byId("profilePhotoRemoveButton");

  if (removeButton) {
    removeButton.hidden = !profileImageFileName;
  }

  if (!profileImageFileName) {
    avatar.textContent = initials(name);

    return;
  }

  const image = document.createElement("img");

  image.className = "workspace-avatar-image";

  image.alt = `${name || "User"} profile photo`;

  image.src = `${API_ENDPOINTS.profilePhoto}?v=${encodeURIComponent(profileImageFileName)}`;

  image.addEventListener(
    "error",
    () => {
      avatar.classList.remove("has-photo");

      avatar.replaceChildren();

      avatar.textContent = initials(name);
    },
    {
      once: true,
    },
  );

  avatar.classList.add("has-photo");

  avatar.appendChild(image);
}

function activateRoleWorkspace(role, name, email) {
  if (!roleConfiguration[role]) {
    showToast("This account role is not available.", "error");

    return;
  }

  clearTechnicianWorkspace();
  if (role === "technician" || state.activeRole === "technician") {
    closeModal("detailsModal");
    byId("detailsModalContent").replaceChildren();
    byId("detailsModalTitle").textContent = "Details";
  }
  state.activeRole = role;

  state.userName = name;

  state.userEmail = email;

  all(".role-interface").forEach((roleInterface) => {
    roleInterface.hidden = roleInterface.dataset.role !== role;
  });

  buildWorkspaceNavigation(role);

  byId("workspaceUserName").textContent = name;

  const workspaceEmail = byId("workspaceUserEmail");

  if (workspaceEmail) {
    workspaceEmail.textContent = email;

    workspaceEmail.title = email;

    workspaceEmail.setAttribute("aria-label", `Signed in email: ${email}`);
  }

  renderWorkspaceAvatar(name, state.currentAccount?.profileImageFileName);

  byId("workspaceRoleName").textContent = roleConfiguration[role].label;

  all("[data-user-name]").forEach((element) => {
    element.textContent = name;
  });

  if (role === "requester") {
    byId("requesterName").value = name;
    byId("requesterEmail").value = email;
    byId("requestDepartment").value = state.currentAccount?.department || "";
    byId("requesterTypeInput").value = state.currentAccount?.affiliation || "";
    byId("requesterName").readOnly = true;
    byId("requesterEmail").readOnly = true;
  }

  byId("publicContent").hidden = true;

  byId("workspace").hidden = false;

  byId("workspaceNavLink").hidden = false;

  byId("signInButton").textContent = "Switch Account";

  document.body.classList.add("workspace-active");

  renderAll();

  openWorkspaceView(roleConfiguration[role].defaultView);

  if (typeof fetchNotifications === "function") {
    fetchNotifications();
  }

  window.scrollTo({
    top: 0,
    behavior: "smooth",
  });
}

function clearAuthenticatedWorkspace() {
  clearTechnicianWorkspace();
  state.workOrderTimelineVersion += 1;
  state.approverApprovalVersion += 1;
  state.requestHistoryVersion += 1;
  state.auditLoadVersion += 1;
  state.activeRole = null;

  state.userName = "";

  state.userEmail = "";

  state.currentAccount = null;

  // Remove all authenticated/private records from browser memory.
  state.serviceRequests = [];
  state.workOrders = [];
  state.personnel = [];
  state.approvals = [];
  state.approvalHistory = [];
  state.technicianHistory = [];
  state.notifications = [];
  state.notificationUnreadCount = null;
  state.notificationsLoading = false;
  state.auditEntries = [];
  state.auditNextCursor = null;
  state.auditLoading = false;
  state.auditError = "";
  state.auditFilters = {};

  renderPublicMetrics();
  renderPublicActivity();

  all(".role-interface").forEach((roleInterface) => {
    roleInterface.hidden = true;
  });

  byId("workspace").hidden = true;

  byId("publicContent").hidden = false;

  byId("workspaceNavLink").hidden = true;

  byId("signInButton").textContent = "Sign In";

  byId("notificationPanel").hidden = true;

  document.body.classList.remove("workspace-active", "sidebar-open");

  window.scrollTo({
    top: 0,
    behavior: "smooth",
  });
}

async function signOut() {
  const button = byId("signOutButton");

  if (state.activeRole === "technician") clearTechnicianWorkspace();
  if (button) {
    button.disabled = true;
    button.setAttribute("aria-busy", "true");
  }

  try {
    const csrf = await getCsrfToken();

    const response = await fetch(API_ENDPOINTS.logout, {
      method: "POST",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        [csrf.headerName]: csrf.token,
      },
      cache: "no-store",
    });

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success) {
      throw new Error(data.message || "The server did not complete logout.");
    }

    clearAuthenticatedWorkspace();

    showToast("You have securely signed out.", "info");
  } catch (error) {
    console.error("Logout error:", error);
    showToast(
      "Unable to sign out from the server. Check the connection and try again.",
      "error",
    );
  } finally {
    if (button) {
      button.disabled = false;
      button.removeAttribute("aria-busy");
    }
  }
}

/* =========================
   RENDER: PUBLIC AREA
========================= */
function renderPublicMetrics() {
  const records = state.serviceRequests;

  const total = records.length;

  const pending = records.filter((request) =>
    [
      "Submitted",
      "Pending",
      "For Review",
      "Under Review",
      "Awaiting Approval",
    ].includes(request.status),
  ).length;

  const progress = records.filter((request) =>
    ["Assigned", "In Progress"].includes(request.status),
  ).length;

  const completed = records.filter((request) =>
    ["Completed", "Resolved", "Closed"].includes(request.status),
  ).length;

  byId("publicTotalMetric").textContent = String(total).padStart(2, "0");

  byId("publicPendingMetric").textContent = String(pending).padStart(2, "0");

  byId("publicProgressMetric").textContent = String(progress).padStart(2, "0");

  byId("publicCompletedMetric").textContent = String(completed).padStart(
    2,
    "0",
  );
}

function renderPublicActivity() {
  const container = byId("publicRecentActivity");

  if (!container) return;

  container.replaceChildren();

  if (state.serviceRequests.length === 0) {
    const emptyState = createElement(
      "div",
      "activity-empty",
      "No service requests yet.",
    );

    container.appendChild(emptyState);
    return;
  }

  state.serviceRequests.slice(0, 3).forEach((request) => {
    const row = createElement("div", "activity-row");

    const category = request.category || "Service";

    row.appendChild(
      createElement(
        "span",
        "activity-icon",
        category.slice(0, 2).toUpperCase(),
      ),
    );

    const text = createElement("div");

    text.append(
      createElement("strong", "", request.id || "Request"),
      createElement("p", "", request.title || "Service request"),
    );

    row.append(text, createBadge(request.status || "Submitted"));

    container.appendChild(row);
  });
}

/* =========================
   RENDER: REQUESTER
========================= */
function requesterRecords() {
  return state.serviceRequests;
}

function renderRequesterMetrics() {
  const records = requesterRecords();

  const pending = records.filter((request) =>
    ["Pending", "For Review", "Awaiting Approval"].includes(request.status),
  ).length;

  const progress = records.filter((request) =>
    ["Assigned", "In Progress"].includes(request.status),
  ).length;

  const completed = records.filter(
    (request) => request.status === "Completed",
  ).length;

  byId("requesterTotalMetric").textContent = String(records.length).padStart(
    2,
    "0",
  );

  byId("requesterPendingMetric").textContent = String(pending).padStart(2, "0");

  byId("requesterProgressMetric").textContent = String(progress).padStart(
    2,
    "0",
  );

  byId("requesterCompletedMetric").textContent = String(completed).padStart(
    2,
    "0",
  );
}

function requestMatchesFilter(request) {
  const query = normalizeText(byId("requestSearchInput")?.value);

  const status = byId("requestStatusFilter")?.value || "all";

  const searchable = normalizeText(
    `${request.id} ${request.title} ${request.category} ${request.location}`,
  );

  const matchesSearch = !query || searchable.includes(query);

  const matchesStatus = status === "all" || request.status === status;

  return matchesSearch && matchesStatus;
}

function makeRowAction(label, action, id, className = "") {
  const button = createElement(
    "button",
    `row-action ${className}`.trim(),
    label,
  );

  button.type = "button";

  button.dataset.action = action;

  button.dataset.id = id;

  return button;
}

async function deleteServiceRequestDraft(draftId) {
  const normalizedDraftId = Number(draftId);

  if (!Number.isInteger(normalizedDraftId) || normalizedDraftId <= 0) {
    showToast("The selected draft is invalid.", "error");

    return;
  }

  try {
    const csrf = await getCsrfToken();

    const response = await fetch(
      `${API_ENDPOINTS.serviceRequestDrafts}?id=${encodeURIComponent(normalizedDraftId)}`,
      {
        method: "DELETE",

        headers: {
          Accept: "application/json",

          [csrf.headerName]: csrf.token,
        },

        credentials: "same-origin",
      },
    );

    const data = await response.json();

    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to delete the draft.");
    }

    if (Number(state.currentDraftId) === normalizedDraftId) {
      resetServiceRequestDraftEditingState();
    }

    /*
     * Reload from the backend so MySQL remains
     * the authoritative source of draft state.
     */
    await loadRequesterServiceRequestDrafts();

    renderRequesterDrafts();

    showToast("Draft deleted successfully.", "success");
  } catch (error) {
    console.error("Draft delete error:", error);

    showToast(error.message || "Unable to delete the draft.", "error");
  }
}

function confirmDeleteServiceRequestDraft(draftId) {
  const normalizedDraftId = Number(draftId);

  const draft = state.serviceRequestDrafts.find(
    (item) => Number(item.draftId) === normalizedDraftId,
  );

  if (!draft) {
    showToast("The selected draft could not be found.", "error");

    return;
  }

  const draftName = draft.title || `Draft #${normalizedDraftId}`;

  openConfirm({
    title: "Delete this draft?",

    message: `${draftName} will be permanently removed from your saved drafts.`,

    confirmLabel: "Delete Draft",

    type: "danger",

    requireRemarks: false,

    onConfirm: () => deleteServiceRequestDraft(normalizedDraftId),
  });
}

function resumeServiceRequestDraft(draftId) {
  const draft = state.serviceRequestDrafts.find(
    (item) => Number(item.draftId) === Number(draftId),
  );

  if (!draft) {
    showToast("The selected draft could not be found.", "error");

    return;
  }

  const form = byId("serviceRequestForm");

  if (!form) {
    return;
  }

  /*
   * Reset first so stale values, validation messages,
   * and attachment selections are cleared.
   */
  form.reset();

  window.setTimeout(() => {
    byId("requestCategory").value =
      draft.requestedCategoryId != null
        ? String(draft.requestedCategoryId)
        : "AUTO";

    byId("requestPriority").value = draft.preferredPriority || "";

    byId("requestTitle").value = draft.title || "";

    byId("requestDescription").value = draft.description || "";

    byId("requestLocation").value = draft.location || "";

    byId("requestDate").value = draft.dateReported || "";

    byId("requestCharacterCount").textContent = String(
      (draft.description || "").length,
    );

    state.currentDraftId = Number(draft.draftId);

    const draftButton = byId("serviceRequestDraftButton");

    if (draftButton) {
      draftButton.textContent = "Update Draft";
    }

    openModal("serviceRequestModal", "#requestTitle");
  }, 0);
}

function renderRequesterDrafts() {
  const list = byId("requesterDraftList");

  const count = byId("requesterDraftCount");

  const emptyState = byId("requesterDraftEmptyState");

  if (!list || !count || !emptyState) {
    return;
  }

  list.replaceChildren();

  const drafts = Array.isArray(state.serviceRequestDrafts)
    ? state.serviceRequestDrafts
    : [];

  count.textContent = `${drafts.length} ${drafts.length === 1 ? "Draft" : "Drafts"}`;

  emptyState.hidden = drafts.length !== 0;

  drafts.forEach((draft) => {
    const item = document.createElement("div");

    item.className = "compact-list-item";

    const content = document.createElement("div");

    const title = document.createElement("strong");

    title.textContent = draft.title || "Untitled service request";

    const details = document.createElement("small");

    const updatedDate = draft.updatedAt ? new Date(draft.updatedAt) : null;

    const updated =
      updatedDate && !Number.isNaN(updatedDate.getTime())
        ? updatedDate.toLocaleString("en-PH", {
            month: "short",
            day: "numeric",
            year: "numeric",
            hour: "numeric",
            minute: "2-digit",
            timeZone: "Asia/Manila",
          })
        : "Not available";

    details.textContent = `Draft #${draft.draftId} | Updated ${updated}`;

    content.append(title, details);

    const actions = document.createElement("div");

    actions.className = "record-actions";

    const resumeButton = document.createElement("button");

    resumeButton.type = "button";

    resumeButton.className = "button button-small button-secondary";

    resumeButton.textContent = "Resume";

    resumeButton.dataset.resumeDraft = String(draft.draftId);

    actions.appendChild(resumeButton);

    const deleteButton = document.createElement("button");

    deleteButton.type = "button";

    deleteButton.className = "button button-small button-danger";

    deleteButton.textContent = "Delete";

    deleteButton.dataset.deleteDraft = String(draft.draftId);

    actions.appendChild(deleteButton);

    item.append(content, actions);

    list.appendChild(item);
  });
}

function renderRequestTable() {
  const body = byId("serviceRequestTableBody");

  if (!body) {
    return;
  }

  body.replaceChildren();

  const records = requesterRecords().filter(requestMatchesFilter);

  records.forEach((request) => {
    const row = document.createElement("tr");

    const idCell = document.createElement("td");

    idCell.appendChild(createElement("strong", "", request.id));

    const priorityCell = document.createElement("td");

    priorityCell.appendChild(createBadge(request.priority));

    const statusCell = document.createElement("td");

    statusCell.appendChild(createBadge(request.status));

    const actionCell = document.createElement("td");

    const actions = createElement("div", "row-actions");

    actions.append(
      makeRowAction("View", "view-request", request.id),

      makeRowAction("Track", "track-request", request.id, "primary"),
    );

    actionCell.appendChild(actions);

    [
      idCell,

      createElement("td", "", request.title),

      createElement("td", "", request.category),

      createElement("td", "", request.location),

      priorityCell,
      statusCell,

      createElement("td", "", formatDate(request.dateReported)),

      actionCell,
    ].forEach((cell) => row.appendChild(cell));

    body.appendChild(row);
  });

  byId("requestRecordCount").textContent = `${records.length} ${
    records.length === 1 ? "Record" : "Records"
  }`;

  byId("requestEmptyState").hidden = records.length !== 0;
}

function renderRequesterRecentList() {
  const container = byId("requesterRecentList");

  if (!container) {
    return;
  }

  container.replaceChildren();

  const records = requesterRecords().slice(0, 3);

  if (records.length === 0) {
    const emptyState = createElement("div", "requester-empty-state");

    const emptyIcon = createElement("div", "requester-empty-icon", "SR");

    const emptyCopy = createElement("div", "requester-empty-copy");

    emptyCopy.append(
      createElement("strong", "", "No service requests yet"),
      createElement(
        "span",
        "",
        "Submit your first service request when you need campus assistance.",
      ),
    );

    const submitButton = createElement(
      "button",
      "button button-primary button-small",
      "Submit Your First Request",
    );

    submitButton.type = "button";

    submitButton.setAttribute("data-open-request-modal", "");

    emptyState.append(emptyIcon, emptyCopy, submitButton);

    container.appendChild(emptyState);

    return;
  }

  records.forEach((request) => {
    const item = createElement("div", "compact-item");

    const copy = createElement("div");

    copy.append(
      createElement("strong", "", request.title),

      createElement("span", "", `${request.id} \u2022 ${request.category}`),
    );

    item.append(copy, createBadge(request.status));

    container.appendChild(item);
  });
}

function showLatestAiRecommendation(request) {
  byId("latestAiCard").hidden = false;

  byId("latestAiCategory").textContent = request.aiCategory;

  byId("latestAiPriority").textContent = request.aiPriority;

  byId("latestAiDuplicate").textContent = request.duplicateCheck;
}

/* =========================
   RENDER: ADMINISTRATOR
========================= */
function isAuditSessionCurrent(session, version) {
  return state.activeRole === "administrator" &&
    state.currentAccount?.roleId === 2 &&
    session.account === state.currentAccount &&
    session.version === version;
}

function auditDateTimeToInstant(value, fieldName) {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new Error(`${fieldName} must be a valid date and time.`);
  }
  return date.toISOString();
}

function readAuditFilters() {
  const actionType = byId("auditActionType")?.value.trim() || "";
  const outcome = byId("auditOutcome")?.value || "";
  const requestId = byId("auditRequestId")?.value.trim() || "";
  const workOrderId = byId("auditWorkOrderId")?.value.trim() || "";
  const from = auditDateTimeToInstant(byId("auditFrom")?.value, "From");
  const to = auditDateTimeToInstant(byId("auditTo")?.value, "To");
  if (from && to && new Date(from) > new Date(to)) {
    throw new Error("From must not be after To.");
  }
  return { actionType, outcome, requestId, workOrderId, from, to };
}

function auditQuery(filters, cursor) {
  const query = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value) query.set(key, value);
  });
  if (cursor) query.set("cursor", cursor);
  return query.toString();
}

function formatAuditActor(entry) {
  if (entry.actorName) return entry.actorRole ? `${entry.actorName} (${entry.actorRole})` : entry.actorName;
  return entry.actorId ? `Personnel #${entry.actorId}` : "—";
}

function formatAuditEntity(entry) {
  const type = entry.entityType || "Not specified";
  return entry.entityId ? `${type} #${entry.entityId}` : type;
}

function formatAuditAction(actionType) {
  if (!actionType) return "Not specified";
  return String(actionType).toLowerCase().split("_").filter(Boolean)
    .map((word) => `${word.charAt(0).toUpperCase()}${word.slice(1)}`).join(" ") || "Not specified";
}

function createAuditReferences(entry) {
  const references = createElement("div", "audit-references");
  const request = entry.requestNumber || (entry.requestId ? `Request #${entry.requestId}` : "");
  const workOrder = entry.workOrderNumber || (entry.workOrderId ? `Work Order #${entry.workOrderId}` : "");
  if (request) references.appendChild(createElement("span", "", request));
  if (workOrder) references.appendChild(createElement("span", "", workOrder));
  if (!request && !workOrder) references.appendChild(createElement("span", "", "—"));
  return references;
}

function renderAuditViewer() {
  const body = byId("auditTableBody");
  const empty = byId("auditEmptyState");
  const count = byId("auditRecordCount");
  const loadOlder = byId("auditLoadOlderButton");
  if (!body || !empty || !count || !loadOlder) return;

  body.replaceChildren();
  state.auditEntries.forEach((entry) => {
    const row = document.createElement("tr");
    const outcome = document.createElement("td");
    outcome.appendChild(createBadge(entry.outcome || "Unknown"));
    const dateCell = createElement("td", "audit-timestamp", formatDateTime(entry.createdAt));
    const actionCell = createElement("td", "audit-action", formatAuditAction(entry.actionType));
    const actorCell = createElement("td", "audit-actor", formatAuditActor(entry));
    const entityCell = createElement("td", "audit-entity", formatAuditEntity(entry));
    const referencesCell = createElement("td", "audit-reference-cell");
    referencesCell.appendChild(createAuditReferences(entry));
    const summaryCell = createElement("td", "audit-summary", entry.summary || "No summary");
    [
      dateCell,
      actionCell,
      outcome,
      actorCell,
      entityCell,
      referencesCell,
      summaryCell,
    ].forEach((cell) => row.appendChild(cell));
    body.appendChild(row);
  });

  count.textContent = `${state.auditEntries.length} ${state.auditEntries.length === 1 ? "Record" : "Records"}`;
  empty.hidden = state.auditLoading || Boolean(state.auditError) || state.auditEntries.length > 0;
  loadOlder.hidden = !state.auditNextCursor;
  loadOlder.disabled = state.auditLoading || !state.auditNextCursor;
  loadOlder.textContent = state.auditLoading && state.auditNextCursor ? "Loading…" : "Load Older";
}

async function loadAuditEntries(append = false) {
  if (state.activeRole !== "administrator" || !state.currentAccount) return;
  if (state.auditLoading && append) return;
  if (state.auditLoading) state.auditLoadVersion += 1;
  const cursor = append ? state.auditNextCursor : null;
  if (append && !cursor) return;
  const requestVersion = ++state.auditLoadVersion;
  const session = { account: state.currentAccount, version: requestVersion };
  state.auditLoading = true;
  state.auditError = "";
  if (!append) {
    state.auditEntries = [];
    state.auditNextCursor = null;
  }
  renderAuditViewer();

  try {
    const query = auditQuery(state.auditFilters, cursor);
    const response = await fetch(`${API_ENDPOINTS.auditLogs}${query ? `?${query}` : ""}`, {
      method: "GET",
      headers: { Accept: "application/json" },
      credentials: "same-origin",
      cache: "no-store",
    });
    const data = await readJsonResponse(response);
    if (!response.ok || !data?.success) throw new Error(data?.message || "Unable to load audit records.");
    if (!isAuditSessionCurrent(session, requestVersion)) return;
    const entries = Array.isArray(data.entries) ? data.entries : [];
    if (append) {
      const knownIds = new Set(state.auditEntries.map((entry) => entry.auditId));
      state.auditEntries.push(...entries.filter((entry) => !knownIds.has(entry.auditId)));
    } else {
      state.auditEntries = entries;
    }
    state.auditNextCursor = typeof data.nextCursor === "string" && data.nextCursor ? data.nextCursor : null;
  } catch (error) {
    if (!isAuditSessionCurrent(session, requestVersion)) return;
    state.auditError = error.message || "Unable to load audit records.";
    state.auditNextCursor = null;
    showToast(state.auditError, "error");
  } finally {
    if (isAuditSessionCurrent(session, requestVersion)) {
      state.auditLoading = false;
      renderAuditViewer();
    }
  }
}

function adminReviewRecords() {
  return state.adminReviewRequests;
}

function renderAdminMetrics() {
  const queue = adminReviewRecords();

  const awaiting = state.serviceRequests.filter(
    (request) => request.status === "Awaiting Approval",
  ).length;

  const high = state.serviceRequests.filter(
    (request) =>
      ["High", "Urgent"].includes(request.priority) &&
      request.status !== "Completed",
  ).length;

  byId("adminQueueMetric").textContent = String(queue.length).padStart(2, "0");

  byId("adminApprovalMetric").textContent = String(awaiting).padStart(2, "0");

  byId("adminHighMetric").textContent = String(high).padStart(2, "0");

  byId("adminWorkOrderMetric").textContent = String(
    state.workOrders.length,
  ).padStart(2, "0");
}

function renderAdminReviewTable() {
  const body = byId("adminReviewTableBody");

  if (!body) {
    return;
  }

  body.replaceChildren();

  const query = normalizeText(byId("adminRequestSearch")?.value);

  const records = adminReviewRecords().filter((request) => {
    if (!query) {
      return true;
    }

    return normalizeText(
      `${request.id} ${request.title} ${request.aiCategory} ${request.aiPriority}`,
    ).includes(query);
  });

  records.forEach((request) => {
    const row = document.createElement("tr");

    const idCell = document.createElement("td");

    idCell.appendChild(createElement("strong", "", request.id));

    const priorityCell = document.createElement("td");

    priorityCell.appendChild(createBadge(request.aiPriority));

    const statusCell = document.createElement("td");

    statusCell.appendChild(createBadge(request.status));

    const actionCell = document.createElement("td");

    const actions = createElement("div", "row-actions");

    actions.append(
      makeRowAction("View", "view-admin-request", request.id),

      makeRowAction(
        "Run Advisory Analysis",
        "analyze-request",
        request.id,
        "secondary",
      ),

      makeRowAction("Forward", "forward-request", request.id, "primary"),

      makeRowAction(
        "Flag Duplicate",
        "duplicate-request",
        request.id,
        "danger",
      ),
    );

    actionCell.appendChild(actions);

    [
      idCell,

      createElement("td", "", request.title),

      createElement("td", "", request.aiCategory),

      priorityCell,

      createElement("td", "", request.duplicateCheck),

      statusCell,
      actionCell,
    ].forEach((cell) => row.appendChild(cell));

    body.appendChild(row);
  });

  byId("adminReviewCount").textContent = `${records.length} ${
    records.length === 1 ? "Record" : "Records"
  }`;
}

function renderAdminAiSummary() {
  const container = byId("adminAiSummary");

  if (!container) {
    return;
  }

  container.replaceChildren();

  const queue = adminReviewRecords().slice(0, 3);

  if (queue.length === 0) {
    container.appendChild(
      createElement(
        "div",
        "notification-empty",
        "No requests currently need AI recommendation review.",
      ),
    );

    return;
  }

  queue.forEach((request) => {
    const item = createElement("div", "compact-item");

    const copy = createElement("div");

    copy.append(
      createElement("strong", "", request.id),

      createElement(
        "span",
        "",
        `${request.aiCategory} \u2022 ${request.duplicateCheck}`,
      ),
    );

    item.append(copy, createBadge(request.aiPriority));

    container.appendChild(item);
  });
}

function renderWorkOrderTable() {
  const body = byId("workOrderTableBody");

  if (!body) {
    return;
  }

  body.replaceChildren();

  state.workOrders.forEach((workOrder) => {
    const row = document.createElement("tr");

    const idCell = document.createElement("td");

    idCell.appendChild(createElement("strong", "", workOrder.id));

    const statusCell = document.createElement("td");

    statusCell.appendChild(createBadge(workOrder.status));

    const actionCell = document.createElement("td");

    const actions = createElement("div", "row-actions");

    actions.append(makeRowAction("View", "view-work-order", workOrder.id));

    if (workOrder.status === "Created" && !workOrder.assignmentId) {
      actions.append(
        makeRowAction("Assign", "assign-work-order", workOrder.id, "primary"),
      );
    }

    actionCell.appendChild(actions);

    [
      idCell,

      createElement("td", "", workOrder.requestId),

      createElement("td", "", workOrder.assignedPersonnel),

      createElement("td", "", workOrder.serviceUnit),

      statusCell,

      createElement("td", "", formatDate(workOrder.completionDate)),

      actionCell,
    ].forEach((cell) => row.appendChild(cell));

    body.appendChild(row);
  });

  byId("workOrderRecordCount").textContent = `${state.workOrders.length} ${
    state.workOrders.length === 1 ? "Record" : "Records"
  }`;
}

function renderPersonnelTable() {
  const body = byId("personnelTableBody");

  if (!body) {
    return;
  }

  body.replaceChildren();

  state.personnel.forEach((person) => {
    const row = document.createElement("tr");

    const idCell = document.createElement("td");

    idCell.appendChild(createElement("strong", "", person.id));

    const statusCell = document.createElement("td");

    statusCell.appendChild(createBadge(person.status));

    [
      idCell,

      createElement("td", "", person.name),

      createElement("td", "", person.email),

      createElement("td", "", person.department),

      createElement("td", "", person.role),

      statusCell,
    ].forEach((cell) => row.appendChild(cell));

    body.appendChild(row);
  });

  byId("personnelRecordCount").textContent = `${state.personnel.length} ${
    state.personnel.length === 1 ? "Record" : "Records"
  }`;
}

/* =========================
   RENDER: APPROVER
========================= */
function pendingApprovals() {
  return state.approvals.filter((approval) => approval.decision === "Pending");
}

function renderApproverMetrics() {
  const pending = pendingApprovals().length;

  const approved = state.approvalHistory.filter(
    (item) => item.decision === "Approved",
  ).length;

  const rejected = state.approvalHistory.filter(
    (item) => item.decision === "Rejected",
  ).length;

  byId("approverPendingMetric").textContent = String(pending).padStart(2, "0");

  byId("approverApprovedMetric").textContent = String(approved).padStart(
    2,
    "0",
  );

  byId("approverRejectedMetric").textContent = String(rejected).padStart(
    2,
    "0",
  );
}

function renderApprovalTable() {
  const body = byId("approvalTableBody");

  if (!body) {
    return;
  }

  body.replaceChildren();

  pendingApprovals().forEach((approval) => {
    const request = findRequest(approval.requestId) || {
      databaseId: approval.requestId,
      id: approval.requestNumber || `Request #${approval.requestId}`,
      title: approval.title || "Service request",
      priority: approval.priority || "",
      aiCategory: approval.category || "",
      aiPriority: "",
      status: approval.currentStatus || "Awaiting Approval",
    };

    if (!request) {
      return;
    }

    const row = document.createElement("tr");

    const idCell = document.createElement("td");

    idCell.appendChild(createElement("strong", "", request.id));

    const priorityCell = document.createElement("td");

    priorityCell.appendChild(createBadge(request.priority));

    const statusCell = document.createElement("td");

    statusCell.appendChild(createBadge("Awaiting Approval"));

    const actionCell = document.createElement("td");

    const actions = createElement("div", "row-actions");

    actions.append(
      makeRowAction("View", "view-approval", request.id),

      makeRowAction("Approve", "approve-request", request.id, "primary"),

      makeRowAction("Reject", "reject-request", request.id, "danger"),
    );

    actionCell.appendChild(actions);

    [
      idCell,

      createElement("td", "", request.title),

      createElement("td", "", request.aiCategory),

      priorityCell,

      createElement("td", "", `${request.aiCategory} / ${request.aiPriority}`),

      statusCell,
      actionCell,
    ].forEach((cell) => row.appendChild(cell));

    body.appendChild(row);
  });

  const count = pendingApprovals().length;

  byId("approvalRecordCount").textContent = `${count} ${
    count === 1 ? "Record" : "Records"
  }`;
}

function renderApprovalHistory() {
  const body = byId("approvalHistoryTableBody");

  if (!body) {
    return;
  }

  body.replaceChildren();

  state.approvalHistory.forEach((record) => {
    const row = document.createElement("tr");

    const idCell = document.createElement("td");

    idCell.appendChild(createElement("strong", "", record.requestId));

    const decisionCell = document.createElement("td");

    decisionCell.appendChild(createBadge(record.decision));

    [
      idCell,
      decisionCell,

      createElement("td", "", formatDateTime(record.decisionDate)),

      createElement("td", "", record.remarks || "\u2014"),
    ].forEach((cell) => row.appendChild(cell));

    body.appendChild(row);
  });

  if (!state.approvalHistory.length) {
    const row = createElement("tr");
    const cell = createElement("td", "notification-empty", "No approval decisions have been recorded.");
    cell.colSpan = 4;
    row.appendChild(cell);
    body.appendChild(row);
  }

  byId("approvalHistoryCount").textContent = `${state.approvalHistory.length} ${
    state.approvalHistory.length === 1 ? "Record" : "Records"
  }`;
}

/* =========================
   RENDER: TECHNICIAN
========================= */
function renderTechnicianMetrics() {
  const assigned = state.technicianWorkOrders.filter(
    (workOrder) => ["Assigned", "Acknowledged"].includes(workOrder.status),
  ).length;

  const progress = state.technicianWorkOrders.filter(
    (workOrder) => workOrder.status === "In Progress",
  ).length;

  const hold = state.technicianWorkOrders.filter(
    (workOrder) => workOrder.status === "On Hold",
  ).length;

  const completed = state.technicianWorkOrders.filter(
    (workOrder) => workOrder.status === "Completed",
  ).length;

  byId("technicianAssignedMetric").textContent = String(assigned).padStart(
    2,
    "0",
  );

  byId("technicianProgressMetric").textContent = String(progress).padStart(
    2,
    "0",
  );

  byId("technicianHoldMetric").textContent = String(hold).padStart(2, "0");

  byId("technicianCompletedMetric").textContent = String(completed).padStart(
    2,
    "0",
  );
}

function renderTechnicianWorkTable() {
  const body = byId("technicianWorkTableBody");

  if (!body) {
    return;
  }

  body.replaceChildren();

  state.technicianWorkOrders.forEach((workOrder) => {
    const row = document.createElement("tr");

    const idCell = document.createElement("td");

    idCell.appendChild(createElement("strong", "", workOrder.id));

    const statusCell = document.createElement("td");

    statusCell.appendChild(createBadge(workOrder.status));

    const actionCell = document.createElement("td");

    const actions = createElement("div", "row-actions");

    actions.append(makeRowAction("View", "view-tech-work", workOrder.databaseId));
    if (technicianActionsForStatus(workOrder.status).length) {
      actions.append(makeRowAction("Update", "update-tech-work", workOrder.databaseId, "primary"));
    }
    all("button", actions).forEach((button) => { button.disabled = state.technicianSubmitting; });

    actionCell.appendChild(actions);

    [
      idCell,

      createElement("td", "", workOrder.requestId),

      createElement("td", "", workOrder.workDescription),

      createElement("td", "", workOrder.serviceUnit),

      createElement("td", "", workOrder.assignedPersonnel),

      statusCell,
      actionCell,
    ].forEach((cell) => row.appendChild(cell));

    body.appendChild(row);
  });

  if (!state.technicianWorkOrders.length) {
    const row = createElement("tr");
    const cell = createElement("td", "notification-empty", state.technicianLoading
      ? "Loading assigned work orders..."
      : state.technicianQueueError || "No work orders are currently assigned to you.");
    cell.colSpan = 7;
    row.appendChild(cell);
    body.appendChild(row);
  }
  byId("technicianRefreshButton").disabled = state.technicianLoading || state.technicianSubmitting;
  byId("technicianWorkCount").textContent = `${state.technicianWorkOrders.length} ${
    state.technicianWorkOrders.length === 1 ? "Record" : "Records"
  }`;
}

function renderTechnicianHistory() {
  const body = byId("technicianHistoryTableBody");

  const compact = byId("technicianHistoryList");

  if (!body || !compact) {
    return;
  }

  body.replaceChildren();
  compact.replaceChildren();

  state.technicianHistory.forEach((record) => {
    const row = document.createElement("tr");

    const idCell = document.createElement("td");

    idCell.appendChild(createElement("strong", "", record.workOrderNumber));

    const statusCell = document.createElement("td");

    statusCell.appendChild(createElement("span", "", `${record.previousStatus || "\u2014"} → ${record.newStatus || "\u2014"}`));

    [
      idCell,

      createElement("td", "", record.requestNumber),

      createElement("td", "", record.updateType),

      statusCell,

      createElement("td", "", record.progressPercentage == null ? "\u2014" : `${record.progressPercentage}%`),

      createElement("td", "", record.progressNotes),

      createElement("td", "", formatDateTime(record.recordedAt)),
    ].forEach((cell) => row.appendChild(cell));

    body.appendChild(row);
  });

  const selectedHistory = state.technicianWorkOrderId
    ? state.technicianHistory.filter((record) => record.workOrderId === state.technicianWorkOrderId)
    : state.technicianHistory;
  selectedHistory.slice(-4).forEach((record) => {
    const item = createElement("div", "compact-item");

    const copy = createElement("div");

    copy.append(
      createElement("strong", "", `${record.workOrderNumber} · ${record.updateType}`),

      createElement("span", "", record.progressNotes),
      createElement("small", "", formatDateTime(record.recordedAt)),
    );

    item.append(copy, createBadge(record.newStatus));

    compact.appendChild(item);
  });

  if (selectedHistory.length === 0) {
    compact.appendChild(
      createElement(
        "div",
        "notification-empty",
        "No technician updates recorded yet.",
      ),
    );
  }

  if (state.technicianHistory.length === 0) {
    const row = createElement("tr");
    const cell = createElement("td", "notification-empty", "No technician updates recorded yet.");
    cell.colSpan = 7;
    row.appendChild(cell);
    body.appendChild(row);
  }

  byId("technicianHistoryCount").textContent =
    `${state.technicianHistory.length} ${
      state.technicianHistory.length === 1 ? "Record" : "Records"
    }`;
}

/* =========================
   RENDER: NOTIFICATIONS
========================= */
function roleNotifications() {
  if (!state.activeRole) {
    return [];
  }

  return state.notifications.filter(
    (notification) =>
      notification.targetRole === state.activeRole ||
      notification.targetRole === "all",
  );
}

function renderNotifications() {
  const list = byId("notificationList");

  const count = byId("notificationCount");

  const button = byId("notificationButton");

  if (!list || !count) {
    return;
  }

  const notifications = roleNotifications();

  const unreadCount = notifications.length;

  count.textContent = String(unreadCount);

  count.hidden = unreadCount === 0;

  if (button) {
    button.setAttribute(
      "aria-label",
      unreadCount > 0
        ? `Show notifications. ${unreadCount} unread.`
        : "Show notifications",
    );
  }

  list.replaceChildren();

  if (notifications.length === 0) {
    const empty = createElement("div", "notification-empty");

    empty.append(
      createElement("strong", "", "No notifications"),
      createElement(
        "span",
        "",
        "Account updates will appear here when available.",
      ),
    );

    list.appendChild(empty);

    return;
  }

  notifications.slice(0, 8).forEach((notification) => {
    const item = createElement("article", "notification-item");

    item.append(
      createElement("strong", "", notification.title),

      createElement("p", "", notification.message),

      createElement("small", "", formatDate(notification.date)),
    );

    list.appendChild(item);
  });
}

/* =========================
   MASTER RENDER
========================= */
function renderAll() {
  renderPublicMetrics();
  renderPublicActivity();

  renderRequesterMetrics();
  renderRequesterDrafts();
  renderRequestTable();
  renderRequesterRecentList();

  renderAdminMetrics();
  renderAdminReviewTable();
  renderAdminAiSummary();
  renderWorkOrderTable();
  renderPersonnelTable();

  renderApproverMetrics();
  renderApprovalTable();
  renderApprovalHistory();

  renderTechnicianWorkspace();

  renderNotifications();
}

/* =========================
   GENERIC DETAILS RENDERER
========================= */
function showDetails(recordId, recordLabel, fields) {
  const label = byId("detailsModalLabel");

  const title = byId("detailsModalTitle");

  const content = byId("detailsModalContent");

  if (!label || !title || !content) {
    console.error("Details modal elements are missing.");

    return;
  }

  label.textContent = recordLabel || "Record Details";

  title.textContent = recordId || "Details";

  content.replaceChildren();
  delete byId("detailsModal")?.dataset.workOrderTimelineId;
  state.requestHistoryVersion += 1;
  delete byId("detailsModal")?.dataset.requestHistoryId;

  const safeFields = Array.isArray(fields) ? fields : [];

  safeFields.forEach((field) => {
    const item = document.createElement("div");

    item.className = "detail-item";

    if (field?.full) {
      item.classList.add("full");
    }

    const fieldLabel = document.createElement("small");

    fieldLabel.textContent = field?.label || "Detail";

    item.appendChild(fieldLabel);

    const rawValue = field?.value;

    const displayValue =
      rawValue == null || String(rawValue).trim() === ""
        ? "Not available"
        : String(rawValue);

    if (field?.badge) {
      item.appendChild(createBadge(displayValue));
    } else {
      const valueElement = document.createElement("strong");

      valueElement.textContent = displayValue;

      item.appendChild(valueElement);
    }

    /*
     * linkUrl is only supplied by trusted
     * application code below. User-controlled
     * filenames are always rendered with textContent.
     */
    if (field?.linkUrl) {
      const link = document.createElement("a");

      link.className = "button button-secondary";

      link.href = field.linkUrl;

      link.textContent = field.linkLabel || "Open";

      link.style.marginTop = "10px";

      item.appendChild(link);
    }

    content.appendChild(item);
  });

  openModal("detailsModal", "button.modal-close");
}

function appendTimelineSection(title, events, emptyMessage) {
  const section = createElement("div", "detail-item full");
  section.appendChild(createElement("h3", "", title));
  const timelineEvents = Array.isArray(events) ? events : [];
  if (!timelineEvents.length) {
    section.appendChild(createElement("p", "notification-empty", emptyMessage));
    return section;
  }

  timelineEvents.forEach((event) => {
    const item = createElement("div", "compact-item");
    const copy = createElement("div");
    const titleText = event?.title || event?.eventType || "Timeline event";
    const previousStatus = event?.previousStatus || "—";
    const newStatus = event?.newStatus || "—";
    const percentage = Number(event?.percentage);
    const details = [
      `${previousStatus} → ${newStatus}`,
      Number.isFinite(percentage) ? `${percentage}%` : "",
      event?.remarks || "",
    ].filter(Boolean).join(" · ");
    copy.append(
      createElement("strong", "", titleText),
      createElement("span", "", details),
      createElement("small", "", formatDateTime(event?.occurredAt)),
    );
    item.appendChild(copy);
    section.appendChild(item);
  });
  return section;
}

async function loadAndAppendWorkOrderTimeline(workOrderId, role) {
  const id = Number(workOrderId);
  const modal = byId("detailsModal");
  if (!Number.isSafeInteger(id) || id <= 0 || !modal) return;

  const account = state.currentAccount;
  const requestVersion = ++state.workOrderTimelineVersion;
  modal.dataset.workOrderTimelineId = String(id);
  const current = () => state.currentAccount === account && state.activeRole === role &&
    state.workOrderTimelineVersion === requestVersion &&
    modal.dataset.workOrderTimelineId === String(id) && !modal.hidden &&
    (role !== "technician" || state.technicianWorkOrderId === id);

  try {
    const response = await fetch(`${API_ENDPOINTS.workOrderHistory}?workOrderId=${encodeURIComponent(id)}`, {
      method: "GET",
      headers: { Accept: "application/json" },
      credentials: "same-origin",
      cache: "no-store",
    });
    const data = await readJsonResponse(response);
    if (!response.ok || data?.success !== true) {
      const error = new Error(data?.message || "Unable to load work-order history.");
      error.status = response.status;
      throw error;
    }
    if (!current()) return;
    if (Number(data.workOrderId) !== id || !Array.isArray(data.workOrderEvents)) {
      throw new Error("The server returned invalid work-order history data.");
    }

    const content = byId("detailsModalContent");
    if (!content || !current()) return;
    content.appendChild(appendTimelineSection(
      "Work Order Timeline",
      data.workOrderEvents,
      "No work-order history is available.",
    ));
    if (role === "administrator" && Array.isArray(data.requestEvents) && data.requestEvents.length) {
      content.appendChild(appendTimelineSection("Request Timeline", data.requestEvents, ""));
    }
  } catch (error) {
    if (!current()) return;
    if (error.status === 401) {
      clearAuthenticatedWorkspace();
    } else if (role === "technician" && (error.status === 403 || error.status === 404)) {
      clearTechnicianSelection();
      showToast("This work order is no longer available.", "error");
    } else {
      const content = byId("detailsModalContent");
      if (content && current()) {
        content.appendChild(appendTimelineSection(
          "Work Order Timeline",
          [],
          "Timeline data is unavailable. Refresh and try again.",
        ));
      }
      showToast(error.message || "Unable to load work-order history.", "error");
    }
  }
}

function compareApprovalHistoryNewestFirst(left, right) {
  const leftTime = Date.parse(left?.decisionDate);
  const rightTime = Date.parse(right?.decisionDate);
  const normalizedLeftTime = Number.isNaN(leftTime) ? Number.NEGATIVE_INFINITY : leftTime;
  const normalizedRightTime = Number.isNaN(rightTime) ? Number.NEGATIVE_INFINITY : rightTime;
  if (normalizedLeftTime !== normalizedRightTime) return normalizedRightTime - normalizedLeftTime;

  const leftApprovalId = Number(left?.approvalId);
  const rightApprovalId = Number(right?.approvalId);
  const normalizedLeftApprovalId = Number.isSafeInteger(leftApprovalId) ? leftApprovalId : 0;
  const normalizedRightApprovalId = Number.isSafeInteger(rightApprovalId) ? rightApprovalId : 0;
  return normalizedRightApprovalId - normalizedLeftApprovalId;
}

function formatAttachmentSize(fileSizeBytes) {
  const bytes = Number(fileSizeBytes);

  if (!Number.isFinite(bytes) || bytes < 0) {
    return "";
  }

  if (bytes < 1024) {
    return `${bytes} bytes`;
  }

  const kilobytes = bytes / 1024;

  if (kilobytes < 1024) {
    return `${kilobytes.toFixed(kilobytes >= 100 ? 0 : 1)} KB`;
  }

  const megabytes = kilobytes / 1024;

  return `${megabytes.toFixed(1)} MB`;
}

async function loadRequesterAttachmentsForDetails(requestId) {
  const numericRequestId = Number(requestId);

  if (!Number.isInteger(numericRequestId) || numericRequestId <= 0) {
    return [];
  }

  const response = await fetch(
    `${API_ENDPOINTS.serviceRequestAttachments}?requestId=${encodeURIComponent(
      numericRequestId,
    )}`,
    {
      method: "GET",

      headers: {
        Accept: "application/json",
      },

      credentials: "same-origin",

      cache: "no-store",
    },
  );

  const data = await readJsonResponse(response);

  if (!response.ok || !data.success) {
    throw new Error(data.message || "Unable to load request attachments.");
  }

  return Array.isArray(data.attachments) ? data.attachments : [];
}

async function loadRequesterServiceRequestDetails(requestId) {
  const response = await fetch(
    `${API_ENDPOINTS.serviceRequests}?requestId=${encodeURIComponent(requestId)}`,
    {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      credentials: "same-origin",
      cache: "no-store",
    },
  );

  const data = await readJsonResponse(response);

  if (!response.ok || !data.success || !data.request) {
    throw new Error(data.message || "Unable to load request details.");
  }

  return mapServiceRequestFromApi(data.request);
}

function sortRequestHistoryChronologically(history) {
  return [...history].sort((left, right) => {
    const leftTime = Date.parse(left?.changedAt);
    const rightTime = Date.parse(right?.changedAt);
    const normalizedLeftTime = Number.isNaN(leftTime) ? Number.POSITIVE_INFINITY : leftTime;
    const normalizedRightTime = Number.isNaN(rightTime) ? Number.POSITIVE_INFINITY : rightTime;
    if (normalizedLeftTime !== normalizedRightTime) return normalizedLeftTime - normalizedRightTime;

    const leftHistoryId = Number(left?.historyId);
    const rightHistoryId = Number(right?.historyId);
    const normalizedLeftHistoryId = Number.isSafeInteger(leftHistoryId) ? leftHistoryId : 0;
    const normalizedRightHistoryId = Number.isSafeInteger(rightHistoryId) ? rightHistoryId : 0;
    return normalizedLeftHistoryId - normalizedRightHistoryId;
  });
}

function appendRequesterHistoryTimeline(history) {
  const section = createElement("div", "detail-item full");
  section.appendChild(createElement("h3", "", "Request History"));
  if (!history.length) {
    section.appendChild(createElement("p", "notification-empty", "No status history is available."));
    return section;
  }

  history.forEach((event) => {
    const item = createElement("div", "compact-item");
    const copy = createElement("div");
    const newStatus = String(event?.newStatus || "Status updated").trim() || "Status updated";
    const previousStatus = String(event?.previousStatus || "").trim();
    const transition = previousStatus
      ? `${previousStatus} → ${newStatus}`
      : `Initial status: ${newStatus}`;
    copy.append(
      createElement("strong", "", newStatus),
      createElement("span", "", transition),
    );
    if (event?.changeReason != null && String(event.changeReason).trim() !== "") {
      copy.appendChild(createElement("span", "", String(event.changeReason)));
    }
    copy.appendChild(createElement("small", "", formatDateTime(event?.changedAt)));
    item.appendChild(copy);
    section.appendChild(item);
  });
  return section;
}

function renderRequesterHistoryModal(request, requestId, history) {
  showDetails(request.id, "Request History", []);
  const modal = byId("detailsModal");
  const content = byId("detailsModalContent");
  if (!modal || !content) return;
  modal.dataset.requestHistoryId = String(requestId);
  content.appendChild(appendRequesterHistoryTimeline(history));
}

async function showRequestHistory(request) {
  const requestId = Number(request?.databaseId);
  if (!Number.isInteger(requestId) || requestId <= 0) {
    showToast("This request has no valid identifier.", "error");
    return;
  }

  showDetails(request.id, "Request History", [{
    label: "Request History",
    value: "Loading request history...",
    full: true,
  }]);
  const modal = byId("detailsModal");
  const account = state.currentAccount;
  const requestVersion = ++state.requestHistoryVersion;
  if (!modal) return;
  modal.dataset.requestHistoryId = String(requestId);
  const current = () => state.activeRole === "requester" && state.currentAccount === account &&
    state.requestHistoryVersion === requestVersion && !modal.hidden &&
    modal.dataset.requestHistoryId === String(requestId);

  try {
    const response = await fetch(
      `${API_ENDPOINTS.serviceRequests}/history?requestId=${encodeURIComponent(requestId)}`,
      {
        method: "GET",
        headers: { Accept: "application/json" },
        credentials: "same-origin",
        cache: "no-store",
      },
    );
    const data = await readJsonResponse(response);
    if (!response.ok || !data.success) {
      const error = new Error(data.message || "Unable to load request history.");
      error.status = response.status;
      throw error;
    }
    if (!current()) return;
    const history = sortRequestHistoryChronologically(
      Array.isArray(data.history) ? data.history : [],
    );
    renderRequesterHistoryModal(request, requestId, history);
  } catch (error) {
    if (!current()) return;
    if (error.status === 401) {
      clearAuthenticatedWorkspace();
      return;
    }
    console.error("Request history error:", error);
    showToast(error.message || "Unable to load request history.", "error");
  }
}

/* =========================
   REQUEST DETAILS
========================= */
async function showRequestDetails(request) {
  const requestId = Number(request?.databaseId);

  if (!Number.isInteger(requestId) || requestId <= 0) {
    showToast("This request has no valid identifier.", "error");
    return;
  }

  state.requestHistoryVersion += 1;

  let detailRequest;

  try {
    if (state.activeRole === "administrator") {
      detailRequest = state.adminReviewRequests.find(
        (item) => Number(item.databaseId) === requestId,
      );
      if (!detailRequest) {
        throw new Error("This request is no longer available in the Administrator queue.");
      }
    } else {
      detailRequest = await loadRequesterServiceRequestDetails(requestId);
    }
  } catch (error) {
    console.error("Request details error:", error);
    showToast(error.message || "Unable to load request details.", "error");
    return;
  }

  let attachmentFields = [
    {
      label: "Attachment",
      value: detailRequest.attachmentName || "No attachment",
    },
  ];

  /*
   * The current attachment API is intentionally
   * requester-owned. Do not use it for admin,
   * department-head, or technician views.
   */
  if (state.activeRole === "requester") {
    try {
      const attachments = await loadRequesterAttachmentsForDetails(
        requestId,
      );

      if (attachments.length > 0) {
        attachmentFields = attachments.map((attachment, index) => {
          const attachmentId = Number(attachment.attachmentId);

          const fileName = String(attachment.originalFileName || "Attachment");

          const fileSize = formatAttachmentSize(attachment.fileSizeBytes);

          return {
            label:
              attachments.length === 1
                ? "Attachment"
                : `Attachment ${index + 1}`,

            value: fileSize ? `${fileName} (${fileSize})` : fileName,

            full: true,

            linkUrl:
              Number.isInteger(attachmentId) && attachmentId > 0
                ? `${API_ENDPOINTS.serviceRequestAttachments}?attachmentId=${encodeURIComponent(
                    attachmentId,
                  )}&download=1`
                : null,

            linkLabel: "Download Attachment",
          };
        });
      } else {
        attachmentFields = [
          {
            label: "Attachment",
            value: "No attachment",
          },
        ];
      }
    } catch (error) {
      console.error("Request attachment details error:", error);

      attachmentFields = [
        {
          label: "Attachment",
          value: "Unable to load attachment information.",
        },
      ];
    }
  }

  const fields = [
    {
      label: "Request Title",
      value: detailRequest.title,
      full: true,
    },

    {
      label: "Requester",
      value: detailRequest.requesterName,
    },

    {
      label: "Email",
      value: detailRequest.requesterEmail,
    },

    {
      label: "Department / Program",
      value: detailRequest.department,
    },

    {
      label: "Location / Room",
      value: detailRequest.location,
    },

    {
      label: "Category",
      value: detailRequest.category,
    },

    {
      label: "Priority",
      value: detailRequest.priority,
      badge: true,
    },

    {
      label: "Status",
      value: detailRequest.status,
      badge: true,
    },

    {
      label: "Date Reported",
      value: formatDate(detailRequest.dateReported),
    },

    ...attachmentFields,

    {
      label: "Detailed Description",
      value: detailRequest.description,
      full: true,
    },

    {
      label: "AI Suggested Category",
      value: detailRequest.aiCategory,
    },

    {
      label: "AI Suggested Priority",
      value: detailRequest.aiPriority,
    },

    {
      label: "Duplicate Check",
      value: detailRequest.duplicateCheck,
      full: true,
    },

    {
      label: "AI Notice",
      value:
        "Advisory only. Authorized school personnel make the final decision.",
      full: true,
    },
  ];

  showDetails(detailRequest.id, "Service Request", fields);
}

function showWorkOrderDetails(workOrder) {
  showDetails(workOrder.id, "Work Order Assignment", [
    {
      label: "Service Request ID",
      value: workOrder.requestId,
    },

    {
      label: "Assigned Personnel",
      value: workOrder.assignedPersonnel,
    },

    {
      label: "Assigned At",
      value: formatDate(workOrder.assignedAt),
    },

    {
      label: "Assignment Notes",
      value: workOrder.assignmentNotes || "No assignment notes",
      full: true,
    },

    {
      label: "Service Unit",
      value: workOrder.serviceUnit,
    },

    {
      label: "Status",
      value: workOrder.status,
      badge: true,
    },

    {
      label: "Work Description",
      value: workOrder.workDescription,
      full: true,
    },

    {
      label: "Target Completion",
      value: formatDate(workOrder.targetCompletionDate),
    },

    {
      label: "Completed At",
      value: formatDate(workOrder.completionDate),
    },

    {
      label: "Completion Summary",
      value:
        workOrder.completionSummary ||
        "No completion summary recorded yet",
      full: true,
    },

    {
      label: "Last Updated",
      value: formatDate(workOrder.updatedAt),
    },

    {
      label: "Technician Remarks",
      value: workOrder.remarks || "No remarks",
      full: true,
    },
  ]);
  void loadAndAppendWorkOrderTimeline(workOrder.databaseId, "administrator");
}

/* =========================
   FORM HELPERS
========================= */

/* =========================
   TASK 6: STRICT FORM VALIDATION
========================= */
const REMEMBER_STORAGE_KEY = "htcPortalRememberedUser";
const SCHOOL_EMAIL_PATTERN = /^[A-Z0-9._%+-]+@online\.htcgsc\.edu\.ph$/i;
const PERSON_NAME_PATTERN = /^[A-Za-zÀ-ÖØ-öø-ÿÑñ.' -]+$/;
const PASSWORD_PATTERN =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s]).{8,72}$/;
const CONTACT_NUMBER_PATTERN = /^(?:\+63|0)9\d{9}$/;

function normalizeEmail(value) {
  return String(value || "")
    .trim()
    .toLowerCase();
}

function getFieldGroup(input) {
  return input?.closest?.(".field-group") || null;
}

function getValidationMessage(input) {
  const group = getFieldGroup(input);
  if (!group) return null;

  let message = group.querySelector(".validation-message");
  if (!message) {
    message = document.createElement("small");
    message.className = "validation-message";
    message.setAttribute("role", "alert");
    message.id = `${input.id || "field"}ValidationMessage`;
    group.appendChild(message);
  }
  return message;
}

function setFieldError(input, message) {
  if (!input) return false;
  const group = getFieldGroup(input);
  const error = getValidationMessage(input);

  group?.classList.add("is-invalid");
  group?.classList.remove("is-valid");
  input.setAttribute("aria-invalid", "true");
  input.setCustomValidity(message || "Invalid value.");

  if (error) {
    error.textContent = message || "Check this field.";
    const describedBy = new Set(
      (input.getAttribute("aria-describedby") || "")
        .split(/\s+/)
        .filter(Boolean),
    );
    describedBy.add(error.id);
    input.setAttribute("aria-describedby", [...describedBy].join(" "));
  }

  return false;
}

function setFieldSuccess(input) {
  if (!input) return true;
  const group = getFieldGroup(input);
  const error = group?.querySelector(".validation-message");
  if (error) error.textContent = "";
  group?.classList.remove("is-invalid");
  if (input.dataset.touched === "true" && input.type !== "file")
    group?.classList.add("is-valid");
  input.removeAttribute("aria-invalid");
  input.setCustomValidity("");
  return true;
}

function clearFieldState(input) {
  if (!input) return;
  const group = getFieldGroup(input);
  group?.classList.remove("is-invalid", "is-valid");
  group?.querySelector(".validation-message")?.remove();
  input.removeAttribute("aria-invalid");
  input.setCustomValidity("");
  delete input.dataset.touched;
}

function validateName(input) {
  const rawValue = String(input.value || "");

  // Normalize only for validation.
  // Do NOT write the normalized value back while the user is typing.
  const value = rawValue.trim().replace(/\s+/g, " ");

  if (!value) {
    if (!input.required) {
      return setFieldSuccess(input);
    }

    return setFieldError(input, "This name field is required.");
  }

  if (value.length > 70 || !PERSON_NAME_PATTERN.test(value)) {
    return setFieldError(
      input,
      "Use letters, spaces, periods, apostrophes, or hyphens only.",
    );
  }

  return setFieldSuccess(input);
}

function validateEmail(input) {
  const value = normalizeEmail(input.value);
  input.value = value;
  if (!value) return setFieldError(input, "School email is required.");
  if (!SCHOOL_EMAIL_PATTERN.test(value)) {
    return setFieldError(
      input,
      "Enter a valid Holy Trinity College school email ending in @online.htcgsc.edu.ph.",
    );
  }
  return setFieldSuccess(input);
}

function passwordScore(value) {
  let score = 0;
  if (value.length >= 8) score += 1;
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score += 1;
  if (/\d/.test(value) && /[^A-Za-z0-9\s]/.test(value)) score += 1;
  if (value.length >= 12) score += 1;
  return score;
}

function updatePasswordStrength() {
  const input = byId("registerPassword");
  const bar = byId("passwordStrengthBar");
  const text = byId("passwordStrengthText");
  if (!input || !bar || !text) return;

  const score = passwordScore(input.value);
  bar.className = "";

  if (!input.value) {
    text.textContent =
      "Use uppercase, lowercase, number, and special character.";
    return;
  }

  if (score <= 1) {
    bar.classList.add("strength-weak");
    text.textContent = "Weak password \u2014 add more character types.";
  } else if (score <= 2) {
    bar.classList.add("strength-fair");
    text.textContent = "Fair password \u2014 make it longer or more varied.";
  } else {
    bar.classList.add("strength-strong");
    text.textContent = "Strong password.";
  }
}

function validatePassword(input) {
  const value = input.value;
  if (!value) return setFieldError(input, "Password is required.");
  if (value !== value.trim())
    return setFieldError(input, "Password cannot begin or end with spaces.");
  if (!PASSWORD_PATTERN.test(value)) {
    return setFieldError(
      input,
      "Use at least 8 characters with uppercase, lowercase, number, and special character.",
    );
  }
  return setFieldSuccess(input);
}

function validatePasswordMatch(input) {
  if (!input.value) return setFieldError(input, "Confirm your password.");
  if (input.value !== byId("registerPassword")?.value)
    return setFieldError(input, "Passwords do not match.");
  return setFieldSuccess(input);
}

function validateContactNumber(input) {
  const value = String(input.value || "")
    .trim()
    .replace(/[\s-]/g, "");

  if (!value) {
    input.value = "";
    return setFieldSuccess(input);
  }

  if (!CONTACT_NUMBER_PATTERN.test(value)) {
    return setFieldError(input, "Use 09XXXXXXXXX or +639XXXXXXXXX format.");
  }

  input.value = value;
  return setFieldSuccess(input);
}

function validateSelect(input) {
  if (!input.value && input.required)
    return setFieldError(input, "Select a valid option.");
  return setFieldSuccess(input);
}

function validateDateNotFuture(input, required = input.required) {
  if (!input.value) {
    return required
      ? setFieldError(input, "Select a valid date.")
      : setFieldSuccess(input);
  }
  if (input.value > todayInputValue())
    return setFieldError(input, "Date cannot be set in the future.");
  return setFieldSuccess(input);
}

function hasMeaningfulText(value, minimum = 1) {
  const normalized = String(value || "")
    .trim()
    .replace(/\s+/g, " ");
  if (normalized.length < minimum) return false;
  if (/^(.)\1{4,}$/i.test(normalized.replace(/\s/g, ""))) return false;
  return true;
}

function validateTextInput(input) {
  const value = input.value.trim();
  if (input.required && !value)
    return setFieldError(
      input,
      "This field is required and cannot contain only spaces.",
    );
  if (!value && !input.required) return setFieldSuccess(input);

  const minLength = Number(input.getAttribute("minlength") || 0);
  const maxLength = Number(input.getAttribute("maxlength") || 0);
  if (minLength && !hasMeaningfulText(value, minLength))
    return setFieldError(
      input,
      `Enter at least ${minLength} meaningful characters.`,
    );
  if (maxLength && value.length > maxLength)
    return setFieldError(input, `Use no more than ${maxLength} characters.`);
  if (/^(.)\1{5,}$/i.test(value.replace(/\s/g, "")))
    return setFieldError(
      input,
      "Enter meaningful information instead of repeated characters.",
    );
  input.value = value;
  return setFieldSuccess(input);
}

function validateIdPattern(input, prefix) {
  const value = input.value.trim().toUpperCase();
  input.value = value;
  if (!value && !input.required) return setFieldSuccess(input);
  const pattern =
    prefix === "SR"
      ? /^SR-\d{4}-\d{6}$/
      : /^WO-\d{4}-\d{4}$/;
  if (!pattern.test(value))
    return setFieldError(
      input,
      prefix === "SR"
        ? "Use the format SR-YYYY-NNNNNN."
        : "Use the format WO-YYYY-NNNN.",
    );
  return setFieldSuccess(input);
}

function validateControl(input) {
  if (
    !input ||
    input.disabled ||
    input.type === "hidden" ||
    input.type === "submit" ||
    input.type === "button" ||
    input.type === "reset" ||
    input.type === "checkbox"
  )
    return true;

  if (input.closest("#progressUpdateForm")) return validateTechnicianProgressControl(input);
  if (input.tagName === "SELECT") return validateSelect(input);
  if (input.type === "email") return validateEmail(input);
  if (
    [
      "registerFirstName",
      "registerMiddleName",
      "registerLastName",
      "requesterName",
      "personnelName",
    ].includes(input.id)
  )
    return validateName(input);
  if (input.id === "registerPassword") return validatePassword(input);
  if (input.id === "registerConfirmPassword")
    return validatePasswordMatch(input);
  if (input.type === "tel") return validateContactNumber(input);
  if (input.id === "loginPassword") {
    if (!input.value.trim())
      return setFieldError(input, "Password is required.");
    return setFieldSuccess(input);
  }
  if (
    ["requestDate", "completionDate", "progressCompletionDate"].includes(
      input.id,
    )
  )
    return validateDateNotFuture(input, input.required);
  if (["workOrderId", "progressWorkOrderId"].includes(input.id))
    return validateIdPattern(input, "WO");
  if (["workOrderRequestId", "progressRequestId"].includes(input.id))
    return validateIdPattern(input, "SR");
  if (input.type === "file") return validateAttachment(input, false);
  return validateTextInput(input);
}

function validateForm(form) {
  let valid = true;
  const controls = all("input, select, textarea", form);

  controls.forEach((input) => {
    input.dataset.touched = "true";
    if (!validateControl(input)) valid = false;
  });

  if (!valid) {
    const firstInvalid = form.querySelector(
      '[aria-invalid="true"], .is-invalid input, .is-invalid select, .is-invalid textarea',
    );
    firstInvalid?.focus();
    firstInvalid?.scrollIntoView({ behavior: "smooth", block: "center" });
  }

  return valid;
}

function resetValidationState(form) {
  all("input, select, textarea", form).forEach(clearFieldState);
}

function showAuthMessage(message, type = "info") {
  const region = byId("authMessage");
  if (!region) return;
  region.className = `auth-message show ${type}`;
  region.textContent = message;
}

function clearAuthMessage() {
  const region = byId("authMessage");
  if (!region) return;
  region.className = "auth-message";
  region.textContent = "";
}

function setAuthMode(mode) {
  const registerMode = mode === "register";
  byId("loginForm").hidden = registerMode;
  byId("registrationForm").hidden = !registerMode;
  const verificationPanel = byId("verificationPanel");
  if (verificationPanel) verificationPanel.hidden = true;
  byId("authSignInTab").classList.toggle("active", !registerMode);
  byId("authRegisterTab").classList.toggle("active", registerMode);
  byId("authSignInTab").setAttribute("aria-selected", String(!registerMode));
  byId("authRegisterTab").setAttribute("aria-selected", String(registerMode));
  clearAuthMessage();
  window.setTimeout(
    () => byId(registerMode ? "registerFirstName" : "loginEmail")?.focus(),
    20,
  );
}

const EMAIL_VERIFICATION_STORAGE_KEY = "htcEmailVerificationComplete";

function showEmailVerificationSuccess() {
  state.pendingVerificationEmail = "";

  const panel = byId("verificationPanel");
  const loginForm = byId("loginForm");
  const registrationForm = byId("registrationForm");

  if (registrationForm) {
    registrationForm.hidden = true;
  }

  if (loginForm) {
    loginForm.hidden = true;
  }

  if (panel) {
    panel.hidden = false;
  }

  const icon = byId("verificationStatusIcon");

  const title = byId("verificationPanelTitle");

  const message = byId("verificationPanelMessage");

  const noticeTitle = byId("verificationEmailDisplay");

  const description = byId("verificationStatusDescription");

  const resendButton = byId("resendVerificationButton");

  const signInButton = byId("verificationSignInButton");

  const backButton = byId("verificationBackToLogin");

  if (icon) {
    icon.textContent = "\u2713";
    icon.classList.add("is-success");
  }

  if (title) {
    title.textContent = "Email Successfully Verified";
  }

  if (message) {
    message.textContent =
      "Your Holy Trinity College school email has been successfully verified.";
  }

  if (noticeTitle) {
    noticeTitle.textContent = "Verification complete";
  }

  if (description) {
    description.textContent =
      "Your account is ready. You may now sign in to the Service Portal.";
  }

  if (resendButton) {
    resendButton.hidden = true;
  }

  if (backButton) {
    backButton.hidden = true;
  }

  if (signInButton) {
    signInButton.hidden = false;
  }

  showAuthMessage("Email verified successfully.", "success");
}

function handleEmailVerificationReturn() {
  const currentUrl = new URL(window.location.href);

  const result = currentUrl.searchParams.get("verified");

  if (!result) {
    return false;
  }

  /*
   * Remove only the verification parameter immediately.
   * Other legitimate query parameters and the hash are preserved.
   */
  currentUrl.searchParams.delete("verified");

  const remainingQuery = currentUrl.searchParams.toString();

  const cleanUrl =
    currentUrl.pathname +
    (remainingQuery ? `?${remainingQuery}` : "") +
    currentUrl.hash;

  window.history.replaceState({}, document.title, cleanUrl);

  if (result === "success") {
    showEmailVerificationSuccess();

    try {
      localStorage.setItem(EMAIL_VERIFICATION_STORAGE_KEY, String(Date.now()));
    } catch (error) {
      console.warn("Unable to notify another tab about email verification.");
    }

    openModal("loginModal", "#verificationSignInButton");

    return true;
  }

  if (result === "pending") {
    state.pendingVerificationEmail = "";

    setAuthMode("login");

    showAuthMessage(
      "Email verified successfully. Your account is awaiting authorization.",
      "success",
    );

    openModal("loginModal", "#loginEmail");

    return true;
  }

  if (result === "invalid") {
    setAuthMode("login");

    showAuthMessage(
      "This verification link is invalid, expired, or has already been used.",
      "error",
    );

    openModal("loginModal", "#loginEmail");

    return true;
  }

  setAuthMode("login");

  showAuthMessage(
    "Unable to verify your email. Please try again or request a new verification email.",
    "error",
  );

  openModal("loginModal", "#loginEmail");

  return true;
}

function initializeEmailVerificationSync() {
  window.addEventListener("storage", (event) => {
    if (event.key !== EMAIL_VERIFICATION_STORAGE_KEY || !event.newValue) {
      return;
    }

    showEmailVerificationSuccess();

    openModal("loginModal", "#verificationSignInButton");
  });
}

function setSubmitting(form, submitting) {
  const button = form.querySelector('button[type="submit"]');
  if (!button) return;

  const label = button.querySelector("span") || button;
  if (!button.dataset.defaultLabel) {
    button.dataset.defaultLabel = label.textContent.trim();
  }

  button.disabled = submitting;
  button.setAttribute("aria-busy", String(submitting));
  button.classList.toggle("is-loading", submitting);

  if (submitting) {
    const loadingMap = {
      loginForm: "Signing In...",
      registrationForm: "Creating Account...",
      serviceRequestForm: "Submitting...",
      workOrderForm: "Saving...",
      workOrderAssignmentForm: "Assigning...",
      personnelAccessForm: "Saving...",
      progressUpdateForm: "Updating...",
    };
    label.textContent = loadingMap[form.id] || "Processing\u2026";
  } else {
    label.textContent = button.dataset.defaultLabel;
  }
}

function togglePasswordVisibility(inputId, buttonId) {
  const input = byId(inputId);
  const button = byId(buttonId);
  if (!input || !button) return;
  const show = input.type === "password";
  input.type = show ? "text" : "password";
  button.textContent = show ? "Hide" : "Show";
  button.setAttribute("aria-label", show ? "Hide password" : "Show password");
}

function setDateRestrictions() {
  const today = todayInputValue();

  const requestDate = byId("requestDate");

  const completionDate = byId("completionDate");

  const progressCompletionDate = byId("progressCompletionDate");

  if (requestDate) {
    requestDate.max = today;

    requestDate.value = today;
  }

  if (completionDate) {
    completionDate.max = today;
  }

  if (progressCompletionDate) {
    progressCompletionDate.max = today;
  }
}

function validateAttachment(input, report = true) {
  const file = input.files?.[0];
  input.setCustomValidity("");

  if (!file) {
    setFieldSuccess(input);
    return true;
  }

  const allowedTypes = [
    "image/png",
    "image/jpeg",
    "image/webp",
    "application/pdf",
  ];
  const allowedExtensions = /\.(png|jpe?g|webp|pdf)$/i;

  if (!allowedTypes.includes(file.type) || !allowedExtensions.test(file.name)) {
    setFieldError(
      input,
      "Upload a PNG, JPG, WEBP, or PDF file no larger than 5 MB.",
    );
    if (report) input.reportValidity();
    return false;
  }

  if (file.size > 5 * 1024 * 1024) {
    setFieldError(
      input,
      "Upload a PNG, JPG, WEBP, or PDF file no larger than 5 MB.",
    );
    if (report) input.reportValidity();
    return false;
  }

  setFieldSuccess(input);
  return true;
}

function resetServiceRequestFormForUser() {
  resetServiceRequestDraftEditingState();
  window.setTimeout(() => {
    byId("requestCharacterCount").textContent = "0";

    byId("requestDate").value = todayInputValue();

    if (state.activeRole === "requester") {
      byId("requesterName").value = state.userName;
      byId("requesterEmail").value = state.userEmail;
      byId("requestDepartment").value = state.currentAccount?.department || "";
      byId("requesterTypeInput").value =
        state.currentAccount?.affiliation || "";
    }
  }, 0);
}

function populateWorkOrderForm(workOrder) {
  byId("workOrderId").value = workOrder.id;

  byId("workOrderRequestId").value = workOrder.requestId;

  byId("assignedTechnician").value = workOrder.assignedPersonnel;

  byId("serviceUnit").value = workOrder.serviceUnit;

  byId("workDescription").value = workOrder.workDescription;

  byId("workStatus").value = workOrder.status;

  byId("actionTaken").value = workOrder.actionTaken;

  byId("materialsUsed").value = workOrder.materials;

  byId("completionDate").value = workOrder.completionDate;

  byId("technicianRemarks").value = workOrder.remarks;
}

const TECHNICIAN_ACTION_LABELS = Object.freeze({
  acknowledge: "Acknowledge", start: "Start Work", progress: "Update Progress",
  hold: "Put On Hold", resume: "Resume Work", complete: "Complete Work",
});

function technicianActionsForStatus(status) {
  switch (status) {
    case "Assigned": return ["acknowledge"];
    case "Acknowledged": return ["start"];
    case "In Progress": return ["progress", "hold", "complete"];
    case "On Hold": return ["progress", "resume"];
    default: return [];
  }
}

function populateTechnicianUpdateForm(workOrder) {
  byId("progressWorkOrderId").value = workOrder?.id || "";
  byId("progressRequestId").value = workOrder?.requestId || "";
  byId("progressTechnician").value = workOrder?.technicianName || "";
  byId("progressServiceUnit").value = workOrder?.departmentName || "";
  byId("progressWorkDescription").value = workOrder?.workDescription || "";
  byId("progressStatusInput").value = workOrder?.status || "";
  byId("progressActionTaken").value = "";
  byId("progressPercentageInput").value = "";
  byId("progressCompletionSummary").value = "";
  state.technicianAction = "";
  resetValidationState(byId("progressUpdateForm"));
  renderTechnicianProgressActions();
}

function renderTechnicianProgressActions() {
  const order = state.technicianWorkOrder;
  const actions = isTechnicianSessionCurrent(technicianSession()) && order && !state.technicianDetailLoading
    ? technicianActionsForStatus(order.status) : [];
  if (!actions.includes(state.technicianAction)) state.technicianAction = "";
  const action = state.technicianAction;
  const busy = state.technicianSubmitting || state.technicianLoading;
  const container = byId("technicianProgressActions");
  container.replaceChildren();
  actions.forEach((value) => {
    const button = createElement("button", `button button-small ${value === action ? "button-primary" : "button-secondary"}`, TECHNICIAN_ACTION_LABELS[value]);
    button.type = "button";
    button.dataset.techProgressAction = value;
    button.setAttribute("aria-pressed", String(value === action));
    button.disabled = busy;
    container.appendChild(button);
  });
  byId("technicianSelectionMessage").textContent = state.technicianDetailLoading
    ? "Loading work-order details..."
    : !order ? "Select a work order from Assigned Work to view available actions."
      : !actions.length ? "This work order is read-only. No technician actions are available."
        : action ? `Selected action: ${TECHNICIAN_ACTION_LABELS[action]}. Add notes, then submit.`
          : "Choose an action for this work order.";
  byId("progressActionTaken").disabled = !action || busy;
  for (const [value, fieldId, inputId] of [
    ["progress", "progressPercentageField", "progressPercentageInput"],
    ["complete", "progressCompletionSummaryField", "progressCompletionSummary"],
  ]) {
    byId(fieldId).hidden = action !== value;
    byId(inputId).required = action === value;
    byId(inputId).disabled = action !== value || busy;
  }
  const submit = byId("technicianProgressSubmit");
  submit.hidden = !actions.length;
  submit.dataset.defaultLabel = TECHNICIAN_ACTION_LABELS[action] || "Select an Action";
  setSubmitting(byId("progressUpdateForm"), state.technicianSubmitting);
  submit.disabled = !action || busy;
  byId("progressUpdateForm").querySelector('button[type="reset"]').disabled = !order || busy;
}

function selectTechnicianProgressAction(action) {
  if (state.technicianSubmitting || state.technicianLoading || state.technicianDetailLoading ||
      !isTechnicianSessionCurrent(technicianSession()) ||
      !technicianActionsForStatus(state.technicianWorkOrder?.status).includes(action)) return;
  state.technicianAction = action;
  for (const id of ["progressPercentageInput", "progressCompletionSummary"]) {
    byId(id).value = "";
    clearFieldState(byId(id));
  }
  renderTechnicianProgressActions();
  byId("progressActionTaken").focus();
}

function showTechnicianWorkOrderDetails(order) {
  const fields = [
    ["Work Order Number", order.id], ["Request Number", order.requestId],
    ["Request Title", order.requestTitle], ["Request Description", order.requestDescription, true],
    ["Location", order.requestLocation], ["Department", order.departmentName],
    ["Technician", order.technicianName], ["Work Description", order.workDescription, true],
    ["Current Status", order.status], ["Assigned At", formatDateTime(order.assignedAt)],
    ["Acknowledged At", formatDateTime(order.acknowledgedAt)],
    ["Assignment Acknowledged At", formatDateTime(order.assignmentAcknowledgedAt)],
    ["Actual Start", formatDateTime(order.actualStartAt)],
    ["Target Start", formatDate(order.targetStartDate)],
    ["Target Completion", formatDate(order.targetCompletionDate)],
    ["Completed At", formatDateTime(order.completedAt)],
    ["Completion Summary", order.completionSummary, true],
    ["Assignment Notes", order.assignmentNotes, true],
  ].map(([label, value, full]) => ({ label, value: value || "\u2014", full }));
  showDetails(order.id, "Assigned Work Order", fields);
  byId("detailsModal").dataset.technicianWorkOrderId = String(order.databaseId);
  void loadAndAppendWorkOrderTimeline(order.databaseId, "technician");
}

function validateCompletionDate(statusInput, dateInput, actionInput = null) {
  dateInput.setCustomValidity("");

  if (statusInput.value === "Completed" && !dateInput.value) {
    setFieldError(
      dateInput,
      "Enter a completion date when the work status is Completed.",
    );
    return false;
  }

  if (dateInput.value && dateInput.value > todayInputValue()) {
    setFieldError(dateInput, "Completion date cannot be in the future.");
    return false;
  }

  if (
    ["In Progress", "Completed"].includes(statusInput.value) &&
    actionInput &&
    !hasMeaningfulText(actionInput.value, 5)
  ) {
    setFieldError(
      actionInput,
      "Describe the action taken using at least 5 meaningful characters.",
    );
    return false;
  }

  setFieldSuccess(dateInput);
  if (actionInput && actionInput.value.trim()) setFieldSuccess(actionInput);
  return true;
}

/* =========================
   FORM: LOGIN + REGISTRATION
========================= */
function restoreRememberedUser() {
  try {
    const saved = JSON.parse(
      localStorage.getItem(REMEMBER_STORAGE_KEY) || "null",
    );
    if (!saved) return;
    byId("loginEmail").value = saved.email || "";
    byId("rememberMe").checked = Boolean(saved.email);
  } catch {
    localStorage.removeItem(REMEMBER_STORAGE_KEY);
  }
}

const ROLE_ID_BY_KEY = {
  requester: 1,
  administrator: 2,
  approver: 3,
  technician: 4,
};

const ROLE_KEY_BY_ID = {
  1: "requester",
  2: "administrator",
  3: "approver",
  4: "technician",
};

const departmentNamesById = new Map();

function roleValueToId(value) {
  const numericValue = Number(value);
  if (Number.isInteger(numericValue) && numericValue > 0) return numericValue;
  return (
    ROLE_ID_BY_KEY[
      String(value || "")
        .trim()
        .toLowerCase()
    ] || 0
  );
}

function roleIdToKey(roleId) {
  return ROLE_KEY_BY_ID[Number(roleId)] || null;
}

function departmentValueToId(value) {
  const rawValue = String(value ?? "").trim();
  const numericValue = Number(rawValue);

  if (Number.isInteger(numericValue) && numericValue > 0) {
    return numericValue;
  }

  return 0;
}

function getSelectedDepartmentId() {
  const select = byId("registerDepartment");

  if (!select) {
    return 0;
  }

  return departmentValueToId(select.value);
}

function departmentIdToName(departmentId) {
  return departmentNamesById.get(Number(departmentId)) || "";
}

function cacheDepartmentOptions() {
  const select = byId("registerDepartment");
  if (!select) return;

  all("option[value]", select).forEach((option) => {
    const departmentId = departmentValueToId(option.value);
    const departmentName = String(option.textContent || "").trim();

    if (departmentId && departmentName) {
      departmentNamesById.set(departmentId, departmentName);
    }
  });
}

async function loadDepartments() {
  const select = byId("registerDepartment");
  if (!select) return;

  cacheDepartmentOptions();
  const previousValue = select.value;

  try {
    const response = await fetch(API_ENDPOINTS.departments, {
      method: "GET",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
      },
      cache: "no-store",
    });

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success || !Array.isArray(data.departments)) {
      throw new Error(data.message || "Invalid department response.");
    }

    const departments = data.departments
      .map((department) => ({
        id: Number(department.departmentId),
        name: String(department.departmentName || "").trim(),
      }))
      .filter(
        (department) =>
          Number.isInteger(department.id) &&
          department.id > 0 &&
          department.name,
      );

    if (!departments.length) {
      throw new Error("The database returned no departments.");
    }

    departmentNamesById.clear();

    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = "Select department or program";

    const options = departments.map((department) => {
      departmentNamesById.set(department.id, department.name);

      const option = document.createElement("option");
      option.value = String(department.id);
      option.textContent = department.name;
      return option;
    });

    select.replaceChildren(placeholder, ...options);

    if (
      departments.some((department) => String(department.id) === previousValue)
    ) {
      select.value = previousValue;
    }
  } catch (error) {
    console.warn(
      "Department list could not be refreshed from MySQL; using the verified HTML fallback options.",
      error,
    );
  }
}

function splitFullName(fullName) {
  const parts = String(fullName || "")
    .trim()
    .split(/\s+/)
    .filter(Boolean);

  if (parts.length < 2) {
    return { firstName: "", lastName: "" };
  }

  const lastName = parts.pop();
  return {
    firstName: parts.join(" "),
    lastName,
  };
}

async function readJsonResponse(response) {
  const text = await response.text();

  if (!text) return {};

  try {
    return JSON.parse(text);
  } catch {
    return {
      success: false,
      message: "The server returned an invalid response.",
    };
  }
}

function accountFromAuthenticationData(data) {
  const roleId = Number(data.roleId);
  const departmentId = Number(data.departmentId);
  const role = roleIdToKey(roleId);

  if (!role || !roleConfiguration[role]) {
    return null;
  }

  return {
    id: Number(data.personnelId),
    personnelId: Number(data.personnelId),
    name: `${data.firstName || ""} ${data.lastName || ""}`.trim(),
    firstName: String(data.firstName || ""),
    lastName: String(data.lastName || ""),
    email: normalizeEmail(data.email),
    contactNumber: String(data.contactNumber || ""),
    affiliation: String(data.personnelType || ""),
    personnelType: String(data.personnelType || ""),
    departmentId,
    department:
      String(data.departmentName || "").trim() ||
      departmentIdToName(departmentId),
    profileImageFileName: String(data.profileImageFileName || "").trim(),
    roleId,
    role,
  };
}

async function invalidateServerSessionSilently() {
  try {
    await fetch(API_ENDPOINTS.logout, {
      method: "POST",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
      },
      cache: "no-store",
    });
  } catch {
    // A failed cleanup request must not replace the original authentication error.
  }
}

async function restoreServerSession() {
  try {
    const response = await fetch(API_ENDPOINTS.session, {
      method: "GET",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
      },
      cache: "no-store",
    });

    if (response.status === 401 || response.status === 403) {
      return;
    }

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success || !data.authenticated) {
      console.warn(
        "Session restoration was not completed:",
        data.message || response.status,
      );
      return;
    }

    const account = accountFromAuthenticationData(data);

    if (!account) {
      await invalidateServerSessionSilently();
      console.error(
        "The active session contains an unsupported role assignment.",
      );
      return;
    }

    state.currentAccount = account;
    activateRoleWorkspace(account.role, account.name, account.email);

    if (account.role === "technician") await loadTechnicianWorkOrders();

    if (account.role === "administrator") {
      try {
        await loadAdministratorServiceRequestReviews();
      } catch (error) {
        console.warn("Administrator request queue could not be loaded:", error);
      }
    }

    if (account.role === "approver") {
      try {
        await loadApproverApprovals();
      } catch (error) {
        console.warn("Approval records could not be restored.", error);
      }
    }

    if (account.role === "administrator") {
      try {
        await loadAdministratorPersonnel();
      } catch (error) {
        console.warn(
          "Administrator personnel records could not be restored.",
          error,
        );
      }
      try {
        await loadWorkOrders();
      } catch (error) {
        console.warn("Administrator work orders could not be restored.", error);
      }
    }

    if (account.role === "approver") {
      try {
        await loadApproverApprovals();
      } catch (error) {
        showToast(
          "Signed in successfully, but approval records could not be loaded.",
          "error",
        );
      }
    }
  } catch (error) {
    console.warn("The server session could not be checked.", error);
  }
}

async function handleRegistrationSubmit(event) {
  event.preventDefault();
  const form = event.currentTarget;
  clearAuthMessage();
  if (!validateForm(form)) return;

  const firstName = byId("registerFirstName").value.trim();
  const middleName = byId("registerMiddleName").value.trim();
  const lastName = byId("registerLastName").value.trim();
  const suffix = byId("registerSuffix").value;
  const email = normalizeEmail(byId("registerEmail").value);
  const contactNumber = byId("registerContactNumber")?.value.trim() || "";
  const departmentId = getSelectedDepartmentId();
  if (!departmentId) {
    setFieldError(byId("registerDepartment"), "Select a valid department.");
    byId("registerDepartment").focus();
    return;
  }

  const personnelType = byId("registerAffiliation").value;
  const password = byId("registerPassword").value;
  setSubmitting(form, true);
  try {
    const response = await fetch(API_ENDPOINTS.register, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      credentials: "same-origin",
      body: JSON.stringify({
        firstName,
        middleName,
        lastName,
        suffix,
        email,
        contactNumber,
        personnelType,
        password,
        departmentId,
      }),
    });
    const data = await readJsonResponse(response);
    if (!response.ok || !data.success) {
      const message = data.message || "Unable to create account.";
      if (response.status === 409) {
        setFieldError(byId("registerEmail"), message);
        byId("registerEmail").focus();
        return;
      }
      showAuthMessage(message, "error");
      return;
    }

    state.pendingVerificationEmail = email;
    byId("verificationEmailDisplay").textContent = data.maskedEmail
      ? `Verification sent to ${data.maskedEmail}`
      : "Check your school email";
    form.hidden = true;
    byId("loginForm").hidden = true;
    byId("verificationPanel").hidden = false;
    showAuthMessage(
      data.message || "Check your school email to verify your account.",
      data.mailSent === false ? "error" : "success",
    );
    form.reset();
    resetValidationState(form);
    updatePasswordStrength();
  } catch (error) {
    console.error("Registration error:", error);
    showAuthMessage(
      "Unable to connect to the server. Please make sure Tomcat is running.",
      "error",
    );
  } finally {
    setSubmitting(form, false);
  }
}

async function resendVerificationEmail() {
  const email = state.pendingVerificationEmail;
  if (!email) {
    showAuthMessage(
      "Enter your school email again to request verification.",
      "error",
    );
    return;
  }
  const button = byId("resendVerificationButton");
  if (button) button.disabled = true;
  try {
    const response = await fetch(API_ENDPOINTS.resendVerification, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      credentials: "same-origin",
      body: JSON.stringify({ email }),
    });
    const data = await readJsonResponse(response);
    showAuthMessage(
      data.message || "Verification request processed.",
      response.ok && data.success ? "success" : "error",
    );
  } catch (error) {
    showAuthMessage("Unable to resend verification email.", "error");
  } finally {
    window.setTimeout(() => {
      if (button) button.disabled = false;
    }, 60000);
  }
}

async function handleLoginSubmit(event) {
  event.preventDefault();

  const form = event.currentTarget;
  clearAuthMessage();

  if (!validateForm(form)) return;

  const email = normalizeEmail(byId("loginEmail").value);
  const password = byId("loginPassword").value;

  if (state.activeRole === "technician") clearTechnicianWorkspace();
  setSubmitting(form, true);

  try {
    const response = await fetch(API_ENDPOINTS.login, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "same-origin",
      body: JSON.stringify({
        email,
        password,
      }),
    });

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success) {
      showAuthMessage(
        data.message || "Unable to sign in. Check your email and password.",
        "error",
      );

      byId("loginPassword").value = "";
      byId("loginPassword").focus();
      return;
    }

    const account = accountFromAuthenticationData(data);

    if (!account) {
      await invalidateServerSessionSilently();
      showAuthMessage(
        "This account does not have a valid system role assigned.",
        "error",
      );
      return;
    }

    try {
      if (byId("rememberMe").checked) {
        localStorage.setItem(
          REMEMBER_STORAGE_KEY,
          JSON.stringify({ email: data.email }),
        );
      } else {
        localStorage.removeItem(REMEMBER_STORAGE_KEY);
      }
    } catch {
      // Login can continue even if browser storage is unavailable.
    }

    state.currentAccount = account;

    closeModal("loginModal");

    activateRoleWorkspace(account.role, account.name, account.email);

    if (account.role === "technician") {
      await loadTechnicianWorkOrders();
      if (state.currentAccount !== account || state.activeRole !== "technician") return;
    }

    if (account.role === "requester") {
      await loadRequesterServiceRequests();
      await loadRequesterServiceRequestDrafts();
      renderAll();
    }

    if (account.role === "administrator") {
      try {
        await loadAdministratorPersonnel();
      } catch (error) {
        showToast(
          "Signed in successfully, but personnel records could not be loaded.",
          "error",
        );
      }
      try {
        await loadWorkOrders();
      } catch (error) {
        showToast(
          "Signed in successfully, but work orders could not be loaded.",
          "error",
        );
      }
    }

    showToast(
      `Signed in to the ${roleConfiguration[account.role].label} workspace.`,
      "success",
    );

    byId("loginPassword").value = "";
    resetValidationState(form);
  } catch (error) {
    console.error("Login error:", error);

    showAuthMessage(
      "Unable to connect to the server. Please make sure Tomcat is running.",
      "error",
    );
  } finally {
    setSubmitting(form, false);
  }
}
/* =========================
   FORM: SERVICE REQUEST
========================= */
function buildServiceRequestDraftPayload() {
  const selectedCategory = byId("requestCategory")?.value || "";

  let requestedCategoryId = null;

  if (selectedCategory && selectedCategory !== "AUTO") {
    const parsedCategoryId = Number(selectedCategory);

    if (Number.isInteger(parsedCategoryId) && parsedCategoryId > 0) {
      requestedCategoryId = parsedCategoryId;
    }
  }

  const priority = byId("requestPriority")?.value?.trim() || null;

  const title = byId("requestTitle")?.value?.trim() || null;

  const description = byId("requestDescription")?.value?.trim() || null;

  const location = byId("requestLocation")?.value?.trim() || null;

  const dateReported = byId("requestDate")?.value || null;

  return {
    requestedCategoryId,
    preferredPriority: priority,
    title,
    description,
    location,
    dateReported,
  };
}

function resetServiceRequestDraftEditingState() {
  state.currentDraftId = null;

  const button = byId("serviceRequestDraftButton");

  if (button) {
    button.textContent = "Save Draft";

    button.disabled = false;
  }
}

async function handleServiceRequestDraftSave() {
  if (state.activeRole !== "requester") {
    showToast(
      "Only requester accounts may save service request drafts.",
      "error",
    );

    return;
  }

  const button = byId("serviceRequestDraftButton");

  const payload = buildServiceRequestDraftPayload();

  const isUpdate =
    Number.isInteger(Number(state.currentDraftId)) &&
    Number(state.currentDraftId) > 0;

  const draftId = isUpdate ? Number(state.currentDraftId) : null;

  const endpoint = isUpdate
    ? `${API_ENDPOINTS.serviceRequestDrafts}?id=${encodeURIComponent(draftId)}`
    : API_ENDPOINTS.serviceRequestDrafts;

  if (button) {
    button.disabled = true;
    button.textContent = isUpdate ? "Updating Draft..." : "Saving Draft...";
  }

  try {
    const csrf = await getCsrfToken();

    const response = await fetch(endpoint, {
      method: isUpdate ? "PUT" : "POST",

      headers: {
        "Content-Type": "application/json",

        Accept: "application/json",

        [csrf.headerName]: csrf.token,
      },

      credentials: "same-origin",

      body: JSON.stringify(payload),
    });

    const data = await response.json();

    if (!response.ok || !data.success || !data.draft) {
      throw new Error(data.message || "Unable to save the draft.");
    }

    state.currentDraftId = Number(data.draft.draftId);

    const existingIndex = state.serviceRequestDrafts.findIndex(
      (draft) => Number(draft.draftId) === state.currentDraftId,
    );

    if (existingIndex >= 0) {
      state.serviceRequestDrafts[existingIndex] = data.draft;
    } else {
      state.serviceRequestDrafts.unshift(data.draft);
    }

    renderRequesterDrafts();

    showToast(
      isUpdate ? "Draft updated successfully." : "Draft saved successfully.",
      "success",
    );
  } catch (error) {
    console.error("Draft save error:", error);

    showToast(error.message || "Unable to save the draft.", "error");
  } finally {
    if (button) {
      button.disabled = false;

      button.textContent = state.currentDraftId ? "Update Draft" : "Save Draft";
    }
  }
}

async function handleServiceRequestSubmit(event) {
  event.preventDefault();

  const form = event.currentTarget;

  const attachmentInput = byId("requestAttachment");

  if (!validateAttachment(attachmentInput)) {
    return;
  }

  if (!validateForm(form)) {
    return;
  }

  /*
   * Capture the selected file before form.reset().
   * The request itself is created first so the
   * attachment can reference the real Request_ID.
   */
  const selectedAttachment = attachmentInput?.files?.[0] || null;

  const draftIdToDeleteAfterSubmission =
    Number.isInteger(Number(state.currentDraftId)) &&
    Number(state.currentDraftId) > 0
      ? Number(state.currentDraftId)
      : null;

  const selectedCategory = byId("requestCategory").value;

  const requestedCategoryId =
    selectedCategory === "AUTO" ? null : Number(selectedCategory);

  if (
    selectedCategory !== "AUTO" &&
    (!Number.isInteger(requestedCategoryId) || requestedCategoryId <= 0)
  ) {
    showToast("Please select a valid service category.", "error");

    return;
  }

  const payload = {
    requestedCategoryId,

    preferredPriority: byId("requestPriority").value,

    title: byId("requestTitle").value.trim(),

    description: byId("requestDescription").value.trim(),

    location: byId("requestLocation").value.trim(),

    dateReported: byId("requestDate").value,
  };

  setSubmitting(form, true);

  let requestCreated = false;

  let createdRequestNumber = "Service request";

  let attachmentUploadError = null;

  let draftCleanupError = null;

  try {
    const csrf = await getCsrfToken();

    const response = await fetch(API_ENDPOINTS.serviceRequests, {
      method: "POST",

      headers: {
        "Content-Type": "application/json",

        Accept: "application/json",

        [csrf.headerName]: csrf.token,
      },

      credentials: "same-origin",

      body: JSON.stringify(payload),
    });

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to submit the service request.");
    }

    const requestId = Number(data.request?.requestId);

    createdRequestNumber = data.request?.requestNumber || "Service request";

    requestCreated = true;

    /*
     * Upload the optional file only after the
     * authoritative service request exists.
     */
    if (selectedAttachment) {
      if (!Number.isInteger(requestId) || requestId <= 0) {
        attachmentUploadError =
          "The service request was saved, but the server did not return a valid request ID for the attachment.";
      } else {
        try {
          const formData = new FormData();

          formData.append("requestId", String(requestId));

          formData.append("attachment", selectedAttachment);

          const attachmentResponse = await fetch(
            API_ENDPOINTS.serviceRequestAttachments,
            {
              method: "POST",

              headers: {
                Accept: "application/json",

                [csrf.headerName]: csrf.token,
              },

              credentials: "same-origin",

              cache: "no-store",

              body: formData,
            },
          );

          const attachmentData = await readJsonResponse(attachmentResponse);

          if (!attachmentResponse.ok || !attachmentData.success) {
            throw new Error(
              attachmentData.message || "Attachment upload failed.",
            );
          }
        } catch (error) {
          console.error("Service request attachment upload error:", error);

          attachmentUploadError =
            error.message || "The attachment could not be uploaded.";
        }
      }
    }

    /*
     * Once the real service request exists, remove the
     * resumed draft so it cannot remain as a stale duplicate.
     * Failure here must never roll back or misreport the
     * already-created service request.
     */
    if (draftIdToDeleteAfterSubmission) {
      try {
        const draftDeleteResponse = await fetch(
          `${API_ENDPOINTS.serviceRequestDrafts}?id=${encodeURIComponent(draftIdToDeleteAfterSubmission)}`,
          {
            method: "DELETE",

            headers: {
              Accept: "application/json",

              [csrf.headerName]: csrf.token,
            },

            credentials: "same-origin",

            cache: "no-store",
          },
        );

        const draftDeleteData = await readJsonResponse(draftDeleteResponse);

        if (!draftDeleteResponse.ok || !draftDeleteData.success) {
          throw new Error(
            draftDeleteData.message || "The saved draft could not be removed.",
          );
        }

        if (Number(state.currentDraftId) === draftIdToDeleteAfterSubmission) {
          resetServiceRequestDraftEditingState();
        }
      } catch (error) {
        console.error("Submitted request draft cleanup error:", error);

        draftCleanupError =
          error.message || "The saved draft could not be removed.";
      }
    }
    /*
     * Reload authoritative request records from MySQL.
     */
    await loadRequesterServiceRequests();
    await loadRequesterServiceRequestDrafts();

    renderAll();

    form.reset();

    closeModal("serviceRequestModal");

    openWorkspaceView("requester-records");

    if (attachmentUploadError || draftCleanupError) {
      const warnings = [];

      if (attachmentUploadError) {
        warnings.push(
          `the attachment was not saved (${attachmentUploadError})`,
        );
      }

      if (draftCleanupError) {
        warnings.push(`the saved draft was not removed (${draftCleanupError})`);
      }

      showToast(
        `${createdRequestNumber} was submitted, but ${warnings.join(" Also, ")}.`,
        "error",
      );
    } else if (selectedAttachment) {
      showToast(
        `${createdRequestNumber} and its attachment were submitted successfully.`,
        "success",
      );
    } else {
      showToast(`${createdRequestNumber} submitted successfully.`, "success");
    }
  } catch (error) {
    console.error("Service request submission error:", error);

    /*
     * Only report the entire request as failed when
     * creation itself did not succeed.
     */
    if (!requestCreated) {
      showToast(
        error.message || "Unable to submit the service request.",
        "error",
      );
    }
  } finally {
    setSubmitting(form, false);
  }
}

/* =========================
   FORM: PERSONNEL ACCESS
========================= */

function populatePersonnelDepartmentOptions(selectedDepartmentId = null) {
  const select = byId("personnelDepartment");

  if (!select) {
    return;
  }

  cacheDepartmentOptions();

  state.personnel.forEach((person) => {
    const departmentId = Number(person.departmentId);

    const departmentName = String(person.department || "").trim();

    if (Number.isInteger(departmentId) && departmentId > 0 && departmentName) {
      departmentNamesById.set(departmentId, departmentName);
    }
  });

  const placeholder = document.createElement("option");

  placeholder.value = "";
  placeholder.textContent = "Select department";

  const options = Array.from(departmentNamesById.entries())
    .sort((left, right) => left[1].localeCompare(right[1]))
    .map(([departmentId, departmentName]) => {
      const option = document.createElement("option");

      option.value = String(departmentId);

      option.textContent = departmentName;

      return option;
    });

  select.replaceChildren(placeholder, ...options);

  if (
    selectedDepartmentId != null &&
    options.some((option) => option.value === String(selectedDepartmentId))
  ) {
    select.value = String(selectedDepartmentId);
  }
}

function clearPersonnelAccessFields() {
  [
    "personnelName",
    "personnelEmail",
    "personnelTypeDisplay",
    "personnelStatusDisplay",
  ].forEach((id) => {
    const input = byId(id);

    if (input) {
      input.value = "";
    }
  });

  const department = byId("personnelDepartment");

  if (department) {
    department.value = "";
  }

  const role = byId("personnelRole");

  if (role) {
    role.value = "";
  }
}

function populatePersonnelAccessDetails(personnelId) {
  const numericPersonnelId = Number(personnelId);

  const person = state.personnel.find(
    (item) => Number(item.id) === numericPersonnelId,
  );

  if (!person) {
    clearPersonnelAccessFields();
    return;
  }

  const name = byId("personnelName");

  const email = byId("personnelEmail");

  const type = byId("personnelTypeDisplay");

  const status = byId("personnelStatusDisplay");

  if (name) {
    name.value = person.name || "";
  }

  if (email) {
    email.value = person.email || "";
  }

  if (type) {
    type.value = person.personnelType || "";
  }

  if (status) {
    status.value = person.status || "";
  }

  populatePersonnelDepartmentOptions(person.departmentId);

  const role = byId("personnelRole");

  if (role) {
    role.value = person.roleId == null ? "" : String(person.roleId);
  }
}

function populatePersonnelAccessManagement(selectedPersonnelId = null) {
  const select = byId("personnelTargetId");

  if (!select) {
    return;
  }

  const currentPersonnelId = Number(state.currentAccount?.personnelId);

  const placeholder = document.createElement("option");

  placeholder.value = "";
  placeholder.textContent = "Select personnel account";

  const options = state.personnel
    .filter((person) => Number(person.id) !== currentPersonnelId)
    .map((person) => {
      const option = document.createElement("option");

      option.value = String(person.id);

      option.textContent = `${person.name} - ${person.email}`;

      return option;
    });

  select.replaceChildren(placeholder, ...options);

  populatePersonnelDepartmentOptions();

  if (
    selectedPersonnelId != null &&
    options.some((option) => option.value === String(selectedPersonnelId))
  ) {
    select.value = String(selectedPersonnelId);

    populatePersonnelAccessDetails(selectedPersonnelId);
  } else {
    clearPersonnelAccessFields();
  }
}

async function handlePersonnelSubmit(event) {
  event.preventDefault();

  const form = event.currentTarget;

  const targetPersonnelId = Number(byId("personnelTargetId")?.value);

  const departmentId = Number(byId("personnelDepartment")?.value);

  const roleId = Number(byId("personnelRole")?.value);

  if (!Number.isInteger(targetPersonnelId) || targetPersonnelId <= 0) {
    showToast("Select a personnel account to manage.", "error");

    byId("personnelTargetId")?.focus();

    return;
  }

  if (targetPersonnelId === Number(state.currentAccount?.personnelId)) {
    showToast(
      "You cannot change your own personnel access assignment.",
      "error",
    );

    return;
  }

  if (!Number.isInteger(departmentId) || departmentId <= 0) {
    showToast("Select a valid department.", "error");

    byId("personnelDepartment")?.focus();

    return;
  }

  if (!Number.isInteger(roleId) || roleId < 1 || roleId > 4) {
    showToast("Select a valid system role.", "error");

    byId("personnelRole")?.focus();

    return;
  }

  setSubmitting(form, true);

  try {
    const csrf = await getCsrfToken();

    const headers = {
      "Content-Type": "application/json",
      Accept: "application/json",
    };

    headers[csrf.headerName] = csrf.token;

    const response = await fetch(API_ENDPOINTS.personnel, {
      method: "PUT",
      headers,
      credentials: "same-origin",
      cache: "no-store",
      body: JSON.stringify({
        personnelId: targetPersonnelId,
        departmentId,
        roleId,
      }),
    });

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to update personnel access.");
    }

    await loadAdministratorPersonnel();

    populatePersonnelAccessManagement(targetPersonnelId);

    showToast(
      data.message || "Personnel access updated successfully.",
      "success",
    );
  } catch (error) {
    console.error("Personnel access update failed:", error);

    showToast(error.message || "Unable to update personnel access.", "error");
  } finally {
    setSubmitting(form, false);
  }
}

/* =========================
   FORM: TECHNICIAN UPDATE
========================= */

/* =========================
   FORM: WORK ORDER CREATION
========================= */
async function handleWorkOrderSubmit(event) {
  event.preventDefault();

  const form = event.currentTarget;
  const requestInput = byId("workOrderRequestId");
  const descriptionInput = byId("workDescription");
  const requestNumber = requestInput.value.trim().toUpperCase();
  const description = descriptionInput.value.trim();

  requestInput.setCustomValidity("");
  descriptionInput.setCustomValidity("");

  if (!/^SR-\d{4}-\d{6}$/.test(requestNumber)) {
    requestInput.setCustomValidity("Use the format SR-YYYY-NNNNNN.");
    requestInput.reportValidity();
    return;
  }
  if (description.length < 10 || description.length > 2000) {
    descriptionInput.setCustomValidity(
      "Work description must contain 10 to 2000 characters.",
    );
    descriptionInput.reportValidity();
    return;
  }

  setSubmitting(form, true);
  try {
    const csrf = await getCsrfToken();
    const response = await fetch(API_ENDPOINTS.workOrders, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token,
      },
      credentials: "same-origin",
      body: JSON.stringify({
        requestNumber,
        workDescription: description,
      }),
    });
    const data = await readJsonResponse(response);
    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to create the work order.");
    }
    showToast("Work order created successfully.", "success");
    await loadWorkOrders();
    form.reset();
    renderAll();
  } catch (error) {
    console.error("Work-order creation failed:", error);
    showToast(error.message || "Unable to create the work order.", "error");
  } finally {
    setSubmitting(form, false);
  }
}

async function openWorkOrderAssignment(workOrder) {
  if (!workOrder || workOrder.status !== "Created" || workOrder.assignmentId) {
    return;
  }

  const form = byId("workOrderAssignmentForm");
  const technicianSelect = byId("assignmentTechnician");
  if (!form || !technicianSelect) return;

  try {
    if (!state.personnel.length) {
      await loadAdministratorPersonnel();
    }

    const departmentId = Number(workOrder.departmentId);
    const eligible = state.personnel
      .filter(
        (person) =>
          Number(person.roleId) === 4 &&
          String(person.status || "").toLowerCase() === "active" &&
          Number(person.departmentId) === departmentId,
      )
      .sort((left, right) => left.name.localeCompare(right.name));

    if (!eligible.length) {
      showToast(
        "No active technicians are available in this work order department.",
        "error",
      );
      return;
    }

    const workOrderId = Number(workOrder.databaseId);
    if (!Number.isInteger(workOrderId) || workOrderId <= 0) {
      showToast("The work-order identifier is unavailable.", "error");
      return;
    }

    form.dataset.workOrderId = String(workOrderId);
    state.pendingAssignmentWorkOrderId = workOrderId;
    byId("assignmentWorkOrderNumber").value = workOrder.id || "";
    byId("assignmentDepartment").value = workOrder.serviceUnit || "";

    const placeholder = document.createElement("option");
    placeholder.value = "";
    placeholder.textContent = "Select an active technician";
    technicianSelect.replaceChildren(
      placeholder,
      ...eligible.map((person) => {
        const option = document.createElement("option");
        option.value = String(person.id);
        option.textContent = person.email
          ? `${person.name} - ${person.email}`
          : person.name;
        return option;
      }),
    );
    byId("assignmentNotes").value = "";
    openModal("workOrderAssignmentModal", "#assignmentTechnician");
  } catch (error) {
    console.error("Unable to prepare work-order assignment:", error);
    showToast(error.message || "Unable to load technician options.", "error");
  }
}

async function handleWorkOrderAssignmentSubmit(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const workOrderId = Number(form.dataset.workOrderId || state.pendingAssignmentWorkOrderId);
  const technicianId = Number(byId("assignmentTechnician")?.value);
  const notes = byId("assignmentNotes")?.value.trim() || "";

  if (!Number.isInteger(workOrderId) || workOrderId <= 0) {
    showToast("The work-order identifier is unavailable.", "error");
    return;
  }
  if (!Number.isInteger(technicianId) || technicianId <= 0) {
    showToast("Select an active technician.", "error");
    byId("assignmentTechnician")?.focus();
    return;
  }
  if (notes && (notes.length < 3 || notes.length > 1000)) {
    showToast("Assignment notes must contain 3 to 1000 characters when provided.", "error");
    byId("assignmentNotes")?.focus();
    return;
  }

  setSubmitting(form, true);
  try {
    const csrf = await getCsrfToken();
    const response = await fetch(API_ENDPOINTS.workOrders, {
      method: "PUT",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token,
      },
      credentials: "same-origin",
      body: JSON.stringify({
        action: "assign",
        workOrderId,
        technicianId,
        assignmentNotes: notes,
      }),
    });
    const data = await readJsonResponse(response);
    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to assign the technician.");
    }
    showToast(data.message || "Technician assigned successfully.", "success");
    closeModal("workOrderAssignmentModal");
    form.reset();
    state.pendingAssignmentWorkOrderId = null;
    await loadWorkOrders();
    renderAll();
  } catch (error) {
    console.error("Work-order assignment failed:", error);
    showToast(error.message || "Unable to assign the technician.", "error");
  } finally {
    setSubmitting(form, false);
  }
}

function validateTechnicianProgressControl(input) {
  if (input.disabled || input.readOnly) return true;
  const value = input.value.trim();
  if (input.id === "progressPercentageInput") {
    const percentage = Number(value);
    if (!/^\d+$/.test(value) || !Number.isInteger(percentage) || percentage < 1 || percentage > 99) {
      return setFieldError(input, "Enter a whole-number percentage from 1 to 99.");
    }
  } else {
    const minimum = input.id === "progressCompletionSummary" ? 10 : 5;
    if (!hasMeaningfulText(value, minimum) || value.length > 2000) {
      return setFieldError(input, `Enter ${minimum} to 2000 meaningful characters.`);
    }
  }
  input.value = value;
  return setFieldSuccess(input);
}

async function handleProgressUpdateSubmit(event) {
  event.preventDefault();
  const session = technicianSession();
  if (!isTechnicianSessionCurrent(session) || state.technicianSubmitting || state.technicianLoading) return;
  const order = state.technicianWorkOrder;
  const id = order?.databaseId;
  const action = state.technicianAction;
  if (!Number.isSafeInteger(id) || id <= 0 || state.technicianDetailLoading ||
      id !== state.technicianWorkOrderId || !technicianActionsForStatus(order?.status).includes(action)) {
    showToast("Open a work order and choose an available action first.", "error");
    return;
  }
  const form = event.currentTarget;
  if (!validateForm(form)) return;
  const payload = { action, workOrderId: id, progressNotes: byId("progressActionTaken").value.trim() };
  if (action === "progress") payload.progressPercentage = Number(byId("progressPercentageInput").value);
  if (action === "complete") payload.completionSummary = byId("progressCompletionSummary").value.trim();
  const body = JSON.stringify(payload);
  if (new TextEncoder().encode(body).length > 8192) {
    showToast("The update is too large. Shorten the notes or completion summary.", "error");
    return;
  }
  const detailVersion = state.technicianDetailVersion;
  const currentSelection = () => isTechnicianSessionCurrent(session) &&
    id === state.technicianWorkOrderId && detailVersion === state.technicianDetailVersion;
  state.technicianQueueVersion += 1;
  state.technicianLoading = false;
  state.technicianSubmitting = true;
  renderTechnicianProgressActions();
  renderTechnicianWorkTable();
  let saved = false;
  let putSent = false;
  try {
    const csrf = await getCsrfToken();
    if (!currentSelection()) return;
    putSent = true;
    const response = await fetch(API_ENDPOINTS.workOrderProgress, {
      method: "PUT", credentials: "same-origin",
      headers: { Accept: "application/json", "Content-Type": "application/json", [csrf.headerName]: csrf.token },
      body,
    });
    const data = await readTechnicianResponse(response);
    if (!currentSelection()) return;
    saved = true;
    state.technicianAction = "";
    showToast(data.message || "Work-order update saved.", "success");
    const queueLoaded = await loadTechnicianWorkOrders(false);
    if (!isTechnicianSessionCurrent(session)) return;
    const detail = await loadTechnicianWorkOrderDetails(id, false);
    if (!isTechnicianSessionCurrent(session)) return;
    if (!queueLoaded || !detail) {
      clearTechnicianSelection();
      state.technicianWorkOrderId = id;
      showToast("The update was saved, but refreshing the work order failed. Select Refresh or reopen it.", "warning");
    }
  } catch (error) {
    if (!isTechnicianSessionCurrent(session)) return;
    if (saved) {
      clearTechnicianSelection();
      state.technicianWorkOrderId = id;
      showToast("The update was saved, but refreshing failed. Select Refresh or reopen the work order.", "warning");
    } else {
      handleTechnicianRequestError(error, true);
      if (!isTechnicianSessionCurrent(session)) return;
      if (error.status === 409) {
        state.technicianAction = "";
        await loadTechnicianWorkOrders(false);
        if (isTechnicianSessionCurrent(session)) await loadTechnicianWorkOrderDetails(id, false);
      } else if (putSent && (!error.status || error.status >= 500 || error.status === 200)) {
        clearTechnicianSelection();
        state.technicianWorkOrderId = id;
        showToast("The save could not be confirmed. Refresh the work order before trying again.", "warning");
      }
    }
  } finally {
    if (isTechnicianSessionCurrent(session)) {
      state.technicianSubmitting = false;
      renderTechnicianProgressActions();
      renderTechnicianWorkspace();
    }
  }
}

/* =========================
   ADMIN / APPROVAL ACTIONS
========================= */
async function loadForwardReferenceData() {
  const [categoryResponse, departmentResponse] = await Promise.all([
    fetch(API_ENDPOINTS.serviceCategories, {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      credentials: "same-origin",
      cache: "no-store",
    }),

    fetch(API_ENDPOINTS.departments, {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      credentials: "same-origin",
      cache: "no-store",
    }),
  ]);

  const [categoryData, departmentData] = await Promise.all([
    readJsonResponse(categoryResponse),
    readJsonResponse(departmentResponse),
  ]);

  if (
    !categoryResponse.ok ||
    !categoryData.success ||
    !Array.isArray(categoryData.categories)
  ) {
    throw new Error(
      categoryData.message || "Unable to load service categories.",
    );
  }

  if (
    !departmentResponse.ok ||
    !departmentData.success ||
    !Array.isArray(departmentData.departments)
  ) {
    throw new Error(departmentData.message || "Unable to load departments.");
  }

  const categories = categoryData.categories
    .map((category) => ({
      id: Number(category.categoryId),

      name: String(category.categoryName || "").trim(),

      defaultDepartmentId: Number(category.defaultDepartmentId) || null,
    }))
    .filter(
      (category) =>
        Number.isInteger(category.id) && category.id > 0 && category.name,
    );

  const departments = departmentData.departments
    .map((department) => ({
      id: Number(department.departmentId),

      name: String(department.departmentName || "").trim(),
    }))
    .filter(
      (department) =>
        Number.isInteger(department.id) && department.id > 0 && department.name,
    );

  if (!categories.length) {
    throw new Error("No active service categories are available.");
  }

  if (!departments.length) {
    throw new Error("No departments are available.");
  }

  return {
    categories,
    departments,
  };
}

async function openForwardRequestModal(request) {
  const form = byId("adminForwardForm");

  const categorySelect = byId("adminForwardCategory");

  const prioritySelect = byId("adminForwardPriority");

  const departmentSelect = byId("adminForwardDepartment");

  const submitButton = byId("adminForwardSubmit");

  const error = byId("adminForwardError");

  const analysisButton = byId("adminRunAdvisoryAnalysis");

  if (
    !form ||
    !categorySelect ||
    !prioritySelect ||
    !departmentSelect ||
    !submitButton ||
    !error ||
    !analysisButton
  ) {
    showToast("The Forward Request form is unavailable.", "error");

    return;
  }

  byId("adminForwardRequestLabel").textContent =
    `${request.id} - ${request.title}`;

  byId("adminForwardRequestedCategory").textContent =
    request.requestedCategory || request.category || "Not specified";

  renderAdminAdvisoryRecommendation(request);
  analysisButton.disabled = state.adminAnalysisRequestId !== null;
  analysisButton.onclick = () => runAdvisoryAnalysis(request, analysisButton);

  categorySelect.replaceChildren(new Option("Loading categories...", ""));

  departmentSelect.replaceChildren(new Option("Loading departments...", ""));

  prioritySelect.value = ["Low", "Medium", "High", "Urgent"].includes(
    request.preferredPriority,
  )
    ? request.preferredPriority
    : "";

  error.textContent = "";
  submitButton.disabled = true;

  openModal("adminForwardModal", "#adminForwardCategory");

  try {
    const { categories, departments } = await loadForwardReferenceData();

    const categoryPlaceholder = new Option("Select final category", "");

    const categoryOptions = categories.map(
      (category) => new Option(category.name, String(category.id)),
    );

    categorySelect.replaceChildren(categoryPlaceholder, ...categoryOptions);

    const departmentPlaceholder = new Option("Select routed department", "");

    const departmentOptions = departments.map(
      (department) => new Option(department.name, String(department.id)),
    );

    departmentSelect.replaceChildren(
      departmentPlaceholder,
      ...departmentOptions,
    );

    const requestedCategoryId = Number(request.requestedCategoryId);

    if (
      Number.isInteger(requestedCategoryId) &&
      categories.some((category) => category.id === requestedCategoryId)
    ) {
      categorySelect.value = String(requestedCategoryId);
    }

    const applyDefaultDepartment = () => {
      const selectedCategoryId = Number(categorySelect.value);

      const selectedCategory = categories.find(
        (category) => category.id === selectedCategoryId,
      );

      const defaultDepartmentId = selectedCategory?.defaultDepartmentId;

      if (
        defaultDepartmentId &&
        departments.some((department) => department.id === defaultDepartmentId)
      ) {
        departmentSelect.value = String(defaultDepartmentId);
      }
    };

    applyDefaultDepartment();

    categorySelect.onchange = applyDefaultDepartment;

    form.onsubmit = (event) => forwardRequest(event, request);

    submitButton.disabled = false;
  } catch (loadError) {
    console.error("Forward reference data failed:", loadError);

    error.textContent =
      loadError.message || "Unable to prepare the Forward Request form.";

    showToast(error.textContent, "error");
  }
}

function renderAdminAdvisoryRecommendation(request) {
  const heading = byId("adminForwardAiRecommendation");
  const details = byId("adminForwardAiDetails");

  if (!heading || !details) {
    return;
  }

  heading.textContent = request.aiCategory || "Awaiting AI analysis";
  details.replaceChildren();

  const status = request.aiStatus || "Pending";
  details.appendChild(
    createElement(
      "span",
      "",
      `Status: ${status}. ${request.aiRecommendationMethod || "AI recommendations are advisory only."}`,
    ),
  );

  [request.aiCategoryExplanation, request.aiPriorityExplanation, request.aiDuplicateExplanation]
    .filter(Boolean)
    .forEach((explanation) => {
      details.appendChild(createElement("span", "", explanation));
    });

  renderAdminRankedDuplicateCandidates(request);
}

function renderAdminRankedDuplicateCandidates(request) {
  const container = byId("adminForwardAiDuplicateCandidates");

  if (!container) {
    return;
  }

  container.replaceChildren();

  if (String(request.aiStatus || "").toLowerCase() !== "completed") {
    return;
  }

  const candidates = Array.isArray(request.aiDuplicateCandidates)
    ? request.aiDuplicateCandidates
    : [];

  if (!candidates.length) {
    container.appendChild(
      createElement("span", "helper-text", "No likely duplicate requests were identified."),
    );
    return;
  }

  const heading = createElement("strong", "", "Possible Duplicate Requests");
  container.appendChild(heading);

  candidates.forEach((candidate, index) => {
    const item = createElement("div", "compact-item");
    const copy = createElement("div");
    const score = Math.round(candidate.similarityScore * 100);

    copy.append(
      createElement("strong", "", `${index + 1}. ${candidate.requestNumber}`),
      createElement("span", "", candidate.title),
      createElement(
        "span",
        "",
        `${candidate.category} • ${candidate.currentStatus} • Similarity score: ${score}%`,
      ),
    );

    if (candidate.explanation) {
      copy.appendChild(createElement("span", "", candidate.explanation));
    }

    const review = createElement("button", "button button-small button-secondary", "Review");
    review.type = "button";
    review.addEventListener("click", () => {
      closeModal("adminForwardModal");
      openDuplicateRequestModal(request, candidate.requestId);
    });

    item.append(copy, review);
    container.appendChild(item);
  });
}

async function runAdvisoryAnalysis(request, button = null) {
  const requestId = Number(request.databaseId);

  if (!Number.isSafeInteger(requestId) || requestId <= 0) {
    showToast("The database request identifier is unavailable.", "error");
    return;
  }

  if (state.adminAnalysisRequestId !== null) {
    return;
  }

  const account = state.currentAccount;
  state.adminAnalysisRequestId = requestId;
  const originalText = button?.textContent || "Run Advisory Analysis";

  if (button) {
    button.disabled = true;
    button.textContent = "Analyzing...";
  }

  try {
    const csrf = await getCsrfToken();
    const response = await fetch(API_ENDPOINTS.aiRecommendationAnalysis, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token,
      },
      credentials: "same-origin",
      cache: "no-store",
      body: JSON.stringify({ requestId }),
    });
    const data = await readJsonResponse(response);

    if (state.currentAccount !== account || state.activeRole !== "administrator") {
      return;
    }

    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to run advisory analysis.");
    }

    const recommendation = data.recommendation || {};
    if (!data.available) {
      request.aiStatus = recommendation.analysisStatus || "Unavailable";
      request.aiCategory = "Advisory analysis unavailable";
      request.aiPriority = "Unavailable";
      request.aiRecommendationMethod = "None";
      request.aiCategoryExplanation = "";
      request.aiPriorityExplanation = "";
      request.aiDuplicateExplanation = "";
      request.aiDuplicateCandidates = [];
      request.duplicateCheck = "No duplicate analysis available";
      await refreshAdminAdvisoryRequest(request, account);
      if (state.currentAccount !== account || state.activeRole !== "administrator") {
        return;
      }
      renderAdminAdvisoryRecommendation(request);
      renderAdminReviewTable();
      renderAdminAiSummary();
      showToast(data.message || "Advisory analysis is currently unavailable.", "warning");
    } else {
      request.aiStatus = recommendation.analysisStatus || "Completed";
      request.aiCategory = recommendation.recommendedCategoryName || "No AI category recommendation";
      request.aiPriority = recommendation.recommendedPriority || "No AI priority recommendation";
      request.aiRecommendationMethod = recommendation.recommendationMethod || "Rule-Based";
      request.aiCategoryExplanation = recommendation.categoryExplanation || "";
      request.aiPriorityExplanation = recommendation.priorityExplanation || "";
      request.aiDuplicateExplanation = recommendation.duplicateExplanation || "";
      request.aiDuplicateCandidates = [];
      request.duplicateCheck = recommendation.possibleDuplicateRequestNumber
        ? `Possible duplicate: ${recommendation.possibleDuplicateRequestNumber}`
        : "No duplicate indicated";
      await refreshAdminAdvisoryRequest(request, account);
      if (state.currentAccount !== account || state.activeRole !== "administrator") {
        return;
      }
      renderAdminAdvisoryRecommendation(request);
      renderAdminReviewTable();
      renderAdminAiSummary();
      showToast("Advisory analysis is ready for review.", "success");
    }
  } catch (error) {
    if (state.currentAccount === account && state.activeRole === "administrator") {
      showToast(error.message || "Unable to run advisory analysis.", "error");
    }
  } finally {
    if (state.adminAnalysisRequestId === requestId) {
      state.adminAnalysisRequestId = null;
    }
    if (button && state.currentAccount === account) {
      button.disabled = false;
      button.textContent = originalText;
    }
  }
}

async function refreshAdminAdvisoryRequest(request, account) {
  try {
    await loadAdministratorServiceRequestReviews();
    if (state.currentAccount !== account || state.activeRole !== "administrator") {
      return;
    }
    const refreshed = state.adminReviewRequests.find(
      (item) => Number(item.databaseId) === Number(request.databaseId),
    );
    if (refreshed) {
      Object.assign(request, refreshed);
    }
  } catch (error) {
    console.error("Unable to refresh advisory recommendations:", error);
  }
}

async function forwardRequest(event, request) {
  event.preventDefault();

  const categorySelect = byId("adminForwardCategory");

  const prioritySelect = byId("adminForwardPriority");

  const departmentSelect = byId("adminForwardDepartment");

  const submitButton = byId("adminForwardSubmit");

  const error = byId("adminForwardError");

  error.textContent = "";

  const requestId = Number(request.databaseId);

  const finalCategoryId = Number(categorySelect.value);

  const finalPriority = String(prioritySelect.value || "").trim();

  const routedDepartmentId = Number(departmentSelect.value);

  if (!Number.isInteger(requestId) || requestId <= 0) {
    error.textContent = "The database request identifier is unavailable.";

    return;
  }

  if (!Number.isInteger(finalCategoryId) || finalCategoryId <= 0) {
    error.textContent = "Select the final service category.";

    categorySelect.focus();
    return;
  }

  if (!["Low", "Medium", "High", "Urgent"].includes(finalPriority)) {
    error.textContent = "Select a valid final priority.";

    prioritySelect.focus();
    return;
  }

  if (!Number.isInteger(routedDepartmentId) || routedDepartmentId <= 0) {
    error.textContent = "Select the routed department.";

    departmentSelect.focus();
    return;
  }

  submitButton.disabled = true;
  submitButton.textContent = "Forwarding...";

  try {
    const csrf = await getCsrfToken();

    const response = await fetch(API_ENDPOINTS.serviceRequestReviews, {
      method: "PUT",

      headers: {
        Accept: "application/json",

        "Content-Type": "application/json",

        [csrf.headerName]: csrf.token,
      },

      credentials: "same-origin",

      cache: "no-store",

      body: JSON.stringify({
        requestId,
        finalCategoryId,
        finalPriority,
        routedDepartmentId,
      }),
    });

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to forward the service request.");
    }

    closeModal("adminForwardModal");

    showToast(
      `${request.id} was forwarded for department approval.`,
      "success",
    );

    await loadAdministratorServiceRequestReviews();
  } catch (forwardError) {
    console.error("Service-request forwarding failed:", forwardError);

    error.textContent =
      forwardError.message || "Unable to forward the service request.";

    showToast(error.textContent, "error");
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = "Forward Request";
  }
}

async function loadDuplicateCandidates(duplicateRequestId) {
  const response = await fetch(
    `${API_ENDPOINTS.serviceRequestDuplicates}?duplicateRequestId=${encodeURIComponent(
      duplicateRequestId,
    )}`,
    {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      credentials: "same-origin",
      cache: "no-store",
    },
  );

  const data = await readJsonResponse(response);

  if (!response.ok || !data.success || !Array.isArray(data.candidates)) {
    throw new Error(
      data.message || "Unable to load possible original requests.",
    );
  }

  return data.candidates;
}

async function openDuplicateRequestModal(request, preferredOriginalRequestId = null) {
  const form = byId("adminDuplicateForm");

  const originalSelect = byId("adminDuplicateOriginal");

  const reason = byId("adminDuplicateReason");

  const error = byId("adminDuplicateError");

  const submitButton = byId("adminDuplicateSubmit");

  if (!form || !originalSelect || !reason || !error || !submitButton) {
    showToast("The duplicate confirmation form is unavailable.", "error");

    return;
  }

  const duplicateRequestId = Number(request.databaseId);

  if (!Number.isInteger(duplicateRequestId) || duplicateRequestId <= 0) {
    showToast("The database request identifier is unavailable.", "error");

    return;
  }

  byId("adminDuplicateRequestLabel").textContent =
    `${request.id} - ${request.title}`;

  originalSelect.replaceChildren(new Option("Loading requests...", ""));

  reason.value = "";
  error.textContent = "";
  submitButton.disabled = true;

  openModal("adminDuplicateModal", "#adminDuplicateOriginal");

  try {
    const candidates = await loadDuplicateCandidates(duplicateRequestId);

    const placeholder = new Option(
      candidates.length
        ? "Select original request"
        : "No eligible original requests",
      "",
    );

    const options = candidates.map(
      (candidate) =>
        new Option(
          `${candidate.requestNumber} - ${candidate.title} (${candidate.currentStatus})`,
          String(candidate.requestId),
        ),
    );

    originalSelect.replaceChildren(placeholder, ...options);

    const preferredId = Number(preferredOriginalRequestId);
    if (Number.isSafeInteger(preferredId) && preferredId > 0) {
      if (candidates.some((candidate) => Number(candidate.requestId) === preferredId)) {
        originalSelect.value = String(preferredId);
      } else {
        error.textContent = "This suggested request is no longer available for confirmation.";
      }
    }

    form.onsubmit = (event) => flagDuplicate(event, request);

    submitButton.disabled = candidates.length === 0;
  } catch (loadError) {
    console.error("Duplicate candidate loading failed:", loadError);

    error.textContent =
      loadError.message || "Unable to load possible original requests.";

    showToast(error.textContent, "error");
  }
}

async function flagDuplicate(event, request) {
  event.preventDefault();

  const originalSelect = byId("adminDuplicateOriginal");

  const reasonInput = byId("adminDuplicateReason");

  const error = byId("adminDuplicateError");

  const submitButton = byId("adminDuplicateSubmit");

  error.textContent = "";

  const duplicateRequestId = Number(request.databaseId);

  const originalRequestId = Number(originalSelect.value);

  const confirmationReason = String(reasonInput.value || "").trim();

  if (!Number.isInteger(duplicateRequestId) || duplicateRequestId <= 0) {
    error.textContent = "The duplicate request identifier is unavailable.";

    return;
  }

  if (!Number.isInteger(originalRequestId) || originalRequestId <= 0) {
    error.textContent = "Select the original request.";

    originalSelect.focus();
    return;
  }

  if (originalRequestId === duplicateRequestId) {
    error.textContent = "A request cannot be a duplicate of itself.";

    return;
  }

  if (confirmationReason.length < 5) {
    error.textContent =
      "Enter a duplicate confirmation reason of at least 5 characters.";

    reasonInput.focus();
    return;
  }

  if (confirmationReason.length > 1000) {
    error.textContent =
      "The duplicate confirmation reason must not exceed 1000 characters.";

    reasonInput.focus();
    return;
  }

  submitButton.disabled = true;
  submitButton.textContent = "Confirming...";

  try {
    const csrf = await getCsrfToken();

    const response = await fetch(API_ENDPOINTS.serviceRequestDuplicates, {
      method: "POST",

      headers: {
        Accept: "application/json",

        "Content-Type": "application/json",

        [csrf.headerName]: csrf.token,
      },

      credentials: "same-origin",

      cache: "no-store",

      body: JSON.stringify({
        duplicateRequestId,
        originalRequestId,
        confirmationReason,
      }),
    });

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success) {
      throw new Error(
        data.message || "Unable to mark the service request as duplicate.",
      );
    }

    closeModal("adminDuplicateModal");

    showToast(`${request.id} was confirmed as a duplicate.`, "success");

    await loadAdministratorServiceRequestReviews();
  } catch (duplicateError) {
    console.error("Duplicate confirmation failed:", duplicateError);

    error.textContent =
      duplicateError.message ||
      "Unable to mark the service request as duplicate.";

    showToast(error.textContent, "error");
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = "Confirm Duplicate";
  }
}

async function decideApproval(requestId, decision, remarks = "") {
  const approval = state.approvals.find(
    (item) =>
      String(item.requestNumber || item.requestId) === String(requestId),
  );

  if (!approval) {
    showToast("The approval record is no longer available.", "error");
    return;
  }

  try {
    const csrf = await getCsrfToken();
    const response = await fetch(API_ENDPOINTS.approvals, {
      method: "PUT",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token,
      },
      credentials: "same-origin",
      cache: "no-store",
      body: JSON.stringify({
        approvalId: Number(approval.approvalId),
        decision,
        remarks,
      }),
    });
    const data = await readJsonResponse(response);

    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to record the approval decision.");
    }

    showToast(`${approval.requestNumber} was ${decision.toLowerCase()}.`, "success");
    await loadApproverApprovals();
  } catch (error) {
    console.error("Approval decision failed:", error);
    showToast(error.message || "Unable to record the approval decision.", "error");
  }
}

/* =========================
   EVENT DELEGATION:
   REQUESTER TABLE
========================= */
function handleRequestTableAction(event) {
  const button = event.target.closest("[data-action]");

  if (!button) {
    return;
  }

  const request = findRequest(button.dataset.id);

  if (!request) {
    return;
  }

  if (button.dataset.action === "view-request") {
    showRequestDetails(request);
  }

  if (button.dataset.action === "track-request") {
    showRequestHistory(request);
  }
}

/* =========================
   EVENT DELEGATION:
   ADMIN TABLE
========================= */
function handleAdminTableAction(event) {
  const button = event.target.closest("[data-action]");

  if (!button) {
    return;
  }

  const request = findRequest(button.dataset.id);

  if (!request) {
    return;
  }

  if (button.dataset.action === "view-admin-request") {
    showRequestDetails(request);
  }

  if (button.dataset.action === "analyze-request") {
    runAdvisoryAnalysis(request, button);
  }

  if (button.dataset.action === "forward-request") {
    openForwardRequestModal(request);
  }

  if (button.dataset.action === "duplicate-request") {
    openDuplicateRequestModal(request);
  }
}

/* =========================
   EVENT DELEGATION:
   APPROVAL TABLE
========================= */
function handleApprovalTableAction(event) {
  const button = event.target.closest("[data-action]");

  if (!button) {
    return;
  }

  const approval = state.approvals.find(
    (item) =>
      String(item.requestNumber || item.requestId) ===
      String(button.dataset.id),
  );

  if (!approval) {
    showToast("The approval record is no longer available.", "error");
    return;
  }

  const requestLabel =
    approval.requestNumber || `Request #${approval.requestId}`;

  if (button.dataset.action === "view-approval") {
    showDetails(requestLabel, "Approval Request", [
      {
        label: "Request Number",
        value: requestLabel,
      },
      {
        label: "Title",
        value: approval.title || "Service request",
        full: true,
      },
      {
        label: "Requester",
        value: approval.requesterName || "Not available",
      },
      {
        label: "Department",
        value: approval.departmentName || "Not available",
      },
      {
        label: "Location",
        value: approval.location || "Not available",
      },
      {
        label: "Category",
        value: approval.category || "Not available",
      },
      {
        label: "Priority",
        value: approval.priority || "Not available",
        badge: true,
      },
      {
        label: "Status",
        value: approval.currentStatus || "Awaiting Approval",
        badge: true,
      },
      {
        label: "Decision",
        value: approval.decision || "Pending",
        badge: true,
      },
      {
        label: "Description",
        value: approval.description || "No description provided.",
        full: true,
      },
    ]);

    return;
  }

  if (button.dataset.action === "approve-request") {
    openConfirm({
      title: "Approve this request?",
      message: `${requestLabel} will be recorded as Approved and may proceed to work-order assignment.`,
      confirmLabel: "Approve Request",
      requireRemarks: true,
      onConfirm: (remarks) =>
        decideApproval(requestLabel, "Approved", remarks),
    });

    return;
  }

  if (button.dataset.action === "reject-request") {
    openConfirm({
      title: "Reject this request?",
      message: `${requestLabel} will be recorded as Rejected in the approval history.`,
      confirmLabel: "Reject Request",
      type: "danger",
      requireRemarks: true,
      onConfirm: (remarks) =>
        decideApproval(requestLabel, "Rejected", remarks),
    });
  }
}

/* =========================
   EVENT DELEGATION:
   WORK ORDER TABLE
========================= */
function handleWorkOrderTableAction(event) {
  const button = event.target.closest("[data-action]");

  if (!button) {
    return;
  }

  const workOrder = findWorkOrder(button.dataset.id);

  if (!workOrder) {
    return;
  }

  if (button.dataset.action === "view-work-order") {
    showWorkOrderDetails(workOrder);
  }

  if (button.dataset.action === "assign-work-order") {
    openWorkOrderAssignment(workOrder);
  }

  if (button.dataset.action === "edit-work-order") {
    populateWorkOrderForm(workOrder);

    byId("workOrderId")?.focus();

    showToast(`${workOrder.id} loaded into the work-order form.`, "info");
  }
}

/* =========================
   EVENT DELEGATION:
   TECHNICIAN TABLE
========================= */
async function handleTechnicianTableAction(event) {
  const button = event.target.closest("[data-action]");

  if (!button || state.technicianSubmitting ||
      !["view-tech-work", "update-tech-work"].includes(button.dataset.action)) {
    return;
  }

  const session = technicianSession();
  const action = button.dataset.action;
  if (!isTechnicianSessionCurrent(session)) return;
  if (action === "update-tech-work") {
    openWorkspaceView("technician-update");
  }
  const order = await loadTechnicianWorkOrderDetails(button.dataset.id);
  if (!order || !isTechnicianSessionCurrent(session) || state.technicianWorkOrder !== order) return;
  if (action === "view-tech-work") showTechnicianWorkOrderDetails(order);
  else byId("technicianProgressActions").querySelector("button")?.focus();
}

/* =========================
   INITIALIZATION / EVENTS
========================= */
function setProfilePhotoControlsBusy(busy) {
  [
    "profilePhotoChangeButton",
    "profilePhotoChangeTextButton",
    "profilePhotoRemoveButton",
  ].forEach((id) => {
    const button = byId(id);

    if (button) {
      button.disabled = busy;

      if (busy) {
        button.setAttribute("aria-busy", "true");
      } else {
        button.removeAttribute("aria-busy");
      }
    }
  });
}

function openProfilePhotoPicker() {
  const input = byId("profilePhotoInput");

  if (!input) {
    return;
  }

  /*
   * Clear the previous selection so choosing
   * the same file again still triggers change.
   */
  input.value = "";
  input.click();
}

async function handleProfilePhotoFileChange(event) {
  const input = event.currentTarget;

  const file = input.files?.[0];

  if (!file) {
    return;
  }

  const maxBytes = 5 * 1024 * 1024;

  const allowedTypes = new Set(["image/jpeg", "image/png", "image/webp"]);

  if (file.size > maxBytes) {
    input.value = "";

    showToast("Profile photos must not exceed 5 MB.", "error");

    return;
  }

  /*
   * This is only a browser-side convenience check.
   * The Java backend performs the authoritative
   * image-signature validation.
   */
  if (file.type && !allowedTypes.has(file.type.toLowerCase())) {
    input.value = "";

    showToast("Please choose a JPG, PNG, or WEBP image.", "error");

    return;
  }

  setProfilePhotoControlsBusy(true);

  try {
    const csrf = await getCsrfToken();

    const formData = new FormData();

    formData.append("photo", file);

    const response = await fetch(API_ENDPOINTS.profilePhoto, {
      method: "POST",

      headers: {
        Accept: "application/json",
        [csrf.headerName]: csrf.token,
      },

      credentials: "same-origin",

      cache: "no-store",

      body: formData,
    });

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success || !data.profileImageFileName) {
      throw new Error(data.message || "Unable to update the profile photo.");
    }

    if (state.currentAccount) {
      state.currentAccount.profileImageFileName = String(
        data.profileImageFileName,
      );
    }

    renderWorkspaceAvatar(
      state.userName,
      state.currentAccount?.profileImageFileName,
    );

    showToast(data.message || "Profile photo updated successfully.", "success");
  } catch (error) {
    console.error("Profile photo upload failed:", error);

    showToast(error.message || "Unable to upload the profile photo.", "error");
  } finally {
    input.value = "";

    setProfilePhotoControlsBusy(false);
  }
}

async function removeProfilePhoto() {
  if (!state.currentAccount?.profileImageFileName) {
    return;
  }

  const confirmed = window.confirm("Remove your current profile photo?");

  if (!confirmed) {
    return;
  }

  setProfilePhotoControlsBusy(true);

  try {
    const csrf = await getCsrfToken();

    const response = await fetch(API_ENDPOINTS.profilePhoto, {
      method: "DELETE",

      headers: {
        Accept: "application/json",
        [csrf.headerName]: csrf.token,
      },

      credentials: "same-origin",

      cache: "no-store",
    });

    const data = await readJsonResponse(response);

    if (!response.ok || !data.success) {
      throw new Error(data.message || "Unable to remove the profile photo.");
    }

    state.currentAccount.profileImageFileName = "";

    renderWorkspaceAvatar(state.userName, "");

    showToast(data.message || "Profile photo removed successfully.", "success");
  } catch (error) {
    console.error("Profile photo removal failed:", error);

    showToast(error.message || "Unable to remove the profile photo.", "error");
  } finally {
    setProfilePhotoControlsBusy(false);
  }
}

function initializeEvents() {
  /* =========================
     MOBILE MAIN MENU
  ========================= */
  on(byId("menuButton"), "click", () => {
    const nav = byId("mainNav");

    const isOpen = nav.classList.toggle("open");

    byId("menuButton").setAttribute("aria-expanded", String(isOpen));
  });

  all(".main-nav a").forEach((link) => {
    on(link, "click", () => {
      byId("mainNav").classList.remove("open");

      byId("menuButton").setAttribute("aria-expanded", "false");
    });
  });

  /* =========================
     LOGIN BUTTONS
  ========================= */
  on(byId("signInButton"), "click", () => {
    setAuthMode("login");
    openModal("loginModal", "#loginEmail");
  });

  on(byId("heroAccessButton"), "click", () => {
    setAuthMode("login");
    openModal("loginModal", "#loginEmail");
  });

  on(byId("heroTrackButton"), "click", () => {
    if (state.activeRole === "requester") {
      openWorkspaceView("requester-records");
      byId("workspace")?.scrollIntoView({ behavior: "smooth", block: "start" });
      return;
    }
    if (state.activeRole) {
      showToast(
        "Request tracking is available from a Requester account.",
        "info",
      );
      return;
    }
    setAuthMode("login");
    openModal("loginModal", "#loginEmail");
  });

  on(byId("signOutButton"), "click", signOut);

  /* =========================
     PROFILE PHOTO
  ========================= */

  on(byId("profilePhotoChangeTextButton"), "click", openProfilePhotoPicker);

  on(byId("profilePhotoInput"), "change", handleProfilePhotoFileChange);

  on(byId("profilePhotoRemoveButton"), "click", removeProfilePhoto);

  /* =========================
     WORKSPACE SIDEBAR
  ========================= */
  on(byId("sidebarToggle"), "click", () => {
    const isOpen = document.body.classList.toggle("sidebar-open");

    byId("sidebarToggle").setAttribute("aria-expanded", String(isOpen));
  });

  /* =========================
     WORKSPACE NAVIGATION
  ========================= */
  on(byId("workspaceNavigation"), "click", (event) => {
    const button = event.target.closest("[data-workspace-view]");

    if (button) {
      openWorkspaceView(button.dataset.workspaceView);
    }
  });

  on(byId("auditFilterForm"), "submit", (event) => {
    event.preventDefault();
    try {
      state.auditFilters = readAuditFilters();
      void loadAuditEntries();
    } catch (error) {
      showToast(error.message || "Audit filters are invalid.", "error");
    }
  });

  on(byId("auditFilterForm"), "reset", () => {
    queueMicrotask(() => {
      state.auditFilters = {};
      void loadAuditEntries();
    });
  });

  on(byId("auditLoadOlderButton"), "click", () => {
    void loadAuditEntries(true);
  });

  /* =========================
     GENERAL DOCUMENT CLICKS
  ========================= */
  on(document, "click", (event) => {
    const workspaceLink = event.target.closest("[data-workspace-link]");

    if (workspaceLink) {
      openWorkspaceView(workspaceLink.dataset.workspaceLink);
    }

    const requestModalButton = event.target.closest(
      "[data-open-request-modal]",
    );

    if (requestModalButton) {
      if (state.activeRole !== "requester") {
        showToast(
          "Only the Requester workspace can submit a service request.",
          "warning",
        );

        return;
      }

      resetServiceRequestFormForUser();

      openModal("serviceRequestModal", "#requestTitle");
    }

    if (event.target.closest("[data-open-notifications]")) {
      byId("notificationPanel").hidden = false;
    }

    const closeControl = event.target.closest("[data-close-modal]");

    if (closeControl) {
      closeModal(closeControl.dataset.closeModal);
    }
  });

  /* =========================
     ESCAPE KEY
  ========================= */
  on(document, "keydown", (event) => {
    if (event.key === "Escape") {
      if (!byId("notificationPanel").hidden) {
        byId("notificationPanel").hidden = true;
      } else if (document.body.classList.contains("sidebar-open")) {
        document.body.classList.remove("sidebar-open");

        byId("sidebarToggle")?.setAttribute("aria-expanded", "false");
      } else {
        closeTopModal();
      }
    }
  });

  /* =========================
     NOTIFICATIONS
  ========================= */
  on(byId("notificationButton"), "click", () => {
    const panel = byId("notificationPanel");
    panel.hidden = !panel.hidden;
    if (!panel.hidden) {
      fetchNotifications();
    }
  });

  on(byId("closeNotificationPanel"), "click", () => {
    byId("notificationPanel").hidden = true;
  });

  on(byId("markAllNotificationsReadButton"), "click", () => {
    if (typeof markAllNotificationsRead === "function") {
      markAllNotificationsRead();
    }
  });

  /* =========================
     AUTHENTICATION UI
  ========================= */
  on(byId("authSignInTab"), "click", () => setAuthMode("login"));
  on(byId("authRegisterTab"), "click", () => setAuthMode("register"));
  on(byId("openRegistrationButton"), "click", () => setAuthMode("register"));
  on(byId("backToLoginButton"), "click", () => setAuthMode("login"));
  on(byId("verificationBackToLogin"), "click", () => setAuthMode("login"));

  on(byId("verificationSignInButton"), "click", () => setAuthMode("login"));
  on(byId("resendVerificationButton"), "click", resendVerificationEmail);

  on(byId("togglePasswordButton"), "click", () =>
    togglePasswordVisibility("loginPassword", "togglePasswordButton"),
  );
  on(byId("toggleRegisterPassword"), "click", () =>
    togglePasswordVisibility("registerPassword", "toggleRegisterPassword"),
  );
  on(byId("toggleConfirmPassword"), "click", () =>
    togglePasswordVisibility(
      "registerConfirmPassword",
      "toggleConfirmPassword",
    ),
  );

  on(byId("registerPassword"), "input", () => {
    updatePasswordStrength();
    const confirmInput = byId("registerConfirmPassword");
    if (!confirmInput) return;

    if (!confirmInput.value.trim()) {
      clearFieldState(confirmInput);
      return;
    }

    if (
      confirmInput.dataset.touched === "true" ||
      getFieldGroup(confirmInput)?.classList.contains("is-invalid")
    ) {
      validatePasswordMatch(confirmInput);
    }
  });

  all(".record-form input, .record-form select, .record-form textarea").forEach(
    (input) => {
      if (["checkbox", "submit", "button", "reset"].includes(input.type))
        return;

      const isEmpty = () => {
        if (input.type === "file") return !input.files?.length;
        return !String(input.value || "").trim();
      };

      on(input, "blur", () => {
        // Keep untouched/cleared fields quiet during normal editing.
        // Required-empty errors are still shown when the user submits the form.
        if (isEmpty()) {
          clearFieldState(input);
          return;
        }

        input.dataset.touched = "true";
        validateControl(input);
      });

      on(
        input,
        input.tagName === "SELECT" || input.type === "file"
          ? "change"
          : "input",
        () => {
          // If the user clears a field, immediately remove old warnings and red/green states.
          if (isEmpty()) {
            clearFieldState(input);
            return;
          }

          // Do not warn while the user is typing for the first time.
          // Revalidate live only after this field has already been checked or marked invalid.
          if (
            input.dataset.touched === "true" ||
            getFieldGroup(input)?.classList.contains("is-invalid")
          ) {
            validateControl(input);
          }
        },
      );
    },
  );

  /* =========================
     CONFIRMATION MODAL
  ========================= */
  on(byId("confirmActionButton"), "click", () => {
    const action = state.confirmAction;
    const remarks = byId("confirmRemarks")?.value.trim() || "";

    if (state.confirmRequiresRemarks && !hasMeaningfulText(remarks, 5)) {
      byId("confirmRemarksError").textContent =
        "Enter at least 5 meaningful characters explaining the rejection.";
      byId("confirmRemarks").setAttribute("aria-invalid", "true");
      byId("confirmRemarks").focus();
      return;
    }

    state.confirmAction = null;
    state.confirmRequiresRemarks = false;
    closeModal("confirmModal");

    if (typeof action === "function") {
      action(remarks);
    }
  });

  /* =========================
     FORM SUBMISSIONS
  ========================= */
  on(byId("loginForm"), "submit", handleLoginSubmit);

  on(byId("registrationForm"), "submit", handleRegistrationSubmit);

  on(byId("serviceRequestForm"), "submit", handleServiceRequestSubmit);

  on(byId("serviceRequestDraftButton"), "click", handleServiceRequestDraftSave);

  on(byId("workOrderForm"), "submit", handleWorkOrderSubmit);

  on(byId("workOrderAssignmentForm"), "submit", handleWorkOrderAssignmentSubmit);

  on(byId("personnelAccessForm"), "submit", handlePersonnelSubmit);

  on(byId("personnelTargetId"), "change", (event) => {
    populatePersonnelAccessDetails(event.currentTarget.value);
  });

  on(byId("progressUpdateForm"), "submit", handleProgressUpdateSubmit);

  /* =========================
     SERVICE REQUEST FORM
  ========================= */
  on(byId("serviceRequestForm"), "reset", (event) => {
    resetValidationState(event.currentTarget);
    resetServiceRequestFormForUser();
  });

  on(byId("requestDescription"), "input", (event) => {
    byId("requestCharacterCount").textContent = String(
      event.target.value.length,
    );
  });

  on(byId("requestAttachment"), "change", (event) => {
    validateAttachment(event.target);
  });

  /* =========================
     SEARCH / FILTER
  ========================= */
  on(byId("requestSearchInput"), "input", renderRequestTable);

  on(byId("requestStatusFilter"), "change", renderRequestTable);

  on(byId("adminRequestSearch"), "input", renderAdminReviewTable);

  /* =========================
     TABLE ACTION EVENTS
  ========================= */
  on(byId("serviceRequestTableBody"), "click", handleRequestTableAction);

  on(byId("requesterDraftList"), "click", (event) => {
    const deleteButton = event.target.closest("[data-delete-draft]");

    if (deleteButton) {
      confirmDeleteServiceRequestDraft(deleteButton.dataset.deleteDraft);

      return;
    }

    const resumeButton = event.target.closest("[data-resume-draft]");

    if (!resumeButton) {
      return;
    }

    resumeServiceRequestDraft(resumeButton.dataset.resumeDraft);
  });

  on(byId("adminReviewTableBody"), "click", handleAdminTableAction);

  on(byId("approvalTableBody"), "click", handleApprovalTableAction);

  on(byId("workOrderTableBody"), "click", handleWorkOrderTableAction);

  on(byId("technicianWorkTableBody"), "click", handleTechnicianTableAction);

  on(byId("technicianRefreshButton"), "click", refreshTechnicianWorkspace);

  on(byId("technicianProgressActions"), "click", (event) => {
    const button = event.target.closest("[data-tech-progress-action]");
    if (button) selectTechnicianProgressAction(button.dataset.techProgressAction);
  });

  /* =========================
     CLEAR CUSTOM VALIDATION
  ========================= */
  on(byId("workOrderRequestId"), "input", (event) => {
    event.target.setCustomValidity("");
  });

  on(byId("workOrderId"), "input", (event) => {
    event.target.setCustomValidity("");
  });

  on(byId("progressWorkOrderId"), "input", (event) => {
    event.target.setCustomValidity("");
  });

  on(byId("progressRequestId"), "input", (event) => {
    event.target.setCustomValidity("");
  });

  on(byId("completionDate"), "input", (event) => {
    event.target.setCustomValidity("");
  });

  on(byId("personnelAccessForm"), "reset", (event) => {
    window.setTimeout(() => {
      resetValidationState(event.currentTarget);

      clearPersonnelAccessFields();
    }, 0);
  });

  on(byId("workOrderForm"), "reset", (event) =>
    resetValidationState(event.currentTarget),
  );

  on(byId("workOrderAssignmentForm"), "reset", (event) => {
    state.pendingAssignmentWorkOrderId = null;
    delete event.currentTarget.dataset.workOrderId;
    resetValidationState(event.currentTarget);
  });

  /* =========================
     TECHNICIAN FORM RESET
  ========================= */
  on(byId("progressUpdateForm"), "reset", (event) => {
    event.preventDefault();
    if (!state.technicianSubmitting) populateTechnicianUpdateForm(state.technicianWorkOrder);
  });
}

/* =========================
   MODERN INTERFACE ENHANCEMENTS
========================= */
function initializeScrollReveal() {
  const targets = [
    ...all(".section-heading"),
    ...all(".role-preview-grid article"),
    ...all(".service-grid article"),
    ...all(".workflow-list li"),
    ...all(".ai-capabilities article"),
    ...all(".overview-card"),
  ];

  targets.forEach((element, index) => {
    element.classList.add("reveal-up");
    element.style.setProperty(
      "--reveal-delay",
      `${Math.min(index % 6, 5) * 70}ms`,
    );
  });

  if (
    window.matchMedia("(prefers-reduced-motion: reduce)").matches ||
    !("IntersectionObserver" in window)
  ) {
    targets.forEach((element) => element.classList.add("is-visible"));
    return;
  }

  const observer = new IntersectionObserver(
    (entries, obs) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add("is-visible");
        obs.unobserve(entry.target);
      });
    },
    { threshold: 0.12, rootMargin: "0px 0px -7% 0px" },
  );

  targets.forEach((element) => observer.observe(element));
}

function initializeActivePublicNavigation() {
  const header = byId("siteHeader");
  const backToTop = byId("backToTopButton");
  const links = all(".public-nav-link");
  const sections = links
    .map((link) => document.querySelector(link.getAttribute("href")))
    .filter(Boolean);

  const updateScrollState = () => {
    const scrolled = window.scrollY > 20;
    header?.classList.toggle("is-scrolled", scrolled);
    backToTop?.classList.toggle("is-visible", window.scrollY > 520);
  };

  updateScrollState();
  on(window, "scroll", updateScrollState);

  if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        if (!visible) return;
        links.forEach((link) =>
          link.classList.toggle(
            "active",
            link.getAttribute("href") === `#${visible.target.id}`,
          ),
        );
      },
      { rootMargin: "-25% 0px -60% 0px", threshold: [0.01, 0.2, 0.5] },
    );
    sections.forEach((section) => observer.observe(section));
  }

  on(backToTop, "click", () => window.scrollTo({ top: 0, behavior: "smooth" }));
}

function animateMetricElement(element) {
  if (!element || element.dataset.metricAnimated === "true") return;
  const raw = element.textContent.trim();
  const target = Number.parseInt(raw, 10);
  if (Number.isNaN(target)) return;

  element.dataset.metricAnimated = "true";
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

  const duration = 600;
  const start = performance.now();
  const pad = raw.length > 1 && raw.startsWith("0") ? raw.length : 0;
  const step = (now) => {
    const progress = Math.min((now - start) / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    const value = Math.round(target * eased);
    element.textContent = pad
      ? String(value).padStart(pad, "0")
      : String(value);
    if (progress < 1) requestAnimationFrame(step);
  };
  element.textContent = pad ? "0".repeat(pad) : "0";
  requestAnimationFrame(step);
}

function initializeMetricAnimations() {
  [
    "publicTotalMetric",
    "publicPendingMetric",
    "publicProgressMetric",
    "publicCompletedMetric",
    "requesterTotalMetric",
    "requesterPendingMetric",
    "requesterProgressMetric",
    "requesterCompletedMetric",
    "adminQueueMetric",
    "adminApprovalMetric",
    "adminHighMetric",
    "adminWorkOrderMetric",
    "approverPendingMetric",
    "approverApprovedMetric",
    "approverRejectedMetric",
    "technicianAssignedMetric",
    "technicianProgressMetric",
    "technicianHoldMetric",
    "technicianCompletedMetric",
  ].forEach((id) => animateMetricElement(byId(id)));
}

function initializeInterfaceEnhancements() {
  initializeScrollReveal();
  initializeActivePublicNavigation();
  initializeMetricAnimations();
}

/* =========================
   INITIALIZE APPLICATION
========================= */
async function initializeApp() {
  initializeLogoFallbacks();

  setDateRestrictions();

  restoreRememberedUser();

  initializeEvents();

  initializeEmailVerificationSync();

  /*
   * Process verification return before restoring an existing session.
   * A verification return must remain an authentication step and must
   * not automatically open a role workspace.
   */
  const verificationReturnHandled = handleEmailVerificationReturn();

  renderAll();

  initializeInterfaceEnhancements();

  if (byId("workspaceDate")) {
    byId("workspaceDate").textContent = new Intl.DateTimeFormat("en-US", {
      month: "long",
      day: "numeric",
      year: "numeric",
    }).format(new Date());
  }

  await loadDepartments();
  await loadServiceCategories();

  /*
   * If the user just returned from email verification,
   * leave them on the verification/login interface.
   * Otherwise restore an existing authenticated session normally.
   */
  if (!verificationReturnHandled) {
    await restoreServerSession();

    if (state.activeRole === "requester") {
      await loadRequesterServiceRequests();
      await loadRequesterServiceRequestDrafts();
      renderAll();
    }
  }
}

/* =========================
   START APPLICATION
  ========================= */
document.addEventListener("DOMContentLoaded", initializeApp);

/* =========================
   NOTIFICATIONS
========================= */
const NOTIFICATION_ENDPOINT = API_ENDPOINTS.notifications;

function renderNotificationControls() {
  const countEl = byId("notificationCount");
  const markAllEl = byId("markAllNotificationsReadButton");

  if (!countEl) {
    return;
  }

  if (!state.currentAccount || state.notificationsLoading) {
    countEl.hidden = true;
    if (markAllEl) {
      markAllEl.hidden = true;
      markAllEl.disabled = true;
    }
    return;
  }

  const unreadCount = state.notificationUnreadCount;
  if (unreadCount > 0) {
    countEl.textContent = unreadCount > 99 ? "99+" : unreadCount;
    countEl.hidden = false;
  } else {
    countEl.hidden = true;
  }

  if (markAllEl) {
    markAllEl.hidden = unreadCount === 0;
    markAllEl.disabled = unreadCount === 0;
  }
}

function renderNotificationList() {
  const listEl = byId("notificationList");
  if (!listEl) {
    return;
  }

  if (!state.currentAccount) {
    listEl.innerHTML = "";
    return;
  }

  if (state.notifications.length === 0) {
    listEl.innerHTML = "";
    const empty = document.createElement("p");
    empty.className = "notification-empty";
    empty.textContent = state.notificationsLoading ? "Loading notifications..." : "No notifications.";
    listEl.appendChild(empty);
    return;
  }

  listEl.innerHTML = "";

  state.notifications.forEach(function (notification) {
    const item = document.createElement("article");
    item.className = "notification-item";

    const header = document.createElement("div");
    header.className = "notification-item-header";

    const title = document.createElement("strong");
    title.textContent = notification.title || "Notification";
    header.appendChild(title);

    if (notification.isRead) {
      const readBadge = document.createElement("span");
      readBadge.className = "notification-read-badge";
      readBadge.textContent = "Read";
      header.appendChild(readBadge);
    }

    item.appendChild(header);

    if (notification.message) {
      const message = document.createElement("p");
      message.className = "notification-item-message";
      message.textContent = notification.message;
      item.appendChild(message);
    }

    if (notification.createdAt) {
      const time = document.createElement("time");
      time.className = "notification-item-time";
      time.dateTime = notification.createdAt;
      time.textContent = formatDateTime(notification.createdAt);
      item.appendChild(time);
    }

    if (!notification.isRead) {
      const actionRow = document.createElement("div");
      actionRow.className = "notification-item-actions";

      const markReadBtn = document.createElement("button");
      markReadBtn.type = "button";
      markReadBtn.className = "notification-item-action";
      markReadBtn.textContent = "Mark as read";
      markReadBtn.addEventListener("click", function () {
        markNotificationRead(notification.notificationId);
      });
      actionRow.appendChild(markReadBtn);

      item.appendChild(actionRow);
    }

    listEl.appendChild(item);
  });
}

async function markNotificationRead(notificationId) {
  if (!state.currentAccount) return;

  const csrf = await getCsrfToken();
  if (!csrf?.token) return;

  try {
    const response = await fetch(NOTIFICATION_ENDPOINT, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        [csrf.headerName]: csrf.token,
      },
      credentials: "same-origin",
      body: JSON.stringify({
        action: "markRead",
        notificationId: notificationId
      })
    });

    const data = await readJsonResponse(response);

    if (response.ok && data.success) {
      await fetchNotifications();
    } else {
      showToast(data && data.message ? data.message : "Unable to mark notification as read.", "error");
    }
  } catch (error) {
    showToast("Unable to mark notification as read.", "error");
  }
}

async function markAllNotificationsRead() {
  if (!state.currentAccount) return;

  const csrf = await getCsrfToken();
  if (!csrf?.token) return;

  try {
    const response = await fetch(NOTIFICATION_ENDPOINT, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        [csrf.headerName]: csrf.token,
      },
      credentials: "same-origin",
      body: JSON.stringify({
        action: "markAllRead"
      })
    });

    const data = await readJsonResponse(response);

    if (response.ok && data.success) {
      await fetchNotifications();
    } else {
      showToast(data && data.message ? data.message : "Unable to mark notifications as read.", "error");
    }
  } catch (error) {
    showToast("Unable to mark notifications as read.", "error");
  }
}

async function fetchNotifications() {
  if (!state.currentAccount || state.accountLoading) {
    state.notifications = [];
    state.notificationUnreadCount = 0;
    renderNotificationControls();
    renderNotificationList();
    return;
  }

  state.notificationsLoading = true;
  renderNotificationControls();

  const currentEmail = state.currentAccount.email;

  try {
    const response = await fetch(NOTIFICATION_ENDPOINT, {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
      credentials: "same-origin",
      cache: "no-store",
    });

    const data = await readJsonResponse(response);

    if (!state.currentAccount || state.currentAccount.email !== currentEmail) {
      return;
    }

    if (!response.ok || !data || typeof data !== "object") {
      state.notifications = [];
      state.notificationUnreadCount = 0;
      showToast(data && data.message ? data.message : "Unable to load notifications.", "error");
      state.notificationsLoading = false;
      renderNotificationControls();
      renderNotificationList();
      return;
    }

    state.notifications = Array.isArray(data.notifications) ? data.notifications : [];
    state.notificationUnreadCount = Number.isInteger(data.unreadCount) ? data.unreadCount : 0;

    renderNotificationControls();
    renderNotificationList();
  } catch (error) {
    if (!state.currentAccount || state.currentAccount.email !== currentEmail) {
      return;
    }
    state.notifications = [];
    state.notificationUnreadCount = 0;
    showToast(error.message || "Unable to load notifications.", "error");
  } finally {
    if (state.currentAccount && state.currentAccount.email === currentEmail) {
      state.notificationsLoading = false;
      renderNotificationControls();
      renderNotificationList();
    }
  }
}
