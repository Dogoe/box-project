(function () {
    'use strict';

    // Dark/light switch. The page already got its theme from the inline script in <head>
    // (so it never flashes); this only handles the button and remembers the choice.
    const KEY = 'bss-theme';
    const root = document.documentElement;

    function current() {
        return root.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    }

    function apply(theme) {
        root.setAttribute('data-theme', theme);
        root.setAttribute('data-bs-theme', theme);
        try {
            localStorage.setItem(KEY, theme);
        } catch (err) {
            // storage blocked: the choice lasts for this page only
        }
        renderButtons();
    }

    function renderButtons() {
        const dark = current() === 'dark';
        document.querySelectorAll('[data-theme-toggle]').forEach((button) => {
            const icon = button.querySelector('i');
            if (icon) {
                // Shows what you'll switch to.
                icon.className = dark ? 'bi bi-sun' : 'bi bi-moon-stars';
            }
            const label = dark ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro';
            button.setAttribute('aria-label', label);
            button.setAttribute('title', label);
        });
    }

    document.addEventListener('click', (event) => {
        if (event.target.closest('[data-theme-toggle]')) {
            apply(current() === 'dark' ? 'light' : 'dark');
        }
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', renderButtons);
    } else {
        renderButtons();
    }
})();
