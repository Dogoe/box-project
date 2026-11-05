(function () {
    'use strict';

    // Shop customer session, kept apart from the admin's ('adminToken').
    const TOKEN_KEY = 'bss-customer-token';
    const NAME_KEY = 'bss-customer-name';

    function read(key) {
        try {
            return localStorage.getItem(key);
        } catch (err) {
            return null;
        }
    }

    function getToken() {
        return read(TOKEN_KEY);
    }

    function rememberName(account) {
        try {
            localStorage.setItem(NAME_KEY, account.fullName || account.email);
        } catch (err) {
            // storage blocked: the menu just shows "Entrar"
        }
        renderMenuLink();
    }

    function setSession(auth) {
        try {
            localStorage.setItem(TOKEN_KEY, auth.token);
        } catch (err) {
            // storage blocked: the session lasts for this page only
        }
        rememberName(auth.account);
    }

    function clear() {
        try {
            localStorage.removeItem(TOKEN_KEY);
            localStorage.removeItem(NAME_KEY);
        } catch (err) {
            // nothing stored
        }
        renderMenuLink();
    }

    function authHeaders() {
        const token = getToken();
        return token ? { Authorization: 'Bearer ' + token } : {};
    }

    /** The logged-in account, or null (an expired/invalid session is cleared). */
    async function me() {
        if (!getToken()) {
            return null;
        }
        try {
            const response = await fetch('/api/account/me', { headers: authHeaders() });
            if (!response.ok) {
                clear();
                return null;
            }
            const account = await response.json();
            rememberName(account);
            return account;
        } catch (err) {
            return null;
        }
    }

    /** POSTs/PUTs JSON; resolves to { ok, status, body } with the parsed body (or null). */
    async function sendJson(method, url, body, withAuth) {
        const headers = Object.assign({ 'Content-Type': 'application/json' }, withAuth ? authHeaders() : {});
        const response = await fetch(url, { method: method, headers: headers, body: body ? JSON.stringify(body) : undefined });
        const text = await response.text();
        let parsed = null;
        try {
            parsed = text ? JSON.parse(text) : null;
        } catch (err) {
            parsed = null;
        }
        return { ok: response.ok, status: response.status, body: parsed };
    }

    function renderMenuLink() {
        const name = getToken() ? read(NAME_KEY) : null;
        document.querySelectorAll('[data-account-label]').forEach((el) => {
            el.textContent = name ? name.split(' ')[0] : 'Entrar';
        });
    }

    window.BajaSolarAccount = { getToken, setSession, clear, authHeaders, me, sendJson };
    document.addEventListener('DOMContentLoaded', renderMenuLink);
})();
