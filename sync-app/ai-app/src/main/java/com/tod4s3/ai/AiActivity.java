package com.tod4s3.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent AiTod4S3 client. It never uses Tod4S3 Sync pairing or Premium. */
public final class AiActivity extends Activity {
    private static final String API = "https://tod4s3-production-abe8925f.up.railway.app";
    private static final int BG = Color.rgb(12, 13, 19);
    private static final int PANEL = Color.rgb(25, 27, 36);
    private static final int RED = Color.rgb(255, 59, 87);
    private static final int CYAN = Color.rgb(52, 217, 232);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> history = new ArrayList<>();
    private SharedPreferences prefs;
    private String token = "";
    private String username = "";
    private LinearLayout root;
    private LinearLayout transcript;
    private ScrollView scroll;
    private EditText userField;
    private EditText passwordField;
    private EditText composer;
    private TextView status;
    private Button loginButton;
    private ImageButton sendButton;
    private Switch speakSwitch;
    private boolean sending = false;
    private boolean ttsReady = false;
    private boolean micPermissionPending = false;
    private TextToSpeech tts;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9, 10, 15));
        getWindow().setNavigationBarColor(Color.rgb(9, 10, 15));
        prefs = getSharedPreferences("ai_tod4s3_private", MODE_PRIVATE);
        token = prefs.getString("session_token", "");
        username = prefs.getString("session_user", "");
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS) {
                int lang = tts.setLanguage(new Locale("es", "ES"));
                ttsReady = lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
        if (!token.isEmpty() && !username.isEmpty()) {
            loadHistory();
            showChat();
        } else showLogin();
    }

    private void showLogin() {
        root = base();
        LinearLayout content = column();
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(26), dp(34), dp(26), dp(34));
        TextView mark = label("✦", 40, true, CYAN);
        mark.setGravity(Gravity.CENTER);
        content.addView(mark, lp(-1, dp(62)));
        TextView title = label("AiTod4S3", 31, true, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        content.addView(title, lp(-1, dp(44)));
        TextView subtitle = label("Tu asistente inteligente", 15, false, Color.rgb(167, 172, 188));
        subtitle.setGravity(Gravity.CENTER);
        content.addView(subtitle, lp(-1, dp(32)));
        space(content, 22);
        userField = edit("Usuario", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        content.addView(userField, lp(-1, dp(54)));
        space(content, 12);
        passwordField = edit("Contraseña", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        content.addView(passwordField, lp(-1, dp(54)));
        space(content, 16);
        loginButton = button("INICIAR SESIÓN", RED);
        loginButton.setOnClickListener(v -> login());
        content.addView(loginButton, lp(-1, dp(52)));
        status = label("Acceso con la cuenta AiTod4S3 que te creó el administrador.", 13, false, Color.rgb(151, 157, 174));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(12), 0, 0);
        content.addView(status, lp(-1, -2));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void login() {
        final String name = userField.getText().toString().trim();
        final String pass = passwordField.getText().toString();
        if (name.isEmpty() || pass.isEmpty()) { status.setText("Escribe tu usuario y contraseña."); return; }
        loginButton.setEnabled(false);
        status.setText("Verificando acceso…");
        io.execute(() -> {
            try {
                JSONObject request = new JSONObject().put("username", name).put("password", pass).put("device_id", deviceId());
                JSONObject response = post("/api/ai/login", null, request);
                String received = response.optString("token", "");
                if (received.isEmpty()) throw new Exception("No se recibió una sesión válida.");
                runOnUiThread(() -> {
                    token = received;
                    username = name;
                    prefs.edit().putString("session_token", token).putString("session_user", username).apply();
                    history.clear();
                    loadHistory();
                    showChat();
                });
            } catch (Exception e) {
                String message = friendlyError(e);
                runOnUiThread(() -> {
                    loginButton.setEnabled(true);
                    status.setText(message);
                });
            }
        });
    }

    private void showChat() {
        root = base();
        LinearLayout page = column();
        page.setPadding(dp(14), dp(12), dp(14), dp(10));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("✦  AiTod4S3", 21, true, Color.WHITE);
        title.setTextColor(CYAN);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button logout = button("SALIR", PANEL);
        logout.setTextSize(11);
        logout.setOnClickListener(v -> logout());
        header.addView(logout, lp(dp(75), dp(40)));
        page.addView(header, lp(-1, dp(48)));
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(52, 55, 68));
        page.addView(line, lp(-1, dp(1)));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        transcript = column();
        transcript.setPadding(dp(2), dp(12), dp(2), dp(14));
        scroll.addView(transcript, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (history.isEmpty()) addBubble("¡Hola! Soy AiTod4S3. ¿En qué puedo ayudarte?", false);
        else for (JSONObject item : history) addBubble(item.optString("content", ""), "user".equals(item.optString("role")));
        LinearLayout voiceRow = new LinearLayout(this);
        voiceRow.setGravity(Gravity.CENTER_VERTICAL);
        speakSwitch = new Switch(this);
        speakSwitch.setText("La IA responde por voz");
        speakSwitch.setTextColor(Color.rgb(190, 194, 207));
        speakSwitch.setTextSize(13);
        speakSwitch.setChecked(prefs.getBoolean("speak_enabled", false));
        speakSwitch.setOnCheckedChangeListener((view, checked) -> {
            prefs.edit().putBoolean("speak_enabled", checked).apply();
            if (!checked && tts != null) tts.stop();
        });
        voiceRow.addView(speakSwitch, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView microphoneHint = label("Micrófono para dictar", 11, false, Color.rgb(134, 141, 160));
        voiceRow.addView(microphoneHint, lp(-2, dp(42)));
        page.addView(voiceRow, lp(-1, dp(42)));
        LinearLayout composerRow = new LinearLayout(this);
        composerRow.setGravity(Gravity.CENTER_VERTICAL);
        composer = edit("Escribe tu mensaje…", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composer.setMinHeight(dp(48));
        composer.setMaxLines(4);
        composer.setImeOptions(EditorInfo.IME_ACTION_SEND);
        composerRow.addView(composer, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageButton mic = iconButton("🎙", CYAN, "Dictar mensaje por voz");
        mic.setOnClickListener(v -> beginDictation());
        composerRow.addView(mic, lp(dp(44), dp(46)));
        sendButton = iconButton("➤", Color.WHITE, "Enviar mensaje a AiTod4S3");
        sendButton.setBackground(round(RED, 24));
        sendButton.setOnClickListener(v -> sendMessage());
        composerRow.addView(sendButton, lp(dp(46), dp(46)));
        page.addView(composerRow, lp(-1, -2));
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void sendMessage() {
        if (sending || composer == null) return;
        String text = composer.getText().toString().trim();
        if (text.isEmpty()) return;
        composer.setText("");
        JSONObject userMessage = new JSONObject();
        try { userMessage.put("role", "user").put("content", text); } catch (JSONException ignored) { return; }
        history.add(userMessage);
        trimHistory();
        saveHistory();
        addBubble(text, true);
        addBubble("AiTod4S3 está pensando…", false);
        sending = true;
        sendButton.setEnabled(false);
        JSONArray messages = new JSONArray();
        int from = Math.max(0, history.size() - 12);
        for (int i = from; i < history.size(); i++) messages.put(history.get(i));
        io.execute(() -> {
            try {
                JSONObject response = post("/api/ai/chat", token, new JSONObject().put("messages", messages));
                String reply = response.optString("reply", "");
                if (reply.isEmpty()) throw new Exception("La IA devolvió una respuesta vacía.");
                runOnUiThread(() -> {
                    removeThinkingBubble();
                    try {
                        JSONObject assistant = new JSONObject().put("role", "assistant").put("content", reply);
                        history.add(assistant);
                        trimHistory();
                        saveHistory();
                    } catch (JSONException ignored) { }
                    addBubble(reply, false);
                    if (speakSwitch != null && speakSwitch.isChecked() && ttsReady && tts != null)
                        tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "aitod4s3-reply");
                    sending = false;
                    sendButton.setEnabled(true);
                });
            } catch (Exception e) {
                String error = friendlyError(e);
                boolean unauthorized = e.getMessage() != null && e.getMessage().contains("unauthorized");
                runOnUiThread(() -> {
                    removeThinkingBubble();
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    sending = false;
                    sendButton.setEnabled(true);
                    if (unauthorized) logout();
                });
            }
        });
    }

    private void beginDictation() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermissionPending = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 18);
            return;
        }
        launchRecognizer();
    }

    private void launchRecognizer() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CU");
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla con AiTod4S3");
            startActivityForResult(intent, 17);
        } catch (Exception e) {
            Toast.makeText(this, "No hay un servicio de reconocimiento de voz disponible.", Toast.LENGTH_LONG).show();
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 18 && micPermissionPending) {
            micPermissionPending = false;
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) launchRecognizer();
            else Toast.makeText(this, "Permite el micrófono para dictar; también puedes escribir.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 17 && result == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && composer != null) {
                composer.setText(results.get(0));
                composer.setSelection(composer.length());
            }
        }
    }

    private JSONObject post(String path, String bearer, JSONObject payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(API + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(path.endsWith("/chat") ? 90000 : 30000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        if (bearer != null && !bearer.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearer);
            connection.setRequestProperty("X-Ai-Device-Id", deviceId());
        }
        try {
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            StringBuilder text = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null && text.length() < 1_000_000) text.append(line);
                }
            }
            JSONObject response = new JSONObject(text.toString());
            if (code < 200 || code >= 300 || !response.optBoolean("ok", false))
                throw new Exception(response.optString("error", "http_" + code));
            return response;
        } finally { connection.disconnect(); }
    }

    private void logout() {
        String old = token;
        token = "";
        username = "";
        prefs.edit().remove("session_token").remove("session_user").apply();
        if (!old.isEmpty()) io.execute(() -> {
            try { post("/api/ai/logout", old, new JSONObject()); } catch (Exception ignored) { }
        });
        if (tts != null) tts.stop();
        history.clear();
        showLogin();
    }

    private String deviceId() {
        String value = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private String historyKey() { return "conversation_" + username; }
    private void loadHistory() {
        history.clear();
        try {
            JSONArray saved = new JSONArray(prefs.getString(historyKey(), "[]"));
            for (int i = Math.max(0, saved.length() - 40); i < saved.length(); i++) {
                JSONObject row = saved.optJSONObject(i);
                if (row != null && ("user".equals(row.optString("role")) || "assistant".equals(row.optString("role"))))
                    history.add(new JSONObject().put("role", row.optString("role")).put("content", row.optString("content")));
            }
        } catch (Exception ignored) { history.clear(); }
    }
    private void saveHistory() {
        JSONArray saved = new JSONArray();
        for (JSONObject row : history) saved.put(row);
        prefs.edit().putString(historyKey(), saved.toString()).apply();
    }
    private void trimHistory() { while (history.size() > 40) history.remove(0); }

    private void addBubble(String text, boolean mine) {
        TextView bubble = label(text, 15, false, Color.rgb(241, 242, 247));
        bubble.setPadding(dp(14), dp(11), dp(14), dp(11));
        bubble.setBackground(round(mine ? Color.rgb(105, 32, 48) : PANEL, 18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams((int) (getResources().getDisplayMetrics().widthPixels * .82f), -2);
        params.gravity = mine ? Gravity.RIGHT : Gravity.LEFT;
        params.setMargins(dp(4), dp(4), dp(4), dp(6));
        transcript.addView(bubble, params);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void removeThinkingBubble() {
        if (transcript != null && transcript.getChildCount() > 0) {
            View last = transcript.getChildAt(transcript.getChildCount() - 1);
            if (last instanceof TextView && ((TextView) last).getText().toString().startsWith("AiTod4S3 está pensando"))
                transcript.removeView(last);
        }
    }

    private String friendlyError(Exception e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        if (raw.contains("invalid_credentials")) return "Usuario o contraseña incorrectos.";
        if (raw.contains("device_in_use")) return "Esta cuenta ya está vinculada a otro dispositivo. Pide al administrador que libere el dispositivo anterior.";
        if (raw.contains("invalid_device")) return "No se pudo identificar este dispositivo Android.";
        if (raw.contains("unauthorized")) return "La sesión venció. Inicia sesión otra vez.";
        if (raw.contains("login_rate_limited")) return "Demasiados intentos. Espera unos minutos.";
        if (raw.contains("ai_rate_limited")) return "Espera un momento antes de enviar otro mensaje.";
        if (raw.contains("ai_not_configured") || raw.contains("ai_unavailable")) return "La IA no está disponible temporalmente.";
        if (raw.contains("UnknownHostException") || raw.contains("ConnectException")) return "No hay conexión con AiTod4S3. Comprueba Internet.";
        return "No se pudo completar la solicitud. Comprueba la conexión e inténtalo de nuevo.";
    }

    private LinearLayout base() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackgroundColor(BG);
        setContentView(view);
        return view;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private TextView label(String text, int size, boolean bold, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private EditText edit(String hint, int inputType) {
        EditText v = new EditText(this); v.setSingleLine(false); v.setHint(hint); v.setTextColor(Color.WHITE);
        v.setHintTextColor(Color.rgb(120, 126, 145)); v.setTextSize(15); v.setInputType(inputType);
        v.setPadding(dp(14), dp(8), dp(14), dp(8)); v.setBackground(round(PANEL, 14));
        return v;
    }
    private Button button(String text, int color) {
        Button v = new Button(this); v.setText(text); v.setTextColor(Color.WHITE); v.setTextSize(14);
        v.setTypeface(Typeface.DEFAULT, Typeface.BOLD); v.setBackground(round(color, 14));
        return v;
    }
    private ImageButton iconButton(String glyph, int color, String description) {
        ImageButton v = new ImageButton(this); v.setImageDrawable(null); v.setContentDescription(description);
        v.setBackground(round(PANEL, 22));
        TextView overlay = label(glyph, 21, true, color); overlay.setGravity(Gravity.CENTER);
        v.setTag(overlay);
        // ImageButton has no text; use its background glyph through a ripple-free foreground drawable substitute.
        v.setBackground(new GlyphDrawable(glyph, color, this));
        return v;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout.LayoutParams lp(int width, int height) { return new LinearLayout.LayoutParams(width < 0 ? width : dp(width), height < 0 ? height : dp(height)); }
    private void space(LinearLayout parent, int height) { parent.addView(new View(this), lp(1, height)); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        io.shutdownNow();
        super.onDestroy();
    }

    /** Small drawable so the round composer controls remain legible without extra image dependencies. */
    private static final class GlyphDrawable extends android.graphics.drawable.Drawable {
        private final String glyph; private final int color; private final android.graphics.Paint paint = new android.graphics.Paint(3);
        private final Activity activity;
        GlyphDrawable(String glyph, int color, Activity activity) { this.glyph = glyph; this.color = color; this.activity = activity; }
        @Override public void draw(android.graphics.Canvas canvas) {
            android.graphics.Rect b = getBounds();
            paint.setColor(glyph.equals("➤") ? RED : PANEL); paint.setStyle(android.graphics.Paint.Style.FILL);
            canvas.drawRoundRect(b.left, b.top, b.right, b.bottom, activity.getResources().getDisplayMetrics().density * 22, activity.getResources().getDisplayMetrics().density * 22, paint);
            paint.setColor(color); paint.setTextAlign(android.graphics.Paint.Align.CENTER); paint.setTextSize(activity.getResources().getDisplayMetrics().density * 21); paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(glyph, b.exactCenterX(), b.exactCenterY() - (paint.ascent() + paint.descent()) / 2, paint);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
