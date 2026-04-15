package com.roadrunner.dispatch.presentation.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Product;
import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Admin configuration screen for managing shipping templates, discount rules,
 * and matching weights. Provides CRUD operations via simple dialogs.
 */
public class AdminConfigFragment extends Fragment {

    private String orgId;
    private RecyclerView recyclerProducts;
    private RecyclerView recyclerShipping;
    private RecyclerView recyclerDiscounts;
    private TextView tvProductsEmpty;
    private TextView tvShippingEmpty;
    private TextView tvDiscountsEmpty;
    private final ProductConfigAdapter productConfigAdapter = new ProductConfigAdapter();
    private final ShippingTemplateAdapter shippingTemplateAdapter = new ShippingTemplateAdapter();
    private final DiscountRuleAdapter discountRuleAdapter = new DiscountRuleAdapter();

    // Matching weight views
    private TextInputEditText etWeightTime;
    private TextInputEditText etWeightLoad;
    private TextInputEditText etWeightRep;
    private TextInputEditText etWeightZone;

    public AdminConfigFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_config, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!RoleGuard.hasRole("ADMIN")) {
            TextView tvError = view.findViewById(R.id.tv_config_error);
            if (tvError != null) {
                tvError.setVisibility(View.VISIBLE);
                tvError.setText("Access denied. Admin role required.");
            }
            return;
        }

        orgId = ServiceLocator.getInstance().getSessionManager().getOrgId();

        recyclerProducts = view.findViewById(R.id.recycler_products);
        recyclerShipping = view.findViewById(R.id.recycler_shipping_templates);
        recyclerDiscounts = view.findViewById(R.id.recycler_discount_rules);
        tvProductsEmpty = view.findViewById(R.id.tv_products_empty);
        tvShippingEmpty = view.findViewById(R.id.tv_shipping_empty);
        tvDiscountsEmpty = view.findViewById(R.id.tv_discounts_empty);

        recyclerProducts.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerShipping.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerDiscounts.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerProducts.setAdapter(productConfigAdapter);
        recyclerShipping.setAdapter(shippingTemplateAdapter);
        recyclerDiscounts.setAdapter(discountRuleAdapter);

        // Matching weights
        etWeightTime = view.findViewById(R.id.et_weight_time);
        etWeightLoad = view.findViewById(R.id.et_weight_load);
        etWeightRep = view.findViewById(R.id.et_weight_rep);
        etWeightZone = view.findViewById(R.id.et_weight_zone);

        loadMatchingWeights();

        MaterialButton btnSaveWeights = view.findViewById(R.id.btn_save_weights);
        btnSaveWeights.setOnClickListener(v -> saveMatchingWeights());

        MaterialButton btnAddProduct = view.findViewById(R.id.btn_add_product);
        btnAddProduct.setOnClickListener(v -> showAddProductDialog());

        MaterialButton btnAddShipping = view.findViewById(R.id.btn_add_shipping);
        btnAddShipping.setOnClickListener(v -> showAddShippingDialog());

        MaterialButton btnAddDiscount = view.findViewById(R.id.btn_add_discount_rule);
        btnAddDiscount.setOnClickListener(v -> showAddDiscountDialog());

        observeProducts();
        loadShippingTemplates();
        loadDiscountRules();
    }

    private void observeProducts() {
        ServiceLocator.getInstance().getProductRepository()
                .getActiveProducts(orgId)
                .observe(getViewLifecycleOwner(), products -> {
                    if (products == null || products.isEmpty()) {
                        tvProductsEmpty.setVisibility(View.VISIBLE);
                        recyclerProducts.setVisibility(View.GONE);
                    } else {
                        tvProductsEmpty.setVisibility(View.GONE);
                        recyclerProducts.setVisibility(View.VISIBLE);
                        productConfigAdapter.submitList(products);
                    }
                });
    }

    private void showAddProductDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_product, null);
        TextInputEditText etName = dialogView.findViewById(R.id.et_product_name);
        TextInputEditText etBrand = dialogView.findViewById(R.id.et_product_brand);
        TextInputEditText etSeries = dialogView.findViewById(R.id.et_product_series);
        TextInputEditText etModel = dialogView.findViewById(R.id.et_product_model);
        TextInputEditText etDescription = dialogView.findViewById(R.id.et_product_description);
        TextInputEditText etUnitPrice = dialogView.findViewById(R.id.et_product_unit_price);
        TextInputEditText etTaxRate = dialogView.findViewById(R.id.et_product_tax_rate);
        TextInputEditText etImageUri = dialogView.findViewById(R.id.et_product_image_uri);
        android.widget.CheckBox cbRegulated = dialogView.findViewById(R.id.cb_product_regulated);

        new AlertDialog.Builder(requireContext())
                .setTitle("Add Product")
                .setView(dialogView)
                .setPositiveButton(R.string.create, (d, w) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(name)) return;
                    String priceText = etUnitPrice.getText() != null
                            ? etUnitPrice.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(priceText)) return;
                    try {
                        long priceCents = (long) (Double.parseDouble(priceText) * 100);
                        double taxRate = 0.0;
                        String taxText = etTaxRate.getText() != null
                                ? etTaxRate.getText().toString().trim() : "";
                        if (!TextUtils.isEmpty(taxText)) {
                            taxRate = Double.parseDouble(taxText);
                        }
                        String brand = etBrand.getText() != null
                                ? etBrand.getText().toString().trim() : "";
                        String series = etSeries.getText() != null
                                ? etSeries.getText().toString().trim() : "";
                        String model = etModel.getText() != null
                                ? etModel.getText().toString().trim() : "";
                        String description = etDescription.getText() != null
                                ? etDescription.getText().toString().trim() : "";
                        String imageUri = etImageUri.getText() != null
                                ? etImageUri.getText().toString().trim() : "";
                        boolean regulated = cbRegulated.isChecked();

                        Product product = new Product(
                                UUID.randomUUID().toString(),
                                orgId,
                                name,
                                brand,
                                series,
                                model,
                                description,
                                priceCents,
                                taxRate,
                                regulated,
                                "ACTIVE",
                                imageUri
                        );

                        Executors.newSingleThreadExecutor().execute(() ->
                                ServiceLocator.getInstance().getProductRepository().insert(product));
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void loadShippingTemplates() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<ShippingTemplate> templates =
                    ServiceLocator.getInstance().getOrderRepository().getShippingTemplates(orgId);
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (templates == null || templates.isEmpty()) {
                    tvShippingEmpty.setVisibility(View.VISIBLE);
                    recyclerShipping.setVisibility(View.GONE);
                } else {
                    tvShippingEmpty.setVisibility(View.GONE);
                    recyclerShipping.setVisibility(View.VISIBLE);
                    shippingTemplateAdapter.submitList(templates);
                }
            });
        });
    }

    private void loadDiscountRules() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<DiscountRule> rules =
                    ServiceLocator.getInstance().getOrderRepository().getActiveDiscountRules(orgId);
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (rules == null || rules.isEmpty()) {
                    tvDiscountsEmpty.setVisibility(View.VISIBLE);
                    recyclerDiscounts.setVisibility(View.GONE);
                } else {
                    tvDiscountsEmpty.setVisibility(View.GONE);
                    recyclerDiscounts.setVisibility(View.VISIBLE);
                    discountRuleAdapter.submitList(rules);
                }
            });
        });
    }

    private void loadMatchingWeights() {
        android.content.SharedPreferences prefs =
                requireContext().getSharedPreferences("matching_weights",
                        android.content.Context.MODE_PRIVATE);
        double w1 = Double.longBitsToDouble(prefs.getLong("w_time", Double.doubleToLongBits(0.3)));
        double w2 = Double.longBitsToDouble(prefs.getLong("w_load", Double.doubleToLongBits(0.25)));
        double w3 = Double.longBitsToDouble(prefs.getLong("w_rep", Double.doubleToLongBits(0.25)));
        double w4 = Double.longBitsToDouble(prefs.getLong("w_zone", Double.doubleToLongBits(0.2)));
        etWeightTime.setText(String.valueOf(w1));
        etWeightLoad.setText(String.valueOf(w2));
        etWeightRep.setText(String.valueOf(w3));
        etWeightZone.setText(String.valueOf(w4));
    }

    private void saveMatchingWeights() {
        try {
            double w1 = Double.parseDouble(etWeightTime.getText().toString().trim());
            double w2 = Double.parseDouble(etWeightLoad.getText().toString().trim());
            double w3 = Double.parseDouble(etWeightRep.getText().toString().trim());
            double w4 = Double.parseDouble(etWeightZone.getText().toString().trim());

            requireContext().getSharedPreferences("matching_weights",
                            android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putLong("w_time", Double.doubleToLongBits(w1))
                    .putLong("w_load", Double.doubleToLongBits(w2))
                    .putLong("w_rep", Double.doubleToLongBits(w3))
                    .putLong("w_zone", Double.doubleToLongBits(w4))
                    .apply();

            new AlertDialog.Builder(requireContext())
                    .setMessage("Matching weights saved.")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (NumberFormatException e) {
            new AlertDialog.Builder(requireContext())
                    .setMessage("Invalid weight values. Enter decimal numbers (e.g. 0.3).")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    private void showAddShippingDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_shipping, null);
        TextInputEditText etName = dialogView.findViewById(R.id.et_shipping_name);
        TextInputEditText etCost = dialogView.findViewById(R.id.et_shipping_cost);
        TextInputEditText etMinDays = dialogView.findViewById(R.id.et_shipping_min_days);
        TextInputEditText etMaxDays = dialogView.findViewById(R.id.et_shipping_max_days);
        android.widget.CheckBox cbPickup = dialogView.findViewById(R.id.cb_is_pickup);

        new AlertDialog.Builder(requireContext())
                .setTitle("Add Shipping Template")
                .setView(dialogView)
                .setPositiveButton(R.string.create, (d, w) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(name)) return;
                    try {
                        String costText = etCost.getText() != null ? etCost.getText().toString().trim() : "";
                        String minText = etMinDays.getText() != null ? etMinDays.getText().toString().trim() : "";
                        String maxText = etMaxDays.getText() != null ? etMaxDays.getText().toString().trim() : "";
                        long costCents = (long) (Double.parseDouble(costText) * 100);
                        int minDays = Integer.parseInt(minText);
                        int maxDays = Integer.parseInt(maxText);
                        boolean isPickup = cbPickup.isChecked();

                        ShippingTemplate template = new ShippingTemplate(
                                UUID.randomUUID().toString(),
                                orgId,
                                name,
                                "",
                                costCents,
                                minDays,
                                maxDays,
                                isPickup
                        );

                        Executors.newSingleThreadExecutor().execute(() -> {
                            Result<ShippingTemplate> result = ServiceLocator.getInstance()
                                    .getCreateShippingTemplateUseCase()
                                    .execute(template, RoleGuard.currentRole());
                            if (getActivity() != null && result.isSuccess()) {
                                getActivity().runOnUiThread(this::loadShippingTemplates);
                            }
                        });
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showAddDiscountDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_add_discount, null);
        TextInputEditText etName = dialogView.findViewById(R.id.et_discount_name);
        TextInputEditText etValue = dialogView.findViewById(R.id.et_discount_value);
        android.widget.RadioGroup rgType = dialogView.findViewById(R.id.rg_discount_type);

        new AlertDialog.Builder(requireContext())
                .setTitle("Add Discount Rule")
                .setView(dialogView)
                .setPositiveButton(R.string.create, (d, w) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    if (TextUtils.isEmpty(name)) return;
                    try {
                        String valText = etValue.getText() != null ? etValue.getText().toString().trim() : "";
                        double value = Double.parseDouble(valText);
                        String type = (rgType.getCheckedRadioButtonId() == R.id.rb_percent_off)
                                ? "PERCENT_OFF" : "FLAT_OFF";

                        DiscountRule rule = new DiscountRule(
                                UUID.randomUUID().toString(),
                                orgId,
                                name,
                                type,
                                value,
                                "ACTIVE"
                        );

                        Executors.newSingleThreadExecutor().execute(() -> {
                            Result<DiscountRule> result = ServiceLocator.getInstance()
                                    .getCreateDiscountRuleUseCase()
                                    .execute(rule, RoleGuard.currentRole());
                            if (getActivity() != null && result.isSuccess()) {
                                getActivity().runOnUiThread(this::loadDiscountRules);
                            }
                        });
                    } catch (NumberFormatException ignored) {}
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
