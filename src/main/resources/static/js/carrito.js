(function () {
    'use strict';

    const kitsById = new Map((window.CART_KITS || []).map((kit) => [kit.id, kit]));
    const mxnFormatter = new Intl.NumberFormat('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    const emptyEl = document.getElementById('cart-empty');
    const contentEl = document.getElementById('cart-content');
    const itemsEl = document.getElementById('cart-items');
    const totalEl = document.getElementById('cart-total');
    const totalMxnEl = document.getElementById('cart-total-mxn');
    const removedNotice = document.getElementById('cart-removed-notice');
    const form = document.getElementById('checkout-form');
    const alertEl = document.getElementById('checkout-alert');
    const submitBtn = document.getElementById('checkout-submit');
    const cart = window.BajaSolarCart;
    const account = window.BajaSolarAccount;
    const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
    }

    function dropUnavailableKits() {
        const unavailable = cart.read().filter((item) => !kitsById.has(item.kitId));
        unavailable.forEach((item) => cart.remove(item.kitId));
        removedNotice.classList.toggle('d-none', unavailable.length === 0);
    }

    function render() {
        const items = cart.read();
        emptyEl.classList.toggle('d-none', items.length > 0);
        contentEl.classList.toggle('d-none', items.length === 0);

        itemsEl.innerHTML = items.map((item) => {
            const kit = kitsById.get(item.kitId);
            return '<tr data-kit-id="' + kit.id + '">' +
                '<td class="fw-semibold">' + escapeHtml(kit.name) + '</td>' +
                '<td class="text-end small" data-price-mxn="' + kit.sellPrice + '"></td>' +
                '<td><input type="number" class="form-control form-control-sm cart-qty" min="1" max="' + cart.maxUnitsPerKit +
                '" value="' + item.quantity + '" aria-label="Cantidad"></td>' +
                '<td class="text-end cart-line-total" data-price-mxn="0"></td>' +
                '<td class="text-end"><button type="button" class="btn btn-sm btn-outline-danger cart-remove" aria-label="Quitar">' +
                '<i class="bi bi-trash"></i></button></td>' +
                '</tr>';
        }).join('');
        updateTotals();
    }

    // Quantity edits update the numbers in place: rebuilding the rows from a change event that
    // fires on blur would remove the input mid-blur, which browsers reject.
    function updateTotals() {
        let total = 0;
        cart.read().forEach((item) => {
            const lineTotal = Number(kitsById.get(item.kitId).sellPrice) * item.quantity;
            total += lineTotal;
            const row = itemsEl.querySelector('tr[data-kit-id="' + item.kitId + '"]');
            if (row) {
                row.querySelector('.cart-line-total').dataset.priceMxn = lineTotal;
                const qtyInput = row.querySelector('.cart-qty');
                if (Number(qtyInput.value) !== item.quantity) {
                    qtyInput.value = item.quantity;
                }
            }
        });
        totalEl.dataset.priceMxn = total;
        totalMxnEl.textContent = '$' + mxnFormatter.format(total) + ' MXN';
        window.BajaSolarRegion.refresh();
    }

    itemsEl.addEventListener('change', (event) => {
        if (event.target.classList.contains('cart-qty')) {
            const kitId = Number(event.target.closest('tr').dataset.kitId);
            cart.setQuantity(kitId, Number(event.target.value));
            updateTotals();
        }
    });

    itemsEl.addEventListener('click', (event) => {
        const button = event.target.closest('.cart-remove');
        if (button) {
            cart.remove(Number(button.closest('tr').dataset.kitId));
            render();
        }
    });

    function showError(message) {
        alertEl.textContent = message;
        alertEl.classList.remove('d-none');
        alertEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }

    function value(id) {
        return document.getElementById(id).value.trim();
    }

    // ---------------------------------------------------------------
    // Account: logged in, log in inline, or create an account at checkout
    // ---------------------------------------------------------------
    const emailInput = document.getElementById('customer-email');
    const loginBox = document.getElementById('email-login-box');
    const registerBox = document.getElementById('email-register-box');
    const createAccountCheck = document.getElementById('create-account');
    const createAccountPassword = document.getElementById('create-account-password');
    const inlineLoginPassword = document.getElementById('inline-login-password');
    const inlineLoginBtn = document.getElementById('inline-login-btn');
    const inlineLoginAlert = document.getElementById('inline-login-alert');
    let loggedIn = false;
    let checkedEmail = null;

    function setIfEmpty(id, text) {
        const input = document.getElementById(id);
        if (text && !input.value.trim()) {
            input.value = text;
        }
    }

    function showMember(me) {
        loggedIn = true;
        document.getElementById('checkout-member').classList.remove('d-none');
        document.getElementById('checkout-member-name').textContent = me.fullName || me.email;
        document.getElementById('email-optional').classList.add('d-none');
        document.getElementById('email-hint').classList.add('d-none');
        emailInput.value = me.email;
        emailInput.readOnly = true;
        loginBox.classList.add('d-none');
        registerBox.classList.add('d-none');
        createAccountCheck.checked = false;
        setIfEmpty('customer-name', me.fullName);
        setIfEmpty('customer-phone', me.phone);
        setIfEmpty('street', me.defaultStreet);
        setIfEmpty('neighborhood', me.defaultNeighborhood);
        setIfEmpty('postal-code', me.defaultPostalCode);
        setIfEmpty('address-references', me.defaultAddressReferences);
    }

    function showGuest() {
        loggedIn = false;
        checkedEmail = null;
        document.getElementById('checkout-member').classList.add('d-none');
        document.getElementById('email-optional').classList.remove('d-none');
        document.getElementById('email-hint').classList.remove('d-none');
        emailInput.readOnly = false;
        checkEmail();
    }

    function toggleCreateAccount() {
        document.getElementById('create-account-password-box').classList.toggle('d-none', !createAccountCheck.checked);
        createAccountPassword.required = createAccountCheck.checked;
        if (createAccountCheck.checked) {
            createAccountPassword.focus();
        }
    }

    /** Asks the server whether the typed email has an account, and offers login or sign-up. */
    async function checkEmail() {
        const email = emailInput.value.trim().toLowerCase();
        if (loggedIn || email === checkedEmail) {
            return;
        }
        checkedEmail = email;
        loginBox.classList.add('d-none');
        registerBox.classList.add('d-none');
        if (!EMAIL_PATTERN.test(email)) {
            return;
        }
        try {
            const result = await account.sendJson('POST', '/api/account/email-status', { email: email });
            if (!result.ok || checkedEmail !== email || loggedIn) {
                return;
            }
            loginBox.classList.toggle('d-none', !result.body.registered);
            registerBox.classList.toggle('d-none', result.body.registered);
            if (result.body.registered) {
                createAccountCheck.checked = false;
                toggleCreateAccount();
            }
        } catch (err) {
            // offline: the guest checkout still works
        }
    }

    async function inlineLogin() {
        inlineLoginAlert.classList.add('d-none');
        if (!inlineLoginPassword.value) {
            inlineLoginPassword.focus();
            return;
        }
        inlineLoginBtn.disabled = true;
        try {
            const result = await account.sendJson('POST', '/api/account/login',
                { email: emailInput.value.trim(), password: inlineLoginPassword.value });
            if (!result.ok) {
                inlineLoginAlert.textContent = (result.body && result.body.message) || 'No se pudo iniciar sesión.';
                inlineLoginAlert.classList.remove('d-none');
                return;
            }
            inlineLoginPassword.value = '';
            account.setSession(result.body);
            showMember(result.body.account);
        } catch (err) {
            inlineLoginAlert.textContent = 'Ocurrió un error de conexión. Intenta nuevamente.';
            inlineLoginAlert.classList.remove('d-none');
        } finally {
            inlineLoginBtn.disabled = false;
        }
    }

    emailInput.addEventListener('blur', checkEmail);
    emailInput.addEventListener('change', checkEmail);
    createAccountCheck.addEventListener('change', toggleCreateAccount);
    inlineLoginBtn.addEventListener('click', inlineLogin);
    inlineLoginPassword.addEventListener('keydown', (event) => {
        // Enter here means "log in", not "place the order".
        if (event.key === 'Enter') {
            event.preventDefault();
            inlineLogin();
        }
    });
    document.getElementById('checkout-logout').addEventListener('click', () => {
        account.clear();
        showGuest();
    });

    /** Creates the account right before the order, so the order is placed already linked to it. */
    async function registerAtCheckout() {
        const result = await account.sendJson('POST', '/api/account/register', {
            fullName: value('customer-name'),
            phone: value('customer-phone'),
            email: value('customer-email'),
            password: createAccountPassword.value
        });
        if (!result.ok) {
            return (result.body && result.body.message) || 'No se pudo crear tu cuenta.';
        }
        account.setSession(result.body);
        showMember(result.body.account);
        return null;
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        alertEl.classList.add('d-none');
        if (!form.checkValidity()) {
            form.classList.add('was-validated');
            showError(createAccountPassword.required && !createAccountPassword.validity.valid
                ? 'La contraseña de tu cuenta debe tener al menos 8 caracteres.'
                : 'Revisa los campos marcados.');
            return;
        }
        if (value('customer-phone').replace(/\D/g, '').replace(/^52(?=\d{10}$)/, '').length !== 10) {
            showError('El teléfono debe tener 10 dígitos.');
            return;
        }
        const creatingAccount = !loggedIn && createAccountCheck.checked && !registerBox.classList.contains('d-none');
        if (creatingAccount && !value('customer-email')) {
            showError('Escribe tu correo para crear tu cuenta.');
            return;
        }

        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Enviando pedido...';
        try {
            if (creatingAccount) {
                const registerError = await registerAtCheckout();
                if (registerError) {
                    showError(registerError);
                    return;
                }
            }
            const response = await fetch('/api/orders', {
                method: 'POST',
                headers: Object.assign({ 'Content-Type': 'application/json' }, account.authHeaders()),
                body: JSON.stringify({
                    customerName: value('customer-name'),
                    customerPhone: value('customer-phone'),
                    customerEmail: value('customer-email') || null,
                    street: value('street'),
                    neighborhood: value('neighborhood'),
                    postalCode: value('postal-code'),
                    addressReferences: value('address-references') || null,
                    notes: value('notes') || null,
                    items: cart.read()
                })
            });
            const body = await response.json().catch(() => null);
            if (!response.ok) {
                showError((body && body.message) || 'No se pudo enviar tu pedido. Intenta nuevamente.');
                return;
            }
            cart.clear();
            window.location.href = '/pedido/' + encodeURIComponent(body.folio);
        } catch (err) {
            showError('Ocurrió un error de conexión. Intenta nuevamente.');
        } finally {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Hacer pedido';
        }
    });

    document.addEventListener('DOMContentLoaded', async () => {
        dropUnavailableKits();
        render();
        const me = await account.me();
        if (me) {
            showMember(me);
        }
    });
})();
