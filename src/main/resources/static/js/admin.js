(function () {
    'use strict';

    const token = localStorage.getItem('adminToken');
    if (!token) {
        window.location.href = '/admin/login';
        return;
    }

    const listAlert = document.getElementById('list-alert');
    const tableHead = document.getElementById('entity-table-head');
    const tableBody = document.getElementById('entity-table-body');
    const entityTitle = document.getElementById('entity-title');
    const newBtn = document.getElementById('new-entity-btn');
    const entityTableWrapper = document.getElementById('entity-table-wrapper');
    const settingsPanel = document.getElementById('settings-panel');
    const settingsError = document.getElementById('settings-error');
    const settingsSuccess = document.getElementById('settings-success');
    const marginError = document.getElementById('margin-error');
    const marginSuccess = document.getElementById('margin-success');
    const modalEl = document.getElementById('entity-modal');
    const modal = new bootstrap.Modal(modalEl);
    const modalFormEl = document.getElementById('entity-form');
    const modalFieldsEl = document.getElementById('entity-form-fields');
    const modalTitleEl = document.getElementById('entity-modal-title');
    const modalErrorEl = document.getElementById('entity-modal-error');
    const modalDialogEl = modalEl.querySelector('.modal-dialog');
    const wiringError = document.getElementById('wiring-error');
    const storeError = document.getElementById('store-error');
    const storeSuccess = document.getElementById('store-success');
    const ordersFilter = document.getElementById('orders-filter');
    const ordersWhatsappWarning = document.getElementById('orders-whatsapp-warning');
    const pendingOrdersBadge = document.getElementById('pending-orders-badge');
    const orderModalEl = document.getElementById('order-modal');
    const orderModal = new bootstrap.Modal(orderModalEl);
    const orderModalTitle = document.getElementById('order-modal-title');
    const orderModalBody = document.getElementById('order-modal-body');
    const orderModalError = document.getElementById('order-modal-error');
    const wiringSuccess = document.getElementById('wiring-success');

    let currentEntity = null;
    let editingId = null;
    let allProducts = [];

    document.getElementById('logout-btn').addEventListener('click', () => {
        localStorage.removeItem('adminToken');
        window.location.href = '/admin/login';
    });

    async function authFetch(url, options) {
        const response = await fetch(url, Object.assign({}, options, {
            headers: Object.assign({}, (options && options.headers) || {}, {
                'Authorization': 'Bearer ' + token
            })
        }));

        if (response.status === 401 || response.status === 403) {
            localStorage.removeItem('adminToken');
            window.location.href = '/admin/login';
            throw new Error('No autorizado');
        }

        return response;
    }

    async function errorMessage(response, fallback) {
        const body = await response.json().catch(() => null);
        return (body && body.message) || fallback;
    }

    const WIRE_GAUGES = [
        { value: 'AWG_14', label: '14 AWG' },
        { value: 'AWG_12', label: '12 AWG' },
        { value: 'AWG_10', label: '10 AWG' },
        { value: 'AWG_8', label: '8 AWG' },
        { value: 'AWG_6', label: '6 AWG' },
        { value: 'AWG_4', label: '4 AWG' },
        { value: 'AWG_2', label: '2 AWG' },
        { value: 'AWG_1_0', label: '1/0 AWG' },
        { value: 'AWG_2_0', label: '2/0 AWG' },
        { value: 'AWG_4_0', label: '4/0 AWG' }
    ];
    const WIRE_GAUGE_LABELS = Object.fromEntries(WIRE_GAUGES.map((g) => [g.value, g.label]));

    const ENTITY_CONFIGS = {
        panels: {
            label: 'Paneles Solares',
            endpoint: '/api/catalog/panels',
            columns: [
                { key: 'imageUrl', label: '', type: 'thumb' },
                { key: 'sku', label: 'SKU' },
                { key: 'name', label: 'Nombre' },
                { key: 'wattage', label: 'Potencia', suffix: ' W' },
                { key: 'connector', label: 'Conector' },
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
                { key: 'referenceUrl', label: 'Ref.', type: 'link' },
                { key: 'active', label: 'Activo', type: 'boolean' }
            ],
            fields: [
                { key: 'sku', label: 'SKU', type: 'text', required: true },
                { key: 'name', label: 'Nombre', type: 'text', required: true },
                { key: 'brand', label: 'Marca', type: 'text' },
                { key: 'description', label: 'Descripción', type: 'textarea' },
                { key: 'price', label: 'Precio', type: 'number', step: '0.01', required: true },
                { key: 'stockQuantity', label: 'Stock', type: 'number', required: true, defaultValue: 0 },
                { key: 'imageUrls', label: 'Fotos', type: 'images', folder: 'products' },
                { key: 'referenceUrl', label: 'URL de referencia (Amazon, Mercado Libre...; solo admin)', type: 'url', placeholder: 'https://' },
                { key: 'wattage', label: 'Potencia (W)', type: 'number', required: true },
                { key: 'technology', label: 'Tecnología', type: 'select', required: true,
                    options: ['MONOCRYSTALLINE', 'POLYCRYSTALLINE', 'THIN_FILM'] },
                { key: 'efficiencyPercent', label: 'Eficiencia (%)', type: 'number', step: '0.1' },
                { key: 'voc', label: 'Voc (V)', type: 'number', step: '0.1' },
                { key: 'isc', label: 'Isc (A)', type: 'number', step: '0.1' },
                { key: 'connector', label: 'Conector del cable', type: 'select', required: true, defaultValue: 'MC4',
                    options: [{ value: 'MC4', label: 'MC4 (paneles normales)' }, { value: 'SAE', label: 'SAE (kits chicos de 12 V)' }] },
                { key: 'active', label: 'Activo', type: 'checkbox', defaultValue: true }
            ]
        },
        batteries: {
            label: 'Baterías',
            endpoint: '/api/catalog/batteries',
            columns: [
                { key: 'imageUrl', label: '', type: 'thumb' },
                { key: 'sku', label: 'SKU' },
                { key: 'name', label: 'Nombre' },
                { key: 'voltage', label: 'Voltaje', suffix: ' V' },
                { key: 'capacityAh', label: 'Capacidad', suffix: ' Ah' },
                { key: 'capacityKwh', label: 'Energía', suffix: ' kWh' },
                { key: 'chemistry', label: 'Química' },
                { key: 'depthOfDischargePercent', label: 'Descarga útil', suffix: ' %' },
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
                { key: 'referenceUrl', label: 'Ref.', type: 'link' },
                { key: 'active', label: 'Activo', type: 'boolean' }
            ],
            fields: [
                { key: 'sku', label: 'SKU', type: 'text', required: true },
                { key: 'name', label: 'Nombre', type: 'text', required: true },
                { key: 'brand', label: 'Marca', type: 'text' },
                { key: 'description', label: 'Descripción', type: 'textarea' },
                { key: 'price', label: 'Precio', type: 'number', step: '0.01', required: true },
                { key: 'stockQuantity', label: 'Stock', type: 'number', required: true, defaultValue: 0 },
                { key: 'imageUrls', label: 'Fotos', type: 'images', folder: 'products' },
                { key: 'referenceUrl', label: 'URL de referencia (Amazon, Mercado Libre...; solo admin)', type: 'url', placeholder: 'https://' },
                { key: 'capacityAh', label: 'Capacidad (Ah)', type: 'number', step: '0.1', required: true },
                { key: 'voltage', label: 'Voltaje nominal (V)', type: 'number', step: '0.1', required: true },
                { key: 'chemistry', label: 'Química (define la profundidad de descarga)', type: 'select', required: true,
                    options: ['LIFEPO4', 'LITHIUM_ION', 'LEAD_ACID', 'GEL', 'AGM'], defaultValue: 'LIFEPO4' },
                { key: 'cycleLife', label: 'Ciclos de vida', type: 'number' },
                { key: 'bmsMaxDischargeA', label: 'Corriente máx. de descarga del BMS (A) · opcional, solo litio', type: 'number', step: '0.1',
                    help: 'La que dice la ficha de la batería. Si la dejas vacía se supone su capacidad en amperes (100 Ah → 100 A) y la revisión de kits te avisa.' },
                { key: 'active', label: 'Activo', type: 'checkbox', defaultValue: true }
            ]
        },
        inverters: {
            label: 'Inversores',
            endpoint: '/api/catalog/inverters',
            columns: [
                { key: 'imageUrl', label: '', type: 'thumb' },
                { key: 'sku', label: 'SKU' },
                { key: 'name', label: 'Nombre' },
                { key: 'ratedPowerKw', label: 'Potencia', suffix: ' kW' },
                { key: 'batteryVoltage', label: 'Batería', suffix: ' V' },
                { key: 'mpptMaxChargeCurrentA', label: 'Carga MPPT', format: (item) => item.builtInCharger === false
                    ? '<span class="text-muted">Controlador aparte</span>'
                    : (item.mpptMaxChargeCurrentA != null ? item.mpptMaxChargeCurrentA + ' A' : '') },
                { key: 'effectiveMaxPvPowerW', label: 'PV máx.', format: (item) => item.effectiveMaxPvPowerW > 0
                    ? Math.round(item.effectiveMaxPvPowerW) + ' W' + (item.maxPvPowerEstimated ? ' (est.)' : '') : '' },
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
                { key: 'referenceUrl', label: 'Ref.', type: 'link' },
                { key: 'active', label: 'Activo', type: 'boolean' }
            ],
            fields: [
                { key: 'sku', label: 'SKU', type: 'text', required: true },
                { key: 'name', label: 'Nombre', type: 'text', required: true },
                { key: 'brand', label: 'Marca', type: 'text' },
                { key: 'description', label: 'Descripción', type: 'textarea' },
                { key: 'price', label: 'Precio', type: 'number', step: '0.01', required: true },
                { key: 'stockQuantity', label: 'Stock', type: 'number', required: true, defaultValue: 0 },
                { key: 'imageUrls', label: 'Fotos', type: 'images', folder: 'products' },
                { key: 'referenceUrl', label: 'URL de referencia (Amazon, Mercado Libre...; solo admin)', type: 'url', placeholder: 'https://' },
                { key: 'ratedPowerKw', label: 'Potencia nominal (kW)', type: 'number', step: '0.1', required: true },
                { key: 'surgePowerKw', label: 'Potencia pico (kW)', type: 'number', step: '0.1', required: true },
                { key: 'inverterType', label: 'Tipo', type: 'select', required: true,
                    options: ['HYBRID', 'OFF_GRID', 'STRING', 'MICRO'], defaultValue: 'HYBRID' },
                { key: 'batteryVoltage', label: 'Voltaje de baterías (V)', type: 'select', required: true,
                    options: [12, 24, 48], defaultValue: 48, numeric: true },
                { key: 'builtInCharger', label: 'Incluye cargador solar (MPPT)', type: 'checkbox', defaultValue: true,
                    help: 'Desmárcalo si el inversor no carga desde paneles: el kit necesitará un controlador de carga MPPT aparte.' },
                { key: 'mpptChannels', label: 'Canales MPPT', type: 'number', defaultValue: 1, dependsOn: 'builtInCharger' },
                { key: 'maxInputVoltage', label: 'MPPT: voltaje PV máx. (Voc, V)', type: 'number', step: '0.1', required: true, dependsOn: 'builtInCharger' },
                { key: 'mpptMinVoltage', label: 'MPPT: voltaje PV mínimo de operación (V)', type: 'number', step: '0.1', dependsOn: 'builtInCharger' },
                { key: 'mpptMaxInputCurrentA', label: 'MPPT: corriente PV máx. por canal (A)', type: 'number', step: '0.1', dependsOn: 'builtInCharger' },
                { key: 'mpptMaxChargeCurrentA', label: 'MPPT: corriente máx. de carga a batería (A)', type: 'number', step: '0.1', required: true, dependsOn: 'builtInCharger' },
                { key: 'maxPvInputPowerW', label: 'MPPT: potencia PV máx. (W) — opcional, dato de la ficha', type: 'number', step: '1', dependsOn: 'builtInCharger' },
                { key: 'active', label: 'Activo', type: 'checkbox', defaultValue: true }
            ]
        },
        controllers: {
            label: 'Controladores MPPT',
            endpoint: '/api/catalog/charge-controllers',
            columns: [
                { key: 'imageUrl', label: '', type: 'thumb' },
                { key: 'sku', label: 'SKU' },
                { key: 'name', label: 'Nombre' },
                { key: 'supportedBatteryVoltages', label: 'Batería', format: (item) => (item.supportedBatteryVoltages || []).join('/') + ' V' },
                { key: 'maxChargeCurrentA', label: 'Carga', suffix: ' A' },
                { key: 'maxInputVoltage', label: 'PV máx. (Voc)', suffix: ' V' },
                { key: 'maxPvInputPowerW', label: 'PV máx.', format: (item) => item.maxPvInputPowerW ? item.maxPvInputPowerW + ' W' : '<span class="text-muted">estimado</span>' },
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
                { key: 'referenceUrl', label: 'Ref.', type: 'link' },
                { key: 'active', label: 'Activo', type: 'boolean' }
            ],
            fields: [
                { key: 'sku', label: 'SKU', type: 'text', required: true },
                { key: 'name', label: 'Nombre', type: 'text', required: true },
                { key: 'brand', label: 'Marca', type: 'text' },
                { key: 'description', label: 'Descripción', type: 'textarea' },
                { key: 'price', label: 'Precio', type: 'number', step: '0.01', required: true },
                { key: 'stockQuantity', label: 'Stock', type: 'number', required: true, defaultValue: 0 },
                { key: 'imageUrls', label: 'Fotos', type: 'images', folder: 'products' },
                { key: 'referenceUrl', label: 'URL de referencia (Amazon, Mercado Libre...; solo admin)', type: 'url', placeholder: 'https://' },
                { key: 'supportedBatteryVoltages', label: 'Voltajes de batería que admite', type: 'checkboxes',
                    options: [12, 24, 48], defaultValue: [12, 24, 48] },
                { key: 'maxChargeCurrentA', label: 'Corriente máx. de carga a batería (A)', type: 'number', step: '0.1', required: true },
                { key: 'maxInputVoltage', label: 'Voltaje PV máx. (Voc, V)', type: 'number', step: '0.1', required: true },
                { key: 'mpptMinVoltage', label: 'Voltaje PV mínimo de operación (V)', type: 'number', step: '0.1' },
                { key: 'mpptMaxInputCurrentA', label: 'Corriente PV máx. por entrada (A)', type: 'number', step: '0.1' },
                { key: 'maxPvInputPowerW', label: 'Potencia PV máx. (W) — opcional, dato de la ficha', type: 'number', step: '1',
                    placeholder: 'Vacío = se estima con la corriente de carga × voltaje del banco × 1.3' },
                { key: 'mpptChannels', label: 'Entradas MPPT', type: 'number', defaultValue: 1 },
                { key: 'active', label: 'Activo', type: 'checkbox', defaultValue: true }
            ]
        },
        structures: {
            label: 'Estructura de Aluminio',
            endpoint: '/api/catalog/structures',
            columns: [
                { key: 'imageUrl', label: '', type: 'thumb' },
                { key: 'sku', label: 'SKU' },
                { key: 'name', label: 'Nombre' },
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
                { key: 'referenceUrl', label: 'Ref.', type: 'link' },
                { key: 'active', label: 'Activo', type: 'boolean' }
            ],
            fields: [
                { key: 'sku', label: 'SKU', type: 'text', required: true },
                { key: 'name', label: 'Nombre', type: 'text', required: true },
                { key: 'brand', label: 'Marca', type: 'text' },
                { key: 'description', label: 'Descripción', type: 'textarea' },
                { key: 'price', label: 'Precio', type: 'number', step: '0.01', required: true },
                { key: 'stockQuantity', label: 'Stock', type: 'number', required: true, defaultValue: 0 },
                { key: 'imageUrls', label: 'Fotos', type: 'images', folder: 'products' },
                { key: 'referenceUrl', label: 'URL de referencia (Amazon, Mercado Libre...; solo admin)', type: 'url', placeholder: 'https://' },
                { key: 'active', label: 'Activo', type: 'checkbox', defaultValue: true }
            ]
        },
        cables: {
            label: 'Cables de Conexión',
            endpoint: '/api/catalog/cables',
            columns: [
                { key: 'imageUrl', label: '', type: 'thumb' },
                { key: 'sku', label: 'SKU' },
                { key: 'name', label: 'Nombre' },
                { key: 'gauge', label: 'Calibre', labels: WIRE_GAUGE_LABELS },
                { key: 'lengthMeters', label: 'Longitud', suffix: ' m' },
                { key: 'price', label: 'Precio', prefix: '$' },
                { key: 'stockQuantity', label: 'Stock' },
                { key: 'referenceUrl', label: 'Ref.', type: 'link' },
                { key: 'active', label: 'Activo', type: 'boolean' }
            ],
            fields: [
                { key: 'sku', label: 'SKU', type: 'text', required: true },
                { key: 'name', label: 'Nombre', type: 'text', required: true },
                { key: 'brand', label: 'Marca', type: 'text' },
                { key: 'description', label: 'Descripción', type: 'textarea' },
                { key: 'price', label: 'Precio', type: 'number', step: '0.01', required: true },
                { key: 'stockQuantity', label: 'Stock', type: 'number', required: true, defaultValue: 0 },
                { key: 'imageUrls', label: 'Fotos', type: 'images', folder: 'products' },
                { key: 'referenceUrl', label: 'URL de referencia (Amazon, Mercado Libre...; solo admin)', type: 'url', placeholder: 'https://' },
                { key: 'gauge', label: 'Calibre', type: 'select', required: true, options: WIRE_GAUGES },
                { key: 'lengthMeters', label: 'Longitud (m)', type: 'number', step: '0.1', required: true },
                { key: 'active', label: 'Activo', type: 'checkbox', defaultValue: true }
            ]
        }
    };

    function currentEndpoint() {
        return currentEntity === 'kits' ? '/api/catalog/kits' : ENTITY_CONFIGS[currentEntity].endpoint;
    }

    function showEntityTable() {
        newBtn.classList.remove('d-none');
        entityTableWrapper.classList.remove('d-none');
        settingsPanel.classList.add('d-none');
        ordersFilter.classList.add('d-none');
        ordersWhatsappWarning.classList.add('d-none');
    }

    function showSettingsPanel() {
        newBtn.classList.add('d-none');
        entityTableWrapper.classList.add('d-none');
        settingsPanel.classList.remove('d-none');
        ordersFilter.classList.add('d-none');
        ordersWhatsappWarning.classList.add('d-none');
    }

    function showOrdersTable() {
        newBtn.classList.add('d-none');
        entityTableWrapper.classList.remove('d-none');
        settingsPanel.classList.add('d-none');
        ordersFilter.classList.remove('d-none');
    }

    // ---- Generic (panels/batteries/inverters) rendering ----

    function formatValue(item, column) {
        if (column.format) {
            return column.format(item);
        }
        const value = item[column.key];
        if (column.type === 'boolean') {
            return value ? 'Sí' : 'No';
        }
        if (value === null || value === undefined) {
            return '';
        }
        if (column.type === 'thumb') {
            return /^https?:\/\//i.test(value)
                ? '<img src="' + escapeAttr(value) + '" alt="" loading="lazy" class="rounded border" style="width: 40px; height: 40px; object-fit: contain; background: #fff;">'
                : '';
        }
        if (column.type === 'link') {
            // Only http(s) links; the API enforces the same, this just guards old/odd data.
            return /^https?:\/\//i.test(value)
                ? '<a href="' + escapeAttr(value) + '" target="_blank" rel="noopener noreferrer" title="' + escapeAttr(value) + '">' +
                  '<i class="bi bi-box-arrow-up-right"></i></a>'
                : '';
        }
        const display = column.labels ? (column.labels[value] || value) : value;
        return (column.prefix || '') + display + (column.suffix || '');
    }

    function renderTableHead(columns) {
        const headerCells = columns.map((c) => '<th>' + c.label + '</th>').join('');
        tableHead.innerHTML = '<tr>' + headerCells + '<th></th></tr>';
    }

    // Each list load gets a number; a slower answer from a tab the admin already left is dropped,
    // so it can't overwrite the list now on screen.
    let listLoadSeq = 0;

    function startListLoad() {
        listLoadSeq += 1;
        tableBody.innerHTML = tableMessageRow(
            '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>Cargando...');
        return listLoadSeq;
    }

    function isStale(seq) {
        return seq !== listLoadSeq;
    }

    function tableMessageRow(html) {
        const columns = Math.max(1, tableHead.querySelectorAll('th').length);
        return '<tr><td colspan="' + columns + '" class="text-center text-secondary py-5">' + html + '</td></tr>';
    }

    function actionButtonsHtml(id) {
        return '<td class="text-end">' +
            '<button type="button" class="btn btn-sm btn-outline-secondary edit-btn" data-id="' + id + '"><i class="bi bi-pencil"></i></button> ' +
            '<button type="button" class="btn btn-sm btn-outline-danger delete-btn" data-id="' + id + '"><i class="bi bi-trash"></i></button>' +
            '</td>';
    }

    function wireRowActions(onEdit, onDelete) {
        tableBody.querySelectorAll('.edit-btn').forEach((btn) => {
            btn.addEventListener('click', () => onEdit(Number(btn.dataset.id)));
        });
        tableBody.querySelectorAll('.delete-btn').forEach((btn) => {
            btn.addEventListener('click', () => onDelete(Number(btn.dataset.id)));
        });
    }

    async function loadEntity(entityKey) {
        currentEntity = entityKey;
        showEntityTable();
        listAlert.classList.add('d-none');
        const config = ENTITY_CONFIGS[entityKey];
        entityTitle.textContent = config.label;
        renderTableHead(config.columns);
        const seq = startListLoad();

        try {
            const response = await authFetch(config.endpoint);
            const items = await response.json();
            if (isStale(seq)) {
                return;
            }

            tableBody.innerHTML = items.length === 0 ? tableMessageRow('Aún no hay registros. Usa <strong>Nuevo</strong> para agregar uno.') : items.map((item) => {
                const cells = config.columns.map((c) => '<td>' + formatValue(item, c) + '</td>').join('');
                return '<tr>' + cells + actionButtonsHtml(item.id) + '</tr>';
            }).join('');

            wireRowActions((id) => openModal(id), (id) => deleteItem(id));
        } catch (err) {
            if (isStale(seq)) {
                return;
            }
            tableBody.innerHTML = '';
            listAlert.textContent = 'No se pudo cargar la lista.';
            listAlert.classList.remove('d-none');
        }
    }

    function fieldInputHtml(field, value) {
        const id = 'field-' + field.key;
        const hasValue = value !== undefined && value !== null;
        const val = hasValue ? value : (field.defaultValue !== undefined ? field.defaultValue : '');
        const requiredAttr = field.required ? 'required' : '';

        if (field.type === 'select') {
            const options = field.options.map((opt) => {
                const optValue = typeof opt === 'object' ? opt.value : opt;
                const optLabel = typeof opt === 'object' ? opt.label : opt;
                return '<option value="' + optValue + '"' + (String(optValue) === String(val) ? ' selected' : '') + '>' + optLabel + '</option>';
            }).join('');
            return '<select class="form-select" id="' + id + '" ' + requiredAttr + '>' + options + '</select>';
        }
        if (field.type === 'textarea') {
            return '<textarea class="form-control" id="' + id + '">' + (val || '') + '</textarea>';
        }
        if (field.type === 'checkbox') {
            return '<div class="form-check"><input type="checkbox" class="form-check-input" id="' + id + '"' +
                (val ? ' checked' : '') + '></div>';
        }
        if (field.type === 'images') {
            return photoManagerHtml(id, field.folder);
        }
        if (field.type === 'checkboxes') {
            const selected = (Array.isArray(val) ? val : []).map(String);
            return '<div id="' + id + '">' + field.options.map((opt) =>
                '<div class="form-check form-check-inline">' +
                '<input type="checkbox" class="form-check-input" id="' + id + '-' + opt + '" value="' + opt + '"' +
                (selected.includes(String(opt)) ? ' checked' : '') + '>' +
                '<label class="form-check-label" for="' + id + '-' + opt + '">' + opt + ' V</label></div>'
            ).join('') + '</div>';
        }
        const step = field.step ? ' step="' + field.step + '"' : '';
        const placeholder = field.placeholder ? ' placeholder="' + field.placeholder + '"' : '';
        return '<input type="' + field.type + '" class="form-control" id="' + id + '" value="' + escapeAttr(val) + '"' + step + placeholder + ' ' + requiredAttr + '>';
    }

    // ---- Photo lists (uploaded to Cloudflare R2, or pasted links); the first photo is the main one ----

    const MAX_PHOTOS = 8;
    const photoLists = new Map();

    function photoManagerHtml(id, folder) {
        return '<div class="photo-manager" id="' + id + '" data-folder="' + folder + '">' +
            '<div class="d-flex flex-wrap gap-2 photo-list"></div>' +
            '<div class="d-flex flex-wrap gap-2 mt-2 align-items-center">' +
            '<button type="button" class="btn btn-sm btn-outline-secondary photo-upload-btn"><i class="bi bi-upload"></i> Subir fotos</button>' +
            '<div class="input-group input-group-sm" style="max-width: 380px;">' +
            // type="text": a half-typed link must not block the form's Guardar (we validate it ourselves).
            '<input type="text" inputmode="url" class="form-control photo-link-input" placeholder="o pega un link https://...">' +
            '<button type="button" class="btn btn-outline-secondary photo-link-add">Agregar</button></div>' +
            '</div>' +
            '<input type="file" class="d-none photo-file-input" accept="image/jpeg,image/png,image/webp" multiple>' +
            '<div class="form-text">La primera es la principal. Máximo ' + MAX_PHOTOS + ' fotos (JPG, PNG o WebP).</div>' +
            '<div class="small text-danger photo-error d-none"></div>' +
            '</div>';
    }

    /** The photos to save; a valid link left in the box without pressing "Agregar" is included too. */
    function photoManagerValue(id) {
        const photos = (photoLists.get(id) || []).slice();
        const pending = document.querySelector('#' + id + ' .photo-link-input');
        const url = pending ? pending.value.trim() : '';
        if (/^https?:\/\/\S+$/i.test(url) && !photos.includes(url) && photos.length < MAX_PHOTOS) {
            photos.push(url);
        }
        return photos;
    }

    function photoUploadInProgress() {
        return document.querySelector('.photo-manager[data-busy]') !== null;
    }

    function initPhotoManager(id, urls) {
        const root = document.getElementById(id);
        const listEl = root.querySelector('.photo-list');
        const errorEl = root.querySelector('.photo-error');
        const fileInput = root.querySelector('.photo-file-input');
        const uploadBtn = root.querySelector('.photo-upload-btn');
        const linkInput = root.querySelector('.photo-link-input');
        photoLists.set(id, (urls || []).slice());

        const showError = (message) => {
            errorEl.textContent = message || '';
            errorEl.classList.toggle('d-none', !message);
        };
        const tileButton = (action, icon, title) =>
            '<button type="button" class="btn btn-light btn-sm py-0 px-1 border" data-action="' + action + '" title="' + title + '" aria-label="' + title + '">' +
            '<i class="bi ' + icon + '"></i></button>';

        function render(pending) {
            const photos = photoLists.get(id);
            listEl.innerHTML = photos.map((url, index) =>
                '<div class="photo-tile border rounded p-1 text-center bg-white" data-index="' + index + '" style="width: 112px;">' +
                '<img src="' + escapeAttr(url) + '" alt="" style="width: 100px; height: 80px; object-fit: contain;">' +
                (index === 0 ? '<div class="small fw-semibold text-success">Principal</div>'
                    : '<div class="small"><a href="#" data-action="main">Hacer principal</a></div>') +
                '<div class="d-flex justify-content-center gap-1 mt-1">' +
                (index > 0 ? tileButton('left', 'bi-arrow-left', 'Mover a la izquierda') : '') +
                (index < photos.length - 1 ? tileButton('right', 'bi-arrow-right', 'Mover a la derecha') : '') +
                tileButton('remove', 'bi-x-lg', 'Quitar foto') +
                '</div></div>'
            ).join('') + (pending
                ? '<div class="border rounded d-flex align-items-center justify-content-center text-muted small" style="width: 112px; height: 132px;">' +
                  '<span class="spinner-border spinner-border-sm me-1"></span>' + pending + '</div>'
                : '');
            listEl.querySelectorAll('.photo-tile img').forEach((img) => {
                img.onerror = () => img.replaceWith(Object.assign(document.createElement('div'), {
                    className: 'small text-danger d-flex align-items-center justify-content-center',
                    style: 'width: 100px; height: 80px;',
                    textContent: 'No carga'
                }));
            });
            uploadBtn.disabled = Boolean(pending) || photos.length >= MAX_PHOTOS;
        }

        listEl.addEventListener('click', (event) => {
            const target = event.target.closest('[data-action]');
            if (!target) {
                return;
            }
            event.preventDefault();
            const photos = photoLists.get(id);
            const index = Number(target.closest('.photo-tile').dataset.index);
            const move = (from, to) => photos.splice(to, 0, photos.splice(from, 1)[0]);
            if (target.dataset.action === 'remove') {
                photos.splice(index, 1);
            } else if (target.dataset.action === 'left') {
                move(index, index - 1);
            } else if (target.dataset.action === 'right') {
                move(index, index + 1);
            } else if (target.dataset.action === 'main') {
                move(index, 0);
            }
            showError('');
            render();
        });

        root.querySelector('.photo-link-add').addEventListener('click', () => {
            const url = linkInput.value.trim();
            const photos = photoLists.get(id);
            if (!/^https?:\/\/\S+$/i.test(url)) {
                showError('El link debe empezar con http:// o https://');
            } else if (photos.length >= MAX_PHOTOS) {
                showError('Máximo ' + MAX_PHOTOS + ' fotos; quita alguna.');
            } else if (photos.includes(url)) {
                showError('Esa foto ya está en la lista.');
            } else {
                photos.push(url);
                linkInput.value = '';
                showError('');
                render();
            }
        });
        linkInput.addEventListener('keydown', (event) => {
            if (event.key === 'Enter') {
                event.preventDefault();
                root.querySelector('.photo-link-add').click();
            }
        });

        uploadBtn.addEventListener('click', () => fileInput.click());
        fileInput.addEventListener('change', async () => {
            const photos = photoLists.get(id);
            const files = Array.from(fileInput.files).slice(0, MAX_PHOTOS - photos.length);
            const skipped = fileInput.files.length - files.length;
            fileInput.value = '';
            showError('');
            const errors = [];
            root.dataset.busy = '1';
            for (let i = 0; i < files.length; i++) {
                render('Subiendo ' + (i + 1) + ' de ' + files.length);
                try {
                    const form = new FormData();
                    form.append('file', files[i]);
                    form.append('folder', root.dataset.folder);
                    const response = await authFetch('/api/admin/images', { method: 'POST', body: form });
                    if (!response.ok) {
                        throw new Error(await errorMessage(response, 'No se pudo subir la foto.'));
                    }
                    photos.push((await response.json()).url);
                } catch (err) {
                    errors.push(files[i].name + ': ' + (err.message || 'No se pudo subir.'));
                }
            }
            delete root.dataset.busy;
            if (skipped > 0) {
                errors.push(skipped + ' foto(s) no se subieron: el máximo es ' + MAX_PHOTOS + '.');
            }
            render();
            showError(errors.join(' '));
        });

        render();
    }

    function escapeAttr(text) {
        return String(text == null ? '' : text)
            .replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    function renderGenericForm(config, item) {
        modalFieldsEl.innerHTML = config.fields.map((field) =>
            '<div class="mb-3" data-field="' + field.key + '">' +
            '<label class="form-label" for="field-' + field.key + '">' + field.label + '</label>' +
            fieldInputHtml(field, item ? item[field.key] : undefined) +
            (field.help ? '<div class="form-text">' + field.help + '</div>' : '') +
            '</div>'
        ).join('');
        wireFieldDependencies(config);
        config.fields.filter((f) => f.type === 'images').forEach((field) =>
            initPhotoManager('field-' + field.key, item ? item[field.key] : []));
    }

    function isFieldEnabled(field) {
        return !field.dependsOn || document.getElementById('field-' + field.dependsOn).checked;
    }

    /** Hides fields whose controlling checkbox is off (e.g. MPPT data on an inverter without a charger). */
    function wireFieldDependencies(config) {
        const update = () => config.fields.filter((f) => f.dependsOn).forEach((field) => {
            const enabled = isFieldEnabled(field);
            modalFieldsEl.querySelector('[data-field="' + field.key + '"]').classList.toggle('d-none', !enabled);
            document.getElementById('field-' + field.key).required = enabled && !!field.required;
        });
        new Set(config.fields.filter((f) => f.dependsOn).map((f) => f.dependsOn)).forEach((key) => {
            document.getElementById('field-' + key).addEventListener('change', update);
        });
        update();
    }

    function collectGenericFormData(config) {
        const data = {};
        config.fields.forEach((field) => {
            const input = document.getElementById('field-' + field.key);
            if (!isFieldEnabled(field)) {
                data[field.key] = null;
            } else if (field.type === 'images') {
                data[field.key] = photoManagerValue('field-' + field.key);
            } else if (field.type === 'checkboxes') {
                data[field.key] = Array.from(input.querySelectorAll('input:checked')).map((box) => Number(box.value));
            } else if (field.type === 'checkbox') {
                data[field.key] = input.checked;
            } else if (field.type === 'number' || field.numeric) {
                data[field.key] = input.value === '' ? null : Number(input.value);
            } else if (field.type === 'url') {
                data[field.key] = input.value.trim() === '' ? null : input.value.trim();
            } else {
                data[field.key] = input.value;
            }
        });
        return data;
    }

    async function openModal(id) {
        const config = ENTITY_CONFIGS[currentEntity];
        editingId = id || null;
        modalErrorEl.classList.add('d-none');
        modalDialogEl.classList.remove('modal-lg');

        if (id) {
            modalTitleEl.textContent = 'Editar producto';
            const response = await authFetch(config.endpoint + '/' + id);
            renderGenericForm(config, await response.json());
        } else {
            modalTitleEl.textContent = 'Nuevo producto';
            renderGenericForm(config, null);
        }
        if (currentEntity === 'inverters') {
            wireInverterPvEstimate();
        }

        modal.show();
    }

    // Mirrors Inverter.ESTIMATED_PV_TO_CHARGE_POWER_RATIO on the server.
    const PV_ESTIMATE_RATIO = 1.3;

    function wireInverterPvEstimate() {
        const pvInput = document.getElementById('field-maxPvInputPowerW');
        const voltageInput = document.getElementById('field-batteryVoltage');
        const chargeInput = document.getElementById('field-mpptMaxChargeCurrentA');
        const update = () => {
            const estimate = Number(voltageInput.value) * Number(chargeInput.value) * PV_ESTIMATE_RATIO;
            pvInput.placeholder = estimate > 0
                ? 'Vacío = se usa el estimado ~' + Math.round(estimate) + ' W (carga × voltaje × 1.3)'
                : 'Vacío = se estima con la corriente de carga × voltaje × 1.3';
        };
        voltageInput.addEventListener('change', update);
        chargeInput.addEventListener('input', update);
        update();
    }

    // ---- Kits (own logic: nested items referencing existing products) ----

    async function loadAllProducts() {
        const [panels, batteries, inverters, controllers, structures, cables] = await Promise.all([
            authFetch('/api/catalog/panels').then((r) => r.json()),
            authFetch('/api/catalog/batteries').then((r) => r.json()),
            authFetch('/api/catalog/inverters').then((r) => r.json()),
            authFetch('/api/catalog/charge-controllers').then((r) => r.json()),
            authFetch('/api/catalog/structures').then((r) => r.json()),
            authFetch('/api/catalog/cables').then((r) => r.json())
        ]);
        allProducts = panels.concat(batteries, inverters, controllers, structures, cables).map((p) => ({
            id: p.id,
            label: p.sku + ' — ' + p.name + ' ($' + p.price + ')'
        }));
    }

    function productOptionsHtml(selectedId) {
        return allProducts.map((p) =>
            '<option value="' + p.id + '"' + (p.id === selectedId ? ' selected' : '') + '>' + p.label + '</option>'
        ).join('');
    }

    function kitItemRowHtml(item) {
        const productId = item ? item.productId : (allProducts[0] && allProducts[0].id);
        const quantity = item ? item.quantity : 1;
        return '<div class="row g-2 mb-2 kit-item-row align-items-center">' +
            '<div class="col-7"><select class="form-select form-select-sm kit-item-product">' + productOptionsHtml(productId) + '</select></div>' +
            '<div class="col-3"><input type="number" class="form-control form-control-sm kit-item-qty" min="1" value="' + quantity + '"></div>' +
            '<div class="col-2"><button type="button" class="btn btn-sm btn-outline-danger remove-kit-item"><i class="bi bi-trash"></i></button></div>' +
            '</div>';
    }

    function wireKitItemRemoveButtons() {
        document.querySelectorAll('.remove-kit-item').forEach((btn) => {
            btn.onclick = () => {
                btn.closest('.kit-item-row').remove();
                scheduleCompatibilityCheck();
            };
        });
    }

    function collectKitItems() {
        return Array.from(document.querySelectorAll('.kit-item-row')).map((row) => ({
            productId: Number(row.querySelector('.kit-item-product').value),
            quantity: Number(row.querySelector('.kit-item-qty').value)
        }));
    }

    // ---- Kit compatibility (live, while editing) ----

    const SEVERITY_ICONS = {
        OK: 'bi-check-circle-fill text-success',
        WARNING: 'bi-exclamation-triangle-fill text-warning',
        ERROR: 'bi-x-circle-fill text-danger'
    };
    let compatibilityTimer = null;
    let compatibilityRequestId = 0;

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
    }

    function scheduleCompatibilityCheck() {
        clearTimeout(compatibilityTimer);
        compatibilityTimer = setTimeout(runCompatibilityCheck, 300);
    }

    async function runCompatibilityCheck() {
        const panel = document.getElementById('kit-compat-panel');
        if (!panel) {
            return;
        }
        const items = collectKitItems();
        if (items.length === 0 || items.some((i) => !i.productId || !(i.quantity > 0))) {
            panel.innerHTML = '';
            return;
        }

        const requestId = ++compatibilityRequestId;
        const response = await authFetch('/api/catalog/kits/validate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(items)
        });
        if (requestId !== compatibilityRequestId) {
            return;
        }
        panel.innerHTML = response.ok
            ? compatibilityHtml(await response.json())
            : '<p class="small text-muted mb-0">No se pudo revisar la compatibilidad.</p>';
    }

    function compatibilityHtml(report) {
        const summary = [
            report.solarCharger ? '<div><strong>Cargador solar:</strong> ' + escapeHtml(report.solarCharger) + '</div>' : '',
            report.batteryBank ? '<div><strong>Banco de baterías:</strong> ' + escapeHtml(report.batteryBank) + '</div>' : '',
            report.panelArray ? '<div><strong>Paneles:</strong> ' + escapeHtml(report.panelArray) + '</div>' : ''
        ].join('');

        const checks = report.checks.length
            ? '<ul class="list-unstyled small mb-0">' + report.checks.map((c) =>
                '<li class="mb-1"><i class="bi ' + SEVERITY_ICONS[c.severity] + ' me-1"></i>' + escapeHtml(c.message) + '</li>'
            ).join('') + '</ul>'
            : '<p class="small text-muted mb-0">Agrega inversor, baterías y paneles para revisar compatibilidad.</p>';

        const wiring = report.wiring.length
            ? '<table class="table table-sm small mt-3 mb-0"><thead><tr><th>Tramo</th><th title="A la potencia nominal del equipo; el arranque (pico) dura segundos y no define el cable">Corriente máx. continua</th><th>Calibre</th><th>Caída</th><th>Fusible</th></tr></thead><tbody>' +
              report.wiring.map((w) =>
                  '<tr><td>' + escapeHtml(w.segment) +
                  (w.connector ? '<div class="text-secondary">Cable con conector ' + escapeHtml(w.connector) + '</div>' : '') + '</td>' +
                  '<td>' + w.operatingCurrentA + ' A' +
                  (w.currentNote ? '<div class="text-secondary">' + escapeHtml(w.currentNote) + '</div>' : '') + '</td>' +
                  '<td>' + (w.gaugeLabel ? escapeHtml(w.gaugeLabel) : '—') + ' · ' + w.lengthMeters + ' m</td>' +
                  '<td>' + (w.gaugeLabel ? w.voltageDropPercent + ' %' : '—') + '</td>' +
                  '<td>' + (w.fuseA ? w.fuseA + ' A' : '—') + '</td></tr>'
              ).join('') + '</tbody></table>'
            : '';

        return '<div class="border rounded p-3 bg-body-tertiary">' +
            '<h6 class="fw-semibold mb-2"><i class="bi bi-plug"></i> Revisión de compatibilidad</h6>' +
            (summary ? '<div class="small mb-2">' + summary + '</div>' : '') +
            checks + expansionHtml(report.expansion) + wiring +
            '</div>';
    }

    const EXPANSION_BADGE_CLASSES = { EXPANDABLE: 'bg-success', LIMITED: 'bg-warning text-dark', MAXED: 'bg-secondary' };

    function expansionBadgeHtml(expansion) {
        return expansion
            ? '<span class="badge ' + EXPANSION_BADGE_CLASSES[expansion.level] + '">' + escapeHtml(expansion.label) + '</span>'
            : '<span class="text-muted small">—</span>';
    }

    function expansionHtml(expansion) {
        if (!expansion) {
            return '<p class="small text-muted mt-2 mb-0"><strong>Ampliación:</strong> no se puede calcular (revisa los avisos de arriba).</p>';
        }
        const line = (name, aspect) => aspect
            ? '<li><strong>' + name + ':</strong> ' + escapeHtml(aspect.label) + ' — ' + escapeHtml(aspect.detail) + '</li>'
            : '';
        return '<div class="small mt-3"><strong>Ampliación</strong> ' + expansionBadgeHtml(expansion) +
            '<ul class="list-unstyled mt-1 mb-0">' +
            line('Paneles', expansion.panels) +
            line('Baterías', expansion.batteries) +
            '<li><strong>Potencia:</strong> fija en ' + expansion.inverterKw + ' kW (un solo inversor por kit).</li>' +
            '</ul></div>';
    }

    async function loadKits() {
        currentEntity = 'kits';
        showEntityTable();
        listAlert.classList.add('d-none');
        entityTitle.textContent = 'Kits';
        tableHead.innerHTML = '<tr><th></th><th>Nombre</th><th>Costo / Venta / Ganancia</th><th># Ítems</th><th>Ampliación</th><th>Activo</th><th></th></tr>';
        const seq = startListLoad();

        try {
            await loadAllProducts();
            const [kits, compatibilityById] = await Promise.all([
                authFetch('/api/catalog/kits').then((r) => r.json()),
                authFetch('/api/catalog/kits/compatibility').then((r) => r.json())
            ]);
            if (isStale(seq)) {
                return;
            }

            tableBody.innerHTML = kits.length === 0 ? tableMessageRow('Aún no hay kits. Usa <strong>Nuevo</strong> para armar uno.') : kits.map((kit) => {
                const margin = (kit.sellPrice - kit.computedPrice).toFixed(2);
                const priceCell = 'Costo: $' + kit.computedPrice + ' · Venta: $' + kit.sellPrice + ' · Ganancia: $' + margin;
                const report = compatibilityById[kit.id];
                // Same picture the shop shows: the kit's own photo, else the first collage photo.
                const cover = kit.imageUrl || (kit.collage && kit.collage[0] && kit.collage[0].imageUrl);
                return '<tr><td>' + formatValue({ cover: cover }, { key: 'cover', type: 'thumb' }) + '</td><td>' + escapeHtml(kit.name) + '</td><td>' + priceCell + '</td><td>' + kit.items.length + '</td>' +
                    '<td>' + expansionBadgeHtml(report && report.expansion) + '</td>' +
                    '<td>' + (kit.active ? 'Sí' : 'No') + '</td>' + actionButtonsHtml(kit.id) + '</tr>';
            }).join('');

            wireRowActions((id) => openKitModal(id), (id) => deleteItem(id));
        } catch (err) {
            if (isStale(seq)) {
                return;
            }
            tableBody.innerHTML = '';
            listAlert.textContent = 'No se pudo cargar la lista de kits.';
            listAlert.classList.remove('d-none');
        }
    }

    async function openKitModal(id) {
        editingId = id || null;
        modalErrorEl.classList.add('d-none');
        await loadAllProducts();

        let kit = null;
        if (id) {
            const response = await authFetch('/api/catalog/kits/' + id);
            kit = await response.json();
        }

        modalTitleEl.textContent = id ? 'Editar kit' : 'Nuevo kit';

        const itemsHtml = kit && kit.items.length
            ? kit.items.map((item) => kitItemRowHtml(item)).join('')
            : kitItemRowHtml(null);

        modalFieldsEl.innerHTML =
            '<div class="mb-3"><label class="form-label">Nombre</label>' +
            '<input type="text" class="form-control" id="kit-name" value="' + (kit ? kit.name : '') + '" required></div>' +
            '<div class="mb-3"><label class="form-label">Descripción</label>' +
            '<textarea class="form-control" id="kit-description">' + (kit && kit.description ? kit.description : '') + '</textarea></div>' +
            '<div class="mb-3"><label class="form-label">Precio manual (opcional; si se deja vacío se calcula sumando sus productos)</label>' +
            '<input type="number" step="0.01" class="form-control" id="kit-price-override" value="' + (kit && kit.priceOverride != null ? kit.priceOverride : '') + '"></div>' +
            '<div class="mb-3"><label class="form-label">Fotos del kit (opcional; sin fotos se arma un collage con las de sus productos)</label>' +
            photoManagerHtml('kit-photos', 'kits') + '</div>' +
            '<div class="form-check mb-3"><input type="checkbox" class="form-check-input" id="kit-active"' + (!kit || kit.active ? ' checked' : '') + '>' +
            '<label class="form-check-label" for="kit-active">Activo</label></div>' +
            '<label class="form-label">Productos incluidos</label>' +
            '<div id="kit-items-container">' + itemsHtml + '</div>' +
            '<button type="button" class="btn btn-sm btn-outline-success mt-2" id="add-kit-item-btn">' +
            '<i class="bi bi-plus-lg"></i> Agregar producto</button>' +
            '<div id="kit-compat-panel" class="mt-3"></div>';

        initPhotoManager('kit-photos', kit ? kit.imageUrls : []);
        const itemsContainer = document.getElementById('kit-items-container');
        itemsContainer.addEventListener('change', scheduleCompatibilityCheck);
        itemsContainer.addEventListener('input', scheduleCompatibilityCheck);
        document.getElementById('add-kit-item-btn').addEventListener('click', () => {
            itemsContainer.insertAdjacentHTML('beforeend', kitItemRowHtml(null));
            wireKitItemRemoveButtons();
            scheduleCompatibilityCheck();
        });
        wireKitItemRemoveButtons();

        modalDialogEl.classList.add('modal-lg');
        modal.show();
        runCompatibilityCheck();
    }

    function collectKitFormData() {
        const items = collectKitItems();
        const priceOverrideInput = document.getElementById('kit-price-override').value;

        return {
            name: document.getElementById('kit-name').value,
            description: document.getElementById('kit-description').value,
            active: document.getElementById('kit-active').checked,
            priceOverride: priceOverrideInput === '' ? null : Number(priceOverrideInput),
            imageUrls: photoManagerValue('kit-photos'),
            items: items
        };
    }

    // ---- Settings (exchange rate) ----

    async function loadSettings() {
        currentEntity = 'settings';
        listLoadSeq += 1;
        listAlert.classList.add('d-none');
        entityTitle.textContent = 'Configuración';
        showSettingsPanel();
        settingsError.classList.add('d-none');
        settingsSuccess.classList.add('d-none');
        marginError.classList.add('d-none');
        marginSuccess.classList.add('d-none');

        try {
            const response = await authFetch('/api/settings/exchange-rate');
            const data = await response.json();
            document.getElementById('exchange-rate-input').value = data.usdToMxnRate;
        } catch (err) {
            settingsError.textContent = 'No se pudo cargar el tipo de cambio.';
            settingsError.classList.remove('d-none');
        }

        try {
            const response = await authFetch('/api/settings/profit-margin');
            const data = await response.json();
            document.getElementById('margin-input').value = data.marginPercent;
        } catch (err) {
            marginError.textContent = 'No se pudo cargar el margen de ganancia.';
            marginError.classList.remove('d-none');
        }

        storeError.classList.add('d-none');
        storeSuccess.classList.add('d-none');
        try {
            const response = await authFetch('/api/settings/store');
            const data = await response.json();
            document.getElementById('store-whatsapp-input').value = data.whatsappNumber || '';
            document.getElementById('store-email-input').value = data.notificationEmail || '';
            document.getElementById('store-instructions-input').value = data.paymentInstructions || '';
        } catch (err) {
            storeError.textContent = 'No se pudo cargar la configuración de pedidos.';
            storeError.classList.remove('d-none');
        }

        wiringError.classList.add('d-none');
        wiringSuccess.classList.add('d-none');
        try {
            const response = await authFetch('/api/settings/wiring');
            const data = await response.json();
            document.getElementById('wiring-battery-input').value = data.batteryToInverterMeters;
            document.getElementById('wiring-pv-input').value = data.pvToInverterMeters;
            document.getElementById('wiring-controller-input').value = data.controllerToBatteryMeters;
        } catch (err) {
            wiringError.textContent = 'No se pudieron cargar las longitudes de cableado.';
            wiringError.classList.remove('d-none');
        }
    }

    document.getElementById('store-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        storeError.classList.add('d-none');
        storeSuccess.classList.add('d-none');

        const response = await authFetch('/api/settings/store', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                whatsappNumber: document.getElementById('store-whatsapp-input').value.trim() || null,
                notificationEmail: document.getElementById('store-email-input').value.trim() || null,
                paymentInstructions: document.getElementById('store-instructions-input').value
            })
        });

        if (response.ok) {
            const data = await response.json();
            document.getElementById('store-whatsapp-input').value = data.whatsappNumber || '';
            storeSuccess.textContent = 'Configuración de pedidos actualizada.';
            storeSuccess.classList.remove('d-none');
        } else {
            storeError.textContent = await errorMessage(response, 'No se pudo guardar la configuración de pedidos.');
            storeError.classList.remove('d-none');
        }
    });

    document.getElementById('wiring-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        wiringError.classList.add('d-none');
        wiringSuccess.classList.add('d-none');

        const response = await authFetch('/api/settings/wiring', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                batteryToInverterMeters: Number(document.getElementById('wiring-battery-input').value),
                pvToInverterMeters: Number(document.getElementById('wiring-pv-input').value),
                controllerToBatteryMeters: Number(document.getElementById('wiring-controller-input').value)
            })
        });

        if (response.ok) {
            wiringSuccess.textContent = 'Longitudes de cableado actualizadas.';
            wiringSuccess.classList.remove('d-none');
        } else {
            wiringError.textContent = await errorMessage(response, 'No se pudieron guardar las longitudes.');
            wiringError.classList.remove('d-none');
        }
    });

    document.getElementById('settings-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        settingsError.classList.add('d-none');
        settingsSuccess.classList.add('d-none');

        const rate = Number(document.getElementById('exchange-rate-input').value);
        const response = await authFetch('/api/settings/exchange-rate', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ usdToMxnRate: rate })
        });

        if (response.ok) {
            settingsSuccess.textContent = 'Tipo de cambio actualizado.';
            settingsSuccess.classList.remove('d-none');
        } else {
            settingsError.textContent = await errorMessage(response, 'No se pudo guardar el tipo de cambio.');
            settingsError.classList.remove('d-none');
        }
    });

    document.getElementById('margin-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        marginError.classList.add('d-none');
        marginSuccess.classList.add('d-none');

        const marginPercent = Number(document.getElementById('margin-input').value);
        const response = await authFetch('/api/settings/profit-margin', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ marginPercent: marginPercent })
        });

        if (response.ok) {
            marginSuccess.textContent = 'Margen de ganancia actualizado.';
            marginSuccess.classList.remove('d-none');
        } else {
            marginError.textContent = await errorMessage(response, 'No se pudo guardar el margen de ganancia.');
            marginError.classList.remove('d-none');
        }
    });

    // ---- Orders ----

    const ORDER_STATUS_BADGES = {
        PENDING: 'bg-warning text-dark',
        PAID: 'bg-info text-dark',
        DELIVERED: 'bg-success',
        CANCELLED: 'bg-secondary'
    };
    const mxnFormatter = new Intl.NumberFormat('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    let ordersStatusFilter = '';

    function money(value) {
        return '$' + mxnFormatter.format(Number(value)) + ' MXN';
    }

    function dateTime(value) {
        return value ? new Date(value).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' }) : '';
    }

    function statusBadge(order) {
        return '<span class="badge ' + ORDER_STATUS_BADGES[order.status] + '">' + escapeHtml(order.statusLabel) + '</span>';
    }

    function fullAddress(order) {
        return order.street + ', Col. ' + order.neighborhood + ', C.P. ' + order.postalCode + ', ' + order.city;
    }

    async function refreshPendingBadge() {
        try {
            const pending = await authFetch('/api/admin/orders?status=PENDING').then((r) => r.json());
            pendingOrdersBadge.textContent = pending.length;
            pendingOrdersBadge.classList.toggle('d-none', pending.length === 0);
        } catch (err) {
            // badge is a convenience; ignore failures
        }
    }

    async function loadOrders() {
        currentEntity = 'orders';
        showOrdersTable();
        listAlert.classList.add('d-none');
        entityTitle.textContent = 'Pedidos';
        ordersFilter.querySelectorAll('button').forEach((b) => b.classList.toggle('active', b.dataset.status === ordersStatusFilter));
        tableHead.innerHTML = '<tr><th>Folio</th><th>Fecha</th><th>Cliente</th><th>Kits</th><th class="text-end">Total</th><th>Estado</th><th></th></tr>';
        authFetch('/api/settings/store')
            .then((r) => r.json())
            .then((store) => ordersWhatsappWarning.classList.toggle('d-none', Boolean(store.whatsappNumber)))
            .catch(() => ordersWhatsappWarning.classList.add('d-none'));

        const seq = startListLoad();
        try {
            const url = '/api/admin/orders' + (ordersStatusFilter ? '?status=' + ordersStatusFilter : '');
            const orders = await authFetch(url).then((r) => r.json());
            if (isStale(seq)) {
                return;
            }
            tableBody.innerHTML = orders.length
                ? orders.map((order) =>
                    '<tr><td class="fw-semibold">' + escapeHtml(order.folio) + '</td>' +
                    '<td class="small">' + dateTime(order.createdAt) + '</td>' +
                    '<td>' + escapeHtml(order.customerName) + accountBadge(order) + '<div class="small text-muted">' + escapeHtml(order.customerPhone) + '</div></td>' +
                    '<td class="small">' + order.items.map((i) => i.quantity + ' × ' + escapeHtml(i.kitName)).join('<br>') + '</td>' +
                    '<td class="text-end">' + money(order.totalMxn) + '</td>' +
                    '<td>' + statusBadge(order) + '</td>' +
                    '<td class="text-end"><button type="button" class="btn btn-sm btn-outline-secondary order-open-btn" data-id="' + order.id + '">Ver</button></td></tr>'
                ).join('')
                : '<tr><td colspan="7" class="text-center text-muted py-4">No hay pedidos' + (ordersStatusFilter ? ' con este estado' : '') + '.</td></tr>';
            tableBody.querySelectorAll('.order-open-btn').forEach((btn) => {
                btn.addEventListener('click', () => openOrderModal(Number(btn.dataset.id)));
            });
        } catch (err) {
            if (isStale(seq)) {
                return;
            }
            tableBody.innerHTML = '';
            listAlert.textContent = 'No se pudo cargar la lista de pedidos.';
            listAlert.classList.remove('d-none');
        }
        refreshPendingBadge();
    }

    document.getElementById('orders-whatsapp-settings-link').addEventListener('click', (event) => {
        event.preventDefault();
        document.querySelector('.entity-tab[data-entity="settings"]').click();
    });

    ordersFilter.querySelectorAll('button').forEach((btn) => {
        btn.addEventListener('click', () => {
            ordersStatusFilter = btn.dataset.status;
            loadOrders();
        });
    });

    // wa.me opens WhatsApp with this text prefilled; the admin reviews it and presses send.
    function whatsappToCustomerUrl(order) {
        const greeting = 'Hola ' + order.customerName.split(' ')[0] + ', ';
        const messages = {
            PENDING: greeting + 'recibimos tu pedido ' + order.folio + ' por ' + money(order.totalMxn) +
                '. ¿Cuándo te queda bien para coordinar el pago en efectivo y la entrega en ' + fullAddress(order) + '?',
            PAID: greeting + 'confirmamos tu pago del pedido ' + order.folio + '. Te avisamos para coordinar la entrega.',
            DELIVERED: greeting + 'gracias por tu compra (pedido ' + order.folio + '). Cualquier duda con tu sistema, aquí estamos.',
            CANCELLED: greeting + 'tu pedido ' + order.folio + ' fue cancelado. Si tienes dudas, con gusto te ayudamos.'
        };
        return 'https://wa.me/52' + order.customerPhone + '?text=' + encodeURIComponent(messages[order.status]);
    }

    function orderTimelineHtml(order) {
        const lines = ['<li>Recibido: ' + dateTime(order.createdAt) + '</li>'];
        if (order.paidAt) {
            lines.push('<li>Pagado: ' + dateTime(order.paidAt) + ' · ' + money(order.paidAmountMxn) + ' · ' + escapeHtml(order.paidBy) +
                (order.paymentNote ? ' · ' + escapeHtml(order.paymentNote) : '') + '</li>');
        }
        if (order.deliveredAt) {
            lines.push('<li>Entregado: ' + dateTime(order.deliveredAt) + ' · ' + escapeHtml(order.deliveredBy) + '</li>');
        }
        if (order.cancelledAt) {
            lines.push('<li>Cancelado: ' + dateTime(order.cancelledAt) + ' · ' + escapeHtml(order.cancelledBy) + ' · ' + escapeHtml(order.cancelReason) + '</li>');
        }
        return '<ul class="small mb-0">' + lines.join('') + '</ul>';
    }

    function orderActionsHtml(order) {
        if (order.status === 'PENDING') {
            return '<div class="border rounded p-3 bg-body-tertiary">' +
                '<h6 class="fw-semibold">Registrar pago en efectivo</h6>' +
                '<div class="row g-2 align-items-end">' +
                '<div class="col-md-4"><label class="form-label small" for="order-amount">Monto recibido (MXN)</label>' +
                '<input type="number" step="0.01" min="0.01" class="form-control form-control-sm" id="order-amount" value="' + order.totalMxn + '"></div>' +
                '<div class="col-md-8"><label class="form-label small" for="order-payment-note">Nota (opcional)</label>' +
                '<input type="text" class="form-control form-control-sm" id="order-payment-note" maxlength="500" placeholder="Ej. pagó al instalar"></div>' +
                '</div>' +
                '<div class="d-flex gap-2 mt-2">' +
                '<button type="button" class="btn btn-success btn-sm" data-order-action="pay">Confirmar pago</button>' +
                '<button type="button" class="btn btn-outline-success btn-sm" data-order-action="pay-and-deliver">Cobrar y entregar</button>' +
                '</div></div>' + cancelHtml();
        }
        if (order.status === 'PAID') {
            return '<button type="button" class="btn btn-success btn-sm" data-order-action="deliver">Marcar entregado</button>' + cancelHtml();
        }
        return '';
    }

    function cancelHtml() {
        return '<div class="border rounded p-3 mt-3">' +
            '<label class="form-label small" for="order-cancel-reason">Motivo de cancelación</label>' +
            '<div class="input-group input-group-sm">' +
            '<input type="text" class="form-control" id="order-cancel-reason" maxlength="500">' +
            '<button type="button" class="btn btn-outline-danger" data-order-action="cancel">Cancelar pedido</button>' +
            '</div></div>';
    }

    /** Marks orders placed from a customer account (vs. guest checkout). */
    function accountBadge(order) {
        return order.registeredCustomer
            ? ' <span class="badge text-bg-info" title="Pedido hecho con cuenta de cliente"><i class="bi bi-person-check"></i> Cuenta</span>'
            : '';
    }

    function renderOrder(order) {
        orderModalTitle.innerHTML = 'Pedido ' + escapeHtml(order.folio) + ' ' + statusBadge(order);
        orderModalBody.innerHTML =
            '<div class="row g-3 mb-3">' +
            '<div class="col-md-6"><h6 class="fw-semibold mb-1">Cliente</h6>' +
            '<div>' + escapeHtml(order.customerName) + accountBadge(order) + '</div>' +
            '<div class="small">' + escapeHtml(order.customerPhone) + (order.customerEmail ? ' · ' + escapeHtml(order.customerEmail) : '') + '</div>' +
            '<a class="btn btn-success btn-sm mt-2" target="_blank" rel="noopener noreferrer" href="' + escapeAttr(whatsappToCustomerUrl(order)) + '">' +
            '<i class="bi bi-whatsapp"></i> Escribir por WhatsApp</a></div>' +
            '<div class="col-md-6"><h6 class="fw-semibold mb-1">Entrega</h6>' +
            '<div class="small">' + escapeHtml(fullAddress(order)) + '</div>' +
            (order.addressReferences ? '<div class="small text-muted">Ref.: ' + escapeHtml(order.addressReferences) + '</div>' : '') +
            (order.notes ? '<div class="small mt-1"><strong>Notas:</strong> ' + escapeHtml(order.notes) + '</div>' : '') +
            '<a class="small d-inline-block mt-2" target="_blank" rel="noopener noreferrer" href="/pedido/' + encodeURIComponent(order.folio) + '">Ver página del cliente</a>' +
            '</div></div>' +
            '<table class="table table-sm small"><thead><tr><th>Kit</th><th class="text-center">Cant.</th><th class="text-end">Precio</th><th class="text-end">Subtotal</th></tr></thead><tbody>' +
            order.items.map((i) => '<tr><td>' + escapeHtml(i.kitName) + '</td><td class="text-center">' + i.quantity + '</td>' +
                '<td class="text-end">' + money(i.unitPriceMxn) + '</td><td class="text-end">' + money(i.lineTotalMxn) + '</td></tr>').join('') +
            '</tbody><tfoot><tr><th colspan="3" class="text-end">Total</th><th class="text-end">' + money(order.totalMxn) + '</th></tr></tfoot></table>' +
            '<div class="mb-3">' + orderTimelineHtml(order) + '</div>' +
            orderActionsHtml(order);

        orderModalBody.querySelectorAll('[data-order-action]').forEach((btn) => {
            btn.addEventListener('click', () => runOrderAction(order, btn.dataset.orderAction));
        });
    }

    async function openOrderModal(id) {
        orderModalError.classList.add('d-none');
        const response = await authFetch('/api/admin/orders/' + id);
        renderOrder(await response.json());
        orderModal.show();
    }

    async function runOrderAction(order, action) {
        orderModalError.classList.add('d-none');
        let body = null;
        if (action === 'pay' || action === 'pay-and-deliver') {
            body = {
                amountMxn: Number(document.getElementById('order-amount').value),
                note: document.getElementById('order-payment-note').value.trim() || null
            };
        } else if (action === 'cancel') {
            const reason = document.getElementById('order-cancel-reason').value.trim();
            if (!reason) {
                orderModalError.textContent = 'Indica el motivo de la cancelación.';
                orderModalError.classList.remove('d-none');
                return;
            }
            if (!window.confirm('¿Cancelar el pedido ' + order.folio + '?')) {
                return;
            }
            body = { reason: reason };
        }

        const response = await authFetch('/api/admin/orders/' + order.id + '/' + action, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: body ? JSON.stringify(body) : undefined
        });
        if (!response.ok) {
            orderModalError.textContent = await errorMessage(response, 'No se pudo actualizar el pedido.');
            orderModalError.classList.remove('d-none');
            return;
        }
        renderOrder(await response.json());
        loadOrders();
    }

    // ---- Catalog export / import (Configuración) ----

    const catalogAlert = document.getElementById('catalog-alert');
    const catalogFileInput = document.getElementById('catalog-file-input');
    const catalogReviewBtn = document.getElementById('catalog-review-btn');
    const catalogImportBtn = document.getElementById('catalog-import-btn');
    const catalogImportLabel = document.getElementById('catalog-import-label');
    const catalogReview = document.getElementById('catalog-review');
    const catalogSummary = document.getElementById('catalog-summary');
    const catalogReviewBody = document.getElementById('catalog-review-body');
    let catalogFileText = null;

    const CATALOG_ACTIONS = {
        CREATE: { label: 'Nuevo', badge: 'bg-success' },
        UPDATE: { label: 'Se actualiza', badge: 'bg-primary' },
        UNCHANGED: { label: 'Sin cambios', badge: 'bg-secondary' },
        ERROR: { label: 'Error', badge: 'bg-danger' }
    };

    // Same shape as an exported file; the field guide is CATALOGO-JSON.md in the project.
    const CATALOG_EXAMPLE = {
        format: 'baja-solar-catalog',
        version: 1,
        products: [
            {
                type: 'SOLAR_PANEL', sku: 'PNL-550-MONO', name: 'Panel solar 550 W monocristalino', brand: 'Marca',
                description: 'Panel de 144 celdas.', price: 2450.00, stockQuantity: 10,
                imageUrls: ['https://ejemplo.com/fotos/panel-550.jpg'], referenceUrl: 'https://ejemplo.com/panel-550',
                active: true, wattage: 550, technology: 'MONOCRYSTALLINE', efficiencyPercent: 21.3, voc: 49.6, isc: 14.0,
                connector: 'MC4'
            },
            {
                type: 'BATTERY', sku: 'BAT-24V-100AH', name: 'Batería LiFePO4 24 V 100 Ah', price: 8900.00, stockQuantity: 4,
                capacityAh: 100, voltage: 24, chemistry: 'LIFEPO4', cycleLife: 6000
            },
            {
                type: 'INVERTER', sku: 'INV-HIB-3KW', name: 'Inversor híbrido 3 kW 24 V', price: 9800.00, stockQuantity: 2,
                ratedPowerKw: 3, surgePowerKw: 6, inverterType: 'HYBRID', builtInCharger: true, mpptChannels: 1,
                maxInputVoltage: 500, batteryVoltage: 24, mpptMinVoltage: 60, mpptMaxChargeCurrentA: 100,
                mpptMaxInputCurrentA: 22, maxPvInputPowerW: 4000
            }
        ],
        kits: [
            {
                name: 'Kit Cabaña 3 kW', description: 'Para una cabaña con refrigerador, luces y TV.', active: true,
                items: [
                    { sku: 'PNL-550-MONO', quantity: 4 },
                    { sku: 'BAT-24V-100AH', quantity: 1 },
                    { sku: 'INV-HIB-3KW', quantity: 1 }
                ]
            }
        ]
    };

    function showCatalogAlert(kind, html) {
        catalogAlert.className = 'alert alert-' + kind;
        catalogAlert.innerHTML = html;
    }

    function downloadFile(filename, blob) {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }

    document.getElementById('catalog-export-btn').addEventListener('click', async (event) => {
        const button = event.currentTarget;
        button.disabled = true;
        catalogAlert.classList.add('d-none');
        try {
            const response = await authFetch('/api/admin/catalog/export');
            if (!response.ok) {
                showCatalogAlert('danger', escapeHtml(await errorMessage(response, 'No se pudo descargar el catálogo.')));
                return;
            }
            const match = /filename="([^"]+)"/.exec(response.headers.get('Content-Disposition') || '');
            downloadFile(match ? match[1] : 'catalogo.json', await response.blob());
        } catch (err) {
            showCatalogAlert('danger', 'No se pudo descargar el catálogo.');
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('catalog-example-btn').addEventListener('click', () => {
        downloadFile('catalogo-ejemplo.json',
            new Blob([JSON.stringify(CATALOG_EXAMPLE, null, 2)], { type: 'application/json' }));
    });

    function resetCatalogReview() {
        catalogFileText = null;
        catalogAlert.classList.add('d-none');
        catalogReview.classList.add('d-none');
        catalogImportBtn.classList.add('d-none');
        catalogReviewBody.innerHTML = '';
    }

    catalogFileInput.addEventListener('change', () => {
        resetCatalogReview();
        catalogReviewBtn.disabled = catalogFileInput.files.length === 0;
    });

    async function sendCatalog(dryRun) {
        const response = await authFetch('/api/admin/catalog/import?dryRun=' + dryRun, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: catalogFileText
        });
        if (!response.ok) {
            showCatalogAlert('danger', escapeHtml(await errorMessage(response, 'No se pudo leer el archivo.')));
            return null;
        }
        return response.json();
    }

    function countsText(summary, noun) {
        const parts = [];
        if (summary.created) parts.push(summary.created + ' nuevo' + (summary.created === 1 ? '' : 's'));
        if (summary.updated) parts.push(summary.updated + ' se actualiza' + (summary.updated === 1 ? '' : 'n'));
        if (summary.unchanged) parts.push(summary.unchanged + ' sin cambios');
        if (summary.errors) parts.push(summary.errors + ' con error');
        return '<strong>' + noun + ':</strong> ' + (parts.length ? parts.join(' · ') : 'ninguno');
    }

    function renderCatalogResult(result) {
        const changes = result.products.created + result.products.updated + result.kits.created + result.kits.updated;
        catalogSummary.innerHTML = '<div class="small">' + countsText(result.products, 'Productos') + '<br>' +
            countsText(result.kits, 'Kits') + '</div>';

        catalogReviewBody.innerHTML = result.rows.map((row) => {
            const action = CATALOG_ACTIONS[row.action] || CATALOG_ACTIONS.ERROR;
            const errors = row.errors.length
                ? '<ul class="mb-0 ps-3 text-danger">' + row.errors.map((e) => '<li>' + escapeHtml(e) + '</li>').join('') + '</ul>'
                : '';
            return '<tr' + (row.action === 'ERROR' ? ' class="table-danger"' : '') + '>' +
                '<td class="text-secondary">' + row.position + '</td>' +
                '<td class="text-nowrap">' + escapeHtml(row.typeLabel || '—') + '</td>' +
                '<td><code>' + escapeHtml(row.key || '—') + '</code></td>' +
                '<td>' + escapeHtml(row.name || '') + errors + '</td>' +
                '<td><span class="badge ' + action.badge + '">' + action.label + '</span></td>' +
                '</tr>';
        }).join('');
        catalogReview.classList.toggle('d-none', result.rows.length === 0);

        const fileErrors = result.fileErrors.length
            ? '<ul class="mb-0 ps-3">' + result.fileErrors.map((e) => '<li>' + escapeHtml(e) + '</li>').join('') + '</ul>'
            : '';
        if (result.saved) {
            showCatalogAlert('success', '<i class="bi bi-check-circle"></i> Listo, se guardaron ' + changes + ' cambio' +
                (changes === 1 ? '' : 's') + '.');
        } else if (result.errorCount > 0) {
            showCatalogAlert('warning', '<strong>No se guardó nada.</strong> Corrige ' +
                (result.errorCount === 1 ? 'el error marcado' : 'los ' + result.errorCount + ' errores marcados') +
                ' en el archivo y vuelve a revisarlo.' + fileErrors);
        } else if (changes === 0) {
            showCatalogAlert('info', 'El archivo no trae cambios: todo está igual que en la tienda.');
        } else {
            showCatalogAlert('info', 'Revisa el resumen. Aún no se ha guardado nada.');
        }

        const canImport = result.dryRun && result.errorCount === 0 && changes > 0;
        catalogImportBtn.classList.toggle('d-none', !canImport);
        catalogImportLabel.textContent = 'Importar ' + changes + ' cambio' + (changes === 1 ? '' : 's');
    }

    catalogReviewBtn.addEventListener('click', async () => {
        const file = catalogFileInput.files[0];
        if (!file) {
            return;
        }
        resetCatalogReview();
        catalogReviewBtn.disabled = true;
        try {
            catalogFileText = await file.text();
            const result = await sendCatalog(true);
            if (result) {
                renderCatalogResult(result);
            }
        } catch (err) {
            showCatalogAlert('danger', 'No se pudo revisar el archivo.');
        } finally {
            catalogReviewBtn.disabled = false;
        }
    });

    catalogImportBtn.addEventListener('click', async () => {
        if (!catalogFileText) {
            return;
        }
        catalogImportBtn.disabled = true;
        catalogReviewBtn.disabled = true;
        try {
            const result = await sendCatalog(false);
            if (result) {
                renderCatalogResult(result);
                if (result.saved) {
                    allProducts = [];
                    catalogFileInput.value = '';
                    catalogFileText = null;
                }
            }
        } catch (err) {
            showCatalogAlert('danger', 'Ocurrió un error de conexión. Revisa el archivo de nuevo antes de importar.');
        } finally {
            catalogImportBtn.disabled = false;
            catalogReviewBtn.disabled = catalogFileInput.files.length === 0;
        }
    });

    // ---- Shared: delete + create/update submit ----

    async function deleteItem(id) {
        if (!window.confirm('¿Eliminar este producto? Esta acción no se puede deshacer.')) {
            return;
        }
        const response = await authFetch(currentEndpoint() + '/' + id, { method: 'DELETE' });
        if (response.ok) {
            currentEntity === 'kits' ? loadKits() : loadEntity(currentEntity);
        } else {
            window.alert('No se pudo eliminar el producto.');
        }
    }

    modalFormEl.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (photoUploadInProgress()) {
            modalErrorEl.textContent = 'Espera a que terminen de subir las fotos.';
            modalErrorEl.classList.remove('d-none');
            return;
        }

        const data = currentEntity === 'kits' ? collectKitFormData() : collectGenericFormData(ENTITY_CONFIGS[currentEntity]);
        const url = editingId ? currentEndpoint() + '/' + editingId : currentEndpoint();
        const method = editingId ? 'PUT' : 'POST';

        const response = await authFetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });

        if (response.ok) {
            modal.hide();
            currentEntity === 'kits' ? loadKits() : loadEntity(currentEntity);
        } else {
            modalErrorEl.textContent = await errorMessage(response, 'No se pudo guardar el producto. Revisa los datos ingresados.');
            modalErrorEl.classList.remove('d-none');
        }
    });

    newBtn.addEventListener('click', () => {
        currentEntity === 'kits' ? openKitModal(null) : openModal(null);
    });

    document.querySelectorAll('.entity-tab').forEach((tab) => {
        tab.addEventListener('click', (event) => {
            event.preventDefault();
            document.querySelectorAll('.entity-tab').forEach((t) => t.classList.remove('active'));
            tab.classList.add('active');
            if (tab.dataset.entity === 'kits') {
                loadKits();
            } else if (tab.dataset.entity === 'orders') {
                loadOrders();
            } else if (tab.dataset.entity === 'settings') {
                loadSettings();
            } else {
                loadEntity(tab.dataset.entity);
            }
        });
    });

    loadKits();
    refreshPendingBadge();
})();
