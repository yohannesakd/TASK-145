package com.roadrunner.dispatch.presentation.admin;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.User;
import com.roadrunner.dispatch.core.domain.usecase.RegisterUserUseCase;
import com.roadrunner.dispatch.di.ServiceLocator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserManagementViewModel extends ViewModel {

    private final RegisterUserUseCase registerUserUseCase;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<Result<User>> registrationResult = new MutableLiveData<>();

    public UserManagementViewModel(RegisterUserUseCase registerUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
    }

    public LiveData<Result<User>> getRegistrationResult() {
        return registrationResult;
    }

    public void registerUser(String orgId, String username, String password, String role) {
        registerUser(orgId, username, password, role, null);
    }

    public void registerUser(String orgId, String username, String password, String role, String zoneId) {
        executor.execute(() -> {
            Result<User> result = registerUserUseCase.execute(orgId, username, password, role, zoneId);
            registrationResult.postValue(result);
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    public static class Factory implements ViewModelProvider.Factory {
        private final ServiceLocator sl;

        public Factory(ServiceLocator sl) {
            this.sl = sl;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            return (T) new UserManagementViewModel(sl.getRegisterUserUseCase());
        }
    }
}
