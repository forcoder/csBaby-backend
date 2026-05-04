import json
import requests as http_requests
from functools import wraps
from flask import Flask, request, render_template, redirect, url_for, session, flash
from config import API_BASE_URL, SESSION_SECRET

app = Flask(__name__)
app.secret_key = SESSION_SECRET


def login_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if not session.get("admin_phone"):
            return redirect(url_for("login"))
        return f(*args, **kwargs)
    return decorated


def api_get(path, token):
    return http_requests.get(
        f"{API_BASE_URL}{path}",
        headers={"Authorization": f"Bearer {token}"},
        timeout=10
    )


def api_post(path, json_data=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return http_requests.post(
        f"{API_BASE_URL}{path}",
        json=json_data,
        headers=headers,
        timeout=10
    )


def api_put(path, token, data):
    return http_requests.put(
        f"{API_BASE_URL}{path}",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        data=json.dumps(data),
        timeout=10
    )


@app.route("/admin/login", methods=["GET", "POST"])
def login():
    if session.get("admin_phone"):
        return redirect(url_for("dashboard"))
    if request.method == "POST":
        phone = request.form.get("phone", "").strip()
        password = request.form.get("password", "")
        try:
            resp = api_post("/api/auth/login", {"phone": phone, "password": password})
            if resp.status_code == 200:
                result = resp.json()
                session["admin_phone"] = result.get("phone", phone)
                session["admin_token"] = result.get("token", "")
                return redirect(url_for("dashboard"))
            else:
                error = resp.json().get("error", "登录失败")
        except http_requests.exceptions.RequestException:
            error = "无法连接 API 服务"
        return render_template("login.html", error=error)
    return render_template("login.html", error=None)


@app.route("/admin/logout")
def logout():
    session.clear()
    return redirect(url_for("login"))


@app.route("/admin")
def admin_index():
    return redirect(url_for("dashboard"))


@app.route("/admin/dashboard")
@login_required
def dashboard():
    token = session.get("admin_token", "")
    stats = {}
    try:
        resp = api_get("/api/admin/stats", token)
        if resp.status_code == 200:
            stats = resp.json()
    except Exception:
        pass
    return render_template("dashboard.html", stats=stats, admin_phone=session.get("admin_phone"))


@app.route("/admin/tenants")
@login_required
def tenants():
    token = session.get("admin_token", "")
    page = request.args.get("page", 1, type=int)
    search = request.args.get("search", "").strip()
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
    return render_template(
        "tenants.html",
        tenants=tenants_data.get("items", []),
        total=tenants_data.get("total", 0),
        page=page,
        page_size=20,
        search=search,
        admin_phone=session.get("admin_phone"),
    )


@app.route("/admin/tenants/<tenant_id>", methods=["GET", "POST"])
@login_required
def tenant_detail(tenant_id):
    token = session.get("admin_token", "")
    if request.method == "POST":
        is_active = request.form.get("is_active")
        if is_active is not None:
            api_put(f"/api/admin/tenants/{tenant_id}", token, {"is_active": int(is_active)})
        return redirect(url_for("tenant_detail", tenant_id=tenant_id))

    tenant = {}
    try:
        resp = api_get(f"/api/admin/tenants/{tenant_id}", token)
        if resp.status_code == 200:
            tenant = resp.json()
    except Exception:
        pass
    return render_template("tenant_detail.html", tenant=tenant, admin_phone=session.get("admin_phone"))


@app.route("/admin/profile", methods=["GET", "POST"])
@login_required
def profile():
    token = session.get("admin_token", "")
    if request.method == "POST":
        old_password = request.form.get("old_password", "")
        new_password = request.form.get("new_password", "")
        confirm_password = request.form.get("confirm_password", "")
        if not old_password or not new_password:
            return render_template("profile.html", admin_phone=session.get("admin_phone"), error="请填写完整", success=None)
        if new_password != confirm_password:
            return render_template("profile.html", admin_phone=session.get("admin_phone"), error="两次新密码不一致", success=None)
        if len(new_password) < 6:
            return render_template("profile.html", admin_phone=session.get("admin_phone"), error="新密码至少6位", success=None)
        try:
            resp = api_post(
                "/api/auth/change_password",
                {"old_password": old_password, "new_password": new_password},
                token=token,
            )
            if resp.status_code == 200:
                return render_template("profile.html", admin_phone=session.get("admin_phone"), error=None, success="密码修改成功")
            else:
                error = resp.json().get("error", "修改失败")
                return render_template("profile.html", admin_phone=session.get("admin_phone"), error=error, success=None)
        except Exception as e:
            return render_template("profile.html", admin_phone=session.get("admin_phone"), error=f"网络错误: {e}", success=None)
    return render_template("profile.html", admin_phone=session.get("admin_phone"), error=None, success=None)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080, debug=False)
