package com.roadrunner.dispatch.presentation.compliance.cases;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.roadrunner.dispatch.di.ServiceLocator;

public class ComplianceCaseViewModelFactory implements ViewModelProvider.Factory {

    private final ServiceLocator serviceLocator;

    public ComplianceCaseViewModelFactory(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new ComplianceCaseViewModel(
                serviceLocator.getComplianceCaseRepository(),
                serviceLocator.getAuditLogRepository(),
                serviceLocator.getOpenCaseUseCase(),
                serviceLocator.getEnforceViolationUseCase());
    }
}
