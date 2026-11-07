(function () {
    'use strict';

    // Loads the kit's wiring diagram (admin only) with the admin token the panel keeps, and offers
    // printing / saving as PDF (the browser's print dialog) and downloading the drawing as SVG.
    const kitId = document.body.dataset.kitId;
    const root = document.getElementById('diagram-root');
    const printBtn = document.getElementById('diagram-print-btn');
    const svgBtn = document.getElementById('diagram-svg-btn');
    const title = document.getElementById('diagram-kit-name');
    const token = localStorage.getItem('adminToken');

    if (!token) {
        window.location.href = '/admin/login';
        return;
    }

    function showMessage(text) {
        root.innerHTML = '';
        const alert = document.createElement('div');
        alert.className = 'alert alert-danger mb-0';
        alert.textContent = text;
        root.appendChild(alert);
    }

    function slug(text) {
        return (text || 'kit').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')
            .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'kit';
    }

    async function load() {
        let response;
        try {
            response = await fetch('/api/admin/kits/' + encodeURIComponent(kitId) + '/diagram', {
                headers: { 'Authorization': 'Bearer ' + token }
            });
        } catch (err) {
            showMessage('No se pudo cargar el diagrama. Revisa tu conexión e intenta de nuevo.');
            return;
        }
        if (response.status === 401 || response.status === 403) {
            localStorage.removeItem('adminToken');
            window.location.href = '/admin/login';
            return;
        }
        if (response.status === 404) {
            showMessage('Ese kit no existe (quizá se borró).');
            return;
        }
        if (!response.ok) {
            showMessage('No se pudo generar el diagrama (error ' + response.status + ').');
            return;
        }
        // Server-rendered by Thymeleaf from the kit's own data (all values escaped there).
        root.innerHTML = await response.text();

        const svg = root.querySelector('#kit-diagram-svg');
        const heading = svg ? svg.querySelector('text') : null;
        const name = heading ? heading.textContent.replace(/^Diagrama de conexión · /, '') : '';
        if (name) {
            title.textContent = 'Diagrama de conexión · ' + name;
            document.title = 'Diagrama · ' + name + ' | Baja Solar Supply';
        }
        printBtn.disabled = false;
        svgBtn.disabled = !svg;
        svgBtn.onclick = () => downloadSvg(svg, name);
    }

    function downloadSvg(svg, name) {
        const source = new XMLSerializer().serializeToString(svg);
        const blob = new Blob(['<?xml version="1.0" encoding="UTF-8"?>\n', source], { type: 'image/svg+xml' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'diagrama-' + slug(name) + '.svg';
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    }

    printBtn.addEventListener('click', () => window.print());
    load();
})();
