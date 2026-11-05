(function () {
    'use strict';

    const account = window.BajaSolarAccount;
    const mxnFormatter = new Intl.NumberFormat('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const dateFormatter = new Intl.DateTimeFormat('es-MX', { dateStyle: 'medium' });
    const STATUS_BADGES = { PENDING: 'bg-warning text-dark', PAID: 'bg-primary', DELIVERED: 'bg-success', CANCELLED: 'bg-secondary' };

    function $(id) {
        return document.getElementById(id);
    }

    function value(id) {
        return $(id).value.trim();
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
    }

    function showAlert(el, message, kind) {
        el.className = 'alert alert-' + (kind || 'danger');
        el.textContent = message;
    }

    /** Only same-site paths, so ?next= can't send people elsewhere. */
    function nextUrl() {
        const next = new URLSearchParams(window.location.search).get('next');
        return next && next.startsWith('/') && !next.startsWith('//') ? next : null;
    }

    function afterLogin(auth) {
        account.setSession(auth);
        const next = nextUrl();
        if (next) {
            window.location.href = next;
        } else {
            showMember(auth.account);
        }
    }

    async function submitWith(button, label, work) {
        button.disabled = true;
        button.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>' + label;
        try {
            await work();
        } catch (err) {
            return 'Ocurrió un error de conexión. Intenta nuevamente.';
        } finally {
            button.disabled = false;
            button.textContent = button.dataset.idle;
        }
        return null;
    }

    function wireForm(formId, alertId, buttonId, busyLabel, work) {
        const form = $(formId);
        const alertEl = $(alertId);
        const button = $(buttonId);
        button.dataset.idle = button.textContent;
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            alertEl.classList.add('d-none');
            if (!form.checkValidity()) {
                form.classList.add('was-validated');
                showAlert(alertEl, 'Revisa los campos marcados.');
                return;
            }
            const connectionError = await submitWith(button, busyLabel, () => work(alertEl));
            if (connectionError) {
                showAlert(alertEl, connectionError);
            }
        });
    }

    wireForm('login-form', 'login-alert', 'login-submit', 'Entrando...', async (alertEl) => {
        const result = await account.sendJson('POST', '/api/account/login',
            { email: value('login-email'), password: $('login-password').value });
        if (!result.ok) {
            showAlert(alertEl, (result.body && result.body.message) || 'No se pudo iniciar sesión.');
            return;
        }
        afterLogin(result.body);
    });

    wireForm('register-form', 'register-alert', 'register-submit', 'Creando cuenta...', async (alertEl) => {
        const result = await account.sendJson('POST', '/api/account/register', {
            fullName: value('register-name'),
            phone: value('register-phone'),
            email: value('register-email'),
            password: $('register-password').value
        });
        if (!result.ok) {
            showAlert(alertEl, (result.body && result.body.message) || 'No se pudo crear la cuenta.');
            return;
        }
        afterLogin(result.body);
    });

    wireForm('profile-form', 'profile-alert', 'profile-submit', 'Guardando...', async (alertEl) => {
        const result = await account.sendJson('PUT', '/api/account/me', {
            fullName: value('profile-name'),
            phone: value('profile-phone'),
            defaultStreet: value('profile-street') || null,
            defaultNeighborhood: value('profile-neighborhood') || null,
            defaultPostalCode: value('profile-postal-code'),
            defaultAddressReferences: value('profile-references') || null
        }, true);
        if (result.status === 401 || result.status === 403) {
            account.clear();
            showGuest();
            return;
        }
        if (!result.ok) {
            showAlert(alertEl, (result.body && result.body.message) || 'No se pudieron guardar tus datos.');
            return;
        }
        fillProfile(result.body);
        showAlert(alertEl, 'Datos guardados.', 'success');
    });

    $('logout-btn').addEventListener('click', () => {
        account.clear();
        showGuest();
    });

    $('resend-verification').addEventListener('click', async () => {
        const button = $('resend-verification');
        const resultEl = $('resend-result');
        button.disabled = true;
        try {
            const result = await account.sendJson('POST', '/api/account/verification-email', null, true);
            resultEl.textContent = result.ok
                ? 'Listo, te enviamos un nuevo correo. Revisa también tu carpeta de spam.'
                : 'No se pudo enviar el correo. Intenta más tarde.';
        } catch (err) {
            resultEl.textContent = 'Ocurrió un error de conexión. Intenta nuevamente.';
        }
        resultEl.classList.remove('d-none');
        button.disabled = false;
    });

    function show(sectionId) {
        ['account-loading', 'account-guest', 'account-member'].forEach((id) => $(id).classList.toggle('d-none', id !== sectionId));
    }

    function showGuest() {
        show('account-guest');
    }

    function fillProfile(me) {
        $('member-name').textContent = (me.fullName || me.email).split(' ')[0];
        $('profile-email').value = me.email;
        $('profile-name').value = me.fullName || '';
        $('profile-phone').value = me.phone || '';
        $('profile-street').value = me.defaultStreet || '';
        $('profile-neighborhood').value = me.defaultNeighborhood || '';
        $('profile-postal-code').value = me.defaultPostalCode || '';
        $('profile-references').value = me.defaultAddressReferences || '';
        $('verify-email').textContent = me.email;
        $('verify-banner').classList.toggle('d-none', me.emailVerified);
    }

    function renderOrders(orders) {
        $('orders-empty').classList.toggle('d-none', orders.length > 0);
        $('orders-table').classList.toggle('d-none', orders.length === 0);
        $('orders-body').innerHTML = orders.map((order) => {
            const kits = order.items.map((item) => escapeHtml(item.quantity + ' × ' + item.kitName)).join('<br>');
            return '<tr>' +
                '<td><a href="/pedido/' + encodeURIComponent(order.folio) + '" class="fw-semibold">' + escapeHtml(order.folio) + '</a></td>' +
                '<td class="small text-nowrap">' + escapeHtml(dateFormatter.format(new Date(order.createdAt))) + '</td>' +
                '<td class="small">' + kits + '</td>' +
                '<td class="text-end text-nowrap">$' + mxnFormatter.format(order.totalMxn) + '</td>' +
                '<td><span class="badge ' + (STATUS_BADGES[order.status] || 'bg-secondary') + '">' + escapeHtml(order.statusLabel) + '</span></td>' +
                '</tr>';
        }).join('');
    }

    async function showMember(me) {
        fillProfile(me);
        show('account-member');
        try {
            const response = await fetch('/api/account/orders', { headers: account.authHeaders() });
            renderOrders(response.ok ? await response.json() : []);
        } catch (err) {
            renderOrders([]);
        }
    }

    document.addEventListener('DOMContentLoaded', async () => {
        const me = await account.me();
        if (me) {
            showMember(me);
        } else {
            showGuest();
        }
    });
})();
