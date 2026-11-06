(function () {
    'use strict';

    // Kit ids and quantities only; prices are always recalculated by the server at checkout.
    const STORAGE_KEY = 'bss-cart';
    const MAX_UNITS_PER_KIT = 10;

    function read() {
        try {
            const items = JSON.parse(localStorage.getItem(STORAGE_KEY));
            return Array.isArray(items)
                ? items.filter((i) => i && Number.isInteger(i.kitId) && Number.isInteger(i.quantity) && i.quantity > 0)
                : [];
        } catch (err) {
            return [];
        }
    }

    function write(items) {
        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
        } catch (err) {
            // Storage blocked (private mode): the cart just won't persist.
        }
        renderBadge();
    }

    function add(kitId, quantity) {
        const items = read();
        const existing = items.find((i) => i.kitId === kitId);
        if (existing) {
            existing.quantity = Math.min(MAX_UNITS_PER_KIT, existing.quantity + (quantity || 1));
        } else {
            items.push({ kitId: kitId, quantity: Math.min(MAX_UNITS_PER_KIT, quantity || 1) });
        }
        write(items);
    }

    function setQuantity(kitId, quantity) {
        const q = Math.max(1, Math.min(MAX_UNITS_PER_KIT, Math.floor(quantity) || 1));
        write(read().map((i) => (i.kitId === kitId ? { kitId: kitId, quantity: q } : i)));
    }

    function remove(kitId) {
        write(read().filter((i) => i.kitId !== kitId));
    }

    function clear() {
        write([]);
    }

    function count() {
        return read().reduce((sum, i) => sum + i.quantity, 0);
    }

    function renderBadge(bump) {
        const total = count();
        document.querySelectorAll('[data-cart-count]').forEach((el) => {
            el.textContent = total > 99 ? '99+' : total;
            el.classList.toggle('d-none', total === 0);
            if (bump && total > 0) {
                el.classList.remove('cart-bump');
                void el.offsetWidth; // restart the animation
                el.classList.add('cart-bump');
            }
        });
    }

    // ---- Page lock while adding ----
    // Adding is quick, but on a slow phone the button can look unresponsive and get tapped again;
    // the lock shows that something is happening and swallows extra taps until it's done.
    const MIN_LOCK_MS = 600;
    let busy = false;
    let overlay = null;

    function lockScreen() {
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.className = 'cart-busy-overlay';
            overlay.setAttribute('role', 'status');
            overlay.setAttribute('aria-live', 'polite');
            overlay.innerHTML = '<div class="cart-busy-box"><span class="spinner-border spinner-border-sm" aria-hidden="true"></span>' +
                '<span>Agregando al carrito...</span></div>';
            document.body.appendChild(overlay);
        }
        overlay.hidden = false;
        document.body.setAttribute('aria-busy', 'true');
        document.querySelectorAll('[data-add-to-cart]').forEach((b) => { b.disabled = true; });
    }

    function unlockScreen() {
        if (overlay) {
            overlay.hidden = true;
        }
        document.body.removeAttribute('aria-busy');
        document.querySelectorAll('[data-add-to-cart]').forEach((b) => { b.disabled = false; });
    }

    /**
     * Adds a kit with the page locked, then confirms on the button. Taps while it's running are
     * ignored. Resolves to false when it was ignored.
     */
    function addWithLock(kitId, quantity, button) {
        if (busy) {
            return Promise.resolve(false);
        }
        busy = true;
        lockScreen();
        const started = Date.now();
        return new Promise((resolve) => {
            try {
                add(kitId, quantity);
            } finally {
                setTimeout(() => {
                    busy = false;
                    unlockScreen();
                    renderBadge(true);
                    if (button) {
                        flashAdded(button);
                    }
                    resolve(true);
                }, Math.max(0, MIN_LOCK_MS - (Date.now() - started)));
            }
        });
    }

    function flashAdded(button) {
        const original = button.innerHTML;
        button.disabled = true;
        button.innerHTML = button.dataset.iconOnly !== undefined
            ? '<i class="bi bi-check-lg"></i>'
            : '<i class="bi bi-check-lg"></i> Agregado';
        setTimeout(() => {
            button.innerHTML = original;
            button.disabled = false;
        }, 1500);
    }

    function wireAddButtons(root) {
        (root || document).querySelectorAll('[data-add-to-cart]').forEach((button) => {
            button.addEventListener('click', () => {
                addWithLock(Number(button.dataset.addToCart), 1, button);
            });
        });
    }

    /** The floating cart is pointless on the cart itself and on the order confirmation. */
    function setUpFloatingCart() {
        const fab = document.querySelector('.cart-fab');
        if (!fab) {
            return;
        }
        const path = window.location.pathname;
        if (path.startsWith('/carrito') || path.startsWith('/pedido')) {
            fab.remove();
        } else {
            document.body.classList.add('has-cart-fab');
        }
    }

    window.BajaSolarCart = { read, add, addWithLock, setQuantity, remove, clear, count, flashAdded, maxUnitsPerKit: MAX_UNITS_PER_KIT };

    document.addEventListener('DOMContentLoaded', () => {
        setUpFloatingCart();
        renderBadge();
        wireAddButtons();
    });
    // Keep the badge in sync when the cart changes in another tab.
    window.addEventListener('storage', (event) => {
        if (event.key === STORAGE_KEY) {
            renderBadge();
        }
    });
})();
