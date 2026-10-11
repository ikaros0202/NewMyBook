"""Prepare a reusable public TXT reader session, or close its emulator safely.

Python standard library only. No APK installation, app-data clearing, private
input, file search, or broad process termination. This does not launch an AVD.
"""

from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


REPO = Path(__file__).resolve().parents[2]
FIXTURE = REPO / "qa/current/1.0.2/fixtures/bookpage-long.txt"
FIXTURE_SHA = "fc03affa6fb964aad6dad67c12521d2241ecd2dae03de7fa51c0207615c29ddb"
USER_NAME = "NewMyBook_Public_QA"
AVD_NAME = "XinYue_API37"
PACKAGE = "com.xinyue.reader.debug"
FILE_NAME = "bookpage-long.txt"
BOOK_TITLE = "bookpage-long"
DOWNLOADS = "content://media/external_primary/downloads"


class Failure(RuntimeError):
    pass


class Session:
    def __init__(self, args):
        self.args = args
        self.user = None
        self.last_xml = None
        self.stage = "initial"
        self.events = []
        self.output = args.output_dir.resolve()
        self.output.mkdir(parents=True, exist_ok=True)
        self.run_id = args.command + "-" + datetime.now().strftime("%Y%m%dT%H%M%S%f")

    def record(self, stage, **values):
        self.stage = stage
        event = {"stage": stage, "time": datetime.now().astimezone().isoformat(), **values}
        self.events.append(event)
        print(json.dumps(event, ensure_ascii=False), file=sys.stderr, flush=True)

    def manifest_path(self):
        # Machine-local proof; not an application database or public evidence.
        return Path(os.environ["LOCALAPPDATA"]) / "NewMyBookQA" / (self.args.serial + "-session.json")

    def proven_book(self):
        path = self.manifest_path()
        if not path.exists():
            return False
        try:
            previous = json.loads(path.read_text(encoding="utf-8"))
        except (ValueError, OSError):
            raise Failure("LOCAL_SESSION_MANIFEST_INVALID") from None
        return previous == {"avd": AVD_NAME, "serial": self.args.serial, "android_user": self.user,
                            "public_user_name": USER_NAME, "package": PACKAGE, "book_title": BOOK_TITLE,
                            "fixture_sha256": FIXTURE_SHA, "import_verified": True}

    def save_proof(self):
        path = self.manifest_path()
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_suffix(".json.tmp")
        temporary.write_text(json.dumps(
            {"avd": AVD_NAME, "serial": self.args.serial, "android_user": self.user,
             "public_user_name": USER_NAME, "package": PACKAGE, "book_title": BOOK_TITLE,
             "fixture_sha256": FIXTURE_SHA, "import_verified": True}, indent=2) + "\n", encoding="utf-8")
        temporary.replace(path)

    def adb(self, *args, data=None, timeout=10, allow_failure=False):
        # One quoted remote command preserves SQL quotes and spaces through adb shell.
        command = [str(self.args.adb), "-s", self.args.serial]
        if args and args[0] == "shell":
            command += ["shell", shlex.join(str(x) for x in args[1:])]
        else:
            command += [str(x) for x in args]
        try:
            result = subprocess.run(command, input=data, stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE, timeout=timeout,
                                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        except subprocess.TimeoutExpired:
            raise Failure("ADB_COMMAND_TIMEOUT") from None
        except OSError:
            raise Failure("ADB_UNAVAILABLE") from None
        if result.returncode and not allow_failure:
            raise Failure("ADB_COMMAND_FAILED")
        return result

    def text(self, *args, **kwargs):
        return self.adb(*args, **kwargs).stdout.decode("utf-8", errors="replace").strip()

    def wait(self, predicate, seconds, reason):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if predicate():
                return
            time.sleep(0.4)
        raise Failure(reason)

    def endpoint(self):
        state = self.adb("get-state", allow_failure=True, timeout=3)
        if state.returncode or state.stdout.strip() != b"device":
            return False
        avd = self.text("emu", "avd", "name").splitlines()
        if not avd or avd[0].strip() != AVD_NAME:
            raise Failure("WRONG_AVD")
        if self.text("shell", "getprop", "ro.build.version.sdk") != "37":
            raise Failure("WRONG_ANDROID_API")
        return True

    def find_user(self):
        text = self.text("shell", "pm", "list", "users")
        matches = []
        for user_id, name, flags in re.findall(r"UserInfo\{(\d+):([^:}]+):([0-9a-fA-F]+)\}", text):
            if name == USER_NAME:
                matches.append((int(user_id), int(flags, 16)))
        if len(matches) > 1:
            raise Failure("AMBIGUOUS_PUBLIC_USER")
        if matches and (matches[0][0] == 0 or matches[0][1] & 0x100):
            raise Failure("PUBLIC_USER_IS_OWNER_OR_EPHEMERAL")
        return matches[0][0] if matches else None

    def current(self):
        value = self.text("shell", "am", "get-current-user")
        if not value.isdigit():
            raise Failure("CURRENT_USER_UNKNOWN")
        return int(value)

    def guard(self):
        if self.user is None or self.user == 0 or self.find_user() != self.user or self.current() != self.user:
            raise Failure("PUBLIC_QA_USER_GUARD_FAILED")

    def mutate(self, *args, **kwargs):
        self.guard()
        return self.text("shell", *args, **kwargs)

    def snapshot(self):
        remote = "/data/local/tmp/newmybook-public-qa.xml"
        deadline = time.monotonic() + 30
        for attempt in range(3):
            self.guard()  # Guard failures are never retried as an idle-UI issue.
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                break
            try:
                result = self.text("shell", "uiautomator", "dump", remote, timeout=min(12, remaining))
                if "dumped to" not in result:
                    raise Failure("FRESH_UI_DUMP_FAILED")
                data = self.adb("exec-out", "cat", remote, timeout=min(5, max(0.1, deadline - time.monotonic()))).stdout
                tree = ET.fromstring(data)
            except (Failure, ET.ParseError):
                if attempt < 2 and time.monotonic() < deadline:
                    time.sleep(0.4)
                    continue
                break
            self.guard()
            self.last_xml = data
            return tree
        raise Failure("FRESH_UI_DUMP_FAILED_AFTER_BOUNDED_RETRIES")

    def ui_wait(self, predicate, seconds=30):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            tree = self.snapshot()
            if predicate(tree):
                return tree
            time.sleep(0.3)
        raise Failure("UI_STAGE_TIMEOUT")

    @staticmethod
    def has(tree, label):
        return any(n.get("text") == label or n.get("content-desc") == label for n in tree.iter("node"))

    def tap(self, tree, label):
        parents = {child: parent for parent in tree.iter() for child in parent}
        targets = {}
        for node in tree.iter("node"):
            if node.get("text") != label and node.get("content-desc") != label:
                continue
            target = node
            while target.get("clickable") != "true" and target in parents:
                target = parents[target]
            if target.get("clickable") == "true" and target.get("enabled") == "true":
                targets[target.get("bounds", "")] = target
        if len(targets) != 1:
            raise Failure("UI_TARGET_MISSING_OR_AMBIGUOUS")
        bounds = next(iter(targets))
        numbers = [int(x) for x in re.findall(r"\d+", bounds)]
        if len(numbers) != 4 or numbers[2] <= numbers[0] or numbers[3] <= numbers[1]:
            raise Failure("UI_TARGET_BOUNDS_INVALID")
        self.mutate("input", "tap", (numbers[0] + numbers[2]) // 2, (numbers[1] + numbers[3]) // 2)

    def library(self):
        self.record("library-open")
        self.mutate("am", "force-stop", "--user", self.user, PACKAGE)
        self.mutate("am", "start", "-W", "--user", self.user, "-n", PACKAGE + "/com.xinyue.reader.MainActivity",
                    "-f", "0x10008000", timeout=20)
        return self.ui_wait(lambda t: self.has(t, "书架") and
                            (self.has(t, "导入 TXT") or self.has(t, "选择 TXT 文件")))

    def existing_book(self, tree):
        # Scan only this public user's shelf. Do not open a search interface.
        previous = None
        for _ in range(10):
            if self.has(tree, BOOK_TITLE):
                return tree
            visible = tuple(n.get("text", "") for n in tree.iter("node") if n.get("text"))
            if visible == previous:
                return None
            previous = visible
            size = self.text("shell", "wm", "size")
            dimensions = re.findall(r"(\d+)x(\d+)", size)
            if not dimensions:
                raise Failure("DISPLAY_SIZE_UNKNOWN")
            width, height = map(int, dimensions[-1])
            self.mutate("input", "swipe", width // 2, height * 4 // 5, width // 2, height // 3, 250)
            tree = self.snapshot()
        raise Failure("PUBLIC_SHELF_SCAN_TIMEOUT")

    def media_row(self):
        result = self.adb("shell", "content", "query", "--user", self.user, "--uri", DOWNLOADS,
                          "--projection", "_id", "--where", "_display_name='" + FILE_NAME + "'",
                          allow_failure=True)
        text = result.stdout.decode("utf-8", errors="replace")
        combined = text + result.stderr.decode("utf-8", errors="replace")
        if result.returncode or re.search(r"Exception|Error while|Could not find provider|Error:", combined):
            return False, []
        ids = re.findall(r"\b_id=(\d+)\b", text)
        if len(ids) > 1:
            raise Failure("AMBIGUOUS_PUBLIC_DOWNLOAD")
        return True, ids

    def prepare_file(self, data):
        self.record("media-provider-wait")
        self.wait(lambda: self.media_row()[0], 60, "MEDIAPROVIDER_TIMEOUT")
        self.guard()
        _, ids = self.media_row()
        if not ids:
            self.mutate("content", "insert", "--user", self.user, "--uri", DOWNLOADS,
                        "--bind", "_display_name:s:" + FILE_NAME, "--bind", "mime_type:s:text/plain",
                        "--bind", "relative_path:s:Download")
            _, ids = self.media_row()  # Some Android builds omit the insert result URI.
            if not ids:
                raise Failure("MEDIASTORE_INSERT_ID_MISSING")
        uri = DOWNLOADS + "/" + ids[0]
        self.guard()
        # content read preserves this fixture's CRLF bytes through shell+pipe;
        # exec-out on the installed emulator drops CR bytes in this command.
        current = self.adb("shell", "content", "read", "--user", self.user, "--uri", uri,
                           allow_failure=True, timeout=15)
        reused = current.returncode == 0 and hashlib.sha256(current.stdout).hexdigest() == FIXTURE_SHA
        if not reused:
            self.guard()
            self.adb("shell", "content", "write", "--user", self.user, "--uri", uri,
                     data=data, timeout=20)
            self.guard()
            verified = self.adb("shell", "content", "read", "--user", self.user, "--uri", uri, timeout=15)
            if hashlib.sha256(verified.stdout).hexdigest() != FIXTURE_SHA:
                raise Failure("MEDIASTORE_READBACK_MISMATCH")
        self.record("public-file-ready", file=FILE_NAME, sha256=FIXTURE_SHA, reused=reused)

    def import_book(self, tree):
        self.record("import-ui")
        self.tap(tree, "导入 TXT" if self.has(tree, "导入 TXT") else "选择 TXT 文件")
        tree = self.ui_wait(lambda t: self.has(t, "手动选择编码"))
        self.record("encoding-select")
        self.tap(tree, "手动选择编码")
        tree = self.ui_wait(lambda t: self.has(t, "文本编码") and self.has(t, "UTF-8"))
        self.tap(tree, "UTF-8")
        self.record("file-picker")
        tree = self.ui_wait(lambda t: self.has(t, FILE_NAME) or self.has(t, "显示根目录") or
                            self.has(t, "Show roots") or self.has(t, "Downloads") or self.has(t, "下载"))
        if not self.has(tree, FILE_NAME):
            root_label = next((s for s in ("显示根目录", "Show roots") if self.has(tree, s)), None)
            if root_label:
                self.tap(tree, root_label)
                tree = self.ui_wait(lambda t: self.has(t, "Downloads") or self.has(t, "下载"))
            label = next((s for s in ("Downloads", "下载") if self.has(tree, s)), None)
            if not label:
                raise Failure("DOWNLOADS_ROOT_MISSING")
            self.tap(tree, label)
            tree = self.ui_wait(lambda t: self.has(t, FILE_NAME))
        self.tap(tree, FILE_NAME)
        self.record("import-wait-result")

        def complete(t):
            if self.has(t, "按此编码导入"):
                self.record("encoding-confirm")
                if not self.has(t, "UTF-8"):
                    raise Failure("UTF8_CONFIRMATION_MISSING")
                self.tap(t, "UTF-8")
                self.tap(self.snapshot(), "按此编码导入")
                return False
            if self.has(t, "导入失败") or self.has(t, "未加入导入队列"):
                raise Failure("PUBLIC_FIXTURE_IMPORT_FAILED")
            if self.has(t, "需要处理重复内容"):
                raise Failure("PUBLIC_FIXTURE_DUPLICATE_NEEDS_DECISION")
            return self.has(t, FILE_NAME) and (self.has(t, "导入成功") or self.has(t, "已跳过重复内容"))

        self.ui_wait(complete, 90)
        # Return without force-stopping a possibly just-enqueued import worker.
        # A persisted older terminal result alone cannot establish new success.
        self.record("import-shelf-confirm")
        self.mutate("input", "keyevent", "4")
        self.ui_wait(lambda t: self.has(t, "书架") and self.has(t, BOOK_TITLE), 30)
        self.record("import-result", success=True)

    def prepare(self):
        self.record("fixture-check")
        data = FIXTURE.read_bytes()
        if hashlib.sha256(data).hexdigest() != FIXTURE_SHA:
            raise Failure("PUBLIC_FIXTURE_WHITELIST_HASH_MISMATCH")
        data.decode("utf-8", errors="strict")
        self.record("endpoint-wait", serial=self.args.serial, avd=AVD_NAME, api=37)
        self.wait(self.endpoint, 60, "EMULATOR_NOT_READY")
        self.wait(lambda: self.text("shell", "getprop", "sys.boot_completed") == "1", 120, "BOOT_TIMEOUT")
        self.user = self.find_user()
        if self.current() not in (0, self.user):
            raise Failure("ANOTHER_ANDROID_USER_IS_ACTIVE")
        if self.user is None:
            created = self.text("shell", "pm", "create-user", USER_NAME)
            self.user = self.find_user()
            if self.user is None or "Success" not in created:
                raise Failure("PUBLIC_USER_CREATION_FAILED")
        self.record("public-user", android_user=self.user, name=USER_NAME)
        self.record("user-switch")
        self.text("shell", "am", "switch-user", self.user)
        self.wait(lambda: self.current() == self.user, 30, "USER_SWITCH_TIMEOUT")
        self.mutate("input", "keyevent", 224)
        self.mutate("wm", "dismiss-keyguard")
        self.record("user-unlock")
        self.wait(lambda: "RUNNING_UNLOCKED" in self.text("shell", "am", "get-started-user-state", self.user),
                  30, "USER_UNLOCK_TIMEOUT")
        self.mutate("settings", "--user", self.user, "put", "secure", "user_setup_complete", "1")
        self.record("app-enable")
        installed = self.text("shell", "pm", "path", "--user", "0", PACKAGE)
        if not installed.startswith("package:"):
            raise Failure("EXISTING_DEBUG_APK_NOT_INSTALLED")
        self.mutate("cmd", "package", "install-existing", "--user", self.user, PACKAGE)
        self.record("library-check")
        tree = self.library()
        if self.existing_book(tree) is not None:
            if not self.proven_book():
                raise Failure("EXISTING_TITLE_WITHOUT_MATCHING_LOCAL_PROOF")
            self.record("ready", android_user=self.user, book=BOOK_TITLE, reused_book=True,
                        fixture_sha256=FIXTURE_SHA, public_qa_guard=True)
            return
        self.prepare_file(data)
        tree = self.library()
        self.import_book(tree)
        self.save_proof()
        self.record("ready", android_user=self.user, book=BOOK_TITLE, reused_book=False,
                    fixture_sha256=FIXTURE_SHA, public_qa_guard=True)

    def host_processes(self):
        if os.name != "nt":
            raise Failure("WINDOWS_EMULATOR_PROCESS_CHECK_REQUIRED")
        running = Path(os.environ["LOCALAPPDATA"]) / "Temp/avd/running"
        pid = None
        for ini in running.glob("pid_*.ini"):
            values = dict(line.split("=", 1) for line in ini.read_text(encoding="utf-8").splitlines() if "=" in line)
            if values.get("port.serial") == self.args.serial.removeprefix("emulator-"):
                if values.get("avd.name") != AVD_NAME:
                    raise Failure("DISCOVERY_AVD_MISMATCH")
                pid = int(ini.stem.removeprefix("pid_"))
                break
        if pid is None:
            raise Failure("EMULATOR_PROCESS_DISCOVERY_MISSING")
        query = (f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId={pid}'; "
                 "$q=Get-CimInstance Win32_Process -Filter ('ProcessId='+$p.ParentProcessId); "
                 "@($p,$q)|Select-Object Name,ProcessId|ConvertTo-Json -Compress")
        result = subprocess.run(["powershell", "-NoProfile", "-Command", query],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=10,
                                creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            processes = json.loads(result.stdout.decode("utf-8-sig"))
        except (ValueError, UnicodeError):
            raise Failure("EMULATOR_PROCESS_LOOKUP_FAILED") from None
        if not isinstance(processes, list) or len(processes) != 2 or {
            p["Name"] for p in processes
        } != {"qemu-system-x86_64.exe", "emulator.exe"}:
            raise Failure("EMULATOR_PROCESS_OWNERSHIP_UNCONFIRMED")
        kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel.OpenProcess.argtypes = (wintypes.DWORD, wintypes.BOOL, wintypes.DWORD)
        kernel.OpenProcess.restype = wintypes.HANDLE
        kernel.WaitForSingleObject.argtypes = (wintypes.HANDLE, wintypes.DWORD)
        kernel.CloseHandle.argtypes = (wintypes.HANDLE,)
        handles = [kernel.OpenProcess(0x100000, False, p["ProcessId"]) for p in processes]
        if not all(handles):
            for handle in handles:
                if handle:
                    kernel.CloseHandle(handle)
            raise Failure("EMULATOR_PROCESS_HANDLE_UNAVAILABLE")
        return kernel, handles, {p["ProcessId"] for p in processes}

    @staticmethod
    def windows_for(pids):
        user32 = ctypes.WinDLL("user32", use_last_error=True)
        callback_type = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
        user32.GetWindowThreadProcessId.argtypes = (wintypes.HWND, ctypes.POINTER(wintypes.DWORD))
        windows = []

        @callback_type
        def visit(hwnd, _):
            pid = wintypes.DWORD()
            user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            if pid.value in pids:
                windows.append(hwnd)
            return True

        user32.EnumWindows(visit, 0)
        return windows

    def offline_host_absent(self):
        # Offline ADB alone is not proof that the emulator window disappeared.
        # Inspect only process identity; never return command lines or tokens.
        if os.name != "nt":
            raise Failure("WINDOWS_EMULATOR_PROCESS_CHECK_REQUIRED")
        query = ("$p=@(Get-CimInstance Win32_Process -Filter "
                 "\"Name='emulator.exe' OR Name='qemu-system-x86_64.exe'\" | "
                 "Where-Object {$_.CommandLine -match '\\bXinYue_API37\\b' -or "
                 "[string]::IsNullOrWhiteSpace($_.CommandLine)}); $p.Count")
        result = subprocess.run(["powershell", "-NoProfile", "-Command", query],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=10,
                                creationflags=subprocess.CREATE_NO_WINDOW)
        if result.returncode or result.stdout.strip() != b"0":
            raise Failure("ADB_OFFLINE_HOST_CLOSE_UNCONFIRMED")
        return True

    def stop(self):
        self.record("stop-endpoint-check", serial=self.args.serial)
        if not self.endpoint():
            self.offline_host_absent()
            self.record("stopped", already_off=True, process_exit_verified=True, windows_closed=True)
            return
        self.user = self.find_user()
        if self.current() not in (0, self.user):
            raise Failure("PUBLIC_SESSION_OWNERSHIP_UNCONFIRMED")
        kernel, handles, pids = self.host_processes()
        try:
            if self.user is not None:
                if self.current() == self.user:
                    self.mutate("am", "force-stop", "--user", self.user, PACKAGE)
                self.text("shell", "am", "switch-user", "0")
                self.wait(lambda: self.current() == 0, 30, "OWNER_RESTORE_TIMEOUT")
                if self.find_user() != self.user:
                    raise Failure("PUBLIC_USER_IDENTITY_CHANGED")
                stopped = self.text("shell", "am", "stop-user", "-w", self.user, timeout=20)
                if "Error" in stopped or "Failure" in stopped:
                    raise Failure("PUBLIC_USER_STOP_FAILED")
                self.record("public-user-stopped", android_user=self.user, owner_restored=True, books_preserved=True)
            else:
                self.record("public-user-absent", owner_active=True)
            self.adb("emu", "kill", timeout=10)

            def gone():
                state = self.adb("get-state", timeout=3, allow_failure=True)
                return (state.returncode != 0 and all(kernel.WaitForSingleObject(h, 0) == 0 for h in handles)
                        and not self.windows_for(pids))

            self.wait(gone, 30, "EMULATOR_CLOSE_TIMEOUT")
            self.record("stopped", process_exit_verified=True, windows_closed=True, books_preserved=True)
        finally:
            for handle in handles:
                kernel.CloseHandle(handle)

    def report(self, error=None):
        report = {"command": self.args.command, "serial": self.args.serial, "public_user_name": USER_NAME,
                  "android_user": self.user, "success": error is None, "stage": self.stage,
                  "failure": error, "events": self.events}
        (self.output / (self.run_id + ".json")).write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        if error and self.last_xml:
            (self.output / (self.run_id + "-last-public-ui.xml")).write_bytes(self.last_xml)
        print(json.dumps({"success": error is None, "stage": self.stage, "failure": error,
                          "report": self.run_id + ".json"}, ensure_ascii=False), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("prepare", "stop"))
    parser.add_argument("--serial", default="emulator-5554", help="Explicit emulator endpoint (default: emulator-5554)")
    parser.add_argument("--adb", type=Path, default=Path(os.environ.get("USERPROFILE", "")) /
                        ".cache/newmybook-toolchain/android-sdk/platform-tools/adb.exe")
    parser.add_argument("--output-dir", type=Path, default=REPO / "qa/current/public-reader-session")
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-\d+", args.serial):
        parser.error("Only an explicitly named local emulator endpoint is supported")
    session = Session(args)
    try:
        getattr(session, args.command)()
        session.report()
        return 0
    except (Failure, OSError, UnicodeError, subprocess.SubprocessError) as error:
        # Never expose raw command output, arbitrary local paths, or discovery tokens.
        code = str(error) if isinstance(error, Failure) else type(error).__name__
        session.report(code)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
