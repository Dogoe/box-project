(function () {
    'use strict';

    const CATEGORY_LABELS = {
        REFRIGERATOR: 'Refrigerador',
        FREEZER: 'Congelador',
        TV: 'Televisor',
        LIGHTING: 'Iluminación',
        FAN: 'Ventilador',
        AIR_CONDITIONER: 'Aire acondicionado',
        WASHING_MACHINE: 'Lavadora',
        MICROWAVE: 'Microondas',
        PUMP: 'Bomba de agua',
        OTHER: 'Otro'
    };

    let presetsByCategory = {};
    let presetsById = {};

    const form = document.getElementById('quote-form');
    const appliancesContainer = document.getElementById('appliances-container');
    const rowTemplate = document.getElementById('appliance-row-template');
    const addApplianceBtn = document.getElementById('add-appliance-btn');
    const alertBox = document.getElementById('quote-alert');
    const resultsPanel = document.getElementById('results-panel');
    const submitBtn = document.getElementById('submit-btn');

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

    function categoryOptionsHtml() {
        return Object.keys(presetsByCategory)
            .map((category) => `<option value="${category}">${CATEGORY_LABELS[category] || category}</option>`)
            .join('');
    }

    function presetOptionsHtml(category) {
        return (presetsByCategory[category] || [])
            .map((template) => `<option value="${template.id}">${template.label}</option>`)
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

        const categorySelect = node.querySelector('.category-select');
        const presetSelect = node.querySelector('.preset-select');
        const hoursInput = node.querySelector('.hours-input');
        const presetFields = node.querySelector('.preset-fields');
        const customFields = node.querySelector('.custom-fields');
        const modeToggles = node.querySelectorAll('.mode-toggle');
        const motorCheck = node.querySelector('.custom-motor');
        const surgeWrap = node.querySelector('.custom-surge-wrap');
        const removeBtn = node.querySelector('.remove-row');

        categorySelect.innerHTML = categoryOptionsHtml();
        const firstCategory = Object.keys(presetsByCategory)[0];
        categorySelect.value = firstCategory;
        presetSelect.innerHTML = presetOptionsHtml(firstCategory);
        updateHoursFromPreset(presetSelect, hoursInput);

        categorySelect.addEventListener('change', () => {
            presetSelect.innerHTML = presetOptionsHtml(categorySelect.value);
            updateHoursFromPreset(presetSelect, hoursInput);
        });

        presetSelect.addEventListener('change', () => updateHoursFromPreset(presetSelect, hoursInput));

        modeToggles.forEach((radio) => radio.addEventListener('change', () => {
            const isCustom = node.querySelector('.mode-toggle[value="custom"]').checked;
            presetFields.classList.toggle('d-none', isCustom);
            customFields.classList.toggle('d-none', !isCustom);
        }));

        motorCheck.addEventListener('change', () => {
            surgeWrap.classList.toggle('d-none', !motorCheck.checked);
        });

        removeBtn.addEventListener('click', () => {
            node.remove();
        });

        return node;
    }

    function buildApplianceEntry(row) {
        const isCustom = row.querySelector('.mode-toggle[value="custom"]').checked;

        if (isCustom) {
            const motorLoad = row.querySelector('.custom-motor').checked;
            return {
                name: row.querySelector('.custom-name').value,
                watts: parseFloat(row.querySelector('.custom-watts').value),
                hoursPerDay: parseFloat(row.querySelector('.custom-hours').value),
                quantity: parseInt(row.querySelector('.custom-quantity').value, 10),
                motorLoad: motorLoad,
                surgeMultiplier: motorLoad ? parseFloat(row.querySelector('.custom-surge').value) : 1.0
            };
        }

        return {
            templateId: row.querySelector('.preset-select').value,
            quantity: parseInt(row.querySelector('.quantity-input').value, 10),
            hoursPerDayOverride: parseFloat(row.querySelector('.hours-input').value)
        };
    }

    function renderResults(result) {
        document.getElementById('result-daily').textContent = result.dailyConsumptionKwh.toFixed(2) + ' kWh/día';
        document.getElementById('result-peak').textContent = result.peakLoadKw.toFixed(2) + ' kW';
        document.getElementById('result-inverter').textContent = result.recommendedInverterKw.toFixed(2) + ' kW';
        document.getElementById('result-battery').textContent = result.recommendedBatteryCapacityKwh > 0
            ? result.recommendedBatteryCapacityKwh.toFixed(2) + ' kWh'
            : 'No aplica (On-Grid)';
        document.getElementById('result-panels').textContent = result.recommendedPanelArrayKwp.toFixed(2) + ' kWp';

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

            renderResults(await response.json());
        } catch (err) {
            showAlert('Ocurrió un error de conexión. Intenta nuevamente.');
        } finally {
            setLoading(false);
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
    }

    document.addEventListener('DOMContentLoaded', init);
})();
