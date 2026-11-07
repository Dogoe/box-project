(function () {
    'use strict';

    // Pieces shared by the admin panel (admin.js) and the kit editor page (kit-editor.js):
    // the admin session, small escaping helpers and the photo lists.
    const token = localStorage.getItem('adminToken');
    if (!token) {
        window.location.href = '/admin/login';
        return;
    }

    const logoutBtn = document.getElementById('logout-btn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            localStorage.removeItem('adminToken');
            window.location.href = '/admin/login';
        });
    }

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

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
    }

    // ---- Photo lists (uploaded to Cloudflare R2, or pasted links); the first photo is the main one ----

    const MAX_PHOTOS = 8;
    const photoLists = new Map();

    /** {@code options.compact}: small tiles, no help text (it goes in a tooltip), for tight forms. */
    function photoManagerHtml(id, folder, options) {
        const compact = Boolean(options && options.compact);
        const help = 'La primera es la principal. Máximo ' + MAX_PHOTOS + ' fotos (JPG, PNG o WebP).';
        return '<div class="photo-manager' + (compact ? ' photo-compact' : '') + '" id="' + id + '" data-folder="' + folder + '">' +
            '<div class="d-flex flex-wrap gap-2 photo-list"></div>' +
            '<div class="d-flex flex-wrap gap-2 mt-2 align-items-center">' +
            '<button type="button" class="btn btn-sm btn-outline-secondary photo-upload-btn"' + (compact ? ' title="' + help + '"' : '') + '><i class="bi bi-upload"></i> Subir fotos</button>' +
            '<div class="input-group input-group-sm" style="max-width: 380px;">' +
            // type="text": a half-typed link must not block the form's Guardar (we validate it ourselves).
            '<input type="text" inputmode="url" class="form-control photo-link-input" placeholder="' + (compact ? 'o pega un link' : 'o pega un link https://...') + '">' +
            '<button type="button" class="btn btn-outline-secondary photo-link-add">Agregar</button></div>' +
            '</div>' +
            '<input type="file" class="d-none photo-file-input" accept="image/jpeg,image/png,image/webp" multiple>' +
            (compact ? '' : '<div class="form-text">' + help + '</div>') +
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
            const compact = root.classList.contains('photo-compact');
            const tileWidth = compact ? 116 : 112;
            const imgBox = compact ? 'width: 108px; height: 48px;' : 'width: 100px; height: 80px;';
            listEl.innerHTML = photos.map((url, index) =>
                '<div class="photo-tile border rounded p-1 text-center bg-white" data-index="' + index + '" style="width: ' + tileWidth + 'px;">' +
                '<img src="' + escapeAttr(url) + '" alt="" style="' + imgBox + ' object-fit: contain;">' +
                (compact
                    ? ''
                    : index === 0 ? '<div class="small fw-semibold text-success">Principal</div>'
                        : '<div class="small"><a href="#" data-action="main">Hacer principal</a></div>') +
                '<div class="d-flex justify-content-center gap-1 mt-1">' +
                (compact ? (index === 0
                    ? '<span class="btn btn-light btn-sm py-0 px-1 border text-success disabled" title="Foto principal"><i class="bi bi-star-fill"></i></span>'
                    : tileButton('main', 'bi-star', 'Hacer principal')) : '') +
                (index > 0 ? tileButton('left', 'bi-arrow-left', 'Mover a la izquierda') : '') +
                (index < photos.length - 1 ? tileButton('right', 'bi-arrow-right', 'Mover a la derecha') : '') +
                tileButton('remove', 'bi-x-lg', 'Quitar foto') +
                '</div></div>'
            ).join('') + (pending
                ? '<div class="border rounded d-flex align-items-center justify-content-center text-muted small" style="width: ' + tileWidth + 'px; height: ' + (compact ? 80 : 132) + 'px;">' +
                  '<span class="spinner-border spinner-border-sm me-1"></span>' + pending + '</div>'
                : '');
            listEl.querySelectorAll('.photo-tile img').forEach((img) => {
                img.onerror = () => img.replaceWith(Object.assign(document.createElement('div'), {
                    className: 'small text-danger d-flex align-items-center justify-content-center',
                    style: compact ? 'width: 108px; height: 48px;' : 'width: 100px; height: 80px;',
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

    window.AdminCommon = {
        authFetch: authFetch,
        errorMessage: errorMessage,
        escapeHtml: escapeHtml,
        escapeAttr: escapeAttr,
        photoManagerHtml: photoManagerHtml,
        photoManagerValue: photoManagerValue,
        photoUploadInProgress: photoUploadInProgress,
        initPhotoManager: initPhotoManager,
        WIRE_GAUGES: WIRE_GAUGES,
        WIRE_GAUGE_LABELS: WIRE_GAUGE_LABELS
    };
})();
