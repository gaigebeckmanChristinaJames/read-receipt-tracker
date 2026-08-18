package com.rrt.tracker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 内置控制台：WebView 加载 127.0.0.1:5000
 * 也提供「浏览器打开」按钮
 */
public class ConsoleActivity extends Activity {

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 40, 20, 20);
        root.setBackgroundColor(Color.parseColor("#0d1117"));

        // 标题行
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
        title.setText("控制台");
        title.setTextSize(18f);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(16, 4, 0, 0);
        header.addView(title);

        // 浏览器打开按钮
        Button openBtn = new Button(this);
        openBtn.setText("🌐 浏览器打开");
        openBtn.setTextSize(12f);
        openBtn.setBackgroundColor(Color.parseColor("#21262d"));
        openBtn.setTextColor(Color.parseColor("#58a6ff"));
        openBtn.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("http://127.0.0.1:5000"));
            startActivity(intent);
        });
        LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        openLp.setMargins(12, 0, 0, 0);
        header.addView(openBtn, openLp);
        root.addView(header);

        // WebView
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setWebViewClient(new WebViewClient());
        // 支持 confirm() 弹窗（删除确认对话框）
        webView.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public boolean onJsConfirm(WebView view, String url, String message,
                                       android.webkit.JsResult result) {
                new android.app.AlertDialog.Builder(ConsoleActivity.this)
                        .setMessage(message)
                        .setPositiveButton("确定", (d, w) -> result.confirm())
                        .setNegativeButton("取消", (d, w) -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }
        });
        webView.setBackgroundColor(Color.parseColor("#0d1117"));
        webView.loadUrl("http://127.0.0.1:5000/");
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);
    }
}
