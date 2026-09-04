package com.poorgrammera.bydsubai.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.poorgrammera.bydsubai.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dadb.AdbKeyPair;
import dadb.AdbStream;
import dadb.Dadb;
import okio.BufferedSource;
import okio.Okio;

public class LogViewActivity extends AppCompatActivity {

    private static final String TAG = "LogViewActivity";
    private static final int UI_UPDATE_INTERVAL_MS = 200;
    private static final String TARGET_FILTER_TAG = "BYD";

    private Button btnConnect;
    private Button btnRecord;
    private Button btnClear;
    private CheckBox cbFilterByd;
    private CheckBox cbFilterSubAi;
    private CheckBox cbFilterAll;
    private int myPid;

    private RecyclerView rvLogs;
    private LogAdapter logAdapter;
    private LinearLayoutManager layoutManager;

    private Thread adbThread;
    private volatile boolean isRunning = false;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final List<String> temporaryBuffer = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean isRecording = false;
    private final StringBuilder recordBuffer = new StringBuilder();

    private final Runnable uiUpdater = new Runnable() {
        @Override
        public void run() {
            if (!isRunning && temporaryBuffer.isEmpty()) return;

            if (!temporaryBuffer.isEmpty()) {
                List<String> newLogs;
                synchronized (temporaryBuffer) {
                    newLogs = new ArrayList<>(temporaryBuffer);
                    temporaryBuffer.clear();
                }
                logAdapter.addLogs(newLogs);
                rvLogs.scrollToPosition(logAdapter.getItemCount() - 1);
            }

            if (isRunning) {
                uiHandler.postDelayed(this, UI_UPDATE_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_view);

        initViews();
        setupFilterCheckboxes();

        btnConnect.setOnClickListener(v -> {
            if (!isRunning) {
                startAdbConnection();
            } else {
                stopAdbConnection();
            }
        });

        btnRecord.setOnClickListener(v -> toggleRecording());

        btnClear.setOnClickListener(v -> {
            logAdapter.clear();
            temporaryBuffer.clear();
        });
    }

    private void initViews() {
        btnConnect = findViewById(R.id.btn_connect);
        btnRecord = findViewById(R.id.btn_record);
        btnClear = findViewById(R.id.btn_clear);
        cbFilterByd = findViewById(R.id.cb_filter_byd);
        cbFilterSubAi = findViewById(R.id.cb_filter_sub_ai);
        cbFilterAll = findViewById(R.id.cb_filter_all);
        myPid = android.os.Process.myPid();

        rvLogs = findViewById(R.id.rv_logs);
        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvLogs.setLayoutManager(layoutManager);

        logAdapter = new LogAdapter();
        rvLogs.setAdapter(logAdapter);
        rvLogs.setHasFixedSize(true);
    }

    private void setupFilterCheckboxes() {
        cbFilterByd.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) {
                cbFilterAll.setChecked(false);
                cbFilterSubAi.setChecked(false);
            } else if (!cbFilterAll.isChecked() && !cbFilterSubAi.isChecked()) {
                cbFilterByd.setChecked(true);
            }
        });
        cbFilterSubAi.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) {
                cbFilterByd.setChecked(false);
                cbFilterAll.setChecked(false);
            } else if (!cbFilterByd.isChecked() && !cbFilterAll.isChecked()) {
                cbFilterSubAi.setChecked(true);
            }
        });
        cbFilterAll.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) {
                cbFilterByd.setChecked(false);
                cbFilterSubAi.setChecked(false);
            } else if (!cbFilterByd.isChecked() && !cbFilterSubAi.isChecked()) {
                cbFilterAll.setChecked(true);
            }
        });
    }

    private void startAdbConnection() {
        logAdapter.clear();
        temporaryBuffer.clear();
        appendLocalLog("System > [연결 시도] 127.0.0.1:5555");

        btnConnect.setText("중지");
        isRunning = true;

        uiHandler.post(uiUpdater);

        adbThread = new Thread(() -> {
            try {
                File privateKeyFile = new File(getFilesDir(), "adbkey");
                File publicKeyFile = new File(getFilesDir(), "adbkey.pub");

                if (!privateKeyFile.exists()) {
                    AdbKeyPair.Companion.generate(privateKeyFile, publicKeyFile);
                }

                AdbKeyPair adbKeyPair = AdbKeyPair.Companion.read(privateKeyFile, publicKeyFile);
                Dadb device = Dadb.create("127.0.0.1", 5555, adbKeyPair);

                try (AdbStream stream = device.open("shell:logcat -v threadtime")) {
                    BufferedSource source = Okio.buffer(stream.getSource());
                    String line;
                    while (isRunning && (line = source.readUtf8Line()) != null) {
                        final String rawLine = line;

                         boolean isBydLog = LogParser.containsTag(rawLine, TARGET_FILTER_TAG);
                         boolean isSubAiLog = LogParser.isSubAiLog(rawLine, myPid);
                         boolean shouldShow = cbFilterAll.isChecked() 
                                 || (cbFilterByd.isChecked() && isBydLog)
                                 || (cbFilterSubAi.isChecked() && isSubAiLog);

                        if (shouldShow) {
                            String formatted = LogParser.formatLogForTerminal(rawLine);
                            temporaryBuffer.add(formatted);

                            if (isRecording) {
                                synchronized (recordBuffer) {
                                    recordBuffer.append(formatted).append("\n");
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (isRunning) {
                    final String errorMsg = e.getMessage();
                    runOnUiThread(() -> {
                        appendLocalLog("System > [오류] " + errorMsg);
                        stopAdbConnection();
                    });
                }
            }
        });
        adbThread.start();
    }

    private void stopAdbConnection() {
        isRunning = false;
        uiHandler.removeCallbacks(uiUpdater);
        btnConnect.setText("접속");
        if (adbThread != null) {
            adbThread.interrupt();
            adbThread = null;
        }
        appendLocalLog("System > [연결 종료] ADB 수신 중지");
        uiHandler.post(uiUpdater);
    }

    private void appendLocalLog(String message) {
        temporaryBuffer.add(message);
        if (!isRunning) {
            uiHandler.post(uiUpdater);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAdbConnection();
    }

    private void toggleRecording() {
        if (!isRecording) {
            isRecording = true;
            synchronized (recordBuffer) {
                recordBuffer.setLength(0);
            }
            btnRecord.setText("중단");
            btnRecord.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED));
            Toast.makeText(this, "로그 녹화를 시작합니다.", Toast.LENGTH_SHORT).show();
        } else {
            isRecording = false;
            btnRecord.setText("녹화");
            btnRecord.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FF9800")));
            Toast.makeText(this, "녹화가 종료되었습니다.", Toast.LENGTH_SHORT).show();
            showFileNameInputDialog();
        }
    }

    private void showFileNameInputDialog() {
        android.widget.EditText etInput = new android.widget.EditText(this);
        etInput.setHint("파일 제목 입력 (예: test)");
        etInput.setSingleLine(true);
        etInput.setPadding(40, 30, 40, 30);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("녹화 로그 파일 저장")
                .setMessage("저장할 파일의 제목을 입력해 주세요.")
                .setView(etInput)
                .setCancelable(false)
                .setPositiveButton("저장 및 공유", (dialog, which) -> {
                    String title = etInput.getText().toString().trim();
                    if (title.isEmpty()) {
                        title = "log";
                    }
                    saveAndShareLog(title);
                })
                .setNegativeButton("취소", (dialog, which) -> {
                    synchronized (recordBuffer) {
                        recordBuffer.setLength(0);
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private void saveAndShareLog(String title) {
        String logContent;
        synchronized (recordBuffer) {
            logContent = recordBuffer.toString();
            recordBuffer.setLength(0);
        }

        if (logContent.isEmpty()) {
            Toast.makeText(this, "기록된 로그가 없어 저장하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault());
        String timestamp = sdf.format(new java.util.Date());
        String fileName = "[" + timestamp + "]" + title + ".txt";

        try {
            File logFile = new File(getCacheDir(), fileName);
            try (java.io.FileWriter writer = new java.io.FileWriter(logFile)) {
                writer.write(logContent);
            }

            android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "com.poorgrammera.bydsubai.fileprovider",
                    logFile
            );

            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(shareIntent, "로그 파일 공유하기"));

        } catch (Exception e) {
            Log.e(TAG, "Failed to save and share log file", e);
            Toast.makeText(this, "파일 저장 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
