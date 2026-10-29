import ctypes
from ctypes import wintypes
from pathlib import Path
import subprocess
import sys
import time
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]


def main():
    if sys.platform != 'win32':
        print('SKIP: Windows launcher UI test')
        return
    user = ctypes.windll.user32
    user.SetProcessDPIAware()
    user.FindWindowW.argtypes = [wintypes.LPCWSTR, wintypes.LPCWSTR]
    user.FindWindowW.restype = wintypes.HWND
    user.SendMessageW.argtypes = [wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM]
    user.SendMessageW.restype = ctypes.c_ssize_t
    user.GetWindowTextLengthW.argtypes = [wintypes.HWND]
    user.GetWindowTextW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
    user.IsWindowEnabled.argtypes = [wintypes.HWND]
    user.PostMessageW.argtypes = [wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM]
    user.GetWindowRect.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.RECT)]
    user.SetForegroundWindow.argtypes = [wintypes.HWND]
    callback_type = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    user.EnumChildWindows.argtypes = [wintypes.HWND, callback_type, wintypes.LPARAM]

    def text(handle):
        buffer = ctypes.create_unicode_buffer(user.SendMessageW(handle, 0x000E, 0, 0) + 1)
        user.SendMessageW(handle, 0x000D, len(buffer), ctypes.cast(buffer, ctypes.c_void_p).value)
        return buffer.value

    def children(handle):
        controls = []
        callback = callback_type(lambda child, context: controls.append((child, text(child))) or True)
        user.EnumChildWindows(handle, callback, 0)
        return controls

    process = subprocess.Popen([str(ROOT / '易行租车启动器.exe'), '--no-browser'], cwd=ROOT)
    handle = None
    try:
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            handle = user.FindWindowW(None, '易行租车 · 快速启动')
            if handle and any(label == '启动并打开系统' for control, label in children(handle)):
                break
            time.sleep(.1)
        assert handle, 'Launcher window did not appear'
        controls = dict((label, control) for control, label in children(handle))
        assert '启动并打开系统' in controls
        assert not user.IsWindowEnabled(controls['打开网页'])
        user.PostMessageW(controls['启动并打开系统'], 0x00F5, 0, 0)
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            controls = children(handle)
            if any('已连接，不接管原有服务' in label for control, label in controls):
                break
            time.sleep(.1)
        else:
            raise AssertionError(controls)
        labels = dict((label, control) for control, label in controls)
        assert user.IsWindowEnabled(labels['打开网页'])
        assert not user.IsWindowEnabled(labels['停止本次服务'])
        assert any('不会重复启动或停止原有服务' in label for control, label in controls), controls
        deadline = time.monotonic() + 8
        while not user.IsWindowEnabled(labels['启动并打开系统']) and time.monotonic() < deadline:
            time.sleep(.1)
        assert user.IsWindowEnabled(labels['启动并打开系统'])
        assert any('已连接，不接管原有服务' in label for control, label in children(handle)), children(handle)
        user.SetForegroundWindow(handle)
        time.sleep(.4)
        rectangle = wintypes.RECT()
        user.GetWindowRect(handle, ctypes.byref(rectangle))
        graphics = ctypes.windll.gdi32
        user.GetWindowDC.argtypes = [wintypes.HWND]
        user.GetWindowDC.restype = wintypes.HDC
        user.PrintWindow.argtypes = [wintypes.HWND, wintypes.HDC, wintypes.UINT]
        user.ReleaseDC.argtypes = [wintypes.HWND, wintypes.HDC]
        graphics.CreateCompatibleDC.argtypes = [wintypes.HDC]
        graphics.CreateCompatibleDC.restype = wintypes.HDC
        graphics.CreateCompatibleBitmap.argtypes = [wintypes.HDC, ctypes.c_int, ctypes.c_int]
        graphics.CreateCompatibleBitmap.restype = wintypes.HBITMAP
        graphics.SelectObject.argtypes = [wintypes.HDC, wintypes.HGDIOBJ]
        graphics.SelectObject.restype = wintypes.HGDIOBJ
        graphics.DeleteObject.argtypes = [wintypes.HGDIOBJ]
        graphics.DeleteDC.argtypes = [wintypes.HDC]

        class BitmapHeader(ctypes.Structure):
            _fields_ = [('size', wintypes.DWORD), ('width', wintypes.LONG), ('height', wintypes.LONG), ('planes', wintypes.WORD), ('bit_count', wintypes.WORD), ('compression', wintypes.DWORD), ('image_size', wintypes.DWORD), ('horizontal', wintypes.LONG), ('vertical', wintypes.LONG), ('colors', wintypes.DWORD), ('important', wintypes.DWORD)]

        graphics.GetDIBits.argtypes = [wintypes.HDC, wintypes.HBITMAP, wintypes.UINT, wintypes.UINT, ctypes.c_void_p, ctypes.POINTER(BitmapHeader), wintypes.UINT]
        width, height = rectangle.right - rectangle.left, rectangle.bottom - rectangle.top
        window_dc = user.GetWindowDC(handle)
        memory_dc = graphics.CreateCompatibleDC(window_dc)
        bitmap = graphics.CreateCompatibleBitmap(window_dc, width, height)
        previous = graphics.SelectObject(memory_dc, bitmap)
        try:
            assert user.PrintWindow(handle, memory_dc, 2)
            header = BitmapHeader(size=ctypes.sizeof(BitmapHeader), width=width, height=-height, planes=1, bit_count=32)
            pixels = ctypes.create_string_buffer(width * height * 4)
            assert graphics.GetDIBits(memory_dc, bitmap, 0, height, pixels, ctypes.byref(header), 0)
            image = Image.frombytes('RGB', (width, height), pixels.raw, 'raw', 'BGRX')
            image.crop(image.getbbox()).save(ROOT / 'docs/previews/current/launcher.png')
        finally:
            graphics.SelectObject(memory_dc, previous)
            graphics.DeleteObject(bitmap)
            graphics.DeleteDC(memory_dc)
            user.ReleaseDC(handle, window_dc)
        user.PostMessageW(handle, 0x0010, 0, 0)
        process.wait(timeout=5)
        result = subprocess.run([sys.executable, str(ROOT / 'scripts/run.py'), '--check'], capture_output=True, text=True, encoding='utf-8')
        assert result.returncode == 0, result.stdout
        print('PASS: native EXE renders; start connects to existing project, enables browser action and disables external-service stop; original services survive launcher close.')
    finally:
        if process.poll() is None:
            if handle:
                user.PostMessageW(handle, 0x0010, 0, 0)
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.wait(timeout=3)


if __name__ == '__main__':
    main()
