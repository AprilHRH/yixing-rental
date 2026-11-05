import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
import uuid
from datetime import datetime, timedelta

from playwright.sync_api import expect, sync_playwright


ROOT = Path(__file__).resolve().parents[1]
FRONTEND_PORT = 15175
BACKEND_PORT = 18082
BASE = f"http://127.0.0.1:{FRONTEND_PORT}"


def wait_port(port, process):
    for _ in range(120):
        if process.poll() is not None:
            raise RuntimeError(f"Service exited on port {port}")
        with socket.socket() as connection:
            if connection.connect_ex(("127.0.0.1", port)) == 0:
                return
        time.sleep(0.25)
    raise RuntimeError(f"Service did not start on port {port}")


def main():
    jdk = next((path for path in (ROOT / ".tools").glob("jdk*") if path.is_dir()), None)
    java = str(jdk / "bin/java.exe") if jdk else shutil.which("java")
    node = shutil.which("node")
    if not node and Path("D:/edge/NodeJs/node.exe").is_file():
        node = "D:/edge/NodeJs/node.exe"
    assert java and node, "Java and Node are required"
    children = []
    with tempfile.TemporaryDirectory(prefix="yixing-pickup-") as temporary:
        directory = Path(temporary)
        with (directory / "backend.log").open("w", encoding="utf-8") as backend_log, \
             (directory / "frontend.log").open("w", encoding="utf-8") as frontend_log:
            try:
                flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
                children.append(subprocess.Popen(
                    [java, "-jar", str(ROOT / "backend/target/rental-system-1.0.0.jar"),
                     f"--server.port={BACKEND_PORT}",
                     "--spring.datasource.url=jdbc:h2:mem:pickup;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                     f"--app.allowed-origins={BASE}"],
                    cwd=directory, stdin=subprocess.DEVNULL, stdout=backend_log,
                    stderr=subprocess.STDOUT, creationflags=flags))
                env = {**os.environ, "FRONTEND_PORT": str(FRONTEND_PORT),
                       "API_TARGET": f"http://127.0.0.1:{BACKEND_PORT}"}
                children.append(subprocess.Popen(
                    [node, str(ROOT / "frontend/node_modules/vite/bin/vite.js")],
                    cwd=ROOT / "frontend", env=env, stdin=subprocess.DEVNULL,
                    stdout=frontend_log, stderr=subprocess.STDOUT, creationflags=flags))
                wait_port(BACKEND_PORT, children[0])
                wait_port(FRONTEND_PORT, children[1])

                with sync_playwright() as playwright:
                    edge = Path("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe")
                    options = {"headless": True, "args": ["--disable-gpu"]}
                    if edge.is_file():
                        options["executable_path"] = str(edge)
                    browser = playwright.chromium.launch(**options)
                    page = browser.new_page()
                    page.goto(BASE + "/#/login", wait_until="networkidle")
                    page.get_by_role("button", name="登录工作空间").click()
                    expect(page.get_by_role("heading", name="工作台")).to_be_visible()

                    client = page.request
                    headers = {"X-Requested-With": "XMLHttpRequest"}
                    data = client.get(BASE + "/api/bootstrap", headers=headers).json()
                    car = next(item for item in data["vehicles"] if item["status"] == "AVAILABLE")
                    customer = client.post(BASE + "/api/customers", headers=headers, data={
                        "name": "交车验收客户", "identity_no": "PICKUP-" + uuid.uuid4().hex[:12],
                        "phone": "13800009999", "license_no": "PICKUP-DRIVER",
                        "license_expiry": "2030-12-31", "category": "NORMAL", "restricted": False,
                    })
                    assert customer.ok, customer.text()
                    start = datetime.now().replace(microsecond=0) + timedelta(minutes=1)
                    end = start + timedelta(days=2)
                    order_response = client.post(BASE + "/api/orders", headers=headers, data={
                        "vehicle_id": car["id"], "customer_id": customer.json()["id"],
                        "pickup_branch": car["branch_id"], "return_branch": car["branch_id"],
                        "planned_start": start.isoformat(), "planned_end": end.isoformat(),
                        "billing_mode": "DAILY", "expected_mileage": 200,
                    })
                    assert order_response.ok, order_response.text()
                    order = order_response.json()
                    page.goto(BASE + "/#/orders", wait_until="networkidle")
                    page.reload(wait_until="networkidle")
                    expect(page.get_by_text(order["order_no"], exact=True)).to_be_visible()
                    page.get_by_role("button", name="查看 / 办理", exact=True).click()
                    drawer = page.locator(".el-drawer:visible")
                    collect_button = drawer.get_by_role("button", name="登记收款", exact=True)
                    expect(collect_button).to_be_visible()

                    collect_button.click()
                    dialog = page.locator(".el-dialog:visible")
                    with page.expect_response(lambda response: response.url.endswith("/collect")) as collected:
                        dialog.get_by_role("button", name="确认保存").click()
                    assert collected.value.ok, collected.value.text()
                    expect(dialog).not_to_be_visible()

                    drawer.get_by_role("button", name="办理交车", exact=True).click()
                    dialog = page.locator(".el-dialog:visible")
                    with page.expect_response(lambda response: response.url.endswith("/pickup")) as picked_up:
                        dialog.get_by_role("button", name="确认保存").click()
                    assert picked_up.value.ok, picked_up.value.text()
                    expect(dialog).not_to_be_visible()
                    expect(drawer.locator(".order-detail-heading")).to_contain_text("租赁中")
                    result = client.get(BASE + "/api/bootstrap", headers=headers).json()
                    saved = next(item for item in result["orders"] if item["id"] == order["id"])
                    assert saved["status"] == "ACTIVE"
                    browser.close()
                    print("PASS: order UI registered deposit, sent pickup request, and persisted ACTIVE status")
            finally:
                for child in reversed(children):
                    child.terminate()
                for child in reversed(children):
                    try:
                        child.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        child.kill()
                        child.wait(timeout=5)


if __name__ == "__main__":
    main()
