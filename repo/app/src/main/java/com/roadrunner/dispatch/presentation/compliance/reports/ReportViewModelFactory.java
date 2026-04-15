package com.roadrunner.dispatch.presentation.compliance.reports;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.roadrunner.dispatch.di.ServiceLocator;

public class ReportViewModelFactory implements ViewModelProvider.Factory {

    private final ServiceLocator serviceLocator;

    public ReportViewModelFactory(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new ReportViewModel(
                serviceLocator.getReportRepository(),
                serviceLocator.getFileReportUseCase());
    }
}
