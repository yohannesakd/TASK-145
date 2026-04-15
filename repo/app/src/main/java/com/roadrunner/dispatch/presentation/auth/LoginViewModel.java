package com.roadrunner.dispatch.presentation.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.roadrunner.dispatch.core.domain.model.Result;
import com.roadrunner.dispatch.core.domain.model.Session;
import com.roadrunner.dispatch.core.domain.usecase.LoginUseCase;
import com.roadrunner.dispatch.infrastructure.security.SessionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginViewModel extends ViewModel {
    private final LoginUseCase loginUseCase;
    private final SessionManager sessionManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<LoginState> loginState = new MutableLiveData<>();

    public LoginViewModel(LoginUseCase loginUseCase, SessionManager sessionManager) {
        this.loginUseCase = loginUseCase;
        this.sessionManager = sessionManager;
    }

    public LiveData<LoginState> getLoginState() {
        return loginState;
    }

    public void login(String username, String password) {
        loginState.setValue(LoginState.loading());
        executor.execute(() -> {
            Result<Session> result = loginUseCase.execute(username, password);
            if (result.isSuccess()) {
                Session session = result.getData();
                sessionManager.createSession(session.userId, session.orgId, session.role, username);
                loginState.postValue(LoginState.success(session.role));
            } else {
                loginState.postValue(LoginState.error(result.getFirstError()));
            }
        });
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public boolean isLoggedIn() {
        return sessionManager.isLoggedIn();
    }

    public String getCurrentRole() {
        return sessionManager.getRole();
    }

    public void logout() {
        sessionManager.clearSession();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }

    // --- State class ---
    public static class LoginState {
        public enum Status { IDLE, LOADING, SUCCESS, ERROR }

        public final Status status;
        public final String role;
        public final String error;

        private LoginState(Status status, String role, String error) {
            this.status = status;
            this.role = role;
            this.error = error;
        }

        public static LoginState loading() { return new LoginState(Status.LOADING, null, null); }
        public static LoginState success(String role) { return new LoginState(Status.SUCCESS, role, null); }
        public static LoginState error(String error) { return new LoginState(Status.ERROR, null, error); }
    }
}
