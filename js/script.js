"use strict";

/* =========================
   DOM HELPERS
========================= */
const byId = (id) => document.getElementById(id);

const all = (selector, scope = document) => [
  ...scope.querySelectorAll(selector)
];

const on = (target, eventName, handler) => {
  if (target) {
    target.addEventListener(eventName, handler);
  }
};


/* =========================
   PROTOTYPE STATE / DEMO DATA
========================= */
const state = {
  activeRole: null,
  userName: "",
  userEmail: "",

  requestNumber: 24,
  workOrderNumber: 18,
  personnelNumber: 3,

  publicBaseTotal: 24,
  publicBasePending: 8,

  lastFocusedElement: null,
  confirmAction: null,

  serviceRequests: [
    {
      id: "SR-2026-000024",
      requesterName: "Demo Requester",
      requesterEmail: "requester@online.htcgsc.edu.ph",
      requesterType: "Student",
      department: "College of Engineering and Technology Education",
      category: "Internet & Network",
      requestedCategory: "AI Recommendation",
      title: "No internet connection in Laboratory 2",
      description:
        "Computers in Laboratory 2 cannot connect to the campus internet during class.",
      location: "Laboratory 2",
      preferredPriority: "High",
      aiCategory: "Internet & Network",
      aiPriority: "High",
      duplicateCheck: "No close match found",
      priority: "High",
      status: "In Progress",
      dateReported: "2026-08-25",
      attachmentName: "network-photo.jpg"
    },

    {
      id: "SR-2026-000023",
      requesterName: "Demo Requester",
      requesterEmail: "requester@online.htcgsc.edu.ph",
      requesterType: "Faculty",
      department: "College Department",
      category: "Equipment",
      requestedCategory: "Equipment",
      title: "Broken classroom electric fan",
      description:
        "The electric fan in Room 204 is not turning on and the room becomes very warm.",
      location: "Room 204",
      preferredPriority: "Medium",
      aiCategory: "Equipment",
      aiPriority: "Medium",
      duplicateCheck: "No close match found",
      priority: "Medium",
      status: "Assigned",
      dateReported: "2026-08-24",
      attachmentName: ""
    },

    {
      id: "SR-2026-000022",
      requesterName: "Demo Requester",
      requesterEmail: "requester@online.htcgsc.edu.ph",
      requesterType: "School Personnel",
      department: "Administration Office",
      category: "Maintenance",
      requestedCategory: "Maintenance",
      title: "Door handle needs repair",
      description:
        "The door handle in Room 106 is loose and needs repair before it becomes unusable.",
      location: "Room 106",
      preferredPriority: "Low",
      aiCategory: "Facilities",
      aiPriority: "Low",
      duplicateCheck: "Possible match: SR-2026-000018",
      priority: "Low",
      status: "For Review",
      dateReported: "2026-08-23",
      attachmentName: ""
    },

    {
      id: "SR-2026-000021",
      requesterName: "Demo Requester",
      requesterEmail: "requester@online.htcgsc.edu.ph",
      requesterType: "Faculty",
      department: "College Department",
      category: "Electrical",
      requestedCategory: "Electrical",
      title: "Flickering lights near staircase",
      description:
        "Several lights beside the second-floor staircase are flickering and may need inspection.",
      location: "Second Floor Staircase",
      preferredPriority: "High",
      aiCategory: "Electrical",
      aiPriority: "High",
      duplicateCheck: "No close match found",
      priority: "High",
      status: "Awaiting Approval",
      dateReported: "2026-08-22",
      attachmentName: ""
    },

    {
      id: "SR-2026-000020",
      requesterName: "Demo Requester",
      requesterEmail: "requester@online.htcgsc.edu.ph",
      requesterType: "Faculty",
      department: "College Department",
      category: "Equipment",
      requestedCategory: "Equipment",
      title: "Projector not displaying",
      description:
        "The classroom projector powers on but does not display the connected laptop screen.",
      location: "Room 205",
      preferredPriority: "Medium",
      aiCategory: "Equipment",
      aiPriority: "Medium",
      duplicateCheck: "No close match found",
      priority: "Medium",
      status: "Completed",
      dateReported: "2026-08-20",
      attachmentName: ""
    }
  ],

  workOrders: [
    {
      id: "WO-2026-0018",
      requestId: "SR-2026-000024",
      assignedPersonnel: "IT Support Team",
      serviceUnit: "Information Technology Office",
      workDescription:
        "Inspect the laboratory network connection, switch port, and affected computers.",
      status: "In Progress",
      actionTaken:
        "Initial network checks completed. Switch port is being tested.",
      materials: "Network tester",
      completionDate: "",
      remarks:
        "Continue diagnostics during the next available laboratory period.",
      updatedAt: "2026-08-26"
    },

    {
      id: "WO-2026-0017",
      requestId: "SR-2026-000023",
      assignedPersonnel: "Facilities Team",
      serviceUnit: "Facilities Management",
      workDescription:
        "Inspect the electric fan and determine whether repair or replacement is required.",
      status: "Assigned",
      actionTaken: "",
      materials: "",
      completionDate: "",
      remarks: "",
      updatedAt: "2026-08-25"
    },

    {
      id: "WO-2026-0016",
      requestId: "SR-2026-000020",
      assignedPersonnel: "IT Support Team",
      serviceUnit: "Information Technology Office",
      workDescription:
        "Diagnose the projector input issue and restore display output.",
      status: "Completed",
      actionTaken:
        "Replaced the damaged HDMI cable and tested projector output.",
      materials: "HDMI cable",
      completionDate: "2026-08-21",
      remarks: "Projector tested successfully.",
      updatedAt: "2026-08-21"
    }
  ],

  personnel: [
    {
      id: "SP-001",
      name: "Alex Rivera",
      email: "alex.rivera@online.htcgsc.edu.ph",
      department: "Information Technology Office",
      role: "Service Personnel / Technician",
      rights: ["Update Work"],
      status: "Active"
    },

    {
      id: "SP-002",
      name: "Jamie Santos",
      email: "jamie.santos@online.htcgsc.edu.ph",
      department: "Facilities Management",
      role: "Service Personnel / Technician",
      rights: ["Update Work"],
      status: "Active"
    },

    {
      id: "SP-003",
      name: "Patricia Cruz",
      email: "patricia.cruz@online.htcgsc.edu.ph",
      department: "Administration Office",
      role: "Service Administrator",
      rights: ["Review Requests", "Manage Work Orders"],
      status: "Active"
    }
  ],

  approvals: [
    {
      requestId: "SR-2026-000021",
      decision: "Pending",
      decisionDate: "",
      remarks: ""
    }
  ],

  approvalHistory: [],

  technicianHistory: [
    {
      workOrderId: "WO-2026-0016",
      requestId: "SR-2026-000020",
      status: "Completed",
      actionTaken:
        "Replaced the damaged HDMI cable and tested projector output.",
      completionDate: "2026-08-21",
      remarks: "Projector tested successfully."
    }
  ],

  notifications: [
    {
      id: 1,
      targetRole: "requester",
      title: "Request in progress",
      message: "SR-2026-000024 is currently being serviced.",
      date: "2026-08-26"
    },

    {
      id: 2,
      targetRole: "administrator",
      title: "Request awaiting review",
      message: "SR-2026-000022 is ready for administrator review.",
      date: "2026-08-26"
    },

    {
      id: 3,
      targetRole: "approver",
      title: "Approval required",
      message: "SR-2026-000021 requires an authorization decision.",
      date: "2026-08-26"
    },

    {
      id: 4,
      targetRole: "technician",
      title: "Assigned work order",
      message: "WO-2026-0017 is in the technician service queue.",
      date: "2026-08-26"
    }
  ]
};


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
        icon: "▦",
        label: "Overview"
      },

      {
        view: "requester-new",
        icon: "+",
        label: "New Request"
      },

      {
        view: "requester-records",
        icon: "▤",
        label: "My Requests"
      }
    ]
  },

  administrator: {
    label: "Service Administrator / Coordinator",
    defaultView: "administrator-overview",

    navigation: [
      {
        view: "administrator-overview",
        icon: "▦",
        label: "Overview"
      },

      {
        view: "administrator-review",
        icon: "✓",
        label: "Request Queue"
      },

      {
        view: "administrator-work-orders",
        icon: "⚒",
        label: "Work Orders"
      },

      {
        view: "administrator-access",
        icon: "◆",
        label: "Personnel Access"
      }
    ]
  },

  approver: {
    label: "Department Head / Authorized Approver",
    defaultView: "approver-overview",

    navigation: [
      {
        view: "approver-overview",
        icon: "▦",
        label: "Overview"
      },

      {
        view: "approver-queue",
        icon: "✓",
        label: "Approval Queue"
      },

      {
        view: "approver-history",
        icon: "▤",
        label: "Approval History"
      }
    ]
  },

  technician: {
    label: "Service Personnel / Technician",
    defaultView: "technician-overview",

    navigation: [
      {
        view: "technician-overview",
        icon: "▦",
        label: "Overview"
      },

      {
        view: "technician-assigned",
        icon: "⚒",
        label: "Assigned Work"
      },

      {
        view: "technician-update",
        icon: "↻",
        label: "Update Progress"
      },

      {
        view: "technician-history",
        icon: "▤",
        label: "Work History"
      }
    ]
  }
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

    fallback.setAttribute(
      "aria-label",
      image.alt || label
    );

    fallback.textContent = label;

    image.replaceWith(fallback);
  };

  image.addEventListener(
    "error",
    replaceImage,
    {
      once: true
    }
  );

  if (
    image.complete &&
    image.naturalWidth === 0
  ) {
    replaceImage();
  }
}


function initializeLogoFallbacks() {
  all(
    'img[src$="wildcatslogo.png"]'
  ).forEach((img) => {
    installLogoFallback(
      img,
      "WC"
    );
  });

  all(
    'img[src$="htclogo.png"]'
  ).forEach((img) => {
    installLogoFallback(
      img,
      "HTC"
    );
  });
}


function initials(name) {
  return (
    String(name)
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((word) =>
        word
          .charAt(0)
          .toUpperCase()
      )
      .join("") || "GU"
  );
}


function todayInputValue() {
  const now = new Date();

  return [
    now.getFullYear(),
    String(
      now.getMonth() + 1
    ).padStart(
      2,
      "0"
    ),
    String(
      now.getDate()
    ).padStart(
      2,
      "0"
    )
  ].join("-");
}


function formatDate(value) {
  if (!value) {
    return "—";
  }

  const date = new Date(
    `${value}T00:00:00`
  );

  if (
    Number.isNaN(
      date.getTime()
    )
  ) {
    return value;
  }

  return new Intl.DateTimeFormat(
    "en-US",
    {
      month: "short",
      day: "numeric",
      year: "numeric"
    }
  ).format(date);
}


function normalizeText(value) {
  return String(
    value || ""
  )
    .trim()
    .toLowerCase();
}


function createElement(
  tag,
  className,
  text
) {
  const element =
    document.createElement(tag);

  if (className) {
    element.className =
      className;
  }

  if (
    text !== undefined
  ) {
    element.textContent =
      text;
  }

  return element;
}


function findRequest(
  requestId
) {
  return state.serviceRequests.find(
    (request) =>
      request.id ===
      requestId
  );
}


function findWorkOrder(
  workOrderId
) {
  return state.workOrders.find(
    (workOrder) =>
      workOrder.id ===
      workOrderId
  );
}


function addNotification(
  targetRole,
  title,
  message
) {
  state.notifications.unshift(
    {
      id:
        Date.now() +
        Math.random(),

      targetRole,
      title,
      message,

      date:
        todayInputValue()
    }
  );

  renderNotifications();
}


/* =========================
   TOAST NOTIFICATIONS
========================= */
function showToast(
  message,
  type = "success"
) {
  const region =
    byId("toastRegion");

  if (!region) {
    return;
  }

  const toast =
    createElement(
      "div",
      `toast ${type}`
    );

  toast.setAttribute(
    "role",
    type === "error"
      ? "alert"
      : "status"
  );

  const iconMap = {
    success: "✓",
    warning: "!",
    error: "×",
    info: "i"
  };

  const icon =
    createElement(
      "span",
      "toast-icon",
      iconMap[type] || "✓"
    );

  const text =
    createElement(
      "p",
      "",
      message
    );

  const close =
    createElement(
      "button",
      "",
      "×"
    );

  close.type =
    "button";

  close.setAttribute(
    "aria-label",
    "Dismiss notification"
  );

  on(
    close,
    "click",
    () => {
      toast.remove();
    }
  );

  toast.append(
    icon,
    text,
    close
  );

  region.appendChild(
    toast
  );

  window.setTimeout(
    () => {
      toast.remove();
    },
    4600
  );
}


/* =========================
   BADGES
========================= */
function badgeClass(value) {
  const text =
    normalizeText(value);

  if (
    text.includes("urgent")
  ) {
    return "badge-urgent";
  }

  if (
    text.includes("high")
  ) {
    return "badge-high";
  }

  if (
    text.includes("medium")
  ) {
    return "badge-medium";
  }

  if (
    text.includes("low")
  ) {
    return "badge-low";
  }

  if (
    text.includes("progress")
  ) {
    return "badge-progress";
  }

  if (
    text.includes("assigned")
  ) {
    return "badge-assigned";
  }

  if (
    text.includes("hold")
  ) {
    return "badge-hold";
  }

  if (
    text.includes("review") ||
    text.includes("awaiting") ||
    text.includes("pending")
  ) {
    return "badge-review";
  }

  if (
    text.includes("reject")
  ) {
    return "badge-rejected";
  }

  if (
    text.includes("duplicate")
  ) {
    return "badge-duplicate";
  }

  if (
    text.includes("inactive")
  ) {
    return "badge-inactive";
  }

  if (
    text.includes("complete")
  ) {
    return "badge-completed";
  }

  if (
    text.includes("approve")
  ) {
    return "badge-approved";
  }

  if (
    text.includes("active")
  ) {
    return "badge-active";
  }

  return "badge-pending";
}


function createBadge(value) {
  return createElement(
    "span",
    `badge ${badgeClass(value)}`,
    value
  );
}


/* =========================
   MODALS
========================= */
function openModal(
  modalId,
  focusSelector
) {
  const modal =
    byId(modalId);

  if (!modal) {
    return;
  }

  state.lastFocusedElement =
    document.activeElement;

  modal.hidden = false;

  document.body.classList.add(
    "modal-open"
  );

  window.setTimeout(
    () => {
      const target =
        focusSelector
          ? modal.querySelector(
              focusSelector
            )
          : modal.querySelector(
              "input, select, textarea, button"
            );

      target?.focus();
    },
    20
  );
}


function closeModal(
  modalId
) {
  const modal =
    byId(modalId);

  if (!modal) {
    return;
  }

  modal.hidden = true;

  const anyOpenModal =
    all(".modal").some(
      (item) =>
        !item.hidden
    );

  if (!anyOpenModal) {
    document.body.classList.remove(
      "modal-open"
    );
  }

  if (
    state.lastFocusedElement
      instanceof HTMLElement &&
    state.lastFocusedElement
      .isConnected
  ) {
    state.lastFocusedElement.focus();
  }
}


function closeTopModal() {
  const openModals =
    all(".modal").filter(
      (modal) =>
        !modal.hidden
    );

  const topModal =
    openModals.at(-1);

  if (topModal) {
    closeModal(
      topModal.id
    );
  }
}


function openConfirm({
  title,
  message,
  confirmLabel = "Confirm",
  type = "primary",
  onConfirm
}) {
  byId(
    "confirmTitle"
  ).textContent = title;

  byId(
    "confirmMessage"
  ).textContent = message;

  byId(
    "confirmActionButton"
  ).textContent =
    confirmLabel;

  byId(
    "confirmActionButton"
  ).className =
    "button button-primary";

  byId(
    "confirmIcon"
  ).textContent =
    type === "danger"
      ? "!"
      : "?";

  state.confirmAction =
    onConfirm;

  openModal(
    "confirmModal",
    "#confirmActionButton"
  );
}


function showDetails(
  title,
  label,
  fields
) {
  byId(
    "detailsModalTitle"
  ).textContent =
    title;

  byId(
    "detailsModalLabel"
  ).textContent =
    label;

  const container =
    byId(
      "detailsModalContent"
    );

  container.replaceChildren();

  fields.forEach(
    ({
      label: fieldLabel,
      value,
      full = false,
      badge = false
    }) => {
      const item =
        createElement(
          "div",
          `detail-item${
            full
              ? " full"
              : ""
          }`
        );

      item.appendChild(
        createElement(
          "small",
          "",
          fieldLabel
        )
      );

      if (badge) {
        item.appendChild(
          createBadge(
            value || "—"
          )
        );
      } else {
        item.appendChild(
          createElement(
            "span",
            "",
            value || "—"
          )
        );
      }

      container.appendChild(
        item
      );
    }
  );

  openModal(
    "detailsModal",
    ".modal-close"
  );
}


/* =========================
   AI DEMO RULES
========================= */
function recommendCategory(
  textValue
) {
  const text =
    normalizeText(
      textValue
    );

  if (
    /internet|wifi|wi-fi|network|router|connection|switch port|lan/.test(
      text
    )
  ) {
    return "Internet & Network";
  }

  if (
    /computer|laptop|printer|software|monitor|keyboard|mouse/.test(
      text
    )
  ) {
    return "IT & Computer";
  }

  if (
    /light|outlet|power|electrical|electric|wiring|switch|spark|shock/.test(
      text
    )
  ) {
    return "Electrical";
  }

  if (
    /projector|aircon|air conditioner|fan|equipment|machine/.test(
      text
    )
  ) {
    return "Equipment";
  }

  if (
    /door|window|chair|desk|ceiling|classroom|building|room/.test(
      text
    )
  ) {
    return "Facilities";
  }

  return "Maintenance";
}


function recommendPriority(
  textValue
) {
  const text =
    normalizeText(
      textValue
    );

  if (
    /fire|smoke|spark|shock|danger|flood|emergency|exposed wire|injury|hazard/.test(
      text
    )
  ) {
    return "Urgent";
  }

  if (
    /unavailable|several|multiple|entire|all users|cannot access|not working|no internet/.test(
      text
    )
  ) {
    return "High";
  }

  if (
    /minor|loose|occasionally|intermittent|cosmetic/.test(
      text
    )
  ) {
    return "Low";
  }

  return "Medium";
}


function importantWords(
  textValue
) {
  const stopWords =
    new Set([
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
      "have"
    ]);

  return normalizeText(
    textValue
  )
    .replace(
      /[^a-z0-9\s]/g,
      " "
    )
    .split(/\s+/)
    .filter(
      (word) =>
        word.length > 3 &&
        !stopWords.has(
          word
        )
    );
}


function findPossibleDuplicate(
  title,
  description
) {
  const incomingWords =
    new Set(
      importantWords(
        `${title} ${description}`
      )
    );

  let bestMatch = null;
  let bestScore = 0;

  state.serviceRequests.forEach(
    (request) => {
      const existingWords =
        new Set(
          importantWords(
            `${request.title} ${request.description}`
          )
        );

      const score =
        [
          ...incomingWords
        ].filter(
          (word) =>
            existingWords.has(
              word
            )
        ).length;

      if (
        score >
        bestScore
      ) {
        bestScore =
          score;

        bestMatch =
          request;
      }
    }
  );

  return (
    bestScore >= 3 &&
    bestMatch
  )
    ? `Possible match: ${bestMatch.id}`
    : "No close match found";
}


/* =========================
   WORKSPACE NAVIGATION
========================= */
function viewTitle(
  viewId
) {
  if (
    !state.activeRole ||
    !roleConfiguration[
      state.activeRole
    ]
  ) {
    return "Workspace";
  }

  const item =
    roleConfiguration[
      state.activeRole
    ].navigation.find(
      (entry) =>
        entry.view ===
        viewId
    );

  return item
    ? item.label
    : "Workspace";
}


function buildWorkspaceNavigation(
  role
) {
  const navigation =
    byId(
      "workspaceNavigation"
    );

  navigation.replaceChildren();

  roleConfiguration[
    role
  ].navigation.forEach(
    (item) => {
      const button =
        createElement(
          "button",
          "workspace-nav-button"
        );

      button.type =
        "button";

      button.dataset.workspaceView =
        item.view;

      button.append(
        createElement(
          "span",
          "",
          item.icon
        ),

        createElement(
          "strong",
          "",
          item.label
        )
      );

      navigation.appendChild(
        button
      );
    }
  );
}


function openWorkspaceView(
  viewId
) {
  if (
    !state.activeRole
  ) {
    return;
  }

  const activeInterface =
    document.querySelector(
      `.role-interface[data-role="${state.activeRole}"]`
    );

  if (
    !activeInterface
  ) {
    return;
  }

  all(
    ".workspace-view",
    activeInterface
  ).forEach(
    (view) => {
      view.hidden =
        view.dataset.view !==
        viewId;
    }
  );

  all(
    ".workspace-nav-button",
    byId(
      "workspaceNavigation"
    )
  ).forEach(
    (button) => {
      button.classList.toggle(
        "active",
        button.dataset.workspaceView ===
          viewId
      );
    }
  );

  const title =
    viewTitle(
      viewId
    );

  byId(
    "workspacePageTitle"
  ).textContent =
    title;

  byId(
    "workspaceBreadcrumb"
  ).textContent =
    `Service Portal / ${
      roleConfiguration[
        state.activeRole
      ].label
    } / ${title}`;

  document.body.classList.remove(
    "sidebar-open"
  );

  byId(
    "sidebarToggle"
  )?.setAttribute(
    "aria-expanded",
    "false"
  );

  if (
    window.matchMedia(
      "(max-width: 980px)"
    ).matches
  ) {
    byId(
      "workspace"
    )?.scrollIntoView({
      behavior: "smooth",
      block: "start"
    });
  }
}


function activateRoleWorkspace(
  role,
  name,
  email
) {
  if (
    !roleConfiguration[
      role
    ]
  ) {
    showToast(
      "The selected role is not configured.",
      "error"
    );

    return;
  }

  state.activeRole =
    role;

  state.userName =
    name;

  state.userEmail =
    email;

  all(
    ".role-interface"
  ).forEach(
    (roleInterface) => {
      roleInterface.hidden =
        roleInterface.dataset.role !==
        role;
    }
  );

  buildWorkspaceNavigation(
    role
  );

  byId(
    "workspaceUserName"
  ).textContent =
    name;

  byId(
    "workspaceUserEmail"
  ).textContent =
    email;

  byId(
    "workspaceAvatar"
  ).textContent =
    initials(name);

  byId(
    "workspaceRoleName"
  ).textContent =
    roleConfiguration[
      role
    ].label;

  all(
    "[data-user-name]"
  ).forEach(
    (element) => {
      element.textContent =
        name;
    }
  );

  if (
    role ===
    "requester"
  ) {
    byId(
      "requesterName"
    ).value =
      name;

    byId(
      "requesterEmail"
    ).value =
      email;
  }

  if (
    role ===
    "technician"
  ) {
    byId(
      "progressTechnician"
    ).value =
      name;
  }

  byId(
    "publicContent"
  ).hidden =
    true;

  byId(
    "workspace"
  ).hidden =
    false;

  byId(
    "workspaceNavLink"
  ).hidden =
    false;

  byId(
    "signInButton"
  ).textContent =
    "Switch Account";

  document.body.classList.add(
    "workspace-active"
  );

  renderAll();

  openWorkspaceView(
    roleConfiguration[
      role
    ].defaultView
  );

  window.scrollTo({
    top: 0,
    behavior: "smooth"
  });
}


function signOut() {
  state.activeRole =
    null;

  state.userName =
    "";

  state.userEmail =
    "";

  all(
    ".role-interface"
  ).forEach(
    (roleInterface) => {
      roleInterface.hidden =
        true;
    }
  );

  byId(
    "workspace"
  ).hidden =
    true;

  byId(
    "publicContent"
  ).hidden =
    false;

  byId(
    "workspaceNavLink"
  ).hidden =
    true;

  byId(
    "signInButton"
  ).textContent =
    "Sign In";

  byId(
    "notificationPanel"
  ).hidden =
    true;

  document.body.classList.remove(
    "workspace-active",
    "sidebar-open"
  );

  window.scrollTo({
    top: 0,
    behavior: "smooth"
  });

  showToast(
    "You have signed out of the role workspace.",
    "info"
  );
}


/* =========================
   RENDER: PUBLIC AREA
========================= */
function renderPublicMetrics() {
  const addedRequests =
    Math.max(
      0,
      state.serviceRequests.length -
        5
    );

  const total =
    state.publicBaseTotal +
    addedRequests;

  const pending =
    state.publicBasePending +
    state.serviceRequests
      .slice(5)
      .filter(
        (request) =>
          [
            "Pending",
            "For Review",
            "Awaiting Approval"
          ].includes(
            request.status
          )
      ).length;

  const progress =
    state.serviceRequests.filter(
      (request) =>
        [
          "Assigned",
          "In Progress"
        ].includes(
          request.status
        )
    ).length + 2;

  const completed =
    Math.max(
      0,
      total -
        pending -
        progress
    );

  byId(
    "publicTotalMetric"
  ).textContent =
    String(
      total
    ).padStart(
      2,
      "0"
    );

  byId(
    "publicPendingMetric"
  ).textContent =
    String(
      pending
    ).padStart(
      2,
      "0"
    );

  byId(
    "publicProgressMetric"
  ).textContent =
    String(
      progress
    ).padStart(
      2,
      "0"
    );

  byId(
    "publicCompletedMetric"
  ).textContent =
    String(
      completed
    ).padStart(
      2,
      "0"
    );
}


function renderPublicActivity() {
  const container =
    byId(
      "publicRecentActivity"
    );

  container.replaceChildren();

  state.serviceRequests
    .slice(
      0,
      3
    )
    .forEach(
      (request) => {
        const row =
          createElement(
            "div",
            "activity-row"
          );

        row.appendChild(
          createElement(
            "span",
            "activity-icon",
            request.category
              .slice(0, 2)
              .toUpperCase()
          )
        );

        const text =
          createElement(
            "div"
          );

        text.append(
          createElement(
            "strong",
            "",
            request.id
          ),

          createElement(
            "p",
            "",
            request.title
          )
        );

        row.append(
          text,
          createBadge(
            request.status
          )
        );

        container.appendChild(
          row
        );
      }
    );
}


/* =========================
   RENDER: REQUESTER
========================= */
function requesterRecords() {
  return state.serviceRequests;
}


function renderRequesterMetrics() {
  const records =
    requesterRecords();

  const pending =
    records.filter(
      (request) =>
        [
          "Pending",
          "For Review",
          "Awaiting Approval"
        ].includes(
          request.status
        )
    ).length;

  const progress =
    records.filter(
      (request) =>
        [
          "Assigned",
          "In Progress"
        ].includes(
          request.status
        )
    ).length;

  const completed =
    records.filter(
      (request) =>
        request.status ===
        "Completed"
    ).length;

  byId(
    "requesterTotalMetric"
  ).textContent =
    String(
      records.length
    ).padStart(
      2,
      "0"
    );

  byId(
    "requesterPendingMetric"
  ).textContent =
    String(
      pending
    ).padStart(
      2,
      "0"
    );

  byId(
    "requesterProgressMetric"
  ).textContent =
    String(
      progress
    ).padStart(
      2,
      "0"
    );

  byId(
    "requesterCompletedMetric"
  ).textContent =
    String(
      completed
    ).padStart(
      2,
      "0"
    );
}


function requestMatchesFilter(
  request
) {
  const query =
    normalizeText(
      byId(
        "requestSearchInput"
      )?.value
    );

  const status =
    byId(
      "requestStatusFilter"
    )?.value || "all";

  const searchable =
    normalizeText(
      `${request.id} ${request.title} ${request.category} ${request.location}`
    );

  const matchesSearch =
    !query ||
    searchable.includes(
      query
    );

  const matchesStatus =
    status === "all" ||
    request.status ===
      status;

  return (
    matchesSearch &&
    matchesStatus
  );
}


function makeRowAction(
  label,
  action,
  id,
  className = ""
) {
  const button =
    createElement(
      "button",
      `row-action ${className}`.trim(),
      label
    );

  button.type =
    "button";

  button.dataset.action =
    action;

  button.dataset.id =
    id;

  return button;
}


function renderRequestTable() {
  const body =
    byId(
      "serviceRequestTableBody"
    );

  if (!body) {
    return;
  }

  body.replaceChildren();

  const records =
    requesterRecords().filter(
      requestMatchesFilter
    );

  records.forEach(
    (request) => {
      const row =
        document.createElement(
          "tr"
        );

      const idCell =
        document.createElement(
          "td"
        );

      idCell.appendChild(
        createElement(
          "strong",
          "",
          request.id
        )
      );

      const priorityCell =
        document.createElement(
          "td"
        );

      priorityCell.appendChild(
        createBadge(
          request.priority
        )
      );

      const statusCell =
        document.createElement(
          "td"
        );

      statusCell.appendChild(
        createBadge(
          request.status
        )
      );

      const actionCell =
        document.createElement(
          "td"
        );

      const actions =
        createElement(
          "div",
          "row-actions"
        );

      actions.append(
        makeRowAction(
          "View",
          "view-request",
          request.id
        ),

        makeRowAction(
          "Track",
          "track-request",
          request.id,
          "primary"
        )
      );

      actionCell.appendChild(
        actions
      );

      [
        idCell,

        createElement(
          "td",
          "",
          request.title
        ),

        createElement(
          "td",
          "",
          request.category
        ),

        createElement(
          "td",
          "",
          request.location
        ),

        priorityCell,
        statusCell,

        createElement(
          "td",
          "",
          formatDate(
            request.dateReported
          )
        ),

        actionCell
      ].forEach(
        (cell) =>
          row.appendChild(
            cell
          )
      );

      body.appendChild(
        row
      );
    }
  );

  byId(
    "requestRecordCount"
  ).textContent =
    `${records.length} ${
      records.length === 1
        ? "Record"
        : "Records"
    }`;

  byId(
    "requestEmptyState"
  ).hidden =
    records.length !==
    0;
}


function renderRequesterRecentList() {
  const container =
    byId(
      "requesterRecentList"
    );

  if (!container) {
    return;
  }

  container.replaceChildren();

  requesterRecords()
    .slice(
      0,
      3
    )
    .forEach(
      (request) => {
        const item =
          createElement(
            "div",
            "compact-item"
          );

        const copy =
          createElement(
            "div"
          );

        copy.append(
          createElement(
            "strong",
            "",
            request.title
          ),

          createElement(
            "span",
            "",
            `${request.id} • ${request.category}`
          )
        );

        item.append(
          copy,
          createBadge(
            request.status
          )
        );

        container.appendChild(
          item
        );
      }
    );
}


function showLatestAiRecommendation(
  request
) {
  byId(
    "latestAiCard"
  ).hidden =
    false;

  byId(
    "latestAiCategory"
  ).textContent =
    request.aiCategory;

  byId(
    "latestAiPriority"
  ).textContent =
    request.aiPriority;

  byId(
    "latestAiDuplicate"
  ).textContent =
    request.duplicateCheck;
}


/* =========================
   RENDER: ADMINISTRATOR
========================= */
function adminReviewRecords() {
  return state.serviceRequests.filter(
    (request) =>
      [
        "For Review",
        "Pending"
      ].includes(
        request.status
      )
  );
}


function renderAdminMetrics() {
  const queue =
    adminReviewRecords();

  const awaiting =
    state.serviceRequests.filter(
      (request) =>
        request.status ===
        "Awaiting Approval"
    ).length;

  const high =
    state.serviceRequests.filter(
      (request) =>
        [
          "High",
          "Urgent"
        ].includes(
          request.priority
        ) &&
        request.status !==
          "Completed"
    ).length;

  byId(
    "adminQueueMetric"
  ).textContent =
    String(
      queue.length
    ).padStart(
      2,
      "0"
    );

  byId(
    "adminApprovalMetric"
  ).textContent =
    String(
      awaiting
    ).padStart(
      2,
      "0"
    );

  byId(
    "adminHighMetric"
  ).textContent =
    String(
      high
    ).padStart(
      2,
      "0"
    );

  byId(
    "adminWorkOrderMetric"
  ).textContent =
    String(
      state.workOrders.length
    ).padStart(
      2,
      "0"
    );
}


function renderAdminReviewTable() {
  const body =
    byId(
      "adminReviewTableBody"
    );

  if (!body) {
    return;
  }

  body.replaceChildren();

  const query =
    normalizeText(
      byId(
        "adminRequestSearch"
      )?.value
    );

  const records =
    adminReviewRecords().filter(
      (request) => {
        if (!query) {
          return true;
        }

        return normalizeText(
          `${request.id} ${request.title} ${request.aiCategory} ${request.aiPriority}`
        ).includes(
          query
        );
      }
    );

  records.forEach(
    (request) => {
      const row =
        document.createElement(
          "tr"
        );

      const idCell =
        document.createElement(
          "td"
        );

      idCell.appendChild(
        createElement(
          "strong",
          "",
          request.id
        )
      );

      const priorityCell =
        document.createElement(
          "td"
        );

      priorityCell.appendChild(
        createBadge(
          request.aiPriority
        )
      );

      const statusCell =
        document.createElement(
          "td"
        );

      statusCell.appendChild(
        createBadge(
          request.status
        )
      );

      const actionCell =
        document.createElement(
          "td"
        );

      const actions =
        createElement(
          "div",
          "row-actions"
        );

      actions.append(
        makeRowAction(
          "View",
          "view-admin-request",
          request.id
        ),

        makeRowAction(
          "Forward",
          "forward-request",
          request.id,
          "primary"
        ),

        makeRowAction(
          "Flag Duplicate",
          "duplicate-request",
          request.id,
          "danger"
        )
      );

      actionCell.appendChild(
        actions
      );

      [
        idCell,

        createElement(
          "td",
          "",
          request.title
        ),

        createElement(
          "td",
          "",
          request.aiCategory
        ),

        priorityCell,

        createElement(
          "td",
          "",
          request.duplicateCheck
        ),

        statusCell,
        actionCell
      ].forEach(
        (cell) =>
          row.appendChild(
            cell
          )
      );

      body.appendChild(
        row
      );
    }
  );

  byId(
    "adminReviewCount"
  ).textContent =
    `${records.length} ${
      records.length === 1
        ? "Record"
        : "Records"
    }`;
}


function renderAdminAiSummary() {
  const container =
    byId(
      "adminAiSummary"
    );

  if (!container) {
    return;
  }

  container.replaceChildren();

  const queue =
    adminReviewRecords().slice(
      0,
      3
    );

  if (
    queue.length ===
    0
  ) {
    container.appendChild(
      createElement(
        "div",
        "notification-empty",
        "No requests currently need AI recommendation review."
      )
    );

    return;
  }

  queue.forEach(
    (request) => {
      const item =
        createElement(
          "div",
          "compact-item"
        );

      const copy =
        createElement(
          "div"
        );

      copy.append(
        createElement(
          "strong",
          "",
          request.id
        ),

        createElement(
          "span",
          "",
          `${request.aiCategory} • ${request.duplicateCheck}`
        )
      );

      item.append(
        copy,
        createBadge(
          request.aiPriority
        )
      );

      container.appendChild(
        item
      );
    }
  );
}


function renderWorkOrderTable() {
  const body =
    byId(
      "workOrderTableBody"
    );

  if (!body) {
    return;
  }

  body.replaceChildren();

  state.workOrders.forEach(
    (workOrder) => {
      const row =
        document.createElement(
          "tr"
        );

      const idCell =
        document.createElement(
          "td"
        );

      idCell.appendChild(
        createElement(
          "strong",
          "",
          workOrder.id
        )
      );

      const statusCell =
        document.createElement(
          "td"
        );

      statusCell.appendChild(
        createBadge(
          workOrder.status
        )
      );

      const actionCell =
        document.createElement(
          "td"
        );

      const actions =
        createElement(
          "div",
          "row-actions"
        );

      actions.append(
        makeRowAction(
          "View",
          "view-work-order",
          workOrder.id
        ),

        makeRowAction(
          "Edit",
          "edit-work-order",
          workOrder.id,
          "primary"
        )
      );

      actionCell.appendChild(
        actions
      );

      [
        idCell,

        createElement(
          "td",
          "",
          workOrder.requestId
        ),

        createElement(
          "td",
          "",
          workOrder.assignedPersonnel
        ),

        createElement(
          "td",
          "",
          workOrder.serviceUnit
        ),

        statusCell,

        createElement(
          "td",
          "",
          formatDate(
            workOrder.completionDate
          )
        ),

        actionCell
      ].forEach(
        (cell) =>
          row.appendChild(
            cell
          )
      );

      body.appendChild(
        row
      );
    }
  );

  byId(
    "workOrderRecordCount"
  ).textContent =
    `${state.workOrders.length} ${
      state.workOrders.length === 1
        ? "Record"
        : "Records"
    }`;
}


function renderPersonnelTable() {
  const body =
    byId(
      "personnelTableBody"
    );

  if (!body) {
    return;
  }

  body.replaceChildren();

  state.personnel.forEach(
    (person) => {
      const row =
        document.createElement(
          "tr"
        );

      const idCell =
        document.createElement(
          "td"
        );

      idCell.appendChild(
        createElement(
          "strong",
          "",
          person.id
        )
      );

      const statusCell =
        document.createElement(
          "td"
        );

      statusCell.appendChild(
        createBadge(
          person.status
        )
      );

      [
        idCell,

        createElement(
          "td",
          "",
          person.name
        ),

        createElement(
          "td",
          "",
          person.email
        ),

        createElement(
          "td",
          "",
          person.department
        ),

        createElement(
          "td",
          "",
          person.role
        ),

        statusCell
      ].forEach(
        (cell) =>
          row.appendChild(
            cell
          )
      );

      body.appendChild(
        row
      );
    }
  );

  byId(
    "personnelRecordCount"
  ).textContent =
    `${state.personnel.length} ${
      state.personnel.length === 1
        ? "Record"
        : "Records"
    }`;
}


/* =========================
   RENDER: APPROVER
========================= */
function pendingApprovals() {
  return state.approvals.filter(
    (approval) =>
      approval.decision ===
      "Pending"
  );
}


function renderApproverMetrics() {
  const pending =
    pendingApprovals().length;

  const approved =
    state.approvalHistory.filter(
      (item) =>
        item.decision ===
        "Approved"
    ).length;

  const rejected =
    state.approvalHistory.filter(
      (item) =>
        item.decision ===
        "Rejected"
    ).length;

  byId(
    "approverPendingMetric"
  ).textContent =
    String(
      pending
    ).padStart(
      2,
      "0"
    );

  byId(
    "approverApprovedMetric"
  ).textContent =
    String(
      approved
    ).padStart(
      2,
      "0"
    );

  byId(
    "approverRejectedMetric"
  ).textContent =
    String(
      rejected
    ).padStart(
      2,
      "0"
    );
}


function renderApprovalTable() {
  const body =
    byId(
      "approvalTableBody"
    );

  if (!body) {
    return;
  }

  body.replaceChildren();

  pendingApprovals().forEach(
    (approval) => {
      const request =
        findRequest(
          approval.requestId
        );

      if (!request) {
        return;
      }

      const row =
        document.createElement(
          "tr"
        );

      const idCell =
        document.createElement(
          "td"
        );

      idCell.appendChild(
        createElement(
          "strong",
          "",
          request.id
        )
      );

      const priorityCell =
        document.createElement(
          "td"
        );

      priorityCell.appendChild(
        createBadge(
          request.priority
        )
      );

      const statusCell =
        document.createElement(
          "td"
        );

      statusCell.appendChild(
        createBadge(
          "Awaiting Approval"
        )
      );

      const actionCell =
        document.createElement(
          "td"
        );

      const actions =
        createElement(
          "div",
          "row-actions"
        );

      actions.append(
        makeRowAction(
          "View",
          "view-approval",
          request.id
        ),

        makeRowAction(
          "Approve",
          "approve-request",
          request.id,
          "primary"
        ),

        makeRowAction(
          "Reject",
          "reject-request",
          request.id,
          "danger"
        )
      );

      actionCell.appendChild(
        actions
      );

      [
        idCell,

        createElement(
          "td",
          "",
          request.title
        ),

        createElement(
          "td",
          "",
          request.aiCategory
        ),

        priorityCell,

        createElement(
          "td",
          "",
          `${request.aiCategory} / ${request.aiPriority}`
        ),

        statusCell,
        actionCell
      ].forEach(
        (cell) =>
          row.appendChild(
            cell
          )
      );

      body.appendChild(
        row
      );
    }
  );

  const count =
    pendingApprovals().length;

  byId(
    "approvalRecordCount"
  ).textContent =
    `${count} ${
      count === 1
        ? "Record"
        : "Records"
    }`;
}


function renderApprovalHistory() {
  const body =
    byId(
      "approvalHistoryTableBody"
    );

  if (!body) {
    return;
  }

  body.replaceChildren();

  state.approvalHistory.forEach(
    (record) => {
      const row =
        document.createElement(
          "tr"
        );

      const idCell =
        document.createElement(
          "td"
        );

      idCell.appendChild(
        createElement(
          "strong",
          "",
          record.requestId
        )
      );

      const decisionCell =
        document.createElement(
          "td"
        );

      decisionCell.appendChild(
        createBadge(
          record.decision
        )
      );

      [
        idCell,
        decisionCell,

        createElement(
          "td",
          "",
          formatDate(
            record.decisionDate
          )
        ),

        createElement(
          "td",
          "",
          record.remarks ||
            "—"
        )
      ].forEach(
        (cell) =>
          row.appendChild(
            cell
          )
      );

      body.appendChild(
        row
      );
    }
  );

  byId(
    "approvalHistoryCount"
  ).textContent =
    `${state.approvalHistory.length} ${
      state.approvalHistory.length ===
      1
        ? "Record"
        : "Records"
    }`;
}


/* =========================
   RENDER: TECHNICIAN
========================= */
function renderTechnicianMetrics() {
  const assigned =
    state.workOrders.filter(
      (workOrder) =>
        workOrder.status !==
        "Completed"
    ).length;

  const progress =
    state.workOrders.filter(
      (workOrder) =>
        workOrder.status ===
        "In Progress"
    ).length;

  const hold =
    state.workOrders.filter(
      (workOrder) =>
        workOrder.status ===
        "On Hold"
    ).length;

  const completed =
    state.workOrders.filter(
      (workOrder) =>
        workOrder.status ===
        "Completed"
    ).length;

  byId(
    "technicianAssignedMetric"
  ).textContent =
    String(
      assigned
    ).padStart(
      2,
      "0"
    );

  byId(
    "technicianProgressMetric"
  ).textContent =
    String(
      progress
    ).padStart(
      2,
      "0"
    );

  byId(
    "technicianHoldMetric"
  ).textContent =
    String(
      hold
    ).padStart(
      2,
      "0"
    );

  byId(
    "technicianCompletedMetric"
  ).textContent =
    String(
      completed
    ).padStart(
      2,
      "0"
    );
}


function renderTechnicianWorkTable() {
  const body =
    byId(
      "technicianWorkTableBody"
    );

  if (!body) {
    return;
  }

  body.replaceChildren();

  state.workOrders.forEach(
    (workOrder) => {
      const row =
        document.createElement(
          "tr"
        );

      const idCell =
        document.createElement(
          "td"
        );

      idCell.appendChild(
        createElement(
          "strong",
          "",
          workOrder.id
        )
      );

      const statusCell =
        document.createElement(
          "td"
        );

      statusCell.appendChild(
        createBadge(
          workOrder.status
        )
      );

      const actionCell =
        document.createElement(
          "td"
        );

      const actions =
        createElement(
          "div",
          "row-actions"
        );

      actions.append(
        makeRowAction(
          "View",
          "view-tech-work",
          workOrder.id
        ),

        makeRowAction(
          "Update",
          "update-tech-work",
          workOrder.id,
          "primary"
        )
      );

      actionCell.appendChild(
        actions
      );

      [
        idCell,

        createElement(
          "td",
          "",
          workOrder.requestId
        ),

        createElement(
          "td",
          "",
          workOrder.workDescription
        ),

        createElement(
          "td",
          "",
          workOrder.serviceUnit
        ),

        createElement(
          "td",
          "",
          workOrder.assignedPersonnel
        ),

        statusCell,
        actionCell
      ].forEach(
        (cell) =>
          row.appendChild(
            cell
          )
      );

      body.appendChild(
        row
      );
    }
  );

  byId(
    "technicianWorkCount"
  ).textContent =
    `${state.workOrders.length} ${
      state.workOrders.length === 1
        ? "Record"
        : "Records"
    }`;
}


function renderTechnicianHistory() {
  const body =
    byId(
      "technicianHistoryTableBody"
    );

  const compact =
    byId(
      "technicianHistoryList"
    );

  if (
    !body ||
    !compact
  ) {
    return;
  }

  body.replaceChildren();
  compact.replaceChildren();

  state.technicianHistory.forEach(
    (record) => {
      const row =
        document.createElement(
          "tr"
        );

      const idCell =
        document.createElement(
          "td"
        );

      idCell.appendChild(
        createElement(
          "strong",
          "",
          record.workOrderId
        )
      );

      const statusCell =
        document.createElement(
          "td"
        );

      statusCell.appendChild(
        createBadge(
          record.status
        )
      );

      [
        idCell,

        createElement(
          "td",
          "",
          record.requestId
        ),

        statusCell,

        createElement(
          "td",
          "",
          record.actionTaken
        ),

        createElement(
          "td",
          "",
          formatDate(
            record.completionDate
          )
        ),

        createElement(
          "td",
          "",
          record.remarks ||
            "—"
        )
      ].forEach(
        (cell) =>
          row.appendChild(
            cell
          )
      );

      body.appendChild(
        row
      );
    }
  );

  state.technicianHistory
    .slice(
      0,
      4
    )
    .forEach(
      (record) => {
        const item =
          createElement(
            "div",
            "compact-item"
          );

        const copy =
          createElement(
            "div"
          );

        copy.append(
          createElement(
            "strong",
            "",
            record.workOrderId
          ),

          createElement(
            "span",
            "",
            record.actionTaken
          )
        );

        item.append(
          copy,
          createBadge(
            record.status
          )
        );

        compact.appendChild(
          item
        );
      }
    );

  if (
    state.technicianHistory.length ===
    0
  ) {
    compact.appendChild(
      createElement(
        "div",
        "notification-empty",
        "No technician updates recorded yet."
      )
    );
  }

  byId(
    "technicianHistoryCount"
  ).textContent =
    `${state.technicianHistory.length} ${
      state.technicianHistory.length ===
      1
        ? "Record"
        : "Records"
    }`;
}


/* =========================
   RENDER: NOTIFICATIONS
========================= */
function roleNotifications() {
  if (
    !state.activeRole
  ) {
    return [];
  }

  return state.notifications.filter(
    (notification) =>
      notification.targetRole ===
        state.activeRole ||
      notification.targetRole ===
        "all"
  );
}


function renderNotifications() {
  const list =
    byId(
      "notificationList"
    );

  const count =
    byId(
      "notificationCount"
    );

  if (
    !list ||
    !count
  ) {
    return;
  }

  const notifications =
    roleNotifications();

  count.textContent =
    String(
      notifications.length
    );

  list.replaceChildren();

  if (
    notifications.length ===
    0
  ) {
    list.appendChild(
      createElement(
        "div",
        "notification-empty",
        "No notifications for this role yet."
      )
    );

    return;
  }

  notifications
    .slice(
      0,
      8
    )
    .forEach(
      (notification) => {
        const item =
          createElement(
            "article",
            "notification-item"
          );

        item.append(
          createElement(
            "strong",
            "",
            notification.title
          ),

          createElement(
            "p",
            "",
            notification.message
          ),

          createElement(
            "small",
            "",
            formatDate(
              notification.date
            )
          )
        );

        list.appendChild(
          item
        );
      }
    );
}


/* =========================
   MASTER RENDER
========================= */
function renderAll() {
  renderPublicMetrics();
  renderPublicActivity();

  renderRequesterMetrics();
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

  renderTechnicianMetrics();
  renderTechnicianWorkTable();
  renderTechnicianHistory();

  renderNotifications();
}


/* =========================
   REQUEST DETAILS
========================= */
function showRequestDetails(
  request
) {
  showDetails(
    request.id,
    "SERVICE_REQUEST",
    [
      {
        label:
          "Request Title",
        value:
          request.title,
        full:
          true
      },

      {
        label:
          "Requester",
        value:
          request.requesterName
      },

      {
        label:
          "Email",
        value:
          request.requesterEmail
      },

      {
        label:
          "Department / Program",
        value:
          request.department
      },

      {
        label:
          "Location / Room",
        value:
          request.location
      },

      {
        label:
          "Category",
        value:
          request.category
      },

      {
        label:
          "Priority",
        value:
          request.priority,
        badge:
          true
      },

      {
        label:
          "Status",
        value:
          request.status,
        badge:
          true
      },

      {
        label:
          "Date Reported",
        value:
          formatDate(
            request.dateReported
          )
      },

      {
        label:
          "Attachment",
        value:
          request.attachmentName ||
          "No attachment"
      },

      {
        label:
          "Detailed Description",
        value:
          request.description,
        full:
          true
      },

      {
        label:
          "AI Suggested Category",
        value:
          request.aiCategory
      },

      {
        label:
          "AI Suggested Priority",
        value:
          request.aiPriority
      },

      {
        label:
          "Duplicate Check",
        value:
          request.duplicateCheck,
        full:
          true
      },

      {
        label:
          "AI Notice",
        value:
          "Advisory only. Authorized school personnel make the final decision.",
        full:
          true
      }
    ]
  );
}


function showWorkOrderDetails(
  workOrder
) {
  showDetails(
    workOrder.id,
    "WORK_ORDER + ASSIGNMENT",
    [
      {
        label:
          "Service Request ID",
        value:
          workOrder.requestId
      },

      {
        label:
          "Assigned Personnel",
        value:
          workOrder.assignedPersonnel
      },

      {
        label:
          "Service Unit",
        value:
          workOrder.serviceUnit
      },

      {
        label:
          "Status",
        value:
          workOrder.status,
        badge:
          true
      },

      {
        label:
          "Work Description",
        value:
          workOrder.workDescription,
        full:
          true
      },

      {
        label:
          "Action Taken",
        value:
          workOrder.actionTaken ||
          "No action recorded yet",
        full:
          true
      },

      {
        label:
          "Materials / Resources",
        value:
          workOrder.materials ||
          "None recorded",
        full:
          true
      },

      {
        label:
          "Completion Date",
        value:
          formatDate(
            workOrder.completionDate
          )
      },

      {
        label:
          "Last Updated",
        value:
          formatDate(
            workOrder.updatedAt
          )
      },

      {
        label:
          "Technician Remarks",
        value:
          workOrder.remarks ||
          "No remarks",
        full:
          true
      }
    ]
  );
}


/* =========================
   FORM HELPERS
========================= */
function setDateRestrictions() {
  const today =
    todayInputValue();

  const requestDate =
    byId(
      "requestDate"
    );

  const completionDate =
    byId(
      "completionDate"
    );

  const progressCompletionDate =
    byId(
      "progressCompletionDate"
    );

  if (
    requestDate
  ) {
    requestDate.max =
      today;

    requestDate.value =
      today;
  }

  if (
    completionDate
  ) {
    completionDate.max =
      today;
  }

  if (
    progressCompletionDate
  ) {
    progressCompletionDate.max =
      today;
  }
}


function validateAttachment(
  input
) {
  const file =
    input.files?.[0];

  input.setCustomValidity(
    ""
  );

  if (!file) {
    return true;
  }

  const allowedTypes = [
    "image/png",
    "image/jpeg",
    "image/webp",
    "application/pdf"
  ];

  if (
    !allowedTypes.includes(
      file.type
    )
  ) {
    input.setCustomValidity(
      "Use a PNG, JPG, WEBP, or PDF file."
    );

    input.reportValidity();

    return false;
  }

  if (
    file.size >
    5 * 1024 * 1024
  ) {
    input.setCustomValidity(
      "The selected file must be 5 MB or smaller for this prototype."
    );

    input.reportValidity();

    return false;
  }

  return true;
}


function resetServiceRequestFormForUser() {
  window.setTimeout(
    () => {
      byId(
        "requestCharacterCount"
      ).textContent =
        "0";

      byId(
        "requestDate"
      ).value =
        todayInputValue();

      if (
        state.activeRole ===
        "requester"
      ) {
        byId(
          "requesterName"
        ).value =
          state.userName;

        byId(
          "requesterEmail"
        ).value =
          state.userEmail;
      }
    },
    0
  );
}


function populateWorkOrderForm(
  workOrder
) {
  byId(
    "workOrderId"
  ).value =
    workOrder.id;

  byId(
    "workOrderRequestId"
  ).value =
    workOrder.requestId;

  byId(
    "assignedTechnician"
  ).value =
    workOrder.assignedPersonnel;

  byId(
    "serviceUnit"
  ).value =
    workOrder.serviceUnit;

  byId(
    "workDescription"
  ).value =
    workOrder.workDescription;

  byId(
    "workStatus"
  ).value =
    workOrder.status;

  byId(
    "actionTaken"
  ).value =
    workOrder.actionTaken;

  byId(
    "materialsUsed"
  ).value =
    workOrder.materials;

  byId(
    "completionDate"
  ).value =
    workOrder.completionDate;

  byId(
    "technicianRemarks"
  ).value =
    workOrder.remarks;
}


function populateTechnicianUpdateForm(
  workOrder
) {
  byId(
    "progressWorkOrderId"
  ).value =
    workOrder.id;

  byId(
    "progressRequestId"
  ).value =
    workOrder.requestId;

  byId(
    "progressTechnician"
  ).value =
    workOrder.assignedPersonnel ||
    state.userName;

  byId(
    "progressServiceUnit"
  ).value =
    workOrder.serviceUnit;

  byId(
    "progressWorkDescription"
  ).value =
    workOrder.workDescription;

  byId(
    "progressStatusInput"
  ).value =
    workOrder.status;

  byId(
    "progressCompletionDate"
  ).value =
    workOrder.completionDate;

  byId(
    "progressActionTaken"
  ).value =
    workOrder.actionTaken;

  byId(
    "progressMaterials"
  ).value =
    workOrder.materials;

  byId(
    "progressRemarks"
  ).value =
    workOrder.remarks;
}


function validateCompletionDate(
  statusInput,
  dateInput
) {
  dateInput.setCustomValidity(
    ""
  );

  if (
    statusInput.value ===
      "Completed" &&
    !dateInput.value
  ) {
    dateInput.setCustomValidity(
      "Enter a completion date when the work status is Completed."
    );

    dateInput.reportValidity();

    return false;
  }

  return true;
}


/* =========================
   FORM: LOGIN
========================= */
function restoreRememberedUser() {
  try {
    const saved =
      JSON.parse(
        localStorage.getItem(
          "htcPortalRememberedUser"
        ) || "null"
      );

    if (!saved) {
      return;
    }

    byId(
      "loginName"
    ).value =
      saved.name || "";

    byId(
      "loginEmail"
    ).value =
      saved.email || "";

    byId(
      "rememberMe"
    ).checked =
      true;
  } catch {
    localStorage.removeItem(
      "htcPortalRememberedUser"
    );
  }
}


function handleLoginSubmit(
  event
) {
  event.preventDefault();

  const form =
    event.currentTarget;

  if (
    !form.reportValidity()
  ) {
    return;
  }

  const name =
    byId(
      "loginName"
    ).value.trim();

  const email =
    byId(
      "loginEmail"
    ).value.trim();

  const role =
    byId(
      "loginRole"
    ).value;

  try {
    if (
      byId(
        "rememberMe"
      ).checked
    ) {
      localStorage.setItem(
        "htcPortalRememberedUser",
        JSON.stringify({
          name,
          email
        })
      );
    } else {
      localStorage.removeItem(
        "htcPortalRememberedUser"
      );
    }
  } catch {
    // Local storage may be disabled.
    // The prototype still works.
  }

  closeModal(
    "loginModal"
  );

  activateRoleWorkspace(
    role,
    name,
    email
  );

  showToast(
    `Signed in to the ${roleConfiguration[role].label} workspace.`,
    "success"
  );

  const password =
    byId(
      "loginPassword"
    );

  password.value =
    "";
}


/* =========================
   FORM: SERVICE REQUEST
========================= */
function handleServiceRequestSubmit(
  event
) {
  event.preventDefault();

  const form =
    event.currentTarget;

  const attachment =
    byId(
      "requestAttachment"
    );

  if (
    !validateAttachment(
      attachment
    )
  ) {
    return;
  }

  if (
    !form.reportValidity()
  ) {
    return;
  }

  const title =
    byId(
      "requestTitle"
    ).value.trim();

  const description =
    byId(
      "requestDescription"
    ).value.trim();

  const selectedCategory =
    byId(
      "requestCategory"
    ).value;

  const aiCategory =
    recommendCategory(
      `${title} ${description}`
    );

  const aiPriority =
    recommendPriority(
      `${title} ${description}`
    );

  const duplicateCheck =
    findPossibleDuplicate(
      title,
      description
    );

  const finalCategory =
    selectedCategory ===
    "AI Recommendation"
      ? aiCategory
      : selectedCategory;

  state.requestNumber +=
    1;

  const requestId =
    `SR-2026-${String(
      state.requestNumber
    ).padStart(
      6,
      "0"
    )}`;

  const request = {
    id:
      requestId,

    requesterName:
      byId(
        "requesterName"
      ).value.trim(),

    requesterEmail:
      byId(
        "requesterEmail"
      ).value.trim(),

    requesterType:
      byId(
        "requesterTypeInput"
      ).value,

    department:
      byId(
        "requestDepartment"
      ).value,

    category:
      finalCategory,

    requestedCategory:
      selectedCategory,

    title,
    description,

    location:
      byId(
        "requestLocation"
      ).value.trim(),

    preferredPriority:
      byId(
        "requestPriority"
      ).value,

    aiCategory,
    aiPriority,
    duplicateCheck,

    priority:
      byId(
        "requestPriority"
      ).value,

    status:
      "For Review",

    dateReported:
      byId(
        "requestDate"
      ).value,

    attachmentName:
      attachment.files?.[0]
        ?.name || ""
  };

  state.serviceRequests.unshift(
    request
  );

  addNotification(
    "requester",
    "Service request submitted",
    `${requestId} was added to this browser-only demonstration.`
  );

  addNotification(
    "administrator",
    "New request received",
    `${requestId} is ready for administrator review.`
  );

  renderAll();

  showLatestAiRecommendation(
    request
  );

  form.reset();

  closeModal(
    "serviceRequestModal"
  );

  openWorkspaceView(
    "requester-records"
  );

  showToast(
    `Demo request ${requestId} added locally. Full database functionality is available in the deployed application.`,
    "success"
  );
}


/* =========================
   FORM: WORK ORDER / ASSIGNMENT
========================= */
function handleWorkOrderSubmit(
  event
) {
  event.preventDefault();

  const form =
    event.currentTarget;

  if (
    !form.reportValidity()
  ) {
    return;
  }

  if (
    !validateCompletionDate(
      byId(
        "workStatus"
      ),
      byId(
        "completionDate"
      )
    )
  ) {
    return;
  }

  const requestId =
    byId(
      "workOrderRequestId"
    )
      .value
      .trim()
      .toUpperCase();

  const request =
    findRequest(
      requestId
    );

  if (!request) {
    byId(
      "workOrderRequestId"
    ).setCustomValidity(
      "Enter an existing Service Request ID from the demo records."
    );

    byId(
      "workOrderRequestId"
    ).reportValidity();

    return;
  }

  byId(
    "workOrderRequestId"
  ).setCustomValidity(
    ""
  );

  const enteredWorkOrderId =
    byId(
      "workOrderId"
    )
      .value
      .trim()
      .toUpperCase();

  const existing =
    enteredWorkOrderId
      ? findWorkOrder(
          enteredWorkOrderId
        )
      : null;

  if (
    enteredWorkOrderId &&
    !existing
  ) {
    byId(
      "workOrderId"
    ).setCustomValidity(
      "This Work Order ID does not exist. Leave it blank to create a new work order."
    );

    byId(
      "workOrderId"
    ).reportValidity();

    return;
  }

  byId(
    "workOrderId"
  ).setCustomValidity(
    ""
  );

  const values = {
    requestId,

    assignedPersonnel:
      byId(
        "assignedTechnician"
      ).value,

    serviceUnit:
      byId(
        "serviceUnit"
      ).value,

    workDescription:
      byId(
        "workDescription"
      ).value.trim(),

    status:
      byId(
        "workStatus"
      ).value,

    actionTaken:
      byId(
        "actionTaken"
      ).value.trim(),

    materials:
      byId(
        "materialsUsed"
      ).value.trim(),

    completionDate:
      byId(
        "completionDate"
      ).value,

    remarks:
      byId(
        "technicianRemarks"
      ).value.trim(),

    updatedAt:
      todayInputValue()
  };

  let workOrderId;
  let verb;

  if (existing) {
    Object.assign(
      existing,
      values
    );

    workOrderId =
      existing.id;

    verb =
      "updated";
  } else {
    state.workOrderNumber +=
      1;

    workOrderId =
      `WO-2026-${String(
        state.workOrderNumber
      ).padStart(
        4,
        "0"
      )}`;

    state.workOrders.unshift({
      id:
        workOrderId,
      ...values
    });

    verb =
      "created";
  }

  request.status =
    values.status ===
    "Completed"
      ? "Completed"
      : values.status;

  addNotification(
    "technician",
    "Work order assigned / updated",
    `${workOrderId} for ${requestId} is ${values.status}.`
  );

  addNotification(
    "requester",
    "Request status updated",
    `${requestId} is now ${request.status}.`
  );

  renderAll();

  form.reset();

  showToast(
    `${workOrderId} was ${verb} successfully.`,
    "success"
  );
}


/* =========================
   FORM: PERSONNEL ACCESS
========================= */
function handlePersonnelSubmit(
  event
) {
  event.preventDefault();

  const form =
    event.currentTarget;

  const selectedRights =
    all(
      'input[name="accessRights"]:checked'
    ).map(
      (checkbox) =>
        checkbox.value
    );

  if (
    selectedRights.length ===
    0
  ) {
    byId(
      "permissionError"
    ).textContent =
      "Select at least one access right.";

    all(
      'input[name="accessRights"]'
    )[0]?.focus();

    return;
  }

  if (
    !form.reportValidity()
  ) {
    return;
  }

  byId(
    "permissionError"
  ).textContent =
    "";

  state.personnelNumber +=
    1;

  const person = {
    id:
      `SP-${String(
        state.personnelNumber
      ).padStart(
        3,
        "0"
      )}`,

    name:
      byId(
        "personnelName"
      ).value.trim(),

    email:
      byId(
        "personnelEmail"
      ).value.trim(),

    department:
      byId(
        "personnelDepartment"
      ).value,

    role:
      byId(
        "personnelRole"
      ).value,

    rights:
      selectedRights,

    status:
      byId(
        "personnelStatus"
      ).value
  };

  state.personnel.unshift(
    person
  );

  renderPersonnelTable();

  form.reset();

  showToast(
    `${person.id} was added to the SCHOOL_PERSONNEL demo table.`,
    "success"
  );
}


/* =========================
   FORM: TECHNICIAN UPDATE
========================= */
function handleProgressUpdateSubmit(
  event
) {
  event.preventDefault();

  const form =
    event.currentTarget;

  if (
    !form.reportValidity()
  ) {
    return;
  }

  if (
    !validateCompletionDate(
      byId(
        "progressStatusInput"
      ),
      byId(
        "progressCompletionDate"
      )
    )
  ) {
    return;
  }

  const workOrderId =
    byId(
      "progressWorkOrderId"
    )
      .value
      .trim()
      .toUpperCase();

  const requestId =
    byId(
      "progressRequestId"
    )
      .value
      .trim()
      .toUpperCase();

  const workOrder =
    findWorkOrder(
      workOrderId
    );

  if (!workOrder) {
    byId(
      "progressWorkOrderId"
    ).setCustomValidity(
      "This work order is not in the assigned prototype queue."
    );

    byId(
      "progressWorkOrderId"
    ).reportValidity();

    return;
  }

  byId(
    "progressWorkOrderId"
  ).setCustomValidity(
    ""
  );

  if (
    workOrder.requestId !==
    requestId
  ) {
    byId(
      "progressRequestId"
    ).setCustomValidity(
      `This work order is linked to ${workOrder.requestId}.`
    );

    byId(
      "progressRequestId"
    ).reportValidity();

    return;
  }

  byId(
    "progressRequestId"
  ).setCustomValidity(
    ""
  );

  const status =
    byId(
      "progressStatusInput"
    ).value;

  Object.assign(
    workOrder,
    {
      requestId,

      assignedPersonnel:
        byId(
          "progressTechnician"
        ).value.trim(),

      serviceUnit:
        byId(
          "progressServiceUnit"
        ).value,

      workDescription:
        byId(
          "progressWorkDescription"
        ).value.trim(),

      status,

      actionTaken:
        byId(
          "progressActionTaken"
        ).value.trim(),

      materials:
        byId(
          "progressMaterials"
        ).value.trim(),

      completionDate:
        byId(
          "progressCompletionDate"
        ).value,

      remarks:
        byId(
          "progressRemarks"
        ).value.trim(),

      updatedAt:
        todayInputValue()
    }
  );

  const request =
    findRequest(
      requestId
    );

  if (request) {
    request.status =
      status;
  }

  state.technicianHistory.unshift(
    {
      workOrderId,
      requestId,
      status,

      actionTaken:
        workOrder.actionTaken,

      completionDate:
        workOrder.completionDate,

      remarks:
        workOrder.remarks
    }
  );

  addNotification(
    "administrator",
    "Technician update recorded",
    `${workOrderId} was updated to ${status}.`
  );

  addNotification(
    "requester",
    "Service request updated",
    `${requestId} is now ${status}.`
  );

  renderAll();

  form.reset();

  byId(
    "progressTechnician"
  ).value =
    state.userName;

  openWorkspaceView(
    "technician-assigned"
  );

  showToast(
    `Work order ${workOrderId} updated successfully.`,
    "success"
  );
}


/* =========================
   ADMIN / APPROVAL ACTIONS
========================= */
function forwardRequest(
  requestId
) {
  const request =
    findRequest(
      requestId
    );

  if (!request) {
    return;
  }

  request.status =
    "Awaiting Approval";

  const alreadyPending =
    state.approvals.some(
      (approval) =>
        approval.requestId ===
          requestId &&
        approval.decision ===
          "Pending"
    );

  if (
    !alreadyPending
  ) {
    state.approvals.unshift(
      {
        requestId,
        decision:
          "Pending",
        decisionDate:
          "",
        remarks:
          ""
      }
    );
  }

  addNotification(
    "approver",
    "Approval required",
    `${requestId} was forwarded for authorization.`
  );

  addNotification(
    "requester",
    "Request forwarded",
    `${requestId} is awaiting approval.`
  );

  renderAll();

  showToast(
    `${requestId} was forwarded to the Department Head / Authorized Approver.`,
    "success"
  );
}


function flagDuplicate(
  requestId
) {
  const request =
    findRequest(
      requestId
    );

  if (!request) {
    return;
  }

  request.duplicateCheck =
    request.duplicateCheck.startsWith(
      "Possible match"
    )
      ? request.duplicateCheck
      : "Possible duplicate flagged by administrator";

  request.status =
    "For Review";

  renderAll();

  showToast(
    `${requestId} was flagged for duplicate review.`,
    "warning"
  );
}


function decideApproval(
  requestId,
  decision
) {
  const approval =
    state.approvals.find(
      (item) =>
        item.requestId ===
          requestId &&
        item.decision ===
          "Pending"
    );

  const request =
    findRequest(
      requestId
    );

  if (
    !approval ||
    !request
  ) {
    return;
  }

  approval.decision =
    decision;

  approval.decisionDate =
    todayInputValue();

  approval.remarks =
    decision ===
    "Approved"
      ? "Authorized for service processing."
      : "Request rejected during authorization review.";

  request.status =
    decision;

  state.approvalHistory.unshift(
    {
      ...approval
    }
  );

  addNotification(
    "administrator",
    `Request ${decision.toLowerCase()}`,
    `${requestId} was ${decision.toLowerCase()} by the authorized approver.`
  );

  addNotification(
    "requester",
    `Request ${decision.toLowerCase()}`,
    `${requestId} was ${decision.toLowerCase()} by the authorized approver.`
  );

  renderAll();

  showToast(
    `${requestId} was ${decision.toLowerCase()}.`,
    decision ===
    "Approved"
      ? "success"
      : "warning"
  );
}


/* =========================
   EVENT DELEGATION:
   REQUESTER TABLE
========================= */
function handleRequestTableAction(
  event
) {
  const button =
    event.target.closest(
      "[data-action]"
    );

  if (!button) {
    return;
  }

  const request =
    findRequest(
      button.dataset.id
    );

  if (!request) {
    return;
  }

  if (
    button.dataset.action ===
    "view-request"
  ) {
    showRequestDetails(
      request
    );
  }

  if (
    button.dataset.action ===
    "track-request"
  ) {
    showToast(
      `${request.id} is currently ${request.status}.`,
      "info"
    );
  }
}


/* =========================
   EVENT DELEGATION:
   ADMIN TABLE
========================= */
function handleAdminTableAction(
  event
) {
  const button =
    event.target.closest(
      "[data-action]"
    );

  if (!button) {
    return;
  }

  const request =
    findRequest(
      button.dataset.id
    );

  if (!request) {
    return;
  }

  if (
    button.dataset.action ===
    "view-admin-request"
  ) {
    showRequestDetails(
      request
    );
  }

  if (
    button.dataset.action ===
    "forward-request"
  ) {
    openConfirm({
      title:
        "Forward request for approval?",

      message:
        `${request.id} will be sent to the Department Head / Authorized Approver.`,

      confirmLabel:
        "Forward Request",

      onConfirm:
        () =>
          forwardRequest(
            request.id
          )
    });
  }

  if (
    button.dataset.action ===
    "duplicate-request"
  ) {
    openConfirm({
      title:
        "Flag possible duplicate?",

      message:
        `${request.id} will remain under review and be marked for duplicate checking.`,

      confirmLabel:
        "Flag Duplicate",

      type:
        "danger",

      onConfirm:
        () =>
          flagDuplicate(
            request.id
          )
    });
  }
}


/* =========================
   EVENT DELEGATION:
   APPROVAL TABLE
========================= */
function handleApprovalTableAction(
  event
) {
  const button =
    event.target.closest(
      "[data-action]"
    );

  if (!button) {
    return;
  }

  const request =
    findRequest(
      button.dataset.id
    );

  if (!request) {
    return;
  }

  if (
    button.dataset.action ===
    "view-approval"
  ) {
    showRequestDetails(
      request
    );

    return;
  }

  if (
    button.dataset.action ===
    "approve-request"
  ) {
    openConfirm({
      title:
        "Approve this request?",

      message:
        `${request.id} will be recorded as Approved and may proceed to work-order assignment.`,

      confirmLabel:
        "Approve Request",

      onConfirm:
        () =>
          decideApproval(
            request.id,
            "Approved"
          )
    });
  }

  if (
    button.dataset.action ===
    "reject-request"
  ) {
    openConfirm({
      title:
        "Reject this request?",

      message:
        `${request.id} will be recorded as Rejected in the approval history.`,

      confirmLabel:
        "Reject Request",

      type:
        "danger",

      onConfirm:
        () =>
          decideApproval(
            request.id,
            "Rejected"
          )
    });
  }
}


/* =========================
   EVENT DELEGATION:
   WORK ORDER TABLE
========================= */
function handleWorkOrderTableAction(
  event
) {
  const button =
    event.target.closest(
      "[data-action]"
    );

  if (!button) {
    return;
  }

  const workOrder =
    findWorkOrder(
      button.dataset.id
    );

  if (!workOrder) {
    return;
  }

  if (
    button.dataset.action ===
    "view-work-order"
  ) {
    showWorkOrderDetails(
      workOrder
    );
  }

  if (
    button.dataset.action ===
    "edit-work-order"
  ) {
    populateWorkOrderForm(
      workOrder
    );

    byId(
      "workOrderId"
    )?.focus();

    showToast(
      `${workOrder.id} loaded into the work-order form.`,
      "info"
    );
  }
}


/* =========================
   EVENT DELEGATION:
   TECHNICIAN TABLE
========================= */
function handleTechnicianTableAction(
  event
) {
  const button =
    event.target.closest(
      "[data-action]"
    );

  if (!button) {
    return;
  }

  const workOrder =
    findWorkOrder(
      button.dataset.id
    );

  if (!workOrder) {
    return;
  }

  if (
    button.dataset.action ===
    "view-tech-work"
  ) {
    showWorkOrderDetails(
      workOrder
    );
  }

  if (
    button.dataset.action ===
    "update-tech-work"
  ) {
    populateTechnicianUpdateForm(
      workOrder
    );

    openWorkspaceView(
      "technician-update"
    );

    byId(
      "progressWorkOrderId"
    )?.focus();
  }
}


/* =========================
   INITIALIZATION / EVENTS
========================= */
function initializeEvents() {

  /* =========================
     MOBILE MAIN MENU
  ========================= */
  on(
    byId(
      "menuButton"
    ),
    "click",
    () => {
      const nav =
        byId(
          "mainNav"
        );

      const isOpen =
        nav.classList.toggle(
          "open"
        );

      byId(
        "menuButton"
      ).setAttribute(
        "aria-expanded",
        String(
          isOpen
        )
      );
    }
  );


  all(
    ".main-nav a"
  ).forEach(
    (link) => {
      on(
        link,
        "click",
        () => {
          byId(
            "mainNav"
          ).classList.remove(
            "open"
          );

          byId(
            "menuButton"
          ).setAttribute(
            "aria-expanded",
            "false"
          );
        }
      );
    }
  );


  /* =========================
     LOGIN BUTTONS
  ========================= */
  on(
    byId(
      "signInButton"
    ),
    "click",
    () => {
      openModal(
        "loginModal",
        "#loginName"
      );
    }
  );


  on(
    byId(
      "heroAccessButton"
    ),
    "click",
    () => {
      openModal(
        "loginModal",
        "#loginName"
      );
    }
  );


  on(
    byId(
      "heroTrackButton"
    ),
    "click",
    () => {
      openModal(
        "loginModal",
        "#loginName"
      );
    }
  );


  on(
    byId(
      "signOutButton"
    ),
    "click",
    signOut
  );


  /* =========================
     WORKSPACE SIDEBAR
  ========================= */
  on(
    byId(
      "sidebarToggle"
    ),
    "click",
    () => {
      const isOpen =
        document.body.classList.toggle(
          "sidebar-open"
        );

      byId(
        "sidebarToggle"
      ).setAttribute(
        "aria-expanded",
        String(
          isOpen
        )
      );
    }
  );


  /* =========================
     WORKSPACE NAVIGATION
  ========================= */
  on(
    byId(
      "workspaceNavigation"
    ),
    "click",
    (event) => {
      const button =
        event.target.closest(
          "[data-workspace-view]"
        );

      if (button) {
        openWorkspaceView(
          button.dataset.workspaceView
        );
      }
    }
  );


  /* =========================
     GENERAL DOCUMENT CLICKS
  ========================= */
  on(
    document,
    "click",
    (event) => {

      const workspaceLink =
        event.target.closest(
          "[data-workspace-link]"
        );

      if (
        workspaceLink
      ) {
        openWorkspaceView(
          workspaceLink.dataset.workspaceLink
        );
      }


      const requestModalButton =
        event.target.closest(
          "[data-open-request-modal]"
        );

      if (
        requestModalButton
      ) {
        if (
          state.activeRole !==
          "requester"
        ) {
          showToast(
            "Only the Requester workspace can submit a service request.",
            "warning"
          );

          return;
        }

        resetServiceRequestFormForUser();

        openModal(
          "serviceRequestModal",
          "#requestTitle"
        );
      }


      if (
        event.target.closest(
          "[data-open-notifications]"
        )
      ) {
        byId(
          "notificationPanel"
        ).hidden =
          false;
      }


      const closeControl =
        event.target.closest(
          "[data-close-modal]"
        );

      if (
        closeControl
      ) {
        closeModal(
          closeControl.dataset.closeModal
        );
      }
    }
  );


  /* =========================
     ESCAPE KEY
  ========================= */
  on(
    document,
    "keydown",
    (event) => {
      if (
        event.key ===
        "Escape"
      ) {
        if (
          !byId(
            "notificationPanel"
          ).hidden
        ) {
          byId(
            "notificationPanel"
          ).hidden =
            true;
        }

        else if (
          document.body.classList.contains(
            "sidebar-open"
          )
        ) {
          document.body.classList.remove(
            "sidebar-open"
          );

          byId(
            "sidebarToggle"
          )?.setAttribute(
            "aria-expanded",
            "false"
          );
        }

        else {
          closeTopModal();
        }
      }
    }
  );


  /* =========================
     NOTIFICATIONS
  ========================= */
  on(
    byId(
      "notificationButton"
    ),
    "click",
    () => {
      byId(
        "notificationPanel"
      ).hidden =
        !byId(
          "notificationPanel"
        ).hidden;
    }
  );


  on(
    byId(
      "closeNotificationPanel"
    ),
    "click",
    () => {
      byId(
        "notificationPanel"
      ).hidden =
        true;
    }
  );


  /* =========================
     PASSWORD SHOW / HIDE
  ========================= */
  on(
    byId(
      "togglePasswordButton"
    ),
    "click",
    () => {
      const password =
        byId(
          "loginPassword"
        );

      const showing =
        password.type ===
        "text";

      password.type =
        showing
          ? "password"
          : "text";

      byId(
        "togglePasswordButton"
      ).textContent =
        showing
          ? "Show"
          : "Hide";

      byId(
        "togglePasswordButton"
      ).setAttribute(
        "aria-label",
        showing
          ? "Show password"
          : "Hide password"
      );
    }
  );


  /* =========================
     CONFIRMATION MODAL
  ========================= */
  on(
    byId(
      "confirmActionButton"
    ),
    "click",
    () => {
      const action =
        state.confirmAction;

      state.confirmAction =
        null;

      closeModal(
        "confirmModal"
      );

      if (
        typeof action ===
        "function"
      ) {
        action();
      }
    }
  );


  /* =========================
     FORM SUBMISSIONS
  ========================= */
  on(
    byId(
      "loginForm"
    ),
    "submit",
    handleLoginSubmit
  );


  on(
    byId(
      "serviceRequestForm"
    ),
    "submit",
    handleServiceRequestSubmit
  );


  on(
    byId(
      "workOrderForm"
    ),
    "submit",
    handleWorkOrderSubmit
  );


  on(
    byId(
      "personnelAccessForm"
    ),
    "submit",
    handlePersonnelSubmit
  );


  on(
    byId(
      "progressUpdateForm"
    ),
    "submit",
    handleProgressUpdateSubmit
  );


  /* =========================
     SERVICE REQUEST FORM
  ========================= */
  on(
    byId(
      "serviceRequestForm"
    ),
    "reset",
    resetServiceRequestFormForUser
  );


  on(
    byId(
      "requestDescription"
    ),
    "input",
    (event) => {
      byId(
        "requestCharacterCount"
      ).textContent =
        String(
          event.target.value.length
        );
    }
  );


  on(
    byId(
      "requestAttachment"
    ),
    "change",
    (event) => {
      validateAttachment(
        event.target
      );
    }
  );


  /* =========================
     SEARCH / FILTER
  ========================= */
  on(
    byId(
      "requestSearchInput"
    ),
    "input",
    renderRequestTable
  );


  on(
    byId(
      "requestStatusFilter"
    ),
    "change",
    renderRequestTable
  );


  on(
    byId(
      "adminRequestSearch"
    ),
    "input",
    renderAdminReviewTable
  );


  /* =========================
     TABLE ACTION EVENTS
  ========================= */
  on(
    byId(
      "serviceRequestTableBody"
    ),
    "click",
    handleRequestTableAction
  );


  on(
    byId(
      "adminReviewTableBody"
    ),
    "click",
    handleAdminTableAction
  );


  on(
    byId(
      "approvalTableBody"
    ),
    "click",
    handleApprovalTableAction
  );


  on(
    byId(
      "workOrderTableBody"
    ),
    "click",
    handleWorkOrderTableAction
  );


  on(
    byId(
      "technicianWorkTableBody"
    ),
    "click",
    handleTechnicianTableAction
  );


  /* =========================
     CLEAR CUSTOM VALIDATION
  ========================= */
  on(
    byId(
      "workOrderRequestId"
    ),
    "input",
    (event) => {
      event.target.setCustomValidity(
        ""
      );
    }
  );


  on(
    byId(
      "workOrderId"
    ),
    "input",
    (event) => {
      event.target.setCustomValidity(
        ""
      );
    }
  );


  on(
    byId(
      "progressWorkOrderId"
    ),
    "input",
    (event) => {
      event.target.setCustomValidity(
        ""
      );
    }
  );


  on(
    byId(
      "progressRequestId"
    ),
    "input",
    (event) => {
      event.target.setCustomValidity(
        ""
      );
    }
  );


  on(
    byId(
      "completionDate"
    ),
    "input",
    (event) => {
      event.target.setCustomValidity(
        ""
      );
    }
  );


  on(
    byId(
      "progressCompletionDate"
    ),
    "input",
    (event) => {
      event.target.setCustomValidity(
        ""
      );
    }
  );


  /* =========================
     PERSONNEL ACCESS RIGHTS
  ========================= */
  all(
    'input[name="accessRights"]'
  ).forEach(
    (checkbox) => {
      on(
        checkbox,
        "change",
        () => {
          byId(
            "permissionError"
          ).textContent =
            "";
        }
      );
    }
  );


  on(
    byId(
      "personnelAccessForm"
    ),
    "reset",
    () => {
      byId(
        "permissionError"
      ).textContent =
        "";
    }
  );


  /* =========================
     TECHNICIAN FORM RESET
  ========================= */
  on(
    byId(
      "progressUpdateForm"
    ),
    "reset",
    () => {
      window.setTimeout(
        () => {
          if (
            state.activeRole ===
            "technician"
          ) {
            byId(
              "progressTechnician"
            ).value =
              state.userName;
          }
        },
        0
      );
    }
  );
}


/* =========================
   INITIALIZE APPLICATION
========================= */
function initializeApp() {
  initializeLogoFallbacks();

  setDateRestrictions();

  restoreRememberedUser();

  initializeEvents();

  renderAll();

  if (
    byId(
      "workspaceDate"
    )
  ) {
    byId(
      "workspaceDate"
    ).textContent =
      new Intl.DateTimeFormat(
        "en-US",
        {
          month:
            "long",

          day:
            "numeric",

          year:
            "numeric"
        }
      ).format(
        new Date()
      );
  }
}


/* =========================
   START APPLICATION
========================= */
document.addEventListener(
  "DOMContentLoaded",
  initializeApp
);
