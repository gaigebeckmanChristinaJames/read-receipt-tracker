package com.rrt.tracker;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 主界面：顶部状态 + 隧道，下方功能列表
 */
public class MainActivity extends Activity {

    private TextView statusText;
    private TextView tunnelText;
    private Button toggleButton;
    private boolean running = false;
    private final Handler handler = new Handler();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || !TrackerService.ACTION_STATUS.equals(intent.getAction())) return;
            running = intent.getBooleanExtra(TrackerService.EXTRA_RUNNING, false);
            final String url = intent.getStringExtra(TrackerService.EXTRA_TUNNEL_URL);
            runOnUiThread(() -> updateUI(url));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 56, 36, 36);
        root.setBackgroundColor(Color.parseColor("#0d1117"));

        // ── 标题 ──
        TextView title = new TextView(this);
        title.setText("已读追踪");
        title.setTextSize(24f);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        // ── 顶部状态卡片 ──
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(20, 16, 20, 16);
        statusCard.setBackgroundColor(Color.parseColor("#161b22"));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = 16;
        root.addView(statusCard, cardLp);

        statusText = new TextView(this);
        statusText.setText("● 服务未启动");
        statusText.setTextSize(15f);
        statusText.setTextColor(Color.parseColor("#f0b429"));
        statusText.setTypeface(null, Typeface.BOLD);
        statusCard.addView(statusText);

        // 隧道行 + 复制按钮
        LinearLayout tunnelRow = new LinearLayout(this);
        tunnelRow.setOrientation(LinearLayout.HORIZONTAL);
        tunnelRow.setPadding(0, 10, 0, 0);
        tunnelText = new TextView(this);
        tunnelText.setText("公网隧道: 未启动");
        tunnelText.setTextSize(13f);
        tunnelText.setTextColor(Color.parseColor("#58a6ff"));
        tunnelText.setOnClickListener(v -> {
            String text = tunnelText.getText().toString();
            String url = text.substring(text.indexOf(": ") + 2).trim();
            if (url.startsWith("https://")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
        tunnelRow.addView(tunnelText, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button copyTunnelBtn = new Button(this);
        copyTunnelBtn.setText("📋 复制");
        copyTunnelBtn.setTextSize(11f);
        copyTunnelBtn.setBackgroundColor(Color.parseColor("#21262d"));
        copyTunnelBtn.setTextColor(Color.parseColor("#58a6ff"));
        copyTunnelBtn.setOnClickListener(v -> {
            String text = tunnelText.getText().toString();
            String url = text.contains(": ") ? text.substring(text.indexOf(": ") + 2).trim() : "";
            if (!url.startsWith("https://")) {
                android.widget.Toast.makeText(this, "隧道尚未就绪", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(android.content.ClipData.newPlainText("rrt-tunnel", url));
            android.widget.Toast.makeText(this, "✅ 隧道地址已复制", android.widget.Toast.LENGTH_SHORT).show();
        });
        tunnelRow.addView(copyTunnelBtn);
        statusCard.addView(tunnelRow);

        // 启动/停止按钮
        toggleButton = new Button(this);
        toggleButton.setText("启动服务");
        toggleButton.setBackgroundColor(Color.parseColor("#3fb950"));
        toggleButton.setTextColor(Color.WHITE);
        toggleButton.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = 14;
        statusCard.addView(toggleButton, btnLp);
        toggleButton.setOnClickListener(v -> toggle());

        // ── 功能列表标题 ──
        TextView funcTitle = new TextView(this);
        funcTitle.setText("── 功能 ──");
        funcTitle.setTextSize(12f);
        funcTitle.setTextColor(Color.parseColor("#8b949e"));
        LinearLayout.LayoutParams ftLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ftLp.topMargin = 24;
        ftLp.bottomMargin = 8;
        root.addView(funcTitle, ftLp);

        // 功能列表容器
        LinearLayout funcList = new LinearLayout(this);
        funcList.setOrientation(LinearLayout.VERTICAL);
        root.addView(funcList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 功能项（全部 App 内集成）
        addFuncItem(funcList, "📊", "控制台", "查看消息列表和已读统计（内置页面）", "console");
        addFuncItem(funcList, "📋", "运行日志", "查看 cloudflared 隧道实时日志", "log");
        addFuncItem(funcList, "💻", "GitHub 仓库", "https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker", "github");

        setContentView(root);
    }

    private void addFuncItem(LinearLayout container, String icon, String name,
                             String desc, String url) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setPadding(18, 16, 18, 16);
        item.setBackgroundColor(Color.parseColor("#161b22"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 8;
        container.addView(item, lp);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(22f);
        item.addView(iconView);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(14, 0, 0, 0);
        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(15f);
        nameView.setTextColor(Color.WHITE);
        nameView.setTypeface(null, Typeface.BOLD);
        textCol.addView(nameView);

        TextView descView = new TextView(this);
        descView.setText(desc);
        descView.setTextSize(12f);
        descView.setTextColor(Color.parseColor("#8b949e"));
        textCol.addView(descView);
        item.addView(textCol, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(24f);
        arrow.setTextColor(Color.parseColor("#8b949e"));
        item.addView(arrow);

        item.setOnClickListener(v -> {
            if (url.equals("console")) {
                // 内置控制台
                startActivity(new Intent(this, ConsoleActivity.class));
            } else if (url.equals("log")) {
                // 内置日志页
                startActivity(new Intent(this, LogActivity.class));
            } else if (url.equals("github")) {
                // GitHub 仓库（浏览器打开）
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/gaigebeckmanChristinaJames/read-receipt-tracker")));
            }
        });
    }

    private void toggle() {
        if (!running) {
            Intent intent = new Intent(this, TrackerService.class);
            intent.setAction(TrackerService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            statusText.setText("● 初始化中...");
            toggleButton.setText("停止服务");
            toggleButton.setBackgroundColor(Color.parseColor("#f85149"));
        } else {
            Intent intent = new Intent(this, TrackerService.class);
            intent.setAction(TrackerService.ACTION_STOP);
            startService(intent);
            statusText.setText("● 服务未启动");
            statusText.setTextColor(Color.parseColor("#f0b429"));
            tunnelText.setText("公网隧道: 未启动");
            toggleButton.setText("启动服务");
            toggleButton.setBackgroundColor(Color.parseColor("#3fb950"));
            running = false;
        }
    }

    private void updateUI(String url) {
        if (running) {
            statusText.setText("● 服务运行中");
            statusText.setTextColor(Color.parseColor("#3fb950"));
            toggleButton.setText("停止服务");
            toggleButton.setBackgroundColor(Color.parseColor("#f85149"));
        } else {
            statusText.setText("● 服务未启动");
            statusText.setTextColor(Color.parseColor("#f0b429"));
            toggleButton.setText("启动服务");
            toggleButton.setBackgroundColor(Color.parseColor("#3fb950"));
        }
        if (url != null && !url.isEmpty()) {
            tunnelText.setText("公网隧道: " + url);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, new IntentFilter(TrackerService.ACTION_STATUS),
                        Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(receiver, new IntentFilter(TrackerService.ACTION_STATUS));
            }
        } catch (Exception ignored) {}
        // 主动查询服务状态（隧道可能在 App 打开前已就绪）
        handler.postDelayed(() -> {
            Intent query = new Intent(this, TrackerService.class);
            query.setAction(TrackerService.ACTION_STATUS);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(query);
                } else {
                    startService(query);
                }
            } catch (Exception ignored) {}
        }, 800);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
    }
}
