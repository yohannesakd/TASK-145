package com.roadrunner.dispatch.presentation.auth;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.roadrunner.dispatch.core.domain.usecase.LoginUseCase;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;

public class LoginViewModelFactory implements ViewModelProvider.Factory {
    private final LoginUseCase loginUseCase;
    private final SessionManager sessionManager;

    public LoginViewModelFactory(LoginUseCase loginUseCase, SessionManager sessionManager) {
        this.loginUseCase = loginUseCase;
        this.sessionManager = sessionManager;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new LoginViewModel(loginUseCase, sessionManager);
    }
}
