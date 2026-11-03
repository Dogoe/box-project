(function () {
    'use strict';

    const STORAGE_KEY = 'preferredCurrency';
    let usdToMxnRate = null;

    const mxnNumberFormatter = new Intl.NumberFormat('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    const usdNumberFormatter = new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

    function getCurrency() {
        try {
            return localStorage.getItem(STORAGE_KEY) === 'USD' ? 'USD' : 'MXN';
        } catch (err) {
            return 'MXN';
        }
    }

    function setCurrency(currency) {
        try {
            localStorage.setItem(STORAGE_KEY, currency);
        } catch (err) {
            // private browsing / storage disabled - the toggle still works for this page view
        }
    }

    function formatPrice(mxnValue) {
        const value = Number(mxnValue);
        if (Number.isNaN(value)) {
            return '-';
        }
        if (getCurrency() === 'USD' && usdToMxnRate) {
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
        const currency = getCurrency();
        document.querySelectorAll('.currency-toggle-btn').forEach((btn) => {
            btn.classList.toggle('active', btn.dataset.currency === currency);
        });
    }

    function wireToggle() {
        document.querySelectorAll('.currency-toggle-btn').forEach((btn) => {
            btn.addEventListener('click', () => {
                setCurrency(btn.dataset.currency);
                updateToggleButtons();
                renderAllPrices();
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

    window.BajaSolarCurrency = {
        formatPrice: formatPrice,
        getCurrency: getCurrency,
        refresh: renderAllPrices
    };

    document.addEventListener('DOMContentLoaded', init);
})();
