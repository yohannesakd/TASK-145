package com.roadrunner.dispatch.presentation.commerce.checkout;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.core.domain.model.Order;
import com.roadrunner.dispatch.core.domain.model.OrderItem;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;
import com.roadrunner.dispatch.presentation.common.RoleGuard;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Read-only invoice screen displayed after a successful order finalization.
 *
 * <p>Receives the order ID via navigation arguments and loads all order data
 * from {@link CheckoutViewModel}. The invoice is fully read-only — no edits
 * are possible at this stage.
 */
public class InvoiceFragment extends Fragment {

    private CheckoutViewModel viewModel;
    private SessionManager sessionManager;

    // ── Views ─────────────────────────────────────────────────────────────────

    private TextView       textOrderId;
    private TextView       textInvoiceDate;
    private TextView       textCustomerId;
    private TextView       textStoreId;
    private LinearLayout   containerLineItems;
    private TextView       textInvoiceSubtotal;
    private TextView       textInvoiceDiscount;
    private LinearLayout   rowDiscount;
    private TextView       textInvoiceTax;
    private TextView       textInvoiceShipping;
    private TextView       textInvoiceTotal;
    private LinearLayout   sectionOrderNotes;
    private TextView       textInvoiceNotes;
    private TextView       textShippingMethod;
    private TextView       textStatusBadge;
    private MaterialButton btnDone;

    // ── Fragment lifecycle ────────────────────────────────────────────────────

    public InvoiceFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_invoice, container, false);
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

        // Load the order by ID passed from CheckoutFragment.
        Bundle args = getArguments();
        if (args != null) {
            String orderId = args.getString("orderId");
            if (orderId != null && !orderId.isEmpty()) {
                viewModel.loadOrder(orderId, sessionManager.getOrgId());
            }
        }

        observeViewModel();

        btnDone.setOnClickListener(v ->
                Navigation.findNavController(v).popBackStack(R.id.catalogFragment, false));
    }

    // ── View wiring ───────────────────────────────────────────────────────────

    private void bindViews(@NonNull View view) {
        textOrderId        = view.findViewById(R.id.text_order_id);
        textInvoiceDate    = view.findViewById(R.id.text_invoice_date);
        textCustomerId     = view.findViewById(R.id.text_customer_id);
        textStoreId        = view.findViewById(R.id.text_store_id);
        containerLineItems = view.findViewById(R.id.container_line_items);
        textInvoiceSubtotal = view.findViewById(R.id.text_invoice_subtotal);
        textInvoiceDiscount = view.findViewById(R.id.text_invoice_discount);
        rowDiscount        = view.findViewById(R.id.row_discount);
        textInvoiceTax     = view.findViewById(R.id.text_invoice_tax);
        textInvoiceShipping = view.findViewById(R.id.text_invoice_shipping);
        textInvoiceTotal   = view.findViewById(R.id.text_invoice_total);
        sectionOrderNotes  = view.findViewById(R.id.section_order_notes);
        textInvoiceNotes   = view.findViewById(R.id.text_invoice_notes);
        textShippingMethod = view.findViewById(R.id.text_shipping_method);
        textStatusBadge    = view.findViewById(R.id.text_status_badge);
        btnDone            = view.findViewById(R.id.btn_done);
    }

    // ── ViewModel observation ─────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getOrder().observe(getViewLifecycleOwner(), this::renderOrder);
        viewModel.getOrderItems().observe(getViewLifecycleOwner(), this::renderLineItems);
    }

    private void renderOrder(@Nullable Order order) {
        if (order == null) return;

        textOrderId.setText("Order #" + order.id);
        textInvoiceDate.setText(DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(order.totalsComputedAt > 0
                        ? order.totalsComputedAt : System.currentTimeMillis())));

        textCustomerId.setText("Customer: " + (order.customerId != null ? order.customerId : "N/A"));
        textStoreId.setText("Store: " + (order.storeId != null ? order.storeId : "N/A"));

        // Totals
        textInvoiceSubtotal.setText(formatCents(order.subtotalCents));
        textInvoiceTax.setText(formatCents(order.taxCents));
        textInvoiceShipping.setText(formatCents(order.shippingCents));
        textInvoiceTotal.setText(formatCents(order.totalCents));

        if (order.discountCents > 0) {
            rowDiscount.setVisibility(View.VISIBLE);
            textInvoiceDiscount.setText("-" + formatCents(order.discountCents));
        } else {
            rowDiscount.setVisibility(View.GONE);
        }

        // Order notes
        if (order.orderNotes != null && !order.orderNotes.isEmpty()) {
            sectionOrderNotes.setVisibility(View.VISIBLE);
            textInvoiceNotes.setText(order.orderNotes);
        } else {
            sectionOrderNotes.setVisibility(View.GONE);
        }

        // Shipping method label — resolve template name in background
        if (order.shippingTemplateId != null && !order.shippingTemplateId.isEmpty()) {
            textShippingMethod.setText("Shipping: " + order.shippingTemplateId); // fallback to ID
            final String tmplId = order.shippingTemplateId;
            final String tmplOrgId = order.orgId != null ? order.orgId : sessionManager.getOrgId();
            java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                com.roadrunner.dispatch.core.domain.model.ShippingTemplate tmpl =
                        ServiceLocator.getInstance().getOrderRepository()
                                .getShippingTemplateScoped(tmplId, tmplOrgId);
                if (tmpl != null && isAdded()) {
                    final String label = tmpl.isPickup
                            ? "Pickup: " + tmpl.name
                            : "Shipping: " + tmpl.name;
                    requireActivity().runOnUiThread(() -> textShippingMethod.setText(label));
                }
            });
        } else {
            textShippingMethod.setText("");
        }

        // Status badge
        textStatusBadge.setText(order.status != null ? order.status : "UNKNOWN");
    }

    private void renderLineItems(@Nullable List<OrderItem> items) {
        if (items == null || items.isEmpty()) return;

        // Clear any previously inflated rows (e.g., after a config change)
        // Keep the header TextView at index 0 by removing subsequent children.
        int childCount = containerLineItems.getChildCount();
        if (childCount > 1) {
            containerLineItems.removeViews(1, childCount - 1);
        }

        for (OrderItem item : items) {
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_order_line, containerLineItems, false);

            TextView name      = row.findViewById(R.id.text_order_product_name);
            TextView qtyPrice  = row.findViewById(R.id.text_order_qty_price);
            TextView lineTotal = row.findViewById(R.id.text_order_line_total);

            name.setText(item.productName);
            qtyPrice.setText(String.format(Locale.getDefault(),
                    "%d x %s", item.quantity, formatCents(item.unitPriceCents)));
            lineTotal.setText(formatCents(item.lineTotalCents));

            containerLineItems.addView(row);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String formatCents(long cents) {
        return String.format(Locale.getDefault(), "$%.2f", cents / 100.0);
    }
}
