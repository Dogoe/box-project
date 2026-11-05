(function () {
    'use strict';

    // Forgot-password and reset-password pages (each page has only one of the two forms).
    const account = window.BajaSolarAccount;

    function busy(button, label) {
        const idle = button.textContent;
        button.disabled = true;
        button.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>' + label;
        return () => {
            button.disabled = false;
            button.textContent = idle;
        };
    }

    function showAlert(el, message, kind) {
        el.className = 'alert alert-' + (kind || 'danger');
        el.textContent = message;
    }

    const forgotForm = document.getElementById('forgot-form');
    if (forgotForm) {
        forgotForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const alertEl = document.getElementById('forgot-alert');
            if (!forgotForm.checkValidity()) {
                forgotForm.classList.add('was-validated');
                showAlert(alertEl, 'Escribe un correo válido.');
                return;
            }
            const done = busy(document.getElementById('forgot-submit'), 'Enviando...');
            try {
                const result = await account.sendJson('POST', '/api/account/forgot-password',
                    { email: document.getElementById('forgot-email').value.trim() });
                if (result.ok) {
                    // Same answer whether or not the email has an account.
                    showAlert(alertEl, 'Si ese correo tiene cuenta, te llegará un link en unos minutos. Revisa también tu carpeta de spam.', 'success');
                    forgotForm.reset();
                } else {
                    showAlert(alertEl, (result.body && result.body.message) || 'No se pudo enviar el correo.');
                }
            } catch (err) {
                showAlert(alertEl, 'Ocurrió un error de conexión. Intenta nuevamente.');
            } finally {
                done();
            }
        });
    }

    const resetForm = document.getElementById('reset-form');
    if (resetForm) {
        resetForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const alertEl = document.getElementById('reset-alert');
            const password = document.getElementById('reset-password').value;
            if (!resetForm.checkValidity()) {
                resetForm.classList.add('was-validated');
                showAlert(alertEl, 'La contraseña debe tener al menos 8 caracteres.');
                return;
            }
            if (password !== document.getElementById('reset-confirm').value) {
                showAlert(alertEl, 'Las contraseñas no coinciden.');
                return;
            }
            const done = busy(document.getElementById('reset-submit'), 'Guardando...');
            try {
                const result = await account.sendJson('POST', '/api/account/reset-password',
                    { token: resetForm.dataset.token, password: password });
                if (!result.ok) {
                    showAlert(alertEl, (result.body && result.body.message) || 'No se pudo cambiar la contraseña.');
                    return;
                }
                account.setSession(result.body);
                window.location.href = '/cuenta';
            } catch (err) {
                showAlert(alertEl, 'Ocurrió un error de conexión. Intenta nuevamente.');
            } finally {
                done();
            }
        });
    }
})();
