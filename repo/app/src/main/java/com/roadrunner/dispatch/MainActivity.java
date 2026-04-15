package com.roadrunner.dispatch;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.roadrunner.dispatch.R;

/**
 * Single-activity host for the Navigation component.
 *
 * All role-specific screens (login, admin dashboard, dispatcher dashboard,
 * worker dashboard, compliance dashboard) are implemented as Fragments managed
 * by the NavHostFragment declared in {@code activity_main.xml}.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}
