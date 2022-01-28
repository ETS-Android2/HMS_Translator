package com.jxhau.hms_translator;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.huawei.hmf.tasks.OnFailureListener;
import com.huawei.hmf.tasks.OnSuccessListener;
import com.huawei.hmf.tasks.Task;
import com.huawei.hms.ads.AdParam;
import com.huawei.hms.ads.BannerAdSize;
import com.huawei.hms.ads.banner.BannerView;
import com.huawei.hms.mlsdk.MLAnalyzerFactory;
import com.huawei.hms.mlsdk.common.MLApplication;
import com.huawei.hms.mlsdk.common.MLException;
import com.huawei.hms.mlsdk.common.MLFrame;
import com.huawei.hms.mlsdk.langdetect.MLLangDetectorFactory;
import com.huawei.hms.mlsdk.langdetect.cloud.MLRemoteLangDetector;
import com.huawei.hms.mlsdk.text.MLRemoteTextSetting;
import com.huawei.hms.mlsdk.text.MLText;
import com.huawei.hms.mlsdk.text.MLTextAnalyzer;
import com.huawei.hms.mlsdk.translate.MLTranslateLanguage;
import com.huawei.hms.mlsdk.translate.MLTranslatorFactory;
import com.huawei.hms.mlsdk.translate.cloud.MLRemoteTranslateSetting;
import com.huawei.hms.mlsdk.translate.cloud.MLRemoteTranslator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    // Layout element objects
    Spinner spinner;
    Button imgBtn, translateBtn, copyBtn;
    EditText input;
    TextView output, label;


    // Language detection
    String sourceLanguage, targetLanguage;
    String[] languages = {"en", "zh", "ms", "de", "ja"};
    Handler handler = new Handler();
    long delay = 0;


    // Text recognition
    Uri imageUri;
    MLFrame frame;
    ProgressDialog progressDialog;
    MLTextAnalyzer analyzer;


    // Banner ads
    BannerView bannerView;

    // Exit on back press (twice)
    private long pressTime;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity_layout);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // 1. Call layout elements on screen
        label = findViewById(R.id.text_label);
        spinner = findViewById(R.id.spinner);
        imgBtn = findViewById(R.id.img_btn);
        translateBtn = findViewById(R.id.translate_btn);
        copyBtn = findViewById(R.id.copy_btn);
        input = findViewById(R.id.input_text);
        output = findViewById(R.id.output_text);


        // 2. Initialize API key
        MLApplication.getInstance().setApiKey("DAEDANDUoTrQU0fCwSqscyr6xK1CUSQSjWBtu+lRH25CBlQmlPL9nU76y+YqZdTElKtk8XYGKJemou0IZRWku5vQd7mavva3R0TU6Q==");

        // 3. Start translation when click on button
        translateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                translate(input.getText().toString());
            }
        });


        // 4. Copy result text when click on button
        copyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copy(output.getText().toString());
            }
        });


        // 5. Set dropdown list containing 5 languages
        //    React to user input changes - realtime update
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.languages_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);


        // to detect the language and translate after user changes text
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // Detect and Translate
                if (editable.length() > 0){
                    handler.postDelayed(runnable, delay);
                }

            }
        });


        // 6. Text extraction from image when click on button
        //    Initialize Text Recognition Settings
        initializeSettings();
        imgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textFromImage();
            }
        });



        // 7. Set up banner ads
        bannerView = findViewById(R.id.banner_view);
        bannerView.setAdId("testw6vs28auh3");
        bannerView.setBannerAdSize(BannerAdSize.BANNER_SIZE_360_57);
        bannerView.setBannerRefresh(60);
        AdParam param = new AdParam.Builder().build();
        bannerView.loadAd(param);

    }



    private void translate(final String raw){
        // ML Kit realtime translation
        // raw = user input text
        MLRemoteTranslateSetting setting = new MLRemoteTranslateSetting.Factory()
                .setSourceLangCode(sourceLanguage)  // ISO 639-1
                .setTargetLangCode(targetLanguage)
                .create();

        final MLRemoteTranslator translator = MLTranslatorFactory.getInstance().getRemoteTranslator(setting);

        // Obtain languages from cloud
        MLTranslateLanguage.getCloudAllLanguages().addOnSuccessListener(new OnSuccessListener<Set<String>>() {
            @Override
            public void onSuccess(Set<String> strings) {
                Log.i("CLOUD LANGUAGES", "onSuccess");
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                Log.i("CLOUD LANGUAGES", "onFailure");
            }
        });

        languageDetection();
        Task<String> translateTask = translator.asyncTranslate(raw);
        translateTask.addOnSuccessListener(new OnSuccessListener<String>() {
            @Override
            public void onSuccess(String result) {
                // Translation success
                output.setText(result);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                Toast.makeText(getBaseContext(), "Translation failed", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void copy(String result){
        // result = translated text
        try {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData data = ClipData.newPlainText("Translated result", result);
            clipboardManager.setPrimaryClip(data);
            Toast.makeText(getBaseContext(), "Copied", Toast.LENGTH_SHORT).show();
        } catch (Exception e){
            Toast.makeText(getBaseContext(), "Couldn't copy", Toast.LENGTH_SHORT).show();
        }
    }


    private void languageDetection(){
        // ML Kit language detection
        // automatically detect and obtain language code
        // update source language for translation
        MLRemoteLangDetector detector = MLLangDetectorFactory.getInstance().getRemoteLangDetector();
        Task<String> detectionTask = detector.firstBestDetect(input.getText().toString());
        detectionTask.addOnSuccessListener(new OnSuccessListener<String>() {
            @Override
            public void onSuccess(String s) {
                // language detection success
                sourceLanguage = s;
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                // detection failed
                if (input.getText().length() > 0){
                    Toast.makeText(getBaseContext(), "Language detection failed", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (detector != null){
            detector.stop();
        }
    }


    private void textFromImage(){
        // Create chooser intent and let user to pick an image from gallery
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_PICK);
        startActivityForResult(Intent.createChooser(intent, "Select Image"), 20);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 20 && resultCode == RESULT_OK && data != null){
            imageUri = data.getData();
            frame = null;
            try {
                frame = MLFrame.fromFilePath(this, imageUri);
                startAnalysis();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void initializeSettings(){
        // Text recognition
        List<String> languageList = new ArrayList<>();
        for (String lang:languages){
            languageList.add(lang);
        }

        MLRemoteTextSetting setting = new MLRemoteTextSetting.Factory()
                .setLanguageList(languageList)
                .setBorderType(MLRemoteTextSetting.ARC)
                .setTextDensityScene(MLRemoteTextSetting.OCR_COMPACT_SCENE)
                .create();

        analyzer = MLAnalyzerFactory.getInstance().getRemoteTextAnalyzer(setting);
    }


    private void startAnalysis(){
        // Text recognition
        progressDialog = ProgressDialog.show(this, "Text extraction", "Recognition is in progress...");

        Task<MLText> task = analyzer.asyncAnalyseFrame(frame);
        task.addOnSuccessListener(new OnSuccessListener<MLText>() {
            @Override
            public void onSuccess(MLText mlText) {
                // recognition success - put text to input field, translate
                String recognizedText = mlText.getStringValue();
                progressDialog.dismiss();
                stopAnalyzer();
                input.setText(recognizedText);
                translate(recognizedText);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                MLException mlException = (MLException) e;
                int errorCode = mlException.getErrCode();
                String errorMessage = mlException.getMessage();
                Log.i("Text recognition", "onFailure: " + errorCode + errorMessage);
                progressDialog.dismiss();
                stopAnalyzer();
                Toast.makeText(getBaseContext(), "Text recognition failed", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void stopAnalyzer(){
        // Text recognition
        if (analyzer != null){
            try {
                analyzer.stop();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    // TODO: When user selected a language in dropdown list
    // Update target language
    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int position, long l) {
        targetLanguage = languages[position];
        translate(input.getText().toString());
        // Toast.makeText(getBaseContext(), targetLanguage, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }


    // TODO: Let translation always run (realtime)
    // Handler and Runnable
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            translate(input.getText().toString());
        }
    };

    @Override
    public void onBackPressed() {
        if (pressTime + 2000 > System.currentTimeMillis()){
            super.onBackPressed();
            finish();
        } else {
            Toast.makeText(getBaseContext(), "Press back again to exit", Toast.LENGTH_SHORT).show();
        }
        pressTime = System.currentTimeMillis();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
