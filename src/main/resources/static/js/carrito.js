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

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        alertEl.classList.add('d-none');
        if (!form.checkValidity()) {
            form.classList.add('was-validated');
            showError('Revisa los campos marcados.');
            return;
        }
        if (value('customer-phone').replace(/\D/g, '').replace(/^52(?=\d{10}$)/, '').length !== 10) {
            showError('El teléfono debe tener 10 dígitos.');
            return;
        }

        submitBtn.disabled = true;
        submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Enviando pedido...';
        try {
            const response = await fetch('/api/orders', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
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

    document.addEventListener('DOMContentLoaded', () => {
        dropUnavailableKits();
        render();
    });
})();
