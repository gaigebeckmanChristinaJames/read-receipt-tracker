package com.rrt.tracker;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 运行日志页：实时显示 cloudflared 日志 + 复制按钮
 */
public class LogActivity extends Activity {

    private TextView logText;
    private final Handler handler = new Handler();
    private long lastLogSize = 0;

    private final Runnable logUpdater = new Runnable() {
        @Override
        public void run() {
            refreshLog();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 50, 30, 30);
        root.setBackgroundColor(Color.parseColor("#0d1117"));

        // 标题行 + 返回
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);

        Button backBtn = new Button(this);
        backBtn.setText("← 返回");
        backBtn.setTextSize(14f);
        backBtn.setBackgroundColor(Color.parseColor("#21262d"));
        backBtn.setTextColor(Color.parseColor("#58a6ff"));
        backBtn.setOnClickListener(v -> finish());
        header.addView(backBtn);

        TextView title = new TextView(this);
        title.setText("运行日志");
        title.setTextSize(18f);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(16, 4, 0, 0);
        header.addView(title);

        // 复制按钮
        Button copyBtn = new Button(this);
        copyBtn.setText("📋 复制日志");
        copyBtn.setTextSize(12f);
        copyBtn.setBackgroundColor(Color.parseColor("#21262d"));
        copyBtn.setTextColor(Color.parseColor("#58a6ff"));
        copyBtn.setOnClickListener(v -> {
            String text = logText.getText().toString();
            if (text.isEmpty() || text.contains("暂无日志")) {
                android.widget.Toast.makeText(this, "暂无日志可复制", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("rrt-log", text));
            android.widget.Toast.makeText(this, "✅ 日志已复制", android.widget.Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        copyLp.setMargins(12, 0, 0, 0);
        header.addView(copyBtn, copyLp);
        root.addView(header);

        // 日志区
        ScrollView scroll = new ScrollView(this);
        logText = new TextView(this);
        logText.setText("暂无日志。启动服务后显示实时日志。");
        logText.setTextSize(11f);
        logText.setTextColor(Color.parseColor("#8b949e"));
        logText.setTypeface(Typeface.MONOSPACE);
        scroll.addView(logText);
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        logLp.topMargin = 16;
        root.addView(scroll, logLp);

        setContentView(root);
    }

    private void refreshLog() {
        try {
            File logFile = new File(getFilesDir(), "cloudflared.log");
            if (!logFile.exists()) return;
            long size = logFile.length();
            if (size == lastLogSize && lastLogSize > 0) return;
            lastLogSize = size;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            String[] lines = sb.toString().split("\n");
            int start = Math.max(0, lines.length - 100);
            StringBuilder display = new StringBuilder();
            for (int i = start; i < lines.length; i++) {
                display.append(lines[i]).append("\n");
            }
            logText.setText(display.toString());
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(logUpdater, 500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(logUpdater);
    }
}
