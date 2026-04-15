package com.roadrunner.dispatch.presentation.dispatch.taskdetail;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.roadrunner.dispatch.di.ServiceLocator;

public class TaskDetailViewModelFactory implements ViewModelProvider.Factory {

    private final ServiceLocator serviceLocator;

    public TaskDetailViewModelFactory(ServiceLocator serviceLocator) {
        this.serviceLocator = serviceLocator;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new TaskDetailViewModel(
                serviceLocator.getTaskRepository(),
                serviceLocator.getAcceptTaskUseCase(),
                serviceLocator.getCompleteTaskUseCase(),
                serviceLocator.getMatchTasksUseCase());
    }
}
