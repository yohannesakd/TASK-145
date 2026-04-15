package com.roadrunner.dispatch.presentation.commerce.checkout;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.DiscountRule;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.core.domain.model.OrderTotals;
import com.roadrunner.dispatch.core.domain.model.ShippingTemplate;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import java.util.List;
import java.util.Locale;

/**
 * Fragment that drives the checkout flow.
 *
 * <p>Flow:
 * <ol>
 *   <li>On view creation, creates a DRAFT order from the incoming cart ID.
 *   <li>Shipping template selection via radio buttons (populated from the ViewModel).
 *   <li>Discount chips added via a dialog.
 *   <li>"Calculate Totals" runs the server-side computation and refreshes the summary card.
 *   <li>A stale-totals warning banner appears when shipping or discounts change.
 *   <li>"Finalize Order" submits; on success navigates to {@link InvoiceFragment}.
 * </ol>
 */
public class CheckoutFragment extends Fragment {

    private CheckoutViewModel viewModel;
    private SessionManager sessionManager;
    private OrderItemAdapter orderItemAdapter;

    // Holds the current order ID once the DRAFT order is created.
    private String currentOrderId;

    // Shipping templates loaded from the ViewModel, indexed to match radio buttons.
    private List<ShippingTemplate> shippingTemplates;

    // ── Views ─────────────────────────────────────────────────────────────────

    private RecyclerView    recyclerOrderItems;
    private RadioGroup      radioGroupShipping;
    private ChipGroup       chipGroupDiscounts;
    private MaterialButton  btnAddDiscount;
    private TextInputEditText editOrderNotes;
    private CardView        bannerStale;
    private MaterialButton  btnBannerRecalculate;
    private TextView        textSummarySubtotal;
    private TextView        textSummaryDiscounts;
    private TextView        textSummaryTax;
    private TextView        textSummaryShipping;
    private TextView        textSummaryTotal;
    private MaterialButton  btnComputeTotals;
    private MaterialButton  btnFinalize;
    private ProgressBar     progressCheckout;
    private TextView        textError;

    // ── Fragment lifecycle ────────────────────────────────────────────────────

    public CheckoutFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_checkout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!RoleGuard.hasRole("ADMIN", "DISPATCHER", "WORKER")) {
            android.widget.TextView tvDenied = new android.widget.TextView(requireContext());
            tvDenied.setText("Access denied.");
            tvDenied.setPadding(32, 32, 32, 32);
            ((ViewGroup) view).addView(tvDenied);
            return;
        }

        ServiceLocator serviceLocator = ServiceLocator.getInstance();
        sessionManager = serviceLocator.getSessionManager();

        CheckoutViewModelFactory factory = new CheckoutViewModelFactory(serviceLocator);
        viewModel = new ViewModelProvider(this, factory).get(CheckoutViewModel.class);

        bindViews(view);
        setupRecyclerView();
        setupShippingListeners();
        setupButtonListeners();
        observeViewModel();

        // Create DRAFT order from the incoming cart ID.
        Bundle args = getArguments();
        if (args != null) {
            String cartId = args.getString("cartId");
            if (cartId != null && !cartId.isEmpty()) {
                showLoading(true);
                String actorRoleInit = sessionManager.getRole();
                viewModel.createOrderFromCart(
                        cartId,
                        sessionManager.getUserId(),
                        sessionManager.getOrgId(),
                        actorRoleInit != null ? actorRoleInit : "");
            }
        }
    }

    // ── View wiring ───────────────────────────────────────────────────────────

    private void bindViews(@NonNull View view) {
        recyclerOrderItems    = view.findViewById(R.id.recycler_order_items);
        radioGroupShipping    = view.findViewById(R.id.radio_group_shipping);
        chipGroupDiscounts    = view.findViewById(R.id.chip_group_discounts);
        btnAddDiscount        = view.findViewById(R.id.btn_add_discount);
        editOrderNotes        = view.findViewById(R.id.edit_order_notes);
        bannerStale           = view.findViewById(R.id.banner_stale);
        btnBannerRecalculate  = view.findViewById(R.id.btn_banner_recalculate);
        textSummarySubtotal   = view.findViewById(R.id.text_summary_subtotal);
        textSummaryDiscounts  = view.findViewById(R.id.text_summary_discounts);
        textSummaryTax        = view.findViewById(R.id.text_summary_tax);
        textSummaryShipping   = view.findViewById(R.id.text_summary_shipping);
        textSummaryTotal      = view.findViewById(R.id.text_summary_total);
        btnComputeTotals      = view.findViewById(R.id.btn_compute_totals);
        btnFinalize           = view.findViewById(R.id.btn_finalize);
        progressCheckout      = view.findViewById(R.id.progress_checkout);
        textError             = view.findViewById(R.id.text_error);
    }

    private void setupRecyclerView() {
        orderItemAdapter = new OrderItemAdapter();
        recyclerOrderItems.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerOrderItems.setAdapter(orderItemAdapter);
        recyclerOrderItems.setNestedScrollingEnabled(false);
    }

    private void setupShippingListeners() {
        radioGroupShipping.setOnCheckedChangeListener((group, checkedId) -> {
            if (currentOrderId == null || checkedId == -1) return;

            RadioButton checked = group.findViewById(checkedId);
            if (checked == null) return;

            Object tag = checked.getTag();
            if (tag instanceof String) {
                viewModel.selectShipping(currentOrderId, (String) tag, sessionManager.getOrgId());
            }
        });
    }

    private void setupButtonListeners() {
        btnAddDiscount.setOnClickListener(v -> showDiscountDialog());

        btnBannerRecalculate.setOnClickListener(v -> {
            if (currentOrderId != null) {
                showLoading(true);
                viewModel.computeTotals(currentOrderId, sessionManager.getOrgId());
            }
        });

        btnComputeTotals.setOnClickListener(v -> {
            if (currentOrderId != null) {
                // Persist order notes before computing
                saveOrderNotes();
                showLoading(true);
                viewModel.computeTotals(currentOrderId, sessionManager.getOrgId());
            }
        });

        btnFinalize.setOnClickListener(v -> {
            if (currentOrderId != null) {
                saveOrderNotes();
                showLoading(true);
                btnFinalize.setEnabled(false);
                String actorRole = ServiceLocator.getInstance().getSessionManager().getRole();
                String actorId   = ServiceLocator.getInstance().getSessionManager().getUserId();
                String orgId     = ServiceLocator.getInstance().getSessionManager().getOrgId();
                viewModel.finalize(currentOrderId,
                        actorId != null ? actorId : "",
                        actorRole != null ? actorRole : "",
                        orgId != null ? orgId : "");
            }
        });
    }

    // ── ViewModel observation ─────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getOrder().observe(getViewLifecycleOwner(), this::onOrderUpdated);
        viewModel.getOrderItems().observe(getViewLifecycleOwner(), this::onOrderItemsUpdated);
        viewModel.getTotals().observe(getViewLifecycleOwner(), this::onTotalsUpdated);
        viewModel.getShippingTemplates().observe(getViewLifecycleOwner(), this::onShippingTemplatesUpdated);
        viewModel.getAvailableDiscounts().observe(getViewLifecycleOwner(), discounts -> {
            // Stored for use in the discount dialog; no immediate UI update needed.
        });
        viewModel.getStaleWarning().observe(getViewLifecycleOwner(), stale -> {
            bannerStale.setVisibility(Boolean.TRUE.equals(stale) ? View.VISIBLE : View.GONE);
            if (Boolean.TRUE.equals(stale)) {
                btnFinalize.setEnabled(false);
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            showLoading(false);
            if (error != null && !error.isEmpty()) {
                if (error.startsWith("CONTENT_FLAGGED:")) {
                    // Show confirmation dialog so the user can proceed past flagged content
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Content Warning")
                            .setMessage(error.substring("CONTENT_FLAGGED:".length()).trim())
                            .setPositiveButton("Proceed Anyway", (dialog, which) -> {
                                showLoading(true);
                                btnFinalize.setEnabled(false);
                                String actorRole = ServiceLocator.getInstance().getSessionManager().getRole();
                                String actorId   = ServiceLocator.getInstance().getSessionManager().getUserId();
                                String orgId     = ServiceLocator.getInstance().getSessionManager().getOrgId();
                                viewModel.finalize(currentOrderId,
                                        actorId != null ? actorId : "",
                                        actorRole != null ? actorRole : "",
                                        orgId != null ? orgId : "",
                                        true);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    textError.setText(error);
                    textError.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void onOrderUpdated(@Nullable Order order) {
        showLoading(false);
        if (order == null) return;

        currentOrderId = order.id;

        // Navigate to invoice once finalized
        if ("FINALIZED".equals(order.status)) {
            Bundle args = new Bundle();
            args.putString("orderId", order.id);
            View root = getView();
            if (root != null) {
                Navigation.findNavController(root)
                        .navigate(R.id.action_checkout_to_invoice, args);
            }
        }
    }

    private void onOrderItemsUpdated(@Nullable List<OrderItem> items) {
        if (items != null) {
            orderItemAdapter.submitList(items);
        }
    }

    private void onTotalsUpdated(@Nullable OrderTotals totals) {
        showLoading(false);
        if (totals == null) return;

        textSummarySubtotal.setText(formatCents(totals.subtotalCents));
        textSummaryDiscounts.setText("-" + formatCents(totals.discountCents));
        textSummaryTax.setText(formatCents(totals.taxCents));
        textSummaryShipping.setText(formatCents(totals.shippingCents));
        textSummaryTotal.setText(formatCents(totals.totalCents));

        btnFinalize.setEnabled(true);
        textError.setVisibility(View.GONE);
    }

    private void onShippingTemplatesUpdated(@Nullable List<ShippingTemplate> templates) {
        if (templates == null || templates.isEmpty()) return;
        this.shippingTemplates = templates;

        // Rebuild the RadioGroup from scratch so the buttons always match
        // exactly what the repository returns, regardless of count or order.
        radioGroupShipping.removeAllViews();

        for (ShippingTemplate t : templates) {
            RadioButton btn = new RadioButton(requireContext());
            btn.setId(View.generateViewId());
            btn.setTag(t.id);

            if (t.isPickup) {
                btn.setText(String.format(Locale.getDefault(),
                        "%s — %s", t.name, formatCents(t.costCents)));
            } else {
                btn.setText(String.format(Locale.getDefault(),
                        "%s (%d-%d days) — %s",
                        t.name, t.minDays, t.maxDays, formatCents(t.costCents)));
            }

            RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.MATCH_PARENT,
                    RadioGroup.LayoutParams.WRAP_CONTENT);
            radioGroupShipping.addView(btn, params);
        }
    }

    // ── Discount dialog ───────────────────────────────────────────────────────

    private void showDiscountDialog() {
        List<DiscountRule> available = viewModel.getAvailableDiscounts().getValue();
        if (available == null || available.isEmpty()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("No discounts available")
                    .setMessage("There are no active discount rules for this organisation.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] labels = new String[available.size()];
        for (int i = 0; i < available.size(); i++) {
            DiscountRule rule = available.get(i);
            if ("FLAT_OFF".equals(rule.type)) {
                labels[i] = String.format(Locale.getDefault(),
                        "%s (Flat $%.2f off)", rule.name, rule.value / 100.0);
            } else {
                labels[i] = String.format(Locale.getDefault(),
                        "%s (%.0f%% off)", rule.name, rule.value);
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Select a Discount")
                .setItems(labels, (dialog, which) -> {
                    if (currentOrderId != null) {
                        DiscountRule selected = available.get(which);
                        viewModel.applyDiscount(currentOrderId, selected.id, sessionManager.getOrgId());
                        addDiscountChip(selected);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addDiscountChip(@NonNull DiscountRule rule) {
        Chip chip = new Chip(requireContext());
        chip.setText(rule.name);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            chipGroupDiscounts.removeView(chip);
            if (currentOrderId != null) {
                viewModel.removeDiscount(currentOrderId, rule.id, sessionManager.getOrgId());
            }
        });
        chipGroupDiscounts.addView(chip);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void saveOrderNotes() {
        if (currentOrderId == null) return;
        String notes = editOrderNotes.getText() != null ? editOrderNotes.getText().toString().trim() : "";
        viewModel.saveNotes(currentOrderId, notes, sessionManager.getOrgId());
    }

    private void showLoading(boolean loading) {
        progressCheckout.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnFinalize.setEnabled(!loading);
        btnComputeTotals.setEnabled(!loading);
    }

    private String formatCents(long cents) {
        return String.format(Locale.getDefault(), "$%.2f", cents / 100.0);
    }
}
