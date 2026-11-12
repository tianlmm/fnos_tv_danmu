package com.fntv.app;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

/** 轻量环形日志：同时写 logcat 和 App 私有目录 logs/fntv.log，便于用户导出反馈。 */
public final class AppLog {
    private static final int MAX_LINES = 500;
    private static final long MAX_FILE_BYTES = 512 * 1024;   // 单个日志文件上限 512KB
    private static final int MAX_ENTRY_CHARS = 3000;         // 单条日志上限
    private static int writes = 0;
    private static final LinkedList<String> LINES = new LinkedList<>();
    private static final SimpleDateFormat TS =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    private static File logFile;
    private static boolean inited = false;

    private AppLog() {}

    public static synchronized void init(Context c) {
        if (inited || c == null) return;
        try {
            File dir = new File(c.getExternalFilesDir(null), "logs");
            if (!dir.exists()) dir.mkdirs();
            logFile = new File(dir, "fntv.log");
            if (logFile.length() > 2L * 1024 * 1024) {
                File old = new File(dir, "fntv.old.log");
                if (old.exists()) old.delete();
                logFile.renameTo(old);
            }
            inited = true;
        } catch (Throwable ignored) {
        }
    }

    public static void d(String tag, String msg) {
        Log.d(tag, String.valueOf(msg));
        write("D/" + tag, String.valueOf(msg));
    }

    public static void e(String tag, String msg) {
        Log.e(tag, String.valueOf(msg));
        write("E/" + tag, String.valueOf(msg));
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(tag, String.valueOf(msg), t);
        write("E/" + tag, msg + "\n" + Log.getStackTraceString(t));
    }

    private static synchronized void write(String tag, String msg) {
        if (msg != null && msg.length() > MAX_ENTRY_CHARS) {
            msg = msg.substring(0, MAX_ENTRY_CHARS) + "…(截断)";
        }
        String line = TS.format(new Date()) + " " + tag + ": " + msg;
        LINES.addLast(line);
        while (LINES.size() > MAX_LINES) LINES.removeFirst();
        if (logFile != null) {
            try (FileWriter w = new FileWriter(logFile, true)) {
                w.write(line + "\n");
            } catch (Throwable ignored) {
            }
            // 超过上限就把内存里保留的最后若干条重写回去，避免无限增长
            if (++writes % 50 == 0 && logFile.length() > MAX_FILE_BYTES) {
                try (FileWriter w = new FileWriter(logFile, false)) {
                    int from = Math.max(0, LINES.size() - 300);
                    for (int i = from; i < LINES.size(); i++) {
                        w.write(LINES.get(i) + "\n");
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        // 最新的排在最上面
        for (int i = LINES.size() - 1; i >= 0; i--) sb.append(LINES.get(i)).append('\n');
        if (sb.length() == 0) sb.append("（暂无日志，请先复现一次问题）\n");
        return sb.toString();
    }

    public static File file() {
        return logFile;
    }
}
