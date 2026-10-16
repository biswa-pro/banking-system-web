
function showForm(type) {
    const loginForm = document.getElementById("loginForm");
    const signupForm = document.getElementById("signupForm");

    loginForm.hidden = type !== "login";
    signupForm.hidden = type !== "signup";

    document.getElementById("loginTab")
        .classList.toggle("active", type === "login");

    document.getElementById("signupTab")
        .classList.toggle("active", type === "signup");

    clearMessages();
}

function clearMessages() {
    ["loginMessage", "signupMessage"].forEach(id => {
        const element = document.getElementById(id);
        element.textContent = "";
        element.className = "message";
    });
}

function showMessage(id, message, type) {
    const element = document.getElementById(id);
    element.textContent = message;
    element.className = "message " + type;
}

function getCsrfToken() {
    const cookie = document.cookie
        .split("; ")
        .find(row => row.startsWith("XSRF-TOKEN="));

    return cookie
        ? decodeURIComponent(
            cookie.substring("XSRF-TOKEN=".length)
          )
        : null;
}

async function postJson(url, data) {
    // Initialize the CSRF cookie before sending a POST request.
    if (!getCsrfToken()) {
        const csrfResponse = await fetch("/api/auth/csrf", {
            method: "GET",
            credentials: "same-origin"
        });

        if (!csrfResponse.ok) {
            throw new Error(
                "Unable to initialize security token."
            );
        }
    }

    const csrfToken = getCsrfToken();

    if (!csrfToken) {
        throw new Error(
            "Security token is missing. Refresh the page and try again."
        );
    }

    return fetch(url, {
        method: "POST",
        credentials: "same-origin",
        headers: {
            "Content-Type": "application/json",
            "X-XSRF-TOKEN": csrfToken
        },
        body: JSON.stringify(data)
    });
}

// ==================== LOGIN ====================

document.getElementById("loginForm")
    .addEventListener("submit", async function (event) {
        event.preventDefault();

        const username =
            document.getElementById("loginUsername").value.trim();

        const password =
            document.getElementById("loginPassword").value;

        const button = this.querySelector(".submit-btn");
        button.disabled = true;

        try {
            const response = await postJson("/api/auth/login", {
                username,
                password
            });

            const result = await response.json();

            if (!response.ok) {
                throw new Error(
                    result.message || "Login failed."
                );
            }

            localStorage.setItem("username", result.username);

            showMessage(
                "loginMessage",
                "Login successful. Opening dashboard...",
                "success"
            );

            window.location.href = "/index.html";

        } catch (error) {
            showMessage(
                "loginMessage",
                error.message || "Unable to connect to the server.",
                "error"
            );
        } finally {
            button.disabled = false;
        }
    });

// ==================== SIGN UP ====================

document.getElementById("signupForm")
    .addEventListener("submit", async function (event) {
        event.preventDefault();

        const username =
            document.getElementById("signupUsername").value.trim();

        const email =
            document.getElementById("signupEmail").value.trim();

        const password =
            document.getElementById("signupPassword").value;

        const confirmPassword =
            document.getElementById("confirmPassword").value;

        if (password !== confirmPassword) {
            showMessage(
                "signupMessage",
                "Passwords do not match.",
                "error"
            );
            return;
        }

        const button = this.querySelector(".submit-btn");
        button.disabled = true;

        try {
            const response = await postJson("/api/auth/register", {
                username,
                email,
                password
            });

            const result = await response.json();

            if (!response.ok) {
                throw new Error(
                    result.message || "Registration failed."
                );
            }

            showMessage(
                "signupMessage",
                "Account created successfully! Please log in.",
                "success"
            );

            this.reset();

        } catch (error) {
            showMessage(
                "signupMessage",
                error.message || "Unable to register.",
                "error"
            );
        } finally {
            button.disabled = false;
        }
    });
