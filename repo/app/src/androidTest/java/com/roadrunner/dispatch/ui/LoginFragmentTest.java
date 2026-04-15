package com.roadrunner.dispatch.ui;

import android.content.Context;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.roadrunner.dispatch.R;
import com.roadrunner.dispatch.di.ServiceLocator;
import com.roadrunner.dispatch.presentation.auth.LoginFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;

/**
 * UI tests for {@link LoginFragment}.
 */
@RunWith(AndroidJUnit4.class)
public class LoginFragmentTest {

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        ServiceLocator sl = ServiceLocator.getInstance(context);
        sl.getSessionManager().clearSession();
    }

    @Test
    public void loginForm_displaysAllElements() {
        FragmentScenario.launchInContainer(LoginFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.edit_username)).check(matches(isDisplayed()));
        onView(withId(R.id.edit_password)).check(matches(isDisplayed()));
        onView(withId(R.id.btn_login)).check(matches(isDisplayed()));
    }

    @Test
    public void loginButton_withEmptyFields_showsError() {
        FragmentScenario.launchInContainer(LoginFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.btn_login)).perform(click());
        // Should display error (fields are empty)
        onView(withId(R.id.text_error)).check(matches(isDisplayed()));
    }

    @Test
    public void loginForm_acceptsTextInput() {
        FragmentScenario.launchInContainer(LoginFragment.class, null,
                R.style.Theme_RoadRunner);

        onView(withId(R.id.edit_username))
                .perform(typeText("testuser"), closeSoftKeyboard());
        onView(withId(R.id.edit_password))
                .perform(typeText("testpass"), closeSoftKeyboard());

        onView(withId(R.id.edit_username)).check(matches(withText("testuser")));
    }
}
