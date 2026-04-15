package com.roadrunner.dispatch.presentation.compliance.reports;

import com.roadrunner.dispatch.core.util.AppLogger;

import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executors;

/**
 * Fragment for filing a compliance report.
 *
 * <p>Evidence attachment uses {@link ActivityResultContracts.OpenDocument}
 * and computes a SHA-256 hash of the selected file for tamper detection.
 */
public class ReportFragment extends Fragment {

    private static final String ARG_ORG_ID      = "org_id";
    private static final String ARG_REPORTED_BY = "reported_by";
    private static final String ARG_CASE_ID    = "case_id";

    // Target types that FileReportUseCase can validate. WORKER targets reference people
    // and do not require an org-scoped entity lookup. TASK targets are validated via
    // TaskRepository, which FileReportUseCase now accepts.
    private static final String[] TARGET_TYPES = {
            "EMPLOYER", "ORDER", "WORKER", "TASK"
    };

    private ReportViewModel viewModel;

    private Spinner spinnerTargetType;
    private TextInputEditText etTargetId;
    private TextInputEditText etDescription;
    private MaterialButton btnAttachEvidence;
    private MaterialCardView cardEvidence;
    private TextView tvAttachedFile;
    private TextView tvEvidenceHash;
    private ProgressBar progressBar;
    private TextView tvError;
    private MaterialButton btnFileReport;

    private String orgId;
    private String reportedBy;
    private String caseId;

    // Evidence state
    private Uri evidenceUri;
    private String evidenceHash;
    private String evidenceFileName;

    private final ActivityResultLauncher<String[]> documentPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;

                String mime = requireContext().getContentResolver().getType(uri);
                if (mime == null || (!mime.startsWith("image/") && !mime.equals("application/pdf"))) {
                    tvError.setVisibility(View.VISIBLE);
                    tvError.setText("Only images and PDF files are accepted as evidence");
                    return;
                }

                // Show progress while hashing and encrypting on a background thread.
                progressBar.setVisibility(View.VISIBLE);
                btnAttachEvidence.setEnabled(false);

                final String resolvedFileName = resolveFileName(uri);

                Executors.newSingleThreadExecutor().execute(() -> {
                    final String hash = computeSha256(uri);
                    final Uri internalUri = copyToInternalStorage(uri, resolvedFileName);

                    requireActivity().runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        btnAttachEvidence.setEnabled(true);

                        if (internalUri == null) {
                            tvError.setVisibility(View.VISIBLE);
                            tvError.setText("Evidence encryption failed. Cannot attach unencrypted evidence.");
                            evidenceUri = null;
                            evidenceHash = null;
                            evidenceFileName = null;
                            return;
                        }

                        evidenceUri      = internalUri;
                        evidenceHash     = hash;
                        evidenceFileName = resolvedFileName;

                        cardEvidence.setVisibility(View.VISIBLE);
                        tvAttachedFile.setText(getString(R.string.label_attached_file, evidenceFileName));
                        tvEvidenceHash.setText(getString(R.string.label_evidence_hash,
                                evidenceHash != null ? evidenceHash : "N/A"));
                    });
                });
            });

    public static ReportFragment newInstance(String orgId, String reportedBy) {
        ReportFragment f = new ReportFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ORG_ID, orgId);
        args.putString(ARG_REPORTED_BY, reportedBy);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            orgId      = getArguments().getString(ARG_ORG_ID, "");
            reportedBy = getArguments().getString(ARG_REPORTED_BY, "");
            caseId     = getArguments().getString(ARG_CASE_ID, null);
        }
        if (orgId == null || orgId.isEmpty()) {
            String sessionOrgId = ServiceLocator.getInstance().getSessionManager().getOrgId();
            if (sessionOrgId != null) orgId = sessionOrgId;
        }
        if (reportedBy == null || reportedBy.isEmpty()) {
            String sessionUserId = ServiceLocator.getInstance().getSessionManager().getUserId();
            if (sessionUserId != null) reportedBy = sessionUserId;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_report, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!RoleGuard.hasRole("COMPLIANCE_REVIEWER", "WORKER")) {
            TextView tvDenied = new TextView(requireContext());
            tvDenied.setText("Access denied. Compliance Reviewer or Worker role required.");
            tvDenied.setPadding(32, 32, 32, 32);
            ((ViewGroup) view).addView(tvDenied);
            return;
        }

        spinnerTargetType  = view.findViewById(R.id.spinner_target_type);
        etTargetId         = view.findViewById(R.id.et_target_id);
        etDescription      = view.findViewById(R.id.et_description);
        btnAttachEvidence  = view.findViewById(R.id.btn_attach_evidence);
        cardEvidence       = view.findViewById(R.id.card_evidence);
        tvAttachedFile     = view.findViewById(R.id.tv_attached_file);
        tvEvidenceHash     = view.findViewById(R.id.tv_evidence_hash);
        progressBar        = view.findViewById(R.id.progress_bar);
        tvError            = view.findViewById(R.id.tv_error);
        btnFileReport      = view.findViewById(R.id.btn_file_report);

        // Target type spinner
        spinnerTargetType.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, TARGET_TYPES));

        // ViewModel
        viewModel = new ViewModelProvider(this,
                new ReportViewModelFactory(ServiceLocator.getInstance())
        ).get(ReportViewModel.class);

        viewModel.getFiledReport().observe(getViewLifecycleOwner(), report -> {
            progressBar.setVisibility(View.GONE);
            if (report != null) {
                requireActivity().onBackPressed();
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            progressBar.setVisibility(View.GONE);
            if (error != null) {
                tvError.setVisibility(View.VISIBLE);
                tvError.setText(error);
            }
        });

        btnAttachEvidence.setOnClickListener(v ->
                documentPicker.launch(new String[]{"*/*"}));

        btnFileReport.setOnClickListener(v -> fileReport());
    }

    private void fileReport() {
        String targetId   = etTargetId.getText() != null ? etTargetId.getText().toString().trim() : "";
        String desc       = etDescription.getText() != null ? etDescription.getText().toString().trim() : "";
        String targetType = TARGET_TYPES[spinnerTargetType.getSelectedItemPosition()];

        tvError.setVisibility(View.GONE);
        if (targetId.isEmpty()) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText(getString(R.string.hint_target_id) + " is required");
            return;
        }
        if (desc.isEmpty()) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText(getString(R.string.hint_description) + " is required");
            return;
        }

        String actorRole = ServiceLocator.getInstance().getSessionManager().getRole();
        if (actorRole == null || actorRole.isEmpty()) {
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("Session expired. Please log in again.");
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        viewModel.fileReport(orgId, reportedBy, targetType, targetId, desc,
                evidenceUri != null ? evidenceUri.toString() : null,
                evidenceHash,
                actorRole,
                caseId);
    }

    private String resolveFileName(Uri uri) {
        try (android.database.Cursor cursor = requireContext().getContentResolver()
                .query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return uri.getLastPathSegment();
    }

    private Uri copyToInternalStorage(Uri sourceUri, String fileName) {
        // Get or create AES-GCM key in Android Keystore
        String keyAlias = "evidence_key";
        try {
            java.security.KeyStore existingKs = java.security.KeyStore.getInstance("AndroidKeyStore");
            existingKs.load(null);
            if (!existingKs.containsAlias(keyAlias)) {
                javax.crypto.KeyGenerator keyGen =
                    javax.crypto.KeyGenerator.getInstance(
                        android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                android.security.keystore.KeyGenParameterSpec spec =
                    new android.security.keystore.KeyGenParameterSpec.Builder(
                        keyAlias,
                        android.security.keystore.KeyProperties.PURPOSE_ENCRYPT |
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build();
                keyGen.init(spec);
                keyGen.generateKey();
            }
        } catch (java.security.KeyStoreException | java.security.NoSuchAlgorithmException |
                 java.security.NoSuchProviderException |
                 java.security.InvalidAlgorithmParameterException |
                 java.security.cert.CertificateException | java.io.IOException e) {
            AppLogger.error("ReportFragment", "Evidence encryption key generation failed", e);
            return null;
        }

        try {
            java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            javax.crypto.SecretKey secretKey = ((java.security.KeyStore.SecretKeyEntry)
                ks.getEntry(keyAlias, null)).getSecretKey();

            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();

            java.io.File dir = new java.io.File(requireContext().getFilesDir(), "evidence");
            dir.mkdirs();
            String safeName = fileName != null ? fileName.replaceAll("[^a-zA-Z0-9._-]", "_") : "evidence";
            java.io.File dest = new java.io.File(dir, System.currentTimeMillis() + "_" + safeName + ".enc");

            try (InputStream in = requireContext().getContentResolver().openInputStream(sourceUri);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                if (in == null) return null;
                // Write IV length + IV first, then encrypted data
                fos.write(iv.length);
                fos.write(iv);
                javax.crypto.CipherOutputStream cos = new javax.crypto.CipherOutputStream(fos, cipher);
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) cos.write(buf, 0, read);
                cos.close();
            }
            return Uri.fromFile(dest);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private String computeSha256(Uri uri) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
                if (is == null) return null;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            return null;
        }
    }
}
