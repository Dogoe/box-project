(function () {
    'use strict';

    // Kit page gallery: a main picture with thumbnails (hover or tap to switch), arrows, swipe,
    // and a full-screen viewer with zoom. The first slide may be the kit's collage.
    const data = window.KIT_GALLERY || { photos: [] };
    const slides = (data.collageSlide ? [{ type: 'collage', caption: data.kitName }] : [])
        .concat(data.photos.map((p) => ({ type: 'photo', url: p.url, caption: p.caption, productId: p.productId })));
    const photos = slides.filter((s) => s.type === 'photo');

    const main = document.getElementById('gallery-main');
    const mainImg = document.getElementById('gallery-main-img');
    const collage = document.getElementById('gallery-collage');
    const caption = document.getElementById('gallery-caption');
    const counter = document.getElementById('gallery-counter');
    const thumbs = Array.from(document.querySelectorAll('#gallery-thumbs .gallery-thumb'));
    let current = 0;

    function select(index, revealThumb) {
        if (slides.length === 0) {
            return;
        }
        current = (index + slides.length) % slides.length;
        const slide = slides[current];
        const isPhoto = slide.type === 'photo';
        if (collage) {
            collage.classList.toggle('d-none', isPhoto);
        }
        if (mainImg) {
            mainImg.classList.toggle('d-none', !isPhoto);
            if (isPhoto) {
                mainImg.src = slide.url;
                mainImg.alt = slide.caption;
            }
        }
        main.style.cursor = isPhoto ? 'zoom-in' : (photos.length ? 'pointer' : 'default');
        caption.textContent = slide.caption;
        if (counter) {
            counter.textContent = (current + 1) + ' / ' + slides.length;
        }
        thumbs.forEach((thumb) => thumb.classList.toggle('active', Number(thumb.dataset.index) === current));
        const thumb = thumbs.find((t) => Number(t.dataset.index) === current);
        if (revealThumb && thumb) {
            // Scroll only the thumbnail strip, never the page.
            const strip = thumb.parentElement;
            strip.scrollTo({
                left: thumb.offsetLeft - strip.offsetLeft - (strip.clientWidth - thumb.offsetWidth) / 2,
                top: thumb.offsetTop - strip.offsetTop - (strip.clientHeight - thumb.offsetHeight) / 2,
                behavior: 'smooth'
            });
        }
    }

    thumbs.forEach((thumb) => {
        const index = Number(thumb.dataset.index);
        thumb.addEventListener('click', () => select(index, true));
        thumb.addEventListener('mouseenter', () => select(index, false));
    });

    const prev = document.getElementById('gallery-prev');
    const next = document.getElementById('gallery-next');
    if (prev) {
        prev.addEventListener('click', (event) => {
            event.stopPropagation();
            select(current - 1, true);
        });
        next.addEventListener('click', (event) => {
            event.stopPropagation();
            select(current + 1, true);
        });
    }

    main.addEventListener('keydown', (event) => {
        if (event.key === 'ArrowLeft') {
            select(current - 1, true);
        } else if (event.key === 'ArrowRight') {
            select(current + 1, true);
        } else if (event.key === 'Enter') {
            openViewer();
        }
    });

    // Swipe left/right on phones; a swipe must not also count as a tap that opens the viewer.
    function onSwipe(element, onLeft, onRight) {
        let startX = null;
        let startY = null;
        element.addEventListener('touchstart', (event) => {
            startX = event.touches[0].clientX;
            startY = event.touches[0].clientY;
        }, { passive: true });
        element.addEventListener('touchend', (event) => {
            if (startX === null) {
                return;
            }
            const dx = event.changedTouches[0].clientX - startX;
            const dy = event.changedTouches[0].clientY - startY;
            startX = null;
            if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy)) {
                element.dataset.swiped = '1';
                setTimeout(() => delete element.dataset.swiped, 400);
                (dx < 0 ? onLeft : onRight)();
            }
        });
    }
    onSwipe(main, () => select(current + 1, true), () => select(current - 1, true));

    // ---- Full-screen viewer (photos only) ----

    const viewerEl = document.getElementById('lightbox');
    const viewer = viewerEl && photos.length ? new bootstrap.Modal(viewerEl) : null;
    const viewerImg = document.getElementById('lightbox-img');
    const viewerStage = document.getElementById('lightbox-stage');
    const viewerCaption = document.getElementById('lightbox-caption');
    const viewerCounter = document.getElementById('lightbox-counter');
    let viewing = 0;

    function setZoom(on, event) {
        viewerImg.classList.toggle('zoomed', on);
        if (on && event) {
            const rect = viewerImg.getBoundingClientRect();
            viewerImg.style.transformOrigin = ((event.clientX - rect.left) / rect.width * 100) + '% ' +
                ((event.clientY - rect.top) / rect.height * 100) + '%';
        }
    }

    function showPhoto(index) {
        viewing = (index + photos.length) % photos.length;
        setZoom(false);
        viewerImg.src = photos[viewing].url;
        viewerImg.alt = photos[viewing].caption;
        viewerCaption.textContent = photos[viewing].caption;
        viewerCounter.textContent = (viewing + 1) + ' / ' + photos.length;
        const hide = photos.length < 2;
        document.getElementById('lightbox-prev').classList.toggle('d-none', hide);
        document.getElementById('lightbox-next').classList.toggle('d-none', hide);
        // Warm up the neighbours so arrows feel instant.
        [viewing - 1, viewing + 1].forEach((i) => {
            new Image().src = photos[(i + photos.length) % photos.length].url;
        });
    }

    function openViewer() {
        if (!viewer) {
            return;
        }
        const slide = slides[current];
        showPhoto(slide.type === 'photo' ? photos.indexOf(slide) : 0);
        viewer.show();
    }

    main.addEventListener('click', (event) => {
        if (!main.dataset.swiped && !event.target.closest('.gallery-nav')) {
            openViewer();
        }
    });

    if (viewer) {
        document.getElementById('lightbox-prev').addEventListener('click', () => showPhoto(viewing - 1));
        document.getElementById('lightbox-next').addEventListener('click', () => showPhoto(viewing + 1));
        viewerImg.addEventListener('click', (event) => setZoom(!viewerImg.classList.contains('zoomed'), event));
        viewerStage.addEventListener('mousemove', (event) => {
            if (viewerImg.classList.contains('zoomed')) {
                setZoom(true, event);
            }
        });
        onSwipe(viewerStage, () => {
            if (!viewerImg.classList.contains('zoomed')) {
                showPhoto(viewing + 1);
            }
        }, () => {
            if (!viewerImg.classList.contains('zoomed')) {
                showPhoto(viewing - 1);
            }
        });
        document.addEventListener('keydown', (event) => {
            if (!viewerEl.classList.contains('show')) {
                return;
            }
            if (event.key === 'ArrowLeft') {
                showPhoto(viewing - 1);
            } else if (event.key === 'ArrowRight') {
                showPhoto(viewing + 1);
            }
        });
        // Leave the page gallery on the photo that was last seen full screen.
        viewerEl.addEventListener('hidden.bs.modal', () => select(slides.indexOf(photos[viewing]), true));
    }

    // "Qué incluye": tapping a product's thumbnail jumps to its photos.
    document.querySelectorAll('[data-gallery-product]').forEach((button) => {
        button.addEventListener('click', () => {
            const productId = Number(button.dataset.galleryProduct);
            const index = slides.findIndex((s) => s.productId === productId);
            if (index >= 0) {
                select(index, true);
                document.getElementById('gallery').scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        });
    });

    select(0, false);
})();
