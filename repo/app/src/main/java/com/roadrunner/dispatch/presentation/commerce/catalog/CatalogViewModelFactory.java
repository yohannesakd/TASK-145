package com.roadrunner.dispatch.presentation.commerce.catalog;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.roadrunner.dispatch.di.ServiceLocator;

public class CatalogViewModelFactory implements ViewModelProvider.Factory {

    private final ServiceLocator serviceLocator;

    public CatalogViewModelFactory(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new CatalogViewModel(serviceLocator.getProductRepository());
    }
}
