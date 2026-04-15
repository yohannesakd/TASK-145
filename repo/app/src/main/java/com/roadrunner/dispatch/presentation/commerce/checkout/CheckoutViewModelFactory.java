package com.roadrunner.dispatch.presentation.commerce.checkout;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.roadrunner.dispatch.di.ServiceLocator;

public class CheckoutViewModelFactory implements ViewModelProvider.Factory {

    private final ServiceLocator serviceLocator;

    public CheckoutViewModelFactory(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new CheckoutViewModel(
                serviceLocator.getCreateOrderFromCartUseCase(),
                serviceLocator.getFinalizeCheckoutUseCase(),
                serviceLocator.getComputeOrderTotalsUseCase(),
                serviceLocator.getValidateDiscountsUseCase(),
                serviceLocator.getOrderRepository());
    }
}
