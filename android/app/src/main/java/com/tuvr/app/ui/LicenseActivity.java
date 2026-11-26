package com.tuvr.app.ui;

import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.tuvr.app.R;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Shows the bundled open-source license text from assets/licenses.txt. */
public class LicenseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        TextView text = findViewById(R.id.txt_license);
        text.setText(readLicenses());
    }

    private String readLicenses() {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = getAssets().open("licenses.txt");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception e) {
            return "License text is missing from the app bundle.";
        }
        return sb.toString();
    }
}
