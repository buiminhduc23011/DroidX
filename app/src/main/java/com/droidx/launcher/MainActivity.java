package com.droidx.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String ENGLISH_URL = "https://eng.dbivn.com/";
    private static final int TOTAL_PAGES = 3;

    private ViewFlipper viewFlipper;
    private int currentPage = 0;

    // Authentic Mechanical Split-Flap Clock
    private SplitFlipCardView cardHours, cardMinutes, cardSeconds;
    private TextView tvDate, tvGreeting, tvFunFact;
    private int funIndex = 0;
    private long lastFactTime = 0;
    private String lastHours = "";
    private String lastMinutes = "";
    private String lastSeconds = "";
    private Handler timeHandler;
    private Runnable timeRunnable;

    private static final String[] FUN_FACTS = {
        "Tim bạch tuộc có 3 trái tim, khi bơi một tim sẽ ngừng đập.",
        "Mật ong để hàng nghìn năm cũng không hỏng.",
        "Đố: Cái gì càng lấy đi càng to ra? — Cái hố.",
        "Hươu cao cổ chỉ ngủ khoảng 30 phút mỗi ngày.",
        "Đố: Lịch nào dài nhất? — Lịch sử.",
        "Voi là loài duy nhất không thể nhảy.",
        "Một ngày trên sao Kim dài hơn một năm trên sao Kim.",
        "Đố: Con gì đập thì sống, không đập thì chết? — Con tim.",
        "Sên có thể ngủ liền 3 năm.",
        "Chữ Q là chữ cái duy nhất không có trong tên bang nào của Mỹ.",
        "Đố: Cái gì có cổ mà không có đầu? — Cái chai.",
        "Râu cá mập có thể phát hiện một giọt máu trong bể bơi Olympic."
    };

    // WebView - lazy init để máy stock RAM yếu không lag lúc khởi động
    private WebView wvEnglish;
    private ProgressBar pbWebLoading;
    private boolean webViewInitialized = false;

    // YouTube: viên thuốc mở app YouTube gốc (WebView lag nên bỏ hẳn)

    // Native TextToSpeech Engine
    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;

    // App Drawer Views
    private GridView gvApps;
    private EditText etSearch;
    private AppAdapter appAdapter;
    private List<AppInfo> allApps = new ArrayList<>();
    private List<AppInfo> filteredApps = new ArrayList<>();

    // Touch & Swipe
    private float startX, startY;
    private int screenWidth, screenHeight;
    private TextView tvDots;

    // Pre-cached Material Carousel Animations
    private Animation animSlideInRight, animSlideOutLeft;
    private Animation animSlideInLeft, animSlideOutRight;

    // Cache formatters: tránh new SimpleDateFormat mỗi giây gây GC lag trên máy yếu
    private static final SimpleDateFormat HOUR_FMT = new SimpleDateFormat("HH", Locale.getDefault());
    private static final SimpleDateFormat MIN_FMT = new SimpleDateFormat("mm", Locale.getDefault());
    private static final SimpleDateFormat SEC_FMT = new SimpleDateFormat("ss", Locale.getDefault());
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("EEEE • dd 'THÁNG' MM", new Locale("vi", "VN"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on continuously
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        hideSystemUI();

        setContentView(R.layout.activity_main);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;

        // Ensure media volume is at 100% for English flashcard pronunciation
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, maxVol, 0);
            }
        } catch (Exception ignored) {}

        setupNativeTTS();
        initViews();
        initAnimations();
        setupClock();
        // Không init WebView ngay: đợi vuốt sang trang học mới load -> giảm ~150MB RAM lúc khởi động
        setupAppDrawerAsync();
    }

    private void setupNativeTTS() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int res = textToSpeech.setLanguage(Locale.US);
                if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                    isTtsReady = true;
                    android.util.Log.d("DroidX_TTS", "TTS US initialized successfully!");
                }
            }
        });
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void initViews() {
        viewFlipper = findViewById(R.id.viewFlipper);

        cardHours = findViewById(R.id.cardHours);
        cardMinutes = findViewById(R.id.cardMinutes);
        cardSeconds = findViewById(R.id.cardSeconds);
        tvDate = findViewById(R.id.tvDate);

        cardHours.setTextColor(Color.WHITE);
        cardMinutes.setTextColor(Color.WHITE);
        cardSeconds.setTextColor(Color.parseColor("#8E8E98"));

        tvGreeting = findViewById(R.id.tvGreeting);
        tvFunFact = findViewById(R.id.tvFunFact);
        tvDots = findViewById(R.id.tvDots);
        if (tvFunFact != null && FUN_FACTS.length > 0) {
            tvFunFact.setText(FUN_FACTS[0]);
        }
        if (tvFunFact != null) {
            tvFunFact.setOnClickListener(v -> showNextFact());
            tvFunFact.setOnLongClickListener(v -> { speakCurrentFact(); return true; });
        }

        View ytPill = findViewById(R.id.ytPill);
        if (ytPill != null) {
            // Chạm: mở app YouTube gốc (mượt, không lag WebView)
            ytPill.setOnClickListener(v -> openYouTubeApp());
        }

        wvEnglish = findViewById(R.id.wvEnglish);
        pbWebLoading = findViewById(R.id.pbWebLoading);

        gvApps = findViewById(R.id.gvApps);
        etSearch = findViewById(R.id.etSearch);

        findViewById(R.id.btnReloadWeb).setOnClickListener(v -> {
            if (wvEnglish != null) {
                pbWebLoading.setVisibility(View.VISIBLE);
                wvEnglish.reload();
            }
        });

        findViewById(R.id.btnSettings).setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        });
    }

    private void initAnimations() {
        animSlideInRight = AnimationUtils.loadAnimation(this, R.anim.slide_in_right);
        animSlideOutLeft = AnimationUtils.loadAnimation(this, R.anim.slide_out_left);
        animSlideInLeft = AnimationUtils.loadAnimation(this, R.anim.slide_in_left);
        animSlideOutRight = AnimationUtils.loadAnimation(this, R.anim.slide_out_right);

        Animation.AnimationListener listener = new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                viewFlipper.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                viewFlipper.setLayerType(View.LAYER_TYPE_NONE, null);
                // Pause / resume WebView only AFTER the slide animation completes
                if (webViewInitialized && wvEnglish != null) {
                    if (currentPage == 1) {
                        wvEnglish.onResume();
                        wvEnglish.resumeTimers();
                    } else {
                        wvEnglish.onPause();
                        wvEnglish.pauseTimers();
                    }
                }
                // Nhạc giờ là app gốc, không còn WebView nhạc để pause
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        };

        animSlideInRight.setAnimationListener(listener);
        animSlideInLeft.setAnimationListener(listener);
    }

    // --- INFINITE ROLLING PAGER ---
    private void moveToNext() {
        viewFlipper.setInAnimation(animSlideInRight);
        viewFlipper.setOutAnimation(animSlideOutLeft);
        int next = (currentPage + 1) % TOTAL_PAGES;
        setPage(next);
    }

    private void moveToPrev() {
        viewFlipper.setInAnimation(animSlideInLeft);
        viewFlipper.setOutAnimation(animSlideOutRight);
        int prev = (currentPage - 1 + TOTAL_PAGES) % TOTAL_PAGES;
        setPage(prev);
    }

    private void goToPage(int targetPage) {
        if (targetPage == currentPage) return;
        if (targetPage > currentPage) {
            viewFlipper.setInAnimation(animSlideInRight);
            viewFlipper.setOutAnimation(animSlideOutLeft);
        } else {
            viewFlipper.setInAnimation(animSlideInLeft);
            viewFlipper.setOutAnimation(animSlideOutRight);
        }
        setPage(targetPage);
    }

    private void setPage(int target) {
        if (target == 1 && wvEnglish != null) {
            // Lazy-load: lần đầu vuốt sang mới init WebView
            if (!webViewInitialized) {
                setupWebView();
            } else {
                // Pre-resume so content is immediately visible
                wvEnglish.onResume();
                wvEnglish.resumeTimers();
            }
        }
        currentPage = target;
        viewFlipper.setDisplayedChild(currentPage);
        updateDots();
    }

    private void updateDots() {
        if (tvDots == null) tvDots = findViewById(R.id.tvDots);
        if (tvDots == null) return;
        // Trang 0: đồng hồ • 1: tiếng Anh • 2: kho app
        if (currentPage == 0) tvDots.setText("● ○ ○");
        else if (currentPage == 1) tvDots.setText("○ ● ○");
        else tvDots.setText("○ ○ ●");
    }

    // --- YOUTUBE: mở app gốc, chưa cài thì ra thẳng Play Store ---
    private void openYouTubeApp() {
        String ytPkg = "com.google.android.youtube";
        try {
            getPackageManager().getPackageInfo(ytPkg, 0);
            // Đã cài: mở thẳng trang tìm kiếm nhạc lofi trong app
            Intent i = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.youtube.com/results?search_query=nhac+lofi+chill"));
            i.setPackage(ytPkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            // Chưa cài: mở Play Store đúng trang YouTube để cài 1 chạm
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("market://details?id=" + ytPkg)));
            } catch (Exception e2) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + ytPkg)));
                } catch (Exception ignored) {}
            }
        }
    }

    // --- SWIPE GESTURE DETECTION (INFINITE ROLLING) ---
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                float endX = event.getX();
                float endY = event.getY();
                float diffX = endX - startX;
                float diffY = endY - startY;

                boolean isEdgeSwipe = (startX < 90 && diffX > 70) || (startX > (screenWidth - 90) && diffX < -70);
                boolean isClearHorizontalSwipe = Math.abs(diffX) > 130 && Math.abs(diffX) > 2 * Math.abs(diffY);

                if (currentPage == 1) { // WebView Page
                    if (isEdgeSwipe || isClearHorizontalSwipe) {
                        if (diffX < 0) {
                            moveToNext();
                        } else {
                            moveToPrev();
                        }
                        return true;
                    }
                } else {
                    // Clock Page (0) or Apps Page (2)
                    if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 70) {
                        if (diffX < 0) {
                            moveToNext();
                        } else {
                            moveToPrev();
                        }
                        return true;
                    }
                }
                break;
        }
        return super.dispatchTouchEvent(event);
    }

    // --- JAVASCRIPT TTS BRIDGE FOR WEB FLASHCARDS ---
    public class TTSJavascriptInterface {
        @JavascriptInterface
        public void speak(String text, String lang) {
            android.util.Log.d("DroidX_TTS", "speak called: " + text + " (lang: " + lang + ")");
            if (text == null || text.trim().isEmpty()) return;
            try {
                if (textToSpeech != null && isTtsReady) {
                    // Không setLanguage mỗi lần speak (tốn CPU) — đã set ở init
                    textToSpeech.speak(text.trim(), TextToSpeech.QUEUE_FLUSH, null, "card_pronounce");
                }
            } catch (Exception e) {
                android.util.Log.e("DroidX_TTS", "TTS error", e);
            }
        }

        @JavascriptInterface
        public void stop() {
            if (textToSpeech != null) {
                textToSpeech.stop();
            }
        }
    }

    private void injectSpeechSynthesisPolyfill(WebView view) {
        // Polyfill window.speechSynthesis in Javascript so eng.dbivn.com uses native Android TTS
        String polyfill = "javascript:(function() {" +
                "  if (window.speechSynthesis && window.speechSynthesis.__droidx) return;" +
                "  window.SpeechSynthesisUtterance = function(text) {" +
                "    this.text = text || '';" +
                "    this.lang = 'en-US';" +
                "  };" +
                "  window.speechSynthesis = {" +
                "    __droidx: true," +
                "    speaking: false," +
                "    pending: false," +
                "    paused: false," +
                "    speak: function(utterance) {" +
                "      var t = (utterance && utterance.text) ? utterance.text : (typeof utterance === 'string' ? utterance : '');" +
                "      if (t && window.AndroidTTS) {" +
                "        window.AndroidTTS.speak(t, 'en-US');" +
                "      }" +
                "    }," +
                "    cancel: function() {" +
                "      if (window.AndroidTTS) window.AndroidTTS.stop();" +
                "    }," +
                "    pause: function() {}," +
                "    resume: function() {}," +
                "    getVoices: function() {" +
                "      return [{ name: 'Android Native TTS', lang: 'en-US', default: true }];" +
                "    }" +
                "  };" +
                "})();";
        view.evaluateJavascript(polyfill, null);
    }

    // --- WEBVIEW: HỌC TIẾNG ANH (LAZY + TIẾT KIỆM RAM) ---
    private void setupWebView() {
        if (webViewInitialized || wvEnglish == null) return;
        WebSettings settings = wvEnglish.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Tiết kiệm RAM/CPU trên máy stock yếu:
        settings.setBlockNetworkLoads(false);
        try {
            // Giảm mức render khi không cần pixel-perfect
            wvEnglish.setInitialScale(0);
        } catch (Exception ignored) {}

        wvEnglish.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        wvEnglish.setBackgroundColor(Color.BLACK);

        // Add the TTS interface
        wvEnglish.addJavascriptInterface(new TTSJavascriptInterface(), "AndroidTTS");

        wvEnglish.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (pbWebLoading != null) pbWebLoading.setVisibility(View.GONE);
                injectSpeechSynthesisPolyfill(view);
                if (currentPage != 1) {
                    wvEnglish.onPause();
                    wvEnglish.pauseTimers();
                }
            }
        });

        wvEnglish.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (pbWebLoading == null) return;
                if (newProgress < 100) {
                    pbWebLoading.setVisibility(View.VISIBLE);
                } else {
                    pbWebLoading.setVisibility(View.GONE);
                }
                injectSpeechSynthesisPolyfill(view);
            }
        });

        webViewInitialized = true;
        wvEnglish.loadUrl(ENGLISH_URL);
    }

    // --- APP DRAWER (ASYNC: không block UI lúc khởi động trên ROM gốc nặng) ---
    private void setupAppDrawerAsync() {
        appAdapter = new AppAdapter();
        gvApps.setAdapter(appAdapter);

        gvApps.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= filteredApps.size()) return;
            AppInfo app = filteredApps.get(position);
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (launchIntent != null) {
                startActivity(launchIntent);
            } else {
                Toast.makeText(this, "Không thể mở ứng dụng", Toast.LENGTH_SHORT).show();
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Query + loadIcon chạy background, xong mới refresh UI
        new Thread(() -> {
            loadApps();
            new Handler(Looper.getMainLooper()).post(() -> {
                if (appAdapter != null) appAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void setupAppDrawer() {
        setupAppDrawerAsync();
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
        allApps.clear();

        for (ResolveInfo ri : activities) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;

            // Chặn bloat ROM Vivo (QC: vivo Store thực chất là com.vivo.website)
            if (pkg.contains("com.vivo.appstore") || pkg.contains("com.vivo.website")
                    || pkg.contains("com.bbk.cloud") || pkg.contains("com.vivo.imanager")) {
                continue;
            }

            String label = ri.loadLabel(pm).toString();
            Drawable icon = ri.loadIcon(pm);
            allApps.add(new AppInfo(label, pkg, icon));
        }

        Collections.sort(allApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        filteredApps.clear();
        filteredApps.addAll(allApps);
    }

    private void filterApps(String query) {
        filteredApps.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredApps.addAll(allApps);
        } else {
            String lower = query.toLowerCase().trim();
            for (AppInfo app : allApps) {
                if (app.label.toLowerCase().contains(lower)) {
                    filteredApps.add(app);
                }
            }
        }
        if (appAdapter != null) appAdapter.notifyDataSetChanged();
    }

    private class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() { return filteredApps.size(); }
        @Override
        public Object getItem(int pos) { return filteredApps.get(pos); }
        @Override
        public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_app, parent, false);
            }
            AppInfo app = filteredApps.get(pos);
            ImageView ivIcon = convertView.findViewById(R.id.ivIcon);
            TextView tvLabel = convertView.findViewById(R.id.tvLabel);

            ivIcon.setImageDrawable(app.icon);
            tvLabel.setText(app.label);
            return convertView;
        }
    }

    // --- RETRO MECHANICAL FLIP CLOCK (nhẹ cho máy stock yếu) ---
    private void setupClock() {
        timeHandler = new Handler(Looper.getMainLooper());
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Date date = new Date(now);

                String currentHour = HOUR_FMT.format(date);
                String currentMin = MIN_FMT.format(date);
                String currentSec = SEC_FMT.format(date);
                String currentDate = DATE_FMT.format(date).toUpperCase(Locale.getDefault());

                // Giảm duration 490 -> 170ms: GPU nghỉ ~660ms mỗi giây, hết lag + mát máy
                if (!currentSec.equals(lastSeconds)) {
                    if (!lastSeconds.isEmpty() && currentPage == 0) {
                        cardSeconds.flipTo(currentSec, 170);
                    } else {
                        cardSeconds.setValue(currentSec);
                    }
                    lastSeconds = currentSec;
                }

                if (!currentMin.equals(lastMinutes)) {
                    if (!lastMinutes.isEmpty() && currentPage == 0) {
                        cardMinutes.flipTo(currentMin, 170);
                    } else {
                        cardMinutes.setValue(currentMin);
                    }
                    lastMinutes = currentMin;
                }

                if (!currentHour.equals(lastHours)) {
                    if (!lastHours.isEmpty() && currentPage == 0) {
                        cardHours.flipTo(currentHour, 170);
                    } else {
                        cardHours.setValue(currentHour);
                    }
                    lastHours = currentHour;
                }

                tvDate.setText(currentDate);
                updateGreeting(currentHour);

                // Xoay fact mỗi 30s, không tốn CPU
                if (now - lastFactTime > 30000) {
                    lastFactTime = now;
                    if (tvFunFact != null && FUN_FACTS.length > 0) {
                        funIndex = (funIndex + 1) % FUN_FACTS.length;
                        tvFunFact.setText(FUN_FACTS[funIndex]);
                    }
                }

                // Precise sync to exact wall-clock second boundary
                long nextSecondDelay = 1000 - (System.currentTimeMillis() % 1000);
                timeHandler.postDelayed(this, nextSecondDelay > 0 ? nextSecondDelay : 1000);
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void updateGreeting(String hourStr) {
        if (tvGreeting == null) return;
        int hour = 0;
        try { hour = Integer.parseInt(hourStr); } catch (Exception ignored) {}
        String g;
        if (hour >= 5 && hour < 11) g = "Chào buổi sáng";
        else if (hour >= 11 && hour < 13) g = "Nghỉ trưa một chút";
        else if (hour >= 13 && hour < 17) g = "Chào buổi chiều";
        else if (hour >= 17 && hour < 22) g = "Chào buổi tối";
        else g = "Khuya rồi, ngủ sớm nhé";
        if (!g.equals(tvGreeting.getText().toString())) tvGreeting.setText(g);
    }

    private void showNextFact() {
        if (FUN_FACTS.length == 0) return;
        funIndex = (funIndex + 1) % FUN_FACTS.length;
        lastFactTime = System.currentTimeMillis();
        if (tvFunFact != null) tvFunFact.setText(FUN_FACTS[funIndex]);
    }

    private void speakCurrentFact() {
        if (textToSpeech == null || !isTtsReady || FUN_FACTS.length == 0) return;
        try {
            textToSpeech.setLanguage(new Locale("vi", "VN"));
            textToSpeech.speak(FUN_FACTS[funIndex], TextToSpeech.QUEUE_FLUSH, null, "fun_fact");
        } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timeHandler != null && timeRunnable != null) {
            timeHandler.removeCallbacks(timeRunnable);
        }
        if (wvEnglish != null) {
            try {
                wvEnglish.loadUrl("about:blank");
                wvEnglish.stopLoading();
                wvEnglish.removeJavascriptInterface("AndroidTTS");
                wvEnglish.destroy();
            } catch (Exception ignored) {}
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentPage == 1 && webViewInitialized && wvEnglish != null && wvEnglish.canGoBack()) {
            wvEnglish.goBack();
            return;
        }
        if (currentPage != 0) {
            goToPage(0);
        }
    }
}
