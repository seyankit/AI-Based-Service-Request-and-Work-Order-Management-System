// ==========================================
// TASK 3 - INITIAL SYSTEM INTERFACE
// ==========================================

const requestButton =
    document.getElementById("requestButton");

const trackButton =
    document.getElementById("trackButton");

const getStartedButton =
    document.getElementById("getStartedButton");


// Submit Request Button

requestButton.addEventListener("click", function () {

    alert(
        "SERVICE REQUEST\n\n" +
        "This is the initial interface prototype " +
        "of the AI-Based Service Request and Work " +
        "Order Management System.\n\n" +
        "The actual request form will be implemented " +
        "in a future development phase."
    );

});


// Track Request Button

trackButton.addEventListener("click", function () {

    alert(
        "TRACK REQUEST\n\n" +
        "Request tracking is a proposed function " +
        "of the system.\n\n" +
        "The actual tracking module will be implemented " +
        "in a future development phase."
    );

});


// Get Started Button

getStartedButton.addEventListener("click", function () {

    document
        .getElementById("home")
        .scrollIntoView({
            behavior: "smooth"
        });

});