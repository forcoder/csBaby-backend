import web
import json
import requests as http_requests
from config import API_BASE_URL, SESSION_SECRET

urls = (
    "/admin/login", "Login",
    "/admin/logout", "Logout",
    "/admin/dashboard", "Dashboard",
    "/admin/tenants", "Tenants",
    "/admin/tenants/(\w+)", "TenantDetail",
    "/admin/profile", "Profile",
    "/admin", "AdminIndex",
)

app = web.application(urls, globals())
web.config.session_parameters["cookie_name"] = "csbaby_admin_session"

if web.config.get("_session") is None:
    store = web.session.DiskStore("sessions")
    session = web.session.Session(app, store, initializer={"admin_phone": None})
    web.config._session = session
else:
    session = web.config._session

import jinja2
template_env = jinja2.Environment(loader=jinja2.FileSystemLoader("templates"))


def render(template_name, **context):
    tmpl = template_env.get_template(template_name)
    content = tmpl.render(**context)
    layout = template_env.get_template("layout.html")
    return layout.render(content=content, **context)


def require_admin():
    if not session.get("admin_phone"):
        raise web.seeother("/admin/login")


def api_get(path, token):
    return http_requests.get(
        f"{API_BASE_URL}{path}",
        headers={"Authorization": f"Bearer {token}"},
        timeout=10
    )


def api_put(path, token, data):
    return http_requests.put(
        f"{API_BASE_URL}{path}",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        data=json.dumps(data),
        timeout=10
    )


class Login:
    def GET(self):
        if session.get("admin_phone"):
            raise web.seeother("/admin/dashboard")
        return render("login.html", error=None)

    def POST(self):
        data = web.input()
        phone = data.get("phone", "").strip()
        password = data.get("password", "")
        try:
            resp = http_requests.post(
                f"{API_BASE_URL}/api/auth/login",
                json={"phone": phone, "password": password},
                timeout=10
            )
            if resp.status_code == 200:
                result = resp.json()
                session["admin_phone"] = result.get("phone", phone)
                session["admin_token"] = result.get("token", "")
                raise web.seeother("/admin/dashboard")
            else:
                error = resp.json().get("error", "登录失败")
        except http_requests.exceptions.RequestException:
            error = "无法连接 API 服务"
        return render("login.html", error=error)


class Logout:
    def GET(self):
        session.kill()
        raise web.seeother("/admin/login")


class AdminIndex:
    def GET(self):
        raise web.seeother("/admin/dashboard")


class Dashboard:
    def GET(self):
        require_admin()
        token = session.get("admin_token", "")
        stats = {}
        try:
            resp = api_get("/api/admin/stats", token)
            if resp.status_code == 200:
                stats = resp.json()
        except Exception:
            pass
        return render("dashboard.html", stats=stats, admin_phone=session.get("admin_phone"))


class Tenants:
    def GET(self):
        require_admin()
        token = session.get("admin_token", "")
        params = web.input(page="1", search="")
        page = int(params.page)
        search = params.search.strip()
        tenants_data = {"items": [], "total": 0, "page": page, "page_size": 20}
        try:
            query = f"/api/admin/tenants?page={page}&page_size=20"
            if search:
                query += f"&search={search}"
            resp = api_get(query, token)
            if resp.status_code == 200:
                tenants_data = resp.json()
        except Exception:
            pass
        return render(
            "tenants.html",
            tenants=tenants_data.get("items", []),
            total=tenants_data.get("total", 0),
            page=page,
            page_size=20,
            search=search,
            admin_phone=session.get("admin_phone")
        )


class TenantDetail:
    def GET(self, tenant_id):
        require_admin()
        token = session.get("admin_token", "")
        tenant = {}
        try:
            resp = api_get(f"/api/admin/tenants/{tenant_id}", token)
            if resp.status_code == 200:
                tenant = resp.json()
        except Exception:
            pass
        return render("tenant_detail.html", tenant=tenant, admin_phone=session.get("admin_phone"))

    def POST(self, tenant_id):
        require_admin()
        token = session.get("admin_token", "")
        data = web.input()
        is_active = data.get("is_active")
        if is_active is not None:
            api_put(f"/api/admin/tenants/{tenant_id}", token, {"is_active": int(is_active)})
        raise web.seeother(f"/admin/tenants/{tenant_id}")


class Profile:
    def GET(self):
        require_admin()
        return render("profile.html", admin_phone=session.get("admin_phone"), error=None, success=None)

    def POST(self):
        require_admin()
        token = session.get("admin_token", "")
        data = web.input()
        old_password = data.get("old_password", "")
        new_password = data.get("new_password", "")
        confirm_password = data.get("confirm_password", "")
        if not old_password or not new_password:
            return render("profile.html", admin_phone=session.get("admin_phone"), error="请填写完整", success=None)
        if new_password != confirm_password:
            return render("profile.html", admin_phone=session.get("admin_phone"), error="两次新密码不一致", success=None)
        if len(new_password) < 6:
            return render("profile.html", admin_phone=session.get("admin_phone"), error="新密码至少6位", success=None)
        try:
            resp = http_requests.post(
                f"{API_BASE_URL}/api/auth/change_password",
                json={"old_password": old_password, "new_password": new_password},
                headers={"Authorization": f"Bearer {token}"},
                timeout=10
            )
            if resp.status_code == 200:
                return render("profile.html", admin_phone=session.get("admin_phone"), error=None, success="密码修改成功")
            else:
                error = resp.json().get("error", "修改失败")
                return render("profile.html", admin_phone=session.get("admin_phone"), error=error, success=None)
        except Exception as e:
            return render("profile.html", admin_phone=session.get("admin_phone"), error=f"网络错误: {e}", success=None)


application = app.wsgifunc()

if __name__ == "__main__":
    app.run()
