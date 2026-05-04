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


def api_delete(path, token):
    return http_requests.delete(
        f"{API_BASE_URL}{path}",
        headers={"Authorization": f"Bearer {token}"},
        timeout=10
    )


def api_post_json(path, json_data, token):
    return http_requests.post(
        f"{API_BASE_URL}{path}",
        json=json_data,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        timeout=30
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
    error = None
    success = None

    if request.method == "POST":
        action = request.form.get("action")
        if action == "toggle_status":
            is_active = request.form.get("is_active")
            if is_active is not None:
                api_put(f"/api/admin/tenants/{tenant_id}", token, {"is_active": int(is_active)})
            return redirect(url_for("tenant_detail", tenant_id=tenant_id)))
        elif action == "save_default_model":
            model_data = {
                "name": request.form.get("name", "").strip(),
                "model_type": request.form.get("model_type", "OPENAI").strip(),
                "model": request.form.get("model", "").strip(),
                "api_key": request.form.get("api_key", "").strip(),
                "api_endpoint": request.form.get("api_endpoint", "").strip(),
                "temperature": float(request.form.get("temperature", 0.7)),
                "max_tokens": int(request.form.get("max_tokens", 2000)),
                "enabled": 1 if request.form.get("enabled") else 0,
            }
            try:
                resp = api_post(f"/api/admin/tenants/{tenant_id}/default-model", model_data, token=token)
                if resp.status_code in (200, 201):
                    success = "默认模型配置已保存"
                else:
                    error = resp.json().get("error", "保存失败")
            except Exception as e:
                error = f"网络错误: {e}"
            return render_template("tenant_detail.html", tenant=_get_tenant(tenant_id, token),
                                   default_model=_get_default_model(tenant_id, token),
                                   admin_phone=session.get("admin_phone"), error=error, success=success)
        elif action == "delete_default_model":
            try:
                resp = api_delete(f"/api/admin/tenants/{tenant_id}/default-model", token)
                if resp.status_code == 200:
                    success = "默认模型配置已删除"
                else:
                    error = resp.json().get("error", "删除失败")
            except Exception as e:
                error = f"网络错误: {e}"
            return render_template("tenant_detail.html", tenant=_get_tenant(tenant_id, token),
                                   default_model=None,
                                   admin_phone=session.get("admin_phone"), error=error, success=success)

    tenant = _get_tenant(tenant_id, token)
    default_model = _get_default_model(tenant_id, token)
    return render_template("tenant_detail.html", tenant=tenant, default_model=default_model,
                           admin_phone=session.get("admin_phone"), error=error, success=success)


def _get_tenant(tenant_id, token):
    try:
        resp = api_get(f"/api/admin/tenants/{tenant_id}", token)
        if resp.status_code == 200:
            return resp.json()
    except Exception:
        pass
    return {}


def _get_default_model(tenant_id, token):
    try:
        resp = api_get(f"/api/admin/tenants/{tenant_id}/default-model", token)
        if resp.status_code == 200:
            return resp.json()
    except Exception:
        pass
    return None


@app.route("/admin/default-model", methods=["GET", "POST"])
@login_required
def default_model():
    token = session.get("admin_token", "")
    error = None
    success = None

    if request.method == "POST":
        action = request.form.get("action")
        if action == "apply_to_tenant":
            tenant_id = request.form.get("tenant_id", "").strip()
            if not tenant_id:
                error = "请输入租户 ID"
            else:
                # 获取当前全局默认配置
                current = None
                try:
                    resp = api_get("/api/admin/tenants/_global/default-model", token)
                    if resp.status_code == 200:
                        current = resp.json()
                except Exception:
                    pass
                if not current:
                    error = "请先保存全局默认模型配置"
                else:
                    model_data = {
                        "name": current.get("name", ""),
                        "model_type": current.get("model_type", "OPENAI"),
                        "model": current.get("model", ""),
                        "api_key": current.get("api_key", ""),
                        "api_endpoint": current.get("api_endpoint", ""),
                        "temperature": current.get("temperature", 0.7),
                        "max_tokens": current.get("max_tokens", 2000),
                        "enabled": 1,
                    }
                    try:
                        resp = api_post(f"/api/admin/tenants/{tenant_id}/default-model", model_data, token=token)
                        if resp.status_code in (200, 201):
                            success = f"已应用到租户 {tenant_id[:12]}..."
                        else:
                            error = resp.json().get("error", "应用失败")
                    except Exception as e:
                        error = f"网络错误: {e}"
        else:
            # 保存全局默认（tenant_id = _global）
            model_data = {
                "name": request.form.get("name", "").strip(),
                "model_type": request.form.get("model_type", "OPENAI").strip(),
                "model": request.form.get("model", "").strip(),
                "api_key": request.form.get("api_key", "").strip(),
                "api_endpoint": request.form.get("api_endpoint", "").strip(),
                "temperature": float(request.form.get("temperature", 0.7)),
                "max_tokens": int(request.form.get("max_tokens", 2000)),
                "enabled": 1 if request.form.get("enabled") else 0,
            }
            try:
                resp = api_post("/api/admin/tenants/_global/default-model", model_data, token=token)
                if resp.status_code in (200, 201):
                    success = "全局默认模型已保存"
                else:
                    error = resp.json().get("error", "保存失败")
            except Exception as e:
                error = f"网络错误: {e}"

    # 获取当前全局默认
    current_model = None
    try:
        resp = api_get("/api/admin/tenants/_global/default-model", token)
        if resp.status_code == 200:
            current_model = resp.json()
    except Exception:
        pass

    return render_template("default_model.html", model=current_model,
                           admin_phone=session.get("admin_phone"), error=error, success=success)


@app.route("/admin/tenants/<tenant_id>/rules")
@login_required
def admin_tenant_rules(tenant_id):
    """租户知识库规则管理页面"""
    token = session.get("admin_token", "")
    tenant = _get_tenant(tenant_id, token)
    rules = []
    try:
        resp = api_get(f"/api/admin/tenants/{tenant_id}/rules", token)
        if resp.status_code == 200:
            rules = resp.json()
    except Exception:
        pass
    return render_template(
        "tenant_rules.html",
        tenant=tenant,
        rules=rules,
        admin_phone=session.get("admin_phone"),
    )


@app.route("/admin/tenants/<tenant_id>/rules/add", methods=["POST"])
@login_required
def admin_tenant_rule_add(tenant_id):
    """管理员为租户新增规则"""
    token = session.get("admin_token", "")
    data = {
        "keyword": request.form.get("keyword", "").strip(),
        "match_type": request.form.get("match_type", "CONTAINS").strip(),
        "reply_template": request.form.get("reply_template", "").strip(),
        "category": request.form.get("category", "").strip(),
        "target_type": request.form.get("target_type", "ALL").strip(),
        "target_names": request.form.get("target_names", "[]").strip(),
        "priority": int(request.form.get("priority", 0)),
        "enabled": 1 if request.form.get("enabled") else 0,
    }
    try:
        resp = api_post_json(f"/api/admin/tenants/{tenant_id}/rules", data, token)
        if resp.status_code in (200, 201):
            flash("规则添加成功", "success")
        else:
            flash(resp.json().get("error", "添加失败"), "error")
    except Exception as e:
        flash(f"网络错误: {e}", "error")
    return redirect(url_for("admin_tenant_rules", tenant_id=tenant_id))


@app.route("/admin/tenants/<tenant_id>/rules/<int:rule_id>/edit", methods=["POST"])
@login_required
def admin_tenant_rule_edit(tenant_id, rule_id):
    """管理员编辑租户规则"""
    token = session.get("admin_token", "")
    data = {
        "keyword": request.form.get("keyword", "").strip(),
        "match_type": request.form.get("match_type", "CONTAINS").strip(),
        "reply_template": request.form.get("reply_template", "").strip(),
        "category": request.form.get("category", "").strip(),
        "target_type": request.form.get("target_type", "ALL").strip(),
        "target_names": request.form.get("target_names", "[]").strip(),
        "priority": int(request.form.get("priority", 0)),
        "enabled": 1 if request.form.get("enabled") else 0,
    }
    try:
        resp = api_put(f"/api/admin/tenants/{tenant_id}/rules/{rule_id}", token, data)
        if resp.status_code == 200:
            flash("规则更新成功", "success")
        else:
            flash(resp.json().get("error", "更新失败"), "error")
    except Exception as e:
        flash(f"网络错误: {e}", "error")
    return redirect(url_for("admin_tenant_rules", tenant_id=tenant_id))


@app.route("/admin/tenants/<tenant_id>/rules/<int:rule_id>/delete", methods=["POST"])
@login_required
def admin_tenant_rule_delete(tenant_id, rule_id):
    """管理员删除租户规则"""
    token = session.get("admin_token", "")
    try:
        resp = api_delete(f"/api/admin/tenants/{tenant_id}/rules/{rule_id}", token)
        if resp.status_code == 200:
            flash("规则已删除", "success")
        else:
            flash(resp.json().get("error", "删除失败"), "error")
    except Exception as e:
        flash(f"网络错误: {e}", "error")
    return redirect(url_for("admin_tenant_rules", tenant_id=tenant_id))


@app.route("/admin/tenants/<tenant_id>/rules/batch", methods=["POST"])
@login_required
def admin_tenant_rules_batch(tenant_id):
    """管理员批量导入规则（JSON 格式）"""
    token = session.get("admin_token", "")
    import_data = request.form.get("import_data", "").strip()
    if not import_data:
        flash("请输入 JSON 数据", "error")
        return redirect(url_for("admin_tenant_rules", tenant_id=tenant_id))
    try:
        parsed = json.loads(import_data)
        rules = parsed if isinstance(parsed, list) else parsed.get("rules", [])
        if not rules:
            flash("JSON 中没有可导入的规则", "error")
            return redirect(url_for("admin_tenant_rules", tenant_id=tenant_id))
        resp = api_post_json(f"/api/admin/tenants/{tenant_id}/rules/batch", {"rules": rules}, token)
        if resp.status_code == 200:
            count = resp.json().get("count", len(rules))
            flash(f"成功导入 {count} 条规则", "success")
        else:
            flash(resp.json().get("error", "导入失败"), "error")
    except json.JSONDecodeError:
        flash("JSON 格式错误，请检查后重试", "error")
    except Exception as e:
        flash(f"网络错误: {e}", "error")
    return redirect(url_for("admin_tenant_rules", tenant_id=tenant_id))


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
