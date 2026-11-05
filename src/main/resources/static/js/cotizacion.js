(function () {
    'use strict';

    const CATEGORY_LABELS_ES = {
        REFRIGERATOR: 'Refrigerador',
        FREEZER: 'Congelador',
        TV: 'Televisor',
        LIGHTING: 'Iluminación',
        FAN: 'Ventilador',
        AIR_CONDITIONER: 'Aire acondicionado',
        WASHING_MACHINE: 'Lavadora',
        MICROWAVE: 'Microondas',
        PUMP: 'Bomba de agua',
        ELECTRONICS: 'Electrónica',
        OTHER: 'Otro'
    };

    const CATEGORY_LABELS_EN = {
        REFRIGERATOR: 'Refrigerator',
        FREEZER: 'Freezer',
        TV: 'TV',
        LIGHTING: 'Lighting',
        FAN: 'Fan',
        AIR_CONDITIONER: 'Air Conditioner',
        WASHING_MACHINE: 'Washing Machine',
        MICROWAVE: 'Microwave',
        PUMP: 'Water Pump',
        ELECTRONICS: 'Electronics',
        OTHER: 'Other'
    };

    function categoryLabels() {
        return window.BajaSolarRegion.getLang() === 'en' ? CATEGORY_LABELS_EN : CATEGORY_LABELS_ES;
    }

    const DEFAULT_CUSTOM_SURGE_MULTIPLIER = 3.0;

    let presetsByCategory = {};
    let presetsById = {};

    const form = document.getElementById('quote-form');
    const appliancesContainer = document.getElementById('appliances-container');
    const rowTemplate = document.getElementById('appliance-row-template');
    const addApplianceBtn = document.getElementById('add-appliance-btn');
    const alertBox = document.getElementById('quote-alert');
    const resultsPanel = document.getElementById('results-panel');
    const submitBtn = document.getElementById('submit-btn');
    const downloadPdfBtn = document.getElementById('download-pdf-btn');

    let lastPayload = null;
    let suggestedKitId = null;

    function showAlert(message) {
        alertBox.textContent = message;
        alertBox.classList.remove('d-none');
    }

    function hideAlert() {
        alertBox.classList.add('d-none');
    }

    function setLoading(isLoading) {
        submitBtn.disabled = isLoading;
        submitBtn.innerHTML = isLoading
            ? '<span class="spinner-border spinner-border-sm me-2"></span>Calculando...'
            : 'Calcular mi sistema';
    }

    async function loadPresets() {
        const response = await fetch('/api/quotes/appliance-presets');
        if (!response.ok) {
            throw new Error('No se pudieron cargar los aparatos disponibles.');
        }
        presetsByCategory = await response.json();
        presetsById = {};
        Object.values(presetsByCategory).flat().forEach((template) => {
            presetsById[template.id] = template;
        });
    }

    function presetSelectOptionsHtml() {
        const labels = categoryLabels();
        return Object.entries(presetsByCategory)
            .map(([category, templates]) => {
                const options = templates
                    .map((template) => `<option value="${template.id}">${template.label}</option>`)
                    .join('');
                return `<optgroup label="${labels[category] || category}">${options}</optgroup>`;
            })
            .join('');
    }

    function updateHoursFromPreset(presetSelect, hoursInput) {
        const preset = presetsById[presetSelect.value];
        if (preset) {
            hoursInput.value = preset.defaultHoursPerDay;
        }
    }

    function createApplianceRow() {
        const node = rowTemplate.content.firstElementChild.cloneNode(true);

        const modeSelect = node.querySelector('.mode-select');
        const presetSelect = node.querySelector('.preset-select');
        const customFields = node.querySelector('.custom-fields');
        const hoursInput = node.querySelector('.hours-input');
        const motorCheck = node.querySelector('.custom-motor');
        const removeBtn = node.querySelector('.remove-row');

        presetSelect.innerHTML = presetSelectOptionsHtml();
        updateHoursFromPreset(presetSelect, hoursInput);

        presetSelect.addEventListener('change', () => updateHoursFromPreset(presetSelect, hoursInput));

        modeSelect.addEventListener('change', () => {
            const isCustom = modeSelect.value === 'custom';
            presetSelect.classList.toggle('d-none', isCustom);
            customFields.classList.toggle('d-none', !isCustom);
            motorCheck.classList.toggle('d-none', !isCustom);
            if (!isCustom) {
                motorCheck.checked = false;
            }
        });

        removeBtn.addEventListener('click', () => {
            node.remove();
        });

        return node;
    }

    function buildApplianceEntry(row) {
        const isCustom = row.querySelector('.mode-select').value === 'custom';
        const quantity = parseInt(row.querySelector('.quantity-input').value, 10);
        const hoursPerDay = parseFloat(row.querySelector('.hours-input').value);

        if (isCustom) {
            const motorLoad = row.querySelector('.custom-motor').checked;
            return {
                name: row.querySelector('.custom-name').value,
                watts: parseFloat(row.querySelector('.custom-watts').value),
                hoursPerDay: hoursPerDay,
                quantity: quantity,
                motorLoad: motorLoad,
                surgeMultiplier: motorLoad ? DEFAULT_CUSTOM_SURGE_MULTIPLIER : 1.0
            };
        }

        return {
            templateId: row.querySelector('.preset-select').value,
            quantity: quantity,
            hoursPerDayOverride: hoursPerDay
        };
    }

    function renderKitComparisonRow(prefix, kitValue, targetValue, unit) {
        const row = document.getElementById('kit-compare-' + prefix);
        const valueEl = document.getElementById('kit-compare-' + prefix + '-value');
        const iconEl = document.getElementById('kit-compare-' + prefix + '-icon');
        const diffEl = document.getElementById('kit-compare-' + prefix + '-diff');

        if (targetValue <= 0) {
            row.classList.add('d-none');
            return;
        }
        row.classList.remove('d-none');

        valueEl.textContent = kitValue.toFixed(2) + ' ' + unit;

        if (kitValue >= targetValue) {
            const surplus = kitValue - targetValue;
            iconEl.className = 'bi bi-check-circle-fill text-success ms-1';
            diffEl.className = 'text-success ms-1';
            diffEl.textContent = surplus >= 0.01 ? '(+' + surplus.toFixed(2) + ' ' + unit + ' de sobra)' : '';
        } else {
            const deficit = targetValue - kitValue;
            iconEl.className = 'bi bi-x-circle-fill text-danger ms-1';
            diffEl.className = 'text-danger ms-1';
            diffEl.textContent = '(faltan ' + deficit.toFixed(2) + ' ' + unit + ')';
        }
    }

    function renderResults(result) {
        document.getElementById('result-daily').textContent = result.dailyConsumptionKwh.toFixed(2) + ' kWh/día';
        document.getElementById('result-peak').textContent = result.peakLoadKw.toFixed(2) + ' kW';
        document.getElementById('result-inverter').textContent = result.recommendedInverterKw.toFixed(2) + ' kW';
        document.getElementById('result-battery').textContent = result.recommendedBatteryCapacityKwh > 0
            ? result.recommendedBatteryCapacityKwh.toFixed(2) + ' kWh'
            : 'No aplica (On-Grid)';
        document.getElementById('result-panels').textContent = result.recommendedPanelArrayKwp.toFixed(2) + ' kWp';

        const kitSuggestion = document.getElementById('kit-suggestion');
        if (result.recommendedKit) {
            const kit = result.recommendedKit;
            suggestedKitId = kit.id;
            document.getElementById('kit-suggestion-name').textContent = kit.name;
            document.getElementById('kit-suggestion-description').textContent = kit.description || '';
            const priceEl = document.getElementById('kit-suggestion-price');
            priceEl.dataset.priceMxn = kit.sellPrice;
            priceEl.textContent = window.BajaSolarRegion.formatPrice(kit.sellPrice);

            renderKitComparisonRow('panels', kit.totalPanelArrayKwp, result.recommendedPanelArrayKwp, 'kWp');
            renderKitComparisonRow('battery', kit.totalBatteryCapacityKwh, result.recommendedBatteryCapacityKwh, 'kWh');
            renderKitComparisonRow('inverter', kit.totalInverterPowerKw, result.recommendedInverterKw, 'kW');

            kitSuggestion.classList.remove('d-none');
        } else {
            kitSuggestion.classList.add('d-none');
        }

        resultsPanel.classList.remove('d-none');
        resultsPanel.scrollIntoView({ behavior: 'smooth' });
    }

    async function handleSubmit(event) {
        event.preventDefault();
        hideAlert();
        resultsPanel.classList.add('d-none');

        const rows = Array.from(appliancesContainer.querySelectorAll('.appliance-row'));
        if (rows.length === 0) {
            showAlert('Agrega al menos un aparato para calcular tu sistema.');
            return;
        }

        const payload = {
            systemType: form.querySelector('input[name="systemType"]:checked').value,
            autonomyDays: parseFloat(document.getElementById('autonomyDays').value),
            peakSunHours: parseFloat(document.getElementById('peakSunHours').value),
            appliances: rows.map(buildApplianceEntry)
        };

        setLoading(true);
        try {
            const response = await fetch('/api/quotes/calculate', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                const errorBody = await response.json().catch(() => null);
                showAlert((errorBody && errorBody.message) || 'No se pudo calcular tu sistema. Revisa los datos ingresados.');
                return;
            }

            lastPayload = payload;
            renderResults(await response.json());
        } catch (err) {
            showAlert('Ocurrió un error de conexión. Intenta nuevamente.');
        } finally {
            setLoading(false);
        }
    }

    async function handleDownloadPdf() {
        if (!lastPayload) {
            return;
        }

        downloadPdfBtn.disabled = true;
        try {
            const response = await fetch('/api/quotes/calculate/pdf', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(lastPayload)
            });

            if (!response.ok) {
                showAlert('No se pudo generar el PDF. Intenta nuevamente.');
                return;
            }

            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = 'cotizacion-baja-solar-supply.pdf';
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
        } catch (err) {
            showAlert('Ocurrió un error de conexión al generar el PDF.');
        } finally {
            downloadPdfBtn.disabled = false;
        }
    }

    async function init() {
        try {
            await loadPresets();
        } catch (err) {
            showAlert('No se pudieron cargar los aparatos disponibles. Intenta recargar la página.');
            return;
        }

        appliancesContainer.appendChild(createApplianceRow());
        addApplianceBtn.addEventListener('click', () => {
            appliancesContainer.appendChild(createApplianceRow());
        });
        form.addEventListener('submit', handleSubmit);
        downloadPdfBtn.addEventListener('click', handleDownloadPdf);
        const addSuggestedBtn = document.getElementById('kit-suggestion-add');
        addSuggestedBtn.addEventListener('click', () => {
            if (suggestedKitId != null) {
                window.BajaSolarCart.add(suggestedKitId, 1);
                window.BajaSolarCart.flashAdded(addSuggestedBtn);
            }
        });
    }

    document.addEventListener('DOMContentLoaded', init);
})();
