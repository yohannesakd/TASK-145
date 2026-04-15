package com.roadrunner.dispatch.presentation.admin;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Employer;
import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Zone;
import com.roadrunner.dispatch.core.domain.usecase.CreateDiscountRuleUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CreateProductUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CreateShippingTemplateUseCase;
import com.roadrunner.dispatch.core.domain.usecase.CreateZoneUseCase;
import com.roadrunner.dispatch.core.domain.usecase.VerifyEmployerUseCase;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * Fragment for importing and exporting data via Android's document picker (SAF).
 * Validates file format and computes SHA-256 fingerprints for integrity verification.
 */
public class ImportExportFragment extends Fragment {

    private TextView tvStatus;
    private TextView tvHash;

    private final ActivityResultLauncher<String[]> importPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;

                String mime = requireContext().getContentResolver().getType(uri);
                if (mime == null || !mime.equals("application/json")) {
                    tvStatus.setText("Unsupported file type. Only JSON files are accepted.");
                    tvStatus.setVisibility(View.VISIBLE);
                    return;
                }

                Executors.newSingleThreadExecutor().execute(() -> {
                    String hash = computeSha256(uri);
                    ImportResult result = performImport(uri);
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        tvStatus.setText(result.message);
                        tvStatus.setVisibility(View.VISIBLE);
                        if (hash != null) {
                            tvHash.setText("SHA-256: " + hash);
                            tvHash.setVisibility(View.VISIBLE);
                        }
                    });
                });
            });

    private final ActivityResultLauncher<String> exportPicker =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
                if (uri == null) return;

                Executors.newSingleThreadExecutor().execute(() -> {
                    boolean success = writeExportData(uri);
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> {
                        tvStatus.setText(success ? "Data exported successfully." : "Export failed.");
                        tvStatus.setVisibility(View.VISIBLE);
                    });
                });
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_import_export, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!RoleGuard.hasRole("ADMIN")) {
            TextView tvError = view.findViewById(R.id.tv_ie_status);
            tvError.setText("Access denied. Admin role required.");
            tvError.setVisibility(View.VISIBLE);
            return;
        }

        tvStatus = view.findViewById(R.id.tv_ie_status);
        tvHash = view.findViewById(R.id.tv_ie_hash);

        MaterialButton btnImport = view.findViewById(R.id.btn_import);
        MaterialButton btnExport = view.findViewById(R.id.btn_export);

        btnImport.setOnClickListener(v -> {
            tvStatus.setVisibility(View.GONE);
            tvHash.setVisibility(View.GONE);
            importPicker.launch(new String[]{"application/json"});
        });

        btnExport.setOnClickListener(v -> {
            tvStatus.setVisibility(View.GONE);
            tvHash.setVisibility(View.GONE);
            exportPicker.launch("roadrunner_export.json");
        });
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

    /** Compute SHA-256 of a string encoded as UTF-8. Returns null on error. */
    @Nullable
    private String computeSha256OfString(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            digest.update(bytes);
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private boolean writeExportData(Uri uri) {
        try (OutputStream os = requireContext().getContentResolver().openOutputStream(uri)) {
            if (os == null) return false;

            ServiceLocator sl = ServiceLocator.getInstance();
            String orgId = sl.getSessionManager().getOrgId();
            if (orgId == null) orgId = "";

            JSONObject root = new JSONObject();
            root.put("format", "roadrunner_v1");
            root.put("orgId", orgId);
            root.put("exportedAt", System.currentTimeMillis());

            // Products
            List<Product> products = sl.getProductRepository()
                    .getActiveProductsSync(orgId);
            JSONArray productsArray = new JSONArray();
            if (products != null) {
                for (Product p : products) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", p.id);
                    obj.put("name", p.name);
                    obj.put("brand", p.brand);
                    obj.put("series", p.series);
                    obj.put("model", p.model);
                    obj.put("description", p.description);
                    obj.put("unitPriceCents", p.unitPriceCents);
                    obj.put("taxRate", p.taxRate);
                    obj.put("regulated", p.regulated);
                    obj.put("status", p.status);
                    obj.put("imageUri", p.imageUri != null ? p.imageUri : JSONObject.NULL);
                    productsArray.put(obj);
                }
            }
            root.put("products", productsArray);

            // Shipping templates
            List<ShippingTemplate> templates = sl.getOrderRepository().getShippingTemplates(orgId);
            JSONArray templatesArray = new JSONArray();
            for (ShippingTemplate t : templates) {
                JSONObject obj = new JSONObject();
                obj.put("id", t.id);
                obj.put("name", t.name);
                obj.put("description", t.description);
                obj.put("costCents", t.costCents);
                obj.put("minDays", t.minDays);
                obj.put("maxDays", t.maxDays);
                obj.put("isPickup", t.isPickup);
                templatesArray.put(obj);
            }
            root.put("shippingTemplates", templatesArray);

            // Discount rules
            List<DiscountRule> discountRules = sl.getOrderRepository().getActiveDiscountRules(orgId);
            JSONArray discountArray = new JSONArray();
            for (DiscountRule d : discountRules) {
                JSONObject obj = new JSONObject();
                obj.put("id", d.id);
                obj.put("name", d.name);
                obj.put("type", d.type);
                obj.put("value", d.value);
                obj.put("status", d.status);
                discountArray.put(obj);
            }
            root.put("discountRules", discountArray);

            // Zones
            List<Zone> zones = sl.getZoneRepository().getZones(orgId);
            JSONArray zonesArray = new JSONArray();
            for (Zone z : zones) {
                JSONObject obj = new JSONObject();
                obj.put("id", z.id);
                obj.put("name", z.name);
                obj.put("score", z.score);
                obj.put("description", z.description);
                zonesArray.put(obj);
            }
            root.put("zones", zonesArray);

            // Employers — identity data (EIN, address) is PII and must not be
            // written to plaintext JSON outside the encrypted store.  Export only
            // non-sensitive reference fields so the file can be re-imported without
            // leaking regulated information.
            List<Employer> employers = sl.getEmployerRepository()
                    .getEmployersSync(orgId);
            JSONArray employersArray = new JSONArray();
            if (employers != null) {
                for (Employer e : employers) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", e.id);
                    obj.put("status", e.status);
                    obj.put("warningCount", e.warningCount);
                    obj.put("suspendedUntil", e.suspendedUntil);
                    obj.put("throttled", e.throttled);
                    employersArray.put(obj);
                }
            }
            root.put("employers", employersArray);

            // Compute SHA-256 fingerprint of the payload (without the hash field itself)
            // and embed it so importers can verify integrity.
            String payloadHash = computeSha256OfString(root.toString());
            if (payloadHash != null) {
                root.put("sha256", payloadHash);
            }

            os.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | JSONException e) {
            return false;
        }
    }

    /** Simple container for import outcome. */
    private static class ImportResult {
        final String message;
        ImportResult(String message) { this.message = message; }
    }

    private ImportResult performImport(Uri uri) {
        // Read file content
        String content;
        try (InputStream is = requireContext().getContentResolver().openInputStream(uri)) {
            if (is == null) return new ImportResult("Import failed: cannot open file.");
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] temp = new byte[8192];
            int len;
            while ((len = is.read(temp)) != -1) { buffer.write(temp, 0, len); }
            byte[] bytes = buffer.toByteArray();
            content = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new ImportResult("Import failed: " + e.getMessage());
        }

        // Parse JSON
        JSONObject root;
        try {
            root = new JSONObject(content);
        } catch (JSONException e) {
            return new ImportResult("Import failed: file is not valid JSON.");
        }

        // Validate format
        String format = root.optString("format", "");
        if (!"roadrunner_v1".equals(format)) {
            return new ImportResult("Import failed: unsupported format \"" + format + "\". Expected roadrunner_v1.");
        }

        // Verify SHA-256 fingerprint (required).
        // The embedded hash was computed over the JSON payload before the sha256 field was added,
        // so we remove it, recompute, and compare.
        String embeddedHash = root.optString("sha256", null);
        if (embeddedHash == null || embeddedHash.isEmpty()) {
            return new ImportResult("Import failed: file is missing a SHA-256 fingerprint. Only signed exports are accepted.");
        }
        try {
            JSONObject rootForVerification = new JSONObject(content);
            rootForVerification.remove("sha256");
            String computedHash = computeSha256OfString(rootForVerification.toString());
            if (computedHash == null) {
                return new ImportResult("Import failed: unable to compute SHA-256 for verification.");
            }
            if (!computedHash.equalsIgnoreCase(embeddedHash)) {
                return new ImportResult("Import failed: SHA-256 fingerprint mismatch. File may be corrupted or tampered.");
            }
        } catch (JSONException e) {
            return new ImportResult("Import failed: unable to verify SHA-256 fingerprint.");
        }

        ServiceLocator sl = ServiceLocator.getInstance();
        String orgId = sl.getSessionManager().getOrgId();
        if (orgId == null) orgId = root.optString("orgId", "");

        int totalImported = 0;
        int totalSkipped = 0;

        // Import products (via domain use case with role-aware authorization)
        String actorRole = sl.getSessionManager().getRole();
        JSONArray productsArray = root.optJSONArray("products");
        if (productsArray != null) {
            CreateProductUseCase createProductUseCase = sl.getCreateProductUseCase();
            for (int i = 0; i < productsArray.length(); i++) {
                try {
                    JSONObject obj = productsArray.getJSONObject(i);
                    Product p = new Product(
                            obj.optString("id", null),
                            orgId,
                            obj.optString("name", "").trim(),
                            obj.optString("brand", ""),
                            obj.optString("series", ""),
                            obj.optString("model", ""),
                            obj.optString("description", ""),
                            obj.optLong("unitPriceCents", -1),
                            obj.optDouble("taxRate", 0.0),
                            obj.optBoolean("regulated", false),
                            obj.optString("status", "ACTIVE"),
                            obj.isNull("imageUri") ? null : obj.optString("imageUri", null)
                    );
                    Result<Product> result = createProductUseCase.execute(p, actorRole);
                    if (result.isSuccess()) {
                        totalImported++;
                    } else {
                        totalSkipped++;
                    }
                } catch (JSONException | RuntimeException ignored) {
                    totalSkipped++;
                }
            }
        }

        // Import shipping templates (via domain use case with role-aware authorization)
        JSONArray templatesArray = root.optJSONArray("shippingTemplates");
        if (templatesArray != null) {
            CreateShippingTemplateUseCase createTemplateUseCase = sl.getCreateShippingTemplateUseCase();
            for (int i = 0; i < templatesArray.length(); i++) {
                try {
                    JSONObject obj = templatesArray.getJSONObject(i);
                    ShippingTemplate template = new ShippingTemplate(
                            obj.optString("id", java.util.UUID.randomUUID().toString()),
                            orgId,
                            obj.optString("name", "").trim(),
                            obj.optString("description", ""),
                            obj.optLong("costCents", -1),
                            obj.optInt("minDays", -1),
                            obj.optInt("maxDays", -1),
                            obj.optBoolean("isPickup", false)
                    );
                    Result<ShippingTemplate> result = createTemplateUseCase.execute(template, "ADMIN");
                    if (result.isSuccess()) {
                        totalImported++;
                    } else {
                        totalSkipped++;
                    }
                } catch (JSONException | RuntimeException ignored) {
                    totalSkipped++;
                }
            }
        }

        // Import discount rules (via domain use case with role-aware authorization)
        JSONArray discountArray = root.optJSONArray("discountRules");
        if (discountArray != null) {
            CreateDiscountRuleUseCase createRuleUseCase = sl.getCreateDiscountRuleUseCase();
            for (int i = 0; i < discountArray.length(); i++) {
                try {
                    JSONObject obj = discountArray.getJSONObject(i);
                    DiscountRule rule = new DiscountRule(
                            obj.optString("id", java.util.UUID.randomUUID().toString()),
                            orgId,
                            obj.optString("name", "").trim(),
                            obj.optString("type", ""),
                            obj.optDouble("value", -1.0),
                            obj.optString("status", "ACTIVE")
                    );
                    Result<DiscountRule> result = createRuleUseCase.execute(rule, "ADMIN");
                    if (result.isSuccess()) {
                        totalImported++;
                    } else {
                        totalSkipped++;
                    }
                } catch (JSONException | RuntimeException ignored) {
                    totalSkipped++;
                }
            }
        }

        // Import zones (via domain use case with role-aware authorization)
        JSONArray zonesArray = root.optJSONArray("zones");
        if (zonesArray != null) {
            CreateZoneUseCase createZoneUseCase = sl.getCreateZoneUseCase();
            for (int i = 0; i < zonesArray.length(); i++) {
                try {
                    JSONObject obj = zonesArray.getJSONObject(i);
                    Zone z = new Zone(
                            obj.optString("id", java.util.UUID.randomUUID().toString()),
                            orgId,
                            obj.optString("name", "").trim(),
                            obj.optInt("score", -1),
                            obj.optString("description", "")
                    );
                    Result<Zone> result = createZoneUseCase.execute(z, "ADMIN");
                    if (result.isSuccess()) {
                        totalImported++;
                    } else {
                        totalSkipped++;
                    }
                } catch (JSONException | RuntimeException ignored) {
                    totalSkipped++;
                }
            }
        }

        // Import employers (with EIN/address/content validation via VerifyEmployerUseCase)
        JSONArray employersArray = root.optJSONArray("employers");
        if (employersArray != null) {
            VerifyEmployerUseCase verifyUseCase = sl.getVerifyEmployerUseCase();
            for (int i = 0; i < employersArray.length(); i++) {
                try {
                    JSONObject obj = employersArray.getJSONObject(i);
                    // Build a candidate employer with an empty id so the use case treats
                    // it as a new record: runs full validation (EIN format, state code,
                    // ZIP format, duplicate-EIN check) and inserts on success.
                    Employer candidate = new Employer(
                            "",
                            orgId,
                            obj.optString("legalName", ""),
                            obj.optString("ein", ""),
                            obj.optString("streetAddress", ""),
                            obj.optString("city", ""),
                            obj.optString("state", ""),
                            obj.optString("zipCode", ""),
                            obj.optString("status", "PENDING"),
                            obj.optInt("warningCount", 0),
                            obj.optLong("suspendedUntil", 0),
                            obj.optBoolean("throttled", false)
                    );
                    Result<Employer> validation = verifyUseCase.execute(candidate, "ADMIN");
                    if (!validation.isSuccess()) {
                        totalSkipped++;
                        continue;
                    }
                    // The use case already inserted the validated employer; count it.
                    totalImported++;
                } catch (JSONException | RuntimeException ignored) {
                    // Skip malformed records
                    totalSkipped++;
                }
            }
        }

        String resultMsg = "Imported " + totalImported + " record(s)";
        if (totalSkipped > 0) {
            resultMsg += " (" + totalSkipped + " skipped)";
        }
        return new ImportResult(resultMsg);
    }
}
