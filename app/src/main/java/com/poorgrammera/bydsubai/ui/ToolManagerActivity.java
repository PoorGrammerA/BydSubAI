package com.poorgrammera.bydsubai.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.poorgrammera.bydsubai.R;
import com.poorgrammera.bydsubai.tool.ExternalToolApprovalStore;
import com.poorgrammera.bydsubai.tool.ExternalToolDescriptor;
import com.poorgrammera.bydsubai.tool.ExternalToolDiscovery;

import java.util.ArrayList;
import java.util.List;

/** User-owned trust and enablement UI for independently installed Tool applications. */
public class ToolManagerActivity extends AppCompatActivity {
    private LinearLayout providersContainer;
    private ProgressBar progressBar;
    private TextView scanStatus;
    private ExternalToolApprovalStore approvalStore;
    private int providerCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        approvalStore = new ExternalToolApprovalStore(this);
        setContentView(buildContentView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (providersContainer != null) refreshProviders();
    }

    private View buildContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#121212"));
        LinearLayout root = verticalLayout();
        root.setPadding(dp(20), dp(20), dp(20), dp(32));
        scrollView.addView(root);

        TextView title = text(getString(R.string.external_tools_title), 24, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView warning = text(getString(R.string.external_tools_trust_warning), 14,
                Color.parseColor("#FFCC80"));
        warning.setPadding(0, dp(12), 0, dp(12));
        root.addView(warning);

        Button refresh = new Button(this);
        refresh.setText(R.string.external_tools_refresh);
        refresh.setOnClickListener(v -> refreshProviders());
        root.addView(refresh, matchWrap());

        progressBar = new ProgressBar(this);
        root.addView(progressBar, wrapWrap());

        scanStatus = text(getString(R.string.external_tools_scanning), 14,
                Color.parseColor("#B0BEC5"));
        scanStatus.setPadding(0, dp(8), 0, dp(8));
        root.addView(scanStatus);

        providersContainer = verticalLayout();
        root.addView(providersContainer, matchWrap());

        TextView nextSession = text(getString(R.string.external_tools_next_session), 13,
                Color.parseColor("#80CBC4"));
        nextSession.setPadding(0, dp(12), 0, 0);
        root.addView(nextSession);
        return scrollView;
    }

    private void refreshProviders() {
        providersContainer.removeAllViews();
        providerCount = 0;
        progressBar.setVisibility(View.VISIBLE);
        scanStatus.setVisibility(View.VISIBLE);
        scanStatus.setText(R.string.external_tools_scanning);
        new ExternalToolDiscovery(this).discover(new ExternalToolDiscovery.Listener() {
            @Override
            public void onProvider(ExternalToolDescriptor descriptor, PendingIntent settingsIntent) {
                providerCount++;
                providersContainer.addView(buildProviderCard(descriptor, settingsIntent), matchWrap());
            }

            @Override
            public void onProviderError(android.content.ComponentName component, String message) {
                TextView error = text(getString(R.string.external_tools_provider_error,
                        component.getPackageName(), message), 13, Color.parseColor("#EF9A9A"));
                error.setPadding(0, dp(6), 0, dp(6));
                providersContainer.addView(error);
            }

            @Override
            public void onComplete() {
                progressBar.setVisibility(View.GONE);
                if (providerCount == 0 && providersContainer.getChildCount() == 0) {
                    scanStatus.setText(R.string.external_tools_none);
                } else {
                    scanStatus.setVisibility(View.GONE);
                }
            }
        });
    }

    private View buildProviderCard(ExternalToolDescriptor descriptor, PendingIntent settingsIntent) {
        LinearLayout card = verticalLayout();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.setMargins(0, dp(8), 0, dp(8));
        card.setLayoutParams(cardParams);
        card.setBackgroundColor(Color.parseColor("#1E1E1E"));

        TextView name = text(descriptor.providerName(), 19, Color.parseColor("#FFCC00"));
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(name);

        boolean approved = approvalStore.isApproved(descriptor);
        String readiness = descriptor.isReady()
                ? getString(R.string.external_tools_ready)
                : ("needs_configuration".equals(descriptor.status.optString("status"))
                    ? getString(R.string.external_tools_needs_configuration)
                    : getString(R.string.external_tools_unavailable));
        String approval = approved ? getString(R.string.external_tools_approved)
                : getString(R.string.external_tools_not_approved);
        card.addView(text(approval + " · " + readiness, 14,
                approved ? Color.parseColor("#81C784") : Color.parseColor("#FFB74D")));

        String identity = descriptor.component.getPackageName()
                + "\n" + descriptor.providerVersion()
                + "\nSHA-256 " + groupFingerprint(descriptor.certificateSha256);
        TextView identityView = text(identity, 12, Color.parseColor("#90A4AE"));
        identityView.setTextIsSelectable(true);
        identityView.setPadding(0, dp(6), 0, dp(6));
        card.addView(identityView);

        if (!descriptor.statusMessage().isEmpty()) {
            card.addView(text(descriptor.statusMessage(), 13, Color.parseColor("#FFCC80")));
        }

        List<String> permissions = requestedPermissions(descriptor.component.getPackageName());
        if (!permissions.isEmpty()) {
            card.addView(text("Android permissions\n• " + TextUtils.join("\n• ", permissions),
                    12, Color.parseColor("#B0BEC5")));
        }

        if (!descriptor.disclosures().isEmpty()) {
            card.addView(text("Developer disclosures\n• "
                            + TextUtils.join("\n• ", descriptor.disclosures()),
                    12, Color.parseColor("#B0BEC5")));
        }

        TextView toolsTitle = text("Tools", 14, Color.WHITE);
        toolsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        toolsTitle.setPadding(0, dp(10), 0, 0);
        card.addView(toolsTitle);

        for (ExternalToolDescriptor.Function function : descriptor.functions()) {
            CheckBox enabled = new CheckBox(this);
            enabled.setText(getString(R.string.external_tool_function_summary,
                    function.displayName, function.description));
            enabled.setTextColor(Color.WHITE);
            enabled.setTextSize(13);
            enabled.setEnabled(approved);
            enabled.setChecked(approvalStore.isFunctionEnabled(descriptor.identity(), function.name));
            enabled.setOnCheckedChangeListener((button, checked) ->
                    approvalStore.setFunctionEnabled(descriptor.identity(), function.name, checked));
            card.addView(enabled, matchWrap());
        }

        if (settingsIntent != null) {
            Button settings = new Button(this);
            settings.setText(R.string.external_tools_settings);
            settings.setOnClickListener(v -> {
                try { settingsIntent.send(); }
                catch (PendingIntent.CanceledException e) {
                    Toast.makeText(this, R.string.external_tools_unavailable, Toast.LENGTH_SHORT).show();
                }
            });
            card.addView(settings, matchWrap());
        }

        Button approvalButton = new Button(this);
        approvalButton.setText(approved ? R.string.external_tools_revoke : R.string.external_tools_approve);
        approvalButton.setOnClickListener(v -> {
            try {
                if (approvalStore.isApproved(descriptor)) approvalStore.revoke(descriptor);
                else approvalStore.approve(descriptor);
                refreshProviders();
            } catch (Exception e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        card.addView(approvalButton, matchWrap());
        return card;
    }

    private List<String> requestedPermissions(String packageName) {
        List<String> result = new ArrayList<>();
        try {
            PackageInfo info = getPackageManager().getPackageInfo(packageName,
                    PackageManager.GET_PERMISSIONS);
            if (info.requestedPermissions != null) {
                for (String permission : info.requestedPermissions) result.add(permission);
            }
        } catch (Exception ignored) { }
        return result;
    }

    private LinearLayout verticalLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        return view;
    }

    private static String groupFingerprint(String value) {
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < value.length(); i += 2) {
            if (grouped.length() > 0) grouped.append(':');
            grouped.append(value, i, Math.min(i + 2, value.length()));
        }
        return grouped.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
