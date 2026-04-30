package com.applitools.imagetester.lib.converters;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.io.IOException;
import java.util.List;

/**
 * ProcessRunner that launches child processes on a hidden Win32 desktop so that
 * UI drawn by the child (or system dialogs drawn cross-process in response to
 * the child's API calls, e.g. the Windows Print Spooler's "Please wait for
 * printer connection" modal) never appears on the interactive desktop.
 */
public final class WindowsHiddenDesktopRunner implements ProcessRunner {

    private static final String WINSTATION_NAME = "WinSta0";
    private static final String DESKTOP_NAME = "ImageTesterHiddenDesktop";

    private static final int STANDARD_RIGHTS_REQUIRED = 0x000F0000;
    private static final int DESKTOP_READOBJECTS     = 0x0001;
    private static final int DESKTOP_CREATEWINDOW    = 0x0002;
    private static final int DESKTOP_CREATEMENU      = 0x0004;
    private static final int DESKTOP_HOOKCONTROL     = 0x0008;
    private static final int DESKTOP_JOURNALRECORD   = 0x0010;
    private static final int DESKTOP_JOURNALPLAYBACK = 0x0020;
    private static final int DESKTOP_ENUMERATE       = 0x0040;
    private static final int DESKTOP_WRITEOBJECTS    = 0x0080;
    private static final int DESKTOP_SWITCHDESKTOP   = 0x0100;
    private static final int DESKTOP_ALL_ACCESS      = STANDARD_RIGHTS_REQUIRED
            | DESKTOP_READOBJECTS | DESKTOP_CREATEWINDOW | DESKTOP_CREATEMENU
            | DESKTOP_HOOKCONTROL | DESKTOP_JOURNALRECORD | DESKTOP_JOURNALPLAYBACK
            | DESKTOP_ENUMERATE | DESKTOP_WRITEOBJECTS | DESKTOP_SWITCHDESKTOP;

    private static final int CREATE_NO_WINDOW            = 0x08000000;
    private static final int CREATE_UNICODE_ENVIRONMENT  = 0x00000400;
    private static final int WAIT_TIMEOUT                = 0x00000102;

    public interface User32Ext extends StdCallLibrary {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class, W32APIOptions.DEFAULT_OPTIONS);

        HANDLE CreateDesktop(String lpszDesktop, String lpszDevice, Pointer lpdevmode,
                             int dwFlags, int dwDesiredAccess, Pointer lpsa);

        boolean CloseDesktop(HANDLE hDesktop);
    }

    private final HANDLE desktopHandle;

    public WindowsHiddenDesktopRunner() throws IOException {
        HANDLE handle = User32Ext.INSTANCE.CreateDesktop(
                DESKTOP_NAME, null, null, 0, DESKTOP_ALL_ACCESS, null);
        if (isNullHandle(handle)) {
            int err = Native.getLastError();
            throw new IOException("CreateDesktop failed (Win32 error " + err + ")");
        }
        this.desktopHandle = handle;
        Runtime.getRuntime().addShutdownHook(
                new Thread(this::closeDesktop, "imagetester-hidden-desktop-cleanup"));
    }

    @Override
    public int run(List<String> command, long timeoutSeconds) throws IOException {
        WinBase.STARTUPINFO si = new WinBase.STARTUPINFO();
        si.lpDesktop = WINSTATION_NAME + "\\" + DESKTOP_NAME;

        WinBase.PROCESS_INFORMATION pi = new WinBase.PROCESS_INFORMATION();
        String commandLine = buildCommandLine(command);

        boolean ok = Kernel32.INSTANCE.CreateProcess(
                null,
                commandLine,
                null,
                null,
                false,
                new DWORD(CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT),
                null,
                null,
                si,
                pi);

        if (!ok) {
            int err = Kernel32.INSTANCE.GetLastError();
            throw new IOException("CreateProcess failed on hidden desktop (Win32 error "
                    + err + ") for: " + command.get(0));
        }

        try {
            long ms = Math.max(0L, timeoutSeconds) * 1000L;
            int waitMs = (int) Math.min(ms, Integer.MAX_VALUE);
            int result = Kernel32.INSTANCE.WaitForSingleObject(pi.hProcess, waitMs);
            if (result == WAIT_TIMEOUT) {
                Kernel32.INSTANCE.TerminateProcess(pi.hProcess, 1);
                return -1;
            }
            IntByReference exit = new IntByReference();
            if (!Kernel32.INSTANCE.GetExitCodeProcess(pi.hProcess, exit)) {
                int err = Kernel32.INSTANCE.GetLastError();
                throw new IOException("GetExitCodeProcess failed (Win32 error " + err + ")");
            }
            return exit.getValue();
        } finally {
            Kernel32.INSTANCE.CloseHandle(pi.hProcess);
            Kernel32.INSTANCE.CloseHandle(pi.hThread);
        }
    }

    private void closeDesktop() {
        try {
            User32Ext.INSTANCE.CloseDesktop(desktopHandle);
        } catch (Throwable ignored) {
            // best-effort — JVM is shutting down
        }
    }

    private static boolean isNullHandle(HANDLE h) {
        if (h == null) return true;
        Pointer p = h.getPointer();
        return p == null || Pointer.nativeValue(p) == 0L;
    }

    static String buildCommandLine(List<String> command) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < command.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(quoteArgument(command.get(i)));
        }
        return sb.toString();
    }

    // Quote per CommandLineToArgvW rules used by CreateProcess.
    static String quoteArgument(String arg) {
        if (!arg.isEmpty()
                && arg.indexOf(' ')  < 0 && arg.indexOf('\t') < 0
                && arg.indexOf('\n') < 0 && arg.indexOf('"')  < 0) {
            return arg;
        }
        StringBuilder sb = new StringBuilder(arg.length() + 2);
        sb.append('"');
        int backslashes = 0;
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            if (c == '\\') {
                backslashes++;
            } else if (c == '"') {
                for (int b = 0; b < backslashes * 2 + 1; b++) sb.append('\\');
                sb.append('"');
                backslashes = 0;
            } else {
                for (int b = 0; b < backslashes; b++) sb.append('\\');
                backslashes = 0;
                sb.append(c);
            }
        }
        for (int b = 0; b < backslashes * 2; b++) sb.append('\\');
        sb.append('"');
        return sb.toString();
    }
}
