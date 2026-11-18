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
import android.view.animation.AnimationUtils;
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

    // Flip Clock Views
    private TextView tvFlipHours, tvFlipMinutes, tvFlipSeconds, tvDate;
    private FrameLayout flHourCard, flMinuteCard, flSecondCard;
    private String lastHours = "";
    private String lastMinutes = "";
    private String lastSeconds = "";
    private Handler timeHandler;
    private Runnable timeRunnable;

    // WebView
    private WebView wvEnglish;
    private ProgressBar pbWebLoading;

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
    private int screenWidth;

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
        setupClock();
        setupWebView();
        setupAppDrawer();
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

        tvFlipHours = findViewById(R.id.tvFlipHours);
        tvFlipMinutes = findViewById(R.id.tvFlipMinutes);
        tvFlipSeconds = findViewById(R.id.tvFlipSeconds);
        tvDate = findViewById(R.id.tvDate);
        flHourCard = findViewById(R.id.flHourCard);
        flMinuteCard = findViewById(R.id.flMinuteCard);
        flSecondCard = findViewById(R.id.flSecondCard);

        float scale = getResources().getDisplayMetrics().density * 8000;
        flHourCard.setCameraDistance(scale);
        flMinuteCard.setCameraDistance(scale);
        flSecondCard.setCameraDistance(scale);

        // Hardware Acceleration layer for smooth 60fps GPU rendering
        flHourCard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        flMinuteCard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        flSecondCard.setLayerType(View.LAYER_TYPE_HARDWARE, null);

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

    // --- INFINITE ROLLING PAGER ---
    private void moveToNext() {
        viewFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
        viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left));
        int next = (currentPage + 1) % TOTAL_PAGES;
        setPage(next);
    }

    private void moveToPrev() {
        viewFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_left));
        viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_right));
        int prev = (currentPage - 1 + TOTAL_PAGES) % TOTAL_PAGES;
        setPage(prev);
    }

    private void goToPage(int targetPage) {
        if (targetPage == currentPage) return;
        if (targetPage > currentPage) {
            viewFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right));
            viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left));
        } else {
            viewFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_left));
            viewFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_right));
        }
        setPage(targetPage);
    }

    private void setPage(int target) {
        currentPage = target;
        viewFlipper.setDisplayedChild(currentPage);

        // Dynamic resource optimization: pause WebView JS/rendering when not on English page
        if (wvEnglish != null) {
            if (currentPage == 1) {
                wvEnglish.onResume();
            } else {
                wvEnglish.onPause();
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
                if (textToSpeech != null) {
                    textToSpeech.setLanguage(Locale.US);
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

    // --- WEBVIEW: HỌC TIẾNG ANH ---
    private void setupWebView() {
        WebSettings settings = wvEnglish.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

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
                pbWebLoading.setVisibility(View.GONE);
                injectSpeechSynthesisPolyfill(view);
                if (currentPage != 1) {
                    wvEnglish.onPause();
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
                if (newProgress < 100) {
                    pbWebLoading.setVisibility(View.VISIBLE);
                } else {
                    pbWebLoading.setVisibility(View.GONE);
                }
                injectSpeechSynthesisPolyfill(view);
            }
        });

        wvEnglish.loadUrl(ENGLISH_URL);
    }

    // --- APP DRAWER ---
    private void setupAppDrawer() {
        loadApps();
        appAdapter = new AppAdapter();
        gvApps.setAdapter(appAdapter);

        gvApps.setOnItemClickListener((parent, view, position, id) -> {
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

            if (pkg.contains("com.vivo.appstore") || pkg.contains("com.bbk.cloud") || pkg.contains("com.vivo.imanager")) {
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

    // --- RETRO MECHANICAL FLIP CLOCK ---
    private void setupClock() {
        timeHandler = new Handler(Looper.getMainLooper());
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Date date = new Date(now);

                SimpleDateFormat hourFormat = new SimpleDateFormat("HH", Locale.getDefault());
                SimpleDateFormat minFormat = new SimpleDateFormat("mm", Locale.getDefault());
                SimpleDateFormat secFormat = new SimpleDateFormat("ss", Locale.getDefault());
                SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE • dd 'THÁNG' MM", new Locale("vi", "VN"));

                String currentHour = hourFormat.format(date);
                String currentMin = minFormat.format(date);
                String currentSec = secFormat.format(date);
                String currentDate = dateFormat.format(date).toUpperCase(Locale.getDefault());

                // Only animate 3D flips if user is looking at Clock (currentPage == 0)
                if (currentPage == 0) {
                    // Flip Seconds (snappy 90ms)
                    if (!currentSec.equals(lastSeconds)) {
                        if (!lastSeconds.isEmpty()) {
                            animateFlip(flSecondCard, tvFlipSeconds, currentSec, 90);
                        } else {
                            tvFlipSeconds.setText(currentSec);
                        }
                        lastSeconds = currentSec;
                    }

                    // Flip Minutes (140ms)
                    if (!currentMin.equals(lastMinutes)) {
                        if (!lastMinutes.isEmpty()) {
                            animateFlip(flMinuteCard, tvFlipMinutes, currentMin, 140);
                        } else {
                            tvFlipMinutes.setText(currentMin);
                        }
                        lastMinutes = currentMin;
                    }

                    // Flip Hours (140ms)
                    if (!currentHour.equals(lastHours)) {
                        if (!lastHours.isEmpty()) {
                            animateFlip(flHourCard, tvFlipHours, currentHour, 140);
                        } else {
                            tvFlipHours.setText(currentHour);
                        }
                        lastHours = currentHour;
                    }
                } else {
                    // Instantly update text without wasting CPU on 3D animation
                    tvFlipSeconds.setText(currentSec);
                    tvFlipMinutes.setText(currentMin);
                    tvFlipHours.setText(currentHour);
                    lastSeconds = currentSec;
                    lastMinutes = currentMin;
                    lastHours = currentHour;
                }

                tvDate.setText(currentDate);

                // Precise sync to exact wall-clock second boundary
                long nextSecondDelay = 1000 - (System.currentTimeMillis() % 1000);
                timeHandler.postDelayed(this, nextSecondDelay > 0 ? nextSecondDelay : 1000);
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void animateFlip(final View card, final TextView textView, final String newText, final long durationHalf) {
        card.animate().cancel();
        card.animate()
                .rotationX(90f)
                .setDuration(durationHalf)
                .withEndAction(() -> {
                    textView.setText(newText);
                    card.setRotationX(-90f);
                    card.animate()
                            .rotationX(0f)
                            .setDuration(durationHalf)
                            .start();
                })
                .start();
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
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentPage == 1 && wvEnglish.canGoBack()) {
            wvEnglish.goBack();
            return;
        }
        if (currentPage != 0) {
            goToPage(0);
        }
    }
}
