

const $ = (id) => document.getElementById(id);

// Display the logged-in username
const username = localStorage.getItem("username");
const welcomeElement = $("welcomeMessage");

if (welcomeElement && username) {
    welcomeElement.textContent = `Welcome, ${username}!`;
}

const money = (n) =>
    new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR",
    }).format(Number(n));

function msg(s, err = false) {
    const n = $("notice");
    n.textContent = s;
    n.className = "show" + (err ? " error" : "");
    n.style.display = "block";
}

function esc(s) {
    return String(s).replace(
        /[&<>"']/g,
        (c) =>
            ({
                "&": "&amp;",
                "<": "&lt;",
                ">": "&gt;",
                '"': "&quot;",
                "'": "&#39;",
            })[c],
    );
}

// Read the CSRF token from the cookie
function getCsrfToken() {
    const cookie = document.cookie
        .split("; ")
        .find((row) => row.startsWith("XSRF-TOKEN="));

    return cookie
        ? decodeURIComponent(
              cookie.substring("XSRF-TOKEN=".length),
          )
        : null;
}

// Obtain a CSRF token from the backend if the cookie is missing
async function ensureCsrfToken() {
    let token = getCsrfToken();

    if (!token) {
        await fetch("/api/auth/csrf", {
            method: "GET",
            credentials: "same-origin",
        });

        token = getCsrfToken();
    }

    if (!token) {
        throw new Error(
            "CSRF token is missing. Refresh the page and log in again.",
        );
    }

    return token;
}

// Common API function
async function api(url, opt = {}) {
    const method = (opt.method || "GET").toUpperCase();

    const headers = {
        ...(opt.headers || {}),
    };

    if (opt.body !== undefined && opt.body !== null) {
        headers["Content-Type"] = "application/json";
    }

    // Send CSRF token with every state-changing request
    if (!["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)) {
        const csrfToken = await ensureCsrfToken();
        headers["X-XSRF-TOKEN"] = csrfToken;
    }

    const r = await fetch(url, {
        ...opt,
        method,
        headers,
        credentials: "same-origin",
    });

    const t = await r.text();

    let d = null;

    try {
        d = t ? JSON.parse(t) : null;
    } catch {
        d = null;
    }

    if (!r.ok) {
        if (r.status === 401) {
            throw new Error("Please log in again.");
        }

        if (r.status === 403) {
            throw new Error(
                "You don't have permission to access this account !!",
            );
        }

        throw new Error(
            d?.message ||
            d?.error ||
            `Request failed (${r.status})`,
        );
    }

    return d;
}

// Display account details
function show(a) {
    $("accountId").value = a.id;
    $("fromId").value = a.id;

    $("account").innerHTML =
        `<small>ACCOUNT #${a.id} · ${esc(a.accountType ?? a.type ?? "")}</small>` +
        `<div class="balance">${money(a.balance)}</div>` +
        `<b>${esc(a.fullName)}</b>` +
        `<div>${esc(a.email)}</div>`;
}

// Load transaction history
async function hist(id) {
    const list = await api(
        `/api/accounts/${encodeURIComponent(id)}/transactions`,
    );

    $("count").textContent = `(${list.length})`;

    $("rows").innerHTML = list.length
        ? list
              .map(
                  (t) =>
                      `<tr>
                          <td>${esc(new Date(t.createdAt).toLocaleString())}</td>
                          <td>${esc((t.type || "").replaceAll("_", " "))}</td>
                          <td>${money(t.amount)}</td>
                          <td>${money(t.balanceAfter)}</td>
                          <td>${esc(t.description || "")}</td>
                      </tr>`,
              )
              .join("")
        : '<tr><td colspan="5">No transactions yet.</td></tr>';
}

// Find and display an account
async function load(id) {
    try {
        const a = await api(`/api/accounts/${encodeURIComponent(id)}`);
        show(a);
        await hist(id);
        msg("Account loaded.");
    } catch (e) {
        msg(e.message, true);
    }
}

// Create account
$("create").onsubmit = async (e) => {
    e.preventDefault();

    try {
        const a = await api("/api/accounts", {
            method: "POST",
            body: JSON.stringify({
                fullName: $("name").value.trim(),
                email: $("email").value.trim(),
                accountType: $("type").value,
                initialDeposit: Number($("initial").value),
            }),
        });

        show(a);
        await hist(a.id);
        msg(`Account created. Account ID: ${a.id}`);
    } catch (e) {
        msg(e.message, true);
    }
};

// Find account by ID
$("lookup").onsubmit = (e) => {
    e.preventDefault();

    const id = Number($("accountId").value);

    if (!Number.isInteger(id) || id <= 0) {
        msg("Enter a valid account ID.", true);
        return;
    }

    load(id);
};

// Deposit or withdraw money
async function moneyAction(action) {
    const id = Number($("accountId").value);

    if (!Number.isInteger(id) || id <= 0) {
        return msg("Find or create an account first.", true);
    }

    const raw = prompt(`Enter amount to ${action}:`);

    if (raw === null) return;

    const amount = Number(raw);

    if (!Number.isFinite(amount) || amount <= 0) {
        return msg("Enter a valid amount greater than zero.", true);
    }

    try {
        const a = await api(
            `/api/accounts/${encodeURIComponent(id)}/${action}`,
            {
                method: "POST",
                body: JSON.stringify({ amount }),
            },
        );

        show(a);
        await hist(id);
        msg("Transaction completed.");
    } catch (e) {
        msg(e.message, true);
    }
}

$("deposit").onclick = () => moneyAction("deposit");
$("withdraw").onclick = () => moneyAction("withdraw");

// Refresh transaction history
$("refresh").onclick = () => {
    const id = Number($("accountId").value);

    if (!Number.isInteger(id) || id <= 0) {
        msg("Find an account first.", true);
        return;
    }

    hist(id)
        .then(() => msg("History refreshed."))
        .catch((e) => msg(e.message, true));
};

 //Transfer money
$("transfer").onsubmit = async (e) => {
    e.preventDefault();

    const fromId = Number($("fromId").value);
    const toId = Number($("toId").value);
    const amount = Number($("transferAmount").value);

    if (
        !Number.isInteger(fromId) ||
        fromId <= 0 ||
        !Number.isInteger(toId) ||
        toId <= 0
    ) {
        msg("Enter valid source and destination account IDs.", true);
        return;
    }

    if (fromId === toId) {
        msg("Source and destination accounts must be different.", true);
        return;
    }

    if (!Number.isFinite(amount) || amount <= 0){
        msg("Enter a valid transfer amount.", true);
        return;
    }

    try {
        await api("/api/transfers", {
            method: "POST",
            body: JSON.stringify({
                fromAccountId: fromId,
                toAccountId: toId,
                amount,
            }),
        });

        await load(fromId);
        msg("Transfer completed.");
    } catch (e) {
        msg(e.message, true);
    }
};
// Logout
const logoutBtn = $("logoutBtn");

if (logoutBtn) {
    logoutBtn.addEventListener("click", async () => {
        try {
            logoutBtn.disabled = true;
            logoutBtn.textContent = "Logging out...";

            await api("/api/auth/logout", {
                method: "POST"
            });

            localStorage.removeItem("username");

            window.location.replace("/login.html");

        } catch (error) {
            console.error("Logout failed:", error);
            msg(error.message || "Unable to log out.", true);

            logoutBtn.disabled = false;
            logoutBtn.textContent = "Logout";
        }
    });
}
