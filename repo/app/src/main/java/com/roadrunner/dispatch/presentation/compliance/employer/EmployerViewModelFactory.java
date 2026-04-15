package com.roadrunner.dispatch.presentation.compliance.employer;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.roadrunner.dispatch.di.ServiceLocator;

public class EmployerViewModelFactory implements ViewModelProvider.Factory {

    private final ServiceLocator serviceLocator;

    public EmployerViewModelFactory(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new EmployerViewModel(
                serviceLocator.getEmployerRepository(),
                serviceLocator.getVerifyEmployerUseCase(),
                serviceLocator.getScanContentUseCase());
    }
}
