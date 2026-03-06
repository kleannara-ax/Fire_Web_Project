(function () {
  function esc(v) {
    return String(v ?? "").replace(/[&<>"']/g, function (m) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[m];
    });
  }

  function getUser() {
    try {
      return JSON.parse(localStorage.getItem("fireweb_user") || "null");
    } catch (_) {
      return null;
    }
  }

  function setMenuLinks() {
    var nav = document.querySelector(".fireweb-navbar");
    if (!nav) return;
    var links = nav.querySelectorAll(".navbar-nav.ms-3 .nav-link");
    var defs = [
      { href: "/extinguishers.html", text: "\uC18C\uD654\uAE30 \uBAA9\uB85D" },
      { href: "/hydrants.html", text: "\uC18C\uD654\uC804 \uBAA9\uB85D" },
      { href: "/maps/floor.html?buildingName=%EB%B3%B5%EC%A7%80%EA%B4%80&floorName=1%EC%B8%B5", text: "\uB3C4\uBA74" },
      { href: "/qr", text: "QR\uCF54\uB4DC" }
    ];
    for (var i = 0; i < links.length && i < defs.length; i++) {
      links[i].setAttribute("href", defs[i].href);
      links[i].textContent = defs[i].text;
    }
  }

  function setAccountArea() {
    var area = document.getElementById("navAccountArea");
    if (!area) return;
    var token = localStorage.getItem("fireweb_token");
    var user = getUser();
    if (!token || !user) {
      area.innerHTML = '<li class="nav-item"><a class="btn btn-sm btn-outline-light" href="/login.html">\uB85C\uADF8\uC778</a></li>';
      return;
    }
    var isAdmin = String(user.role || "").toUpperCase() === "ADMIN";
    area.innerHTML =
      '<li class="nav-item dropdown">' +
      '<a class="nav-link dropdown-toggle fw-semibold account-link" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">' +
      esc(user.displayName || user.username || "\uC0AC\uC6A9\uC790") +
      "</a>" +
      '<ul class="dropdown-menu dropdown-menu-end">' +
      '<li><a class="dropdown-item" href="/account/index.html">\uB0B4 \uC815\uBCF4</a></li>' +
      (isAdmin ? '<li><a class="dropdown-item" href="/account/users.html">\uACC4\uC815\uAD00\uB9AC</a></li>' : "") +
      '<li><hr class="dropdown-divider"></li>' +
      '<li><button type="button" class="dropdown-item text-danger" id="fwCommonLogoutBtn">\uB85C\uADF8\uC544\uC6C3</button></li>' +
      "</ul></li>";
    var btn = document.getElementById("fwCommonLogoutBtn");
    if (btn) {
      btn.addEventListener("click", function () {
        localStorage.removeItem("fireweb_token");
        localStorage.removeItem("fireweb_user");
        location.href = "/login.html";
      });
    }
  }

  function mount() {
    setMenuLinks();
    setAccountArea();
  }

  window.FireWebNav = { mount: mount };
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mount);
  } else {
    mount();
  }
})();
