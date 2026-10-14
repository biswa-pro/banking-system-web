const $ = (id) => document.getElementById(id);
const money = (n) =>
    new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(
        Number(n),
    );
function msg(s, err = false) {
    let n = $("notice");
    n.textContent = s;
    n.className = "show" + (err ? " error" : "");
    n.style.display = "block";
}
function esc(s) {
    return String(s).replace(
        /[&<>"']/g,
        (c) =>
            ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
            c
            ],
    );
}
async function api(url, opt = {}) {
    let r = await fetch(url, {
        headers: { "Content-Type": "application/json" },
        ...opt,
    }),
        t = await r.text(),
        d;
    try {
        d = t ? JSON.parse(t) : null;
    } catch {}
    if (!r.ok)
        throw Error(d?.message || d?.error || `Request failed (${r.status})`);
    return d;
}
function show(a) {
    $("accountId").value = a.id;
    $("fromId").value = a.id;
    $("account").innerHTML =
        `<small>ACCOUNT #${a.id} · ${a.type}</small><div class="balance">${money(a.balance)}</div><b>${esc(a.fullName)}</b><div>${esc(a.email)}</div>`;
}
async function hist(id) {
    let list = await api(`/api/accounts/${id}/transactions`);
    $("count").textContent = `(${list.length})`;
    $("rows").innerHTML = list.length
        ? list
            .map(
                (t) =>
                    `<tr><td>${new Date(t.createdAt).toLocaleString()}</td><td>${esc(t.type.replaceAll("_", " "))}</td><td>${money(t.amount)}</td><td>${money(t.balanceAfter)}</td><td>${esc(t.description)}</td></tr>`,
            )
            .join("")
        : '<tr><td colspan="5">No transactions yet.</td></tr>';
}
async function load(id) {
    try {
        let a = await api(`/api/accounts/${id}`);
        show(a);
        await hist(id);
        msg("Account loaded.");
    } catch (e) {
        msg(e.message, true);
    }
}
$("create").onsubmit = async (e) => {
    e.preventDefault();
    try {
        let a = await api("/api/accounts", {
            method: "POST",
            body: JSON.stringify({
                fullName: $("name").value,
                email: $("email").value,
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
$("lookup").onsubmit = (e) => {
    e.preventDefault();
    load(Number($("accountId").value));
};
async function moneyAction(action) {
    let id = Number($("accountId").value);
    if (!id) return msg("Find or create an account first.", true);
    let raw = prompt(`Enter amount to ${action}:`);
    if (raw === null) return;
    try {
        let a = await api(`/api/accounts/${id}/${action}`, {
            method: "POST",
            body: JSON.stringify({ amount: Number(raw) }),
        });
        show(a);
        await hist(id);
        msg("Transaction completed.");
    } catch (e) {
        msg(e.message, true);
    }
}
$("deposit").onclick = () => moneyAction("deposit");
$("withdraw").onclick = () => moneyAction("withdraw");
$("refresh").onclick = () => {
    let id = Number($("accountId").value);
    if (id)
        hist(id)
            .then(() => msg("History refreshed."))
            .catch((e) => msg(e.message, true));
    else msg("Find an account first.", true);
};
$("transfer").onsubmit = async (e) => {
    e.preventDefault();
    try {
        await api("/api/transfers", {
            method: "POST",
            body: JSON.stringify({
                fromAccountId: Number($("fromId").value),
                toAccountId: Number($("toId").value),
                amount: Number($("transferAmount").value),
            }),
        });
        await load(Number($("fromId").value));
        msg("Transfer completed.");
    } catch (e) {
        msg(e.message, true);
    }
};
