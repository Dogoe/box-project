(function () {
    'use strict';

    if (localStorage.getItem('adminToken')) {
        window.location.href = '/admin';
        return;
    }

    const form = document.getElementById('admin-login-form');
    const alertBox = document.getElementById('login-alert');

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        alertBox.classList.add('d-none');

        const email = document.getElementById('email').value;
        const password = document.getElementById('password').value;

        try {
            const response = await fetch('/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: email, password: password })
            });

            const body = await response.json().catch(() => ({}));
            if (response.status === 429) {
                // Too many attempts: the server says how long to wait.
                alertBox.textContent = body.message || 'Demasiados intentos. Intenta más tarde.';
                alertBox.classList.remove('d-none');
                return;
            }
            if (!response.ok) {
                throw new Error('Login failed');
            }

            if (!body.token) {
                throw new Error('No token in response');
            }

            localStorage.setItem('adminToken', body.token);
            window.location.href = '/admin';
        } catch (err) {
            alertBox.textContent = 'No se pudo iniciar sesión. Verifica tu correo y contraseña.';
            alertBox.classList.remove('d-none');
        }
    });
})();
