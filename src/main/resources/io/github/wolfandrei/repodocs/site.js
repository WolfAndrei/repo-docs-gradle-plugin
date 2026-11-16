/*
 * Copyright 2026 Andrei Volkau
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Full-text search over the prebuilt index (window.DOCS_SEARCH_INDEX), heading anchors and the sidebar toggle.
// The script needs no network: the index is loaded by a <script> tag, so search works from the file system too.
(function () {
    "use strict";

    var root = document.body.getAttribute("data-root") || "";
    var TEXT = window.DOCS_TEXT || {};

    function t(key, fallback) {
        return TEXT[key] || fallback;
    }
    var index = (window.DOCS_SEARCH_INDEX || []).map(function (entry) {
        return {
            title: entry.t,
            url: entry.u,
            text: entry.x,
            titleKey: normalize(entry.t),
            textKey: normalize(entry.x)
        };
    });

    function normalize(text) {
        return (text || "").toLowerCase().replace(/ё/g, "е");
    }

    function escapeHtml(text) {
        return text.replace(/[&<>"]/g, function (char) {
            return {"&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;"}[char];
        });
    }

    // Marks the terms in the plain text and escapes the pieces, so that a term never matches inside an entity
    // such as &amp;.
    function highlight(text, terms) {
        var words = terms.filter(function (term) { return term.length > 0; })
            .map(function (term) { return term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"); });
        if (words.length === 0) {
            return escapeHtml(text);
        }
        return text.split(new RegExp("(" + words.join("|") + ")", "gi")).map(function (piece, index) {
            return index % 2 === 1 ? "<mark>" + escapeHtml(piece) + "</mark>" : escapeHtml(piece);
        }).join("");
    }

    function snippet(entry, terms) {
        var position = entry.textKey.indexOf(terms[0]);
        if (position < 0) {
            return entry.text.slice(0, 160);
        }
        var start = Math.max(0, position - 60);
        var prefix = start > 0 ? "…" : "";
        return prefix + entry.text.slice(start, start + 180);
    }

    function count(text, term) {
        var total = 0;
        var position = text.indexOf(term);
        while (position >= 0) {
            total++;
            position = text.indexOf(term, position + term.length);
        }
        return total;
    }

    function search(query) {
        var terms = normalize(query).split(/\s+/).filter(function (term) {
            return term.length > 0;
        });
        if (terms.length === 0) {
            return {terms: terms, results: []};
        }
        var results = [];
        index.forEach(function (entry) {
            var score = 0;
            for (var i = 0; i < terms.length; i++) {
                var inTitle = count(entry.titleKey, terms[i]);
                var inText = count(entry.textKey, terms[i]);
                if (inTitle + inText === 0) {
                    return;
                }
                score += inTitle * 10 + Math.min(inText, 10);
            }
            results.push({entry: entry, score: score});
        });
        results.sort(function (a, b) {
            return b.score - a.score;
        });
        return {terms: terms, results: results.slice(0, 30)};
    }

    var input = document.getElementById("search-input");
    var output = document.getElementById("search-results");
    var selected = -1;

    function render(query) {
        var found = search(query);
        selected = -1;
        if (query.trim().length === 0) {
            output.hidden = true;
            output.innerHTML = "";
            return;
        }
        if (found.results.length === 0) {
            output.innerHTML = "<div class=\"empty\">" + escapeHtml(t("noResults", "No results")) + "</div>";
        } else {
            output.innerHTML = found.results.map(function (result) {
                var entry = result.entry;
                return "<a href=\"" + escapeHtml(root + entry.url) + "\">" +
                    "<div class=\"title\">" + highlight(entry.title, found.terms) + "</div>" +
                    "<div class=\"snippet\">" + highlight(snippet(entry, found.terms), found.terms) + "</div></a>";
            }).join("");
        }
        output.hidden = false;
    }

    function select(step) {
        var links = output.querySelectorAll("a");
        if (links.length === 0) {
            return;
        }
        if (selected >= 0) {
            links[selected].classList.remove("selected");
        }
        selected = (selected + step + links.length) % links.length;
        links[selected].classList.add("selected");
        links[selected].scrollIntoView({block: "nearest"});
    }

    if (input && output) {
        input.addEventListener("input", function () {
            render(input.value);
        });
        input.addEventListener("keydown", function (event) {
            if (event.key === "ArrowDown") {
                select(1);
                event.preventDefault();
            } else if (event.key === "ArrowUp") {
                select(-1);
                event.preventDefault();
            } else if (event.key === "Enter") {
                var links = output.querySelectorAll("a");
                var target = links[selected >= 0 ? selected : 0];
                if (target) {
                    window.location.href = target.getAttribute("href");
                }
            } else if (event.key === "Escape") {
                input.value = "";
                render("");
            }
        });
        document.addEventListener("click", function (event) {
            if (!output.contains(event.target) && event.target !== input) {
                output.hidden = true;
            }
        });
        input.addEventListener("focus", function () {
            if (input.value.trim().length > 0) {
                render(input.value);
            }
        });
        document.addEventListener("keydown", function (event) {
            if (event.key === "/" && document.activeElement !== input) {
                input.focus();
                event.preventDefault();
            }
        });
    }

    document.querySelectorAll(".content h1[id], .content h2[id], .content h3[id], .content h4[id]")
        .forEach(function (heading) {
            var anchor = document.createElement("a");
            anchor.className = "heading-anchor";
            anchor.href = "#" + heading.id;
            anchor.textContent = "#";
            anchor.setAttribute("aria-label", t("headingLink", "Link to this section"));
            heading.appendChild(anchor);
        });

    var toggle = document.querySelector(".nav-toggle");
    if (toggle) {
        toggle.addEventListener("click", function (event) {
            var open = document.body.classList.toggle("nav-open");
            toggle.setAttribute("aria-expanded", open ? "true" : "false");
            event.stopPropagation();
        });
        document.addEventListener("click", function (event) {
            var nav = document.querySelector(".sidebar");
            if (document.body.classList.contains("nav-open") && nav && !nav.contains(event.target)) {
                document.body.classList.remove("nav-open");
                toggle.setAttribute("aria-expanded", "false");
            }
        });
    }

    // Diagram viewer: every SVG picture of a page gets a toolbar (zoom out, zoom in, fit, full screen) and can be
    // zoomed and panned. The picture is moved by a CSS transform, so it stays vector and is never redrawn. Inline,
    // Ctrl/Cmd + wheel or a trackpad pinch zooms around the pointer and the plain wheel scrolls the page; in the full
    // screen view the plain wheel zooms. Keys on a focused diagram: + and - zoom, 0 fits, F opens the full screen,
    // Esc closes it, arrows pan.
    var ICONS = {
        out: "<path d=\"M4 9h10\"/>",
        "in": "<path d=\"M4 9h10M9 4v10\"/>",
        fit: "<path d=\"M3 7V3h4M11 3h4v4M15 11v4h-4M7 15H3v-4\"/>",
        full: "<path d=\"M3 7V3h4M3 3l5 5M11 3h4v4M15 3l-5 5M15 11v4h-4M15 15l-5-5M7 15H3v-4M3 15l5-5\"/>",
        close: "<path d=\"M4 4l10 10M14 4L4 14\"/>"
    };
    var MIN_SCALE = 0.1;
    var MAX_SCALE = 16;
    var PADDING = 16;

    function icon(name) {
        return "<svg viewBox=\"0 0 18 18\" width=\"18\" height=\"18\" aria-hidden=\"true\" fill=\"none\" " +
            "stroke=\"currentColor\" stroke-width=\"1.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\">" +
            ICONS[name] + "</svg>";
    }

    function button(action, text, iconName) {
        var label = escapeHtml(text);
        return "<button type=\"button\" class=\"diagram-button\" data-action=\"" + action + "\" title=\"" + label +
            "\" aria-label=\"" + label + "\">" + icon(iconName) + "</button>";
    }

    function DiagramViewer(picture) {
        var holder = picture.parentElement;
        var figure = document.createElement("figure");
        figure.className = "diagram";
        figure.tabIndex = 0;
        figure.setAttribute("aria-label", t("diagram", "Diagram"));
        figure.innerHTML = "<div class=\"diagram-toolbar\">" +
            button("out", t("zoomOut", "Zoom out"), "out") +
            "<span class=\"diagram-scale\" aria-live=\"polite\">100%</span>" +
            button("in", t("zoomIn", "Zoom in"), "in") +
            button("fit", t("fit", "Fit"), "fit") +
            button("full", t("fullScreen", "Full screen"), "full") +
            "</div><div class=\"diagram-viewport\"><div class=\"diagram-stage\"></div></div>";
        var viewport = figure.querySelector(".diagram-viewport");
        var stage = figure.querySelector(".diagram-stage");
        var label = figure.querySelector(".diagram-scale");
        var fullButton = figure.querySelector("[data-action=full]");
        var replaced = holder && holder.tagName === "P" && holder.children.length === 1 &&
            holder.textContent.trim() === "" ? holder : picture;
        replaced.parentNode.insertBefore(figure, replaced);
        stage.appendChild(picture);
        if (replaced !== picture) {
            replaced.parentNode.removeChild(replaced);
        }

        var state = {scale: 1, x: 0, y: 0, fit: 1, width: 0, height: 0, fitted: true};
        var full = false;

        function apply() {
            stage.style.transform = "translate(" + state.x + "px, " + state.y + "px) scale(" + state.scale + ")";
            label.textContent = Math.round(state.scale * 100) + "%";
            figure.classList.toggle("diagram-zoomed", !state.fitted);
        }

        function measure() {
            var content = stage.firstElementChild;
            if (!content) {
                return false;
            }
            var width = 0;
            var height = 0;
            if (content.tagName === "IMG") {
                width = content.naturalWidth;
                height = content.naturalHeight;
            } else {
                var box = content.viewBox && content.viewBox.baseVal;
                width = parseFloat(content.getAttribute("width")) || (box && box.width) || 0;
                height = parseFloat(content.getAttribute("height")) || (box && box.height) || 0;
            }
            if (!width || !height) {
                return false;
            }
            content.style.width = width + "px";
            content.style.height = height + "px";
            state.width = width;
            state.height = height;
            return true;
        }

        // Fits the whole diagram: inline at most at its own size and the viewport takes its height; in the full screen
        // view the diagram fills the screen.
        function fit() {
            if (!measure()) {
                return;
            }
            var available = viewport.clientWidth - 2 * PADDING;
            var scale;
            if (full) {
                scale = Math.min(available / state.width, (viewport.clientHeight - 2 * PADDING) / state.height);
            } else {
                var maxHeight = Math.max(window.innerHeight * 0.8, 240);
                scale = Math.min(1, available / state.width, maxHeight / state.height);
                viewport.style.height = Math.ceil(state.height * scale + 2 * PADDING) + "px";
            }
            state.fit = scale;
            state.scale = scale;
            state.x = (viewport.clientWidth - state.width * scale) / 2;
            state.y = (viewport.clientHeight - state.height * scale) / 2;
            state.fitted = true;
            apply();
        }

        function zoomAt(factor, clientX, clientY) {
            var rect = viewport.getBoundingClientRect();
            var px = clientX === undefined ? rect.width / 2 : clientX - rect.left;
            var py = clientY === undefined ? rect.height / 2 : clientY - rect.top;
            var scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, state.scale * factor));
            var ratio = scale / state.scale;
            state.x = px - (px - state.x) * ratio;
            state.y = py - (py - state.y) * ratio;
            state.scale = scale;
            state.fitted = false;
            apply();
        }

        function pan(dx, dy) {
            state.x += dx;
            state.y += dy;
            state.fitted = false;
            apply();
        }

        function setFull(value) {
            full = value;
            figure.classList.toggle("diagram-full", full);
            document.documentElement.classList.toggle("diagram-open", full);
            fullButton.innerHTML = icon(full ? "close" : "full");
            fullButton.setAttribute("aria-label", full ? t("close", "Close") : t("fullScreen", "Full screen"));
            fullButton.title = full ? t("closeHint", "Close (Esc)") : t("fullScreen", "Full screen");
            viewport.style.height = "";
            fit();
            figure.focus({preventScroll: true});
        }

        figure.querySelector(".diagram-toolbar").addEventListener("click", function (event) {
            var target = event.target.closest("[data-action]");
            if (!target) {
                return;
            }
            var action = target.getAttribute("data-action");
            if (action === "in") {
                zoomAt(1.25);
            } else if (action === "out") {
                zoomAt(0.8);
            } else if (action === "fit") {
                fit();
            } else if (action === "full") {
                setFull(!full);
            }
        });

        viewport.addEventListener("wheel", function (event) {
            if (!full && !event.ctrlKey && !event.metaKey) {
                return;
            }
            event.preventDefault();
            var delta = event.deltaMode === 1 ? event.deltaY * 16 : event.deltaY;
            zoomAt(Math.exp(-delta * (event.ctrlKey ? 0.01 : 0.002)), event.clientX, event.clientY);
        }, {passive: false});

        // Safari reports a trackpad pinch as gesture events.
        var gestureScale = 1;
        viewport.addEventListener("gesturestart", function (event) {
            event.preventDefault();
            gestureScale = 1;
        });
        viewport.addEventListener("gesturechange", function (event) {
            event.preventDefault();
            zoomAt(event.scale / gestureScale, event.clientX, event.clientY);
            gestureScale = event.scale;
        });

        // Mouse and touch: one pointer pans, two pointers pinch. Inline, a touch pans the diagram only once it is
        // zoomed, so that a finger still scrolls the page.
        var pointers = {};
        var pinch = null;

        function pointerList() {
            return Object.keys(pointers).map(function (id) {
                return pointers[id];
            });
        }

        viewport.addEventListener("pointerdown", function (event) {
            if (event.pointerType === "mouse" && event.button !== 0) {
                return;
            }
            if (event.pointerType === "touch" && !full && state.fitted && pointerList().length === 0) {
                pointers[event.pointerId] = {x: event.clientX, y: event.clientY, passive: true};
                return;
            }
            pointers[event.pointerId] = {x: event.clientX, y: event.clientY};
            viewport.setPointerCapture(event.pointerId);
            figure.classList.add("diagram-dragging");
        });

        viewport.addEventListener("pointermove", function (event) {
            var pointer = pointers[event.pointerId];
            if (!pointer) {
                return;
            }
            var list = pointerList();
            if (list.length === 2) {
                var other = list[0] === pointer ? list[1] : list[0];
                var distance = Math.hypot(event.clientX - other.x, event.clientY - other.y);
                var midX = (event.clientX + other.x) / 2;
                var midY = (event.clientY + other.y) / 2;
                if (pinch) {
                    zoomAt(distance / pinch.distance, midX, midY);
                    pan(midX - pinch.x, midY - pinch.y);
                }
                pinch = {distance: distance, x: midX, y: midY};
            } else if (!pointer.passive) {
                pan(event.clientX - pointer.x, event.clientY - pointer.y);
            }
            pointer.x = event.clientX;
            pointer.y = event.clientY;
        });

        function release(event) {
            delete pointers[event.pointerId];
            pinch = null;
            if (pointerList().length === 0) {
                figure.classList.remove("diagram-dragging");
            }
        }

        viewport.addEventListener("pointerup", release);
        viewport.addEventListener("pointercancel", release);
        viewport.addEventListener("dblclick", function (event) {
            zoomAt(event.shiftKey ? 0.5 : 2, event.clientX, event.clientY);
        });

        figure.addEventListener("keydown", function (event) {
            if (event.target.closest(".diagram-toolbar") && (event.key === "Enter" || event.key === " ")) {
                return;
            }
            var handled = true;
            switch (event.key) {
                case "+":
                case "=":
                    zoomAt(1.25);
                    break;
                case "-":
                case "_":
                    zoomAt(0.8);
                    break;
                case "0":
                    fit();
                    break;
                case "f":
                case "F":
                    setFull(!full);
                    break;
                case "Escape":
                    if (full) {
                        setFull(false);
                    } else {
                        handled = false;
                    }
                    break;
                case "ArrowLeft":
                    pan(40, 0);
                    break;
                case "ArrowRight":
                    pan(-40, 0);
                    break;
                case "ArrowUp":
                    pan(0, 40);
                    break;
                case "ArrowDown":
                    pan(0, -40);
                    break;
                default:
                    handled = false;
            }
            if (handled) {
                event.preventDefault();
            }
        });

        window.addEventListener("resize", function () {
            if (state.fitted) {
                fit();
            }
        });

        this.figure = figure;
        this.fit = fit;

        var image = stage.firstElementChild;
        if (image.tagName === "IMG" && !image.complete) {
            image.addEventListener("load", fit);
        } else {
            fit();
        }
    }

    document.querySelectorAll(".content-article img, .content-landing img").forEach(function (img) {
        var src = img.getAttribute("src") || "";
        if (/\.svg($|[?#])/i.test(src) && !img.closest("a")) {
            new DiagramViewer(img);
        }
    });

    // Theme: the button goes system -> light -> dark; the choice is kept in local storage for every page. The script
    // of the head applied it before the styles; with "system" the page follows a change of the system scheme.
    var THEME_KEY = "repo-docs-theme";
    var THEME_ORDER = ["system", "light", "dark"];
    var themeButton = document.querySelector(".theme-toggle");
    var systemDark = window.matchMedia("(prefers-color-scheme: dark)");

    function applyTheme(choice) {
        var dark = choice === "dark" || choice === "system" && systemDark.matches;
        document.documentElement.setAttribute("data-theme-choice", choice);
        document.documentElement.setAttribute("data-theme", dark ? "dark" : "light");
        if (themeButton) {
            var title = t("theme" + choice.charAt(0).toUpperCase() + choice.slice(1), choice);
            themeButton.title = title;
            themeButton.setAttribute("aria-label", title);
        }
    }

    function currentTheme() {
        return document.documentElement.getAttribute("data-theme-choice") || "system";
    }

    applyTheme(currentTheme());
    if (themeButton) {
        themeButton.addEventListener("click", function () {
            var next = THEME_ORDER[(THEME_ORDER.indexOf(currentTheme()) + 1) % THEME_ORDER.length];
            try {
                window.localStorage.setItem(THEME_KEY, next);
            } catch (e) {
                // No storage, such as in a private window: the choice lasts for this page only.
            }
            applyTheme(next);
        });
    }
    var onSystemChange = function () {
        if (currentTheme() === "system") {
            applyTheme("system");
        }
    };
    if (systemDark.addEventListener) {
        systemDark.addEventListener("change", onSystemChange);
    } else if (systemDark.addListener) {
        systemDark.addListener(onSystemChange);
    }
    // Another page of the site changed the theme.
    window.addEventListener("storage", function (event) {
        if (event.key === THEME_KEY) {
            applyTheme(event.newValue || "system");
        }
    });

    // Live reload: docsBuild writes _assets/build-stamp.js last, a hash of the site. The page loads it again every
    // second with a script tag, which works from the file system too, and reloads when the hash changes, that is
    // after a rebuild by `docsBuild --continuous`.
    var stamp = null;
    window.setInterval(function () {
        var script = document.createElement("script");
        script.src = root + "_assets/build-stamp.js?" + Date.now();
        script.onload = function () {
            var value = window.repoDocsBuildStamp;
            if (stamp !== null && value !== stamp) {
                window.location.reload();
            }
            stamp = value;
            script.remove();
        };
        script.onerror = function () {
            script.remove();
        };
        document.head.appendChild(script);
    }, 1000);

    // Interactive pages: the page in the frame reports its height, the frame takes it.
    var MAX_FRAME_HEIGHT = 20000;
    window.addEventListener("message", function (event) {
        var height = event.data && event.data.repoDocsFrameHeight;
        if (typeof height !== "number") {
            return;
        }
        document.querySelectorAll("iframe.interactive-frame").forEach(function (frame) {
            if (frame.contentWindow === event.source) {
                frame.style.height = Math.min(Math.max(height, 200), MAX_FRAME_HEIGHT) + "px";
            }
        });
    });

    // Branches of the sidebar that the reader opened or closed stay so on the other pages of this site. The branch of
    // the current page is always open. The state lives in localStorage under a key of this site; without storage the
    // sidebar opens as the page builds it.
    var NAV_KEY = "repo-docs-nav:" + new URL(root + "index.html", window.location.href).href;
    var navState = {};
    try {
        navState = JSON.parse(window.localStorage.getItem(NAV_KEY) || "{}") || {};
    } catch (e) {
        navState = {};
    }
    document.querySelectorAll(".sidebar details[data-nav]").forEach(function (branch) {
        var key = branch.getAttribute("data-nav");
        if (Object.prototype.hasOwnProperty.call(navState, key) && !branch.querySelector("a.current")) {
            branch.open = navState[key] === true;
        }
        branch.addEventListener("toggle", function () {
            if (navState[key] === branch.open) {
                return;
            }
            navState[key] = branch.open;
            try {
                window.localStorage.setItem(NAV_KEY, JSON.stringify(navState));
            } catch (e) {
                // No storage: the branch stays as it is on this page only.
            }
        });
    });

    var sidebar = document.querySelector(".sidebar");
    var current = document.querySelector(".sidebar a.current");
    if (sidebar && current) {
        sidebar.scrollTop = current.offsetTop - sidebar.clientHeight / 2;
    }
})();
