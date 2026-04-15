package com.roadrunner.dispatch.presentation.dispatch.tasklist;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.roadrunner.dispatch.di.ServiceLocator;

public class TaskListViewModelFactory implements ViewModelProvider.Factory {

    private final ServiceLocator serviceLocator;

    public TaskListViewModelFactory(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new TaskListViewModel(
                serviceLocator.getTaskRepository(),
                serviceLocator.getWorkerRepository(),
                serviceLocator.getCreateTaskUseCase(),
                serviceLocator.getMatchTasksUseCase(),
                serviceLocator.getScanContentUseCase());
    }
}
