(function () {
    'use strict';

    const COOKIE_NAME = 'lang';
    const COOKIE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;
    let usdToMxnRate = null;

    const mxnNumberFormatter = new Intl.NumberFormat('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const usdNumberFormatter = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    function getLang() {
        const match = document.cookie.match(/(?:^|;\s*)lang=([^;]+)/);
        return match && match[1] === 'en' ? 'en' : 'es';
    }

    function setLang(lang) {
        document.cookie = COOKIE_NAME + '=' + lang + '; path=/; max-age=' + COOKIE_MAX_AGE_SECONDS;
    }

    function formatPrice(mxnValue) {
        const value = Number(mxnValue);
        if (Number.isNaN(value)) {
            return '-';
        }
        if (getLang() === 'en' && usdToMxnRate) {
            return '$' + usdNumberFormatter.format(value / usdToMxnRate) + ' USD';
        }
        return '$' + mxnNumberFormatter.format(value) + ' MXN';
    }

    function renderAllPrices() {
        document.querySelectorAll('[data-price-mxn]').forEach((el) => {
            el.textContent = formatPrice(el.dataset.priceMxn);
        });
    }

    function updateToggleButtons() {
        const lang = getLang();
        document.querySelectorAll('.region-toggle-btn').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.lang === lang);
        });
    }

    function wireToggle() {
        document.querySelectorAll('.region-toggle-btn').forEach((btn) => {
            btn.addEventListener('click', () => {
                if (btn.dataset.lang === getLang()) {
                    return;
                }
                setLang(btn.dataset.lang);
                // A full reload is needed (not just re-rendering prices): the
                // appliance catalog badges on /kits and the wizard's preset
                // dropdown are language-specific content the server renders.
                location.reload();
            });
        });
    }

    async function init() {
        wireToggle();
        updateToggleButtons();
        try {
            const response = await fetch('/api/settings/exchange-rate');
            if (response.ok) {
                const data = await response.json();
                usdToMxnRate = data.usdToMxnRate;
            }
        } catch (err) {
            // keep showing MXN if the rate can't be loaded
        }
        renderAllPrices();
    }

    window.BajaSolarRegion = {
        formatPrice: formatPrice,
        getLang: getLang,
        refresh: renderAllPrices
    };

    document.addEventListener('DOMContentLoaded', init);
})();
