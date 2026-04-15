package com.roadrunner.dispatch.presentation.commerce.cart;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.roadrunner.dispatch.di.ServiceLocator;

public class CartViewModelFactory implements ViewModelProvider.Factory {

    private final ServiceLocator serviceLocator;

    public CartViewModelFactory(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new CartViewModel(
                serviceLocator.getCartRepository(),
                serviceLocator.getAddToCartUseCase(),
                serviceLocator.getResolveCartConflictUseCase());
    }
}
