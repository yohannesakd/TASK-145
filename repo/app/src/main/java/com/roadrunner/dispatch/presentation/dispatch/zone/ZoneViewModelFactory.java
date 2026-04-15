package com.roadrunner.dispatch.presentation.dispatch.zone;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.roadrunner.dispatch.di.ServiceLocator;

public class ZoneViewModelFactory implements ViewModelProvider.Factory {

    private final ServiceLocator serviceLocator;

    public ZoneViewModelFactory(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new ZoneViewModel(serviceLocator.getZoneRepository());
    }
}
