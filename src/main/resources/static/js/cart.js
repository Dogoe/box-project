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

    function renderBadge() {
        const total = count();
        document.querySelectorAll('[data-cart-count]').forEach((el) => {
            el.textContent = total;
            el.classList.toggle('d-none', total === 0);
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
                add(Number(button.dataset.addToCart), 1);
                flashAdded(button);
            });
        });
    }

    window.BajaSolarCart = { read, add, setQuantity, remove, clear, count, flashAdded, maxUnitsPerKit: MAX_UNITS_PER_KIT };

    document.addEventListener('DOMContentLoaded', () => {
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
