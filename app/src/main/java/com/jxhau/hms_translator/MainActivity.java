package com.jxhau.hms_translator;

import android.app.Activity;
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
import android.widget.ImageView;
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

    Button imageButton, translateButton, copyButton;
    TextView textLabel, output;
    EditText input;
    Spinner spinner;
    ImageView arrow;

    // Language detection
    String[] languages = {"en", "zh", "ms", "de", "ja"};
    String sourceLanguage, targetLanguage;
    Handler handler = new Handler();
    long delay = 500;
    long lastTextEdit = 0;

    // Text recognition
    MLTextAnalyzer textAnalyzer;
    MLFrame frame;
    Uri imageUri;
    ProgressDialog progressDialog;
    String recognizedText;

    // Banner Ads
    BannerView bannerView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity_layout);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        textLabel = findViewById(R.id.textLabel);
        input = findViewById(R.id.text_input);
        output = findViewById(R.id.text_translated);
        arrow = findViewById(R.id.arrow);

        // load banner ads
        bannerView = findViewById(R.id.banner_view);
        bannerView.setAdId("testw6vs28auh3");
        bannerView.setBannerAdSize(BannerAdSize.BANNER_SIZE_360_57);
        bannerView.setBannerRefresh(60);
        AdParam adParam = new AdParam.Builder().build();
        bannerView.loadAd(adParam);

        // Initialize API Key
        MLApplication.getInstance().setApiKey("DAEDAN1th4UB+yHzfqawRlkNvkidXG2jw1JtlXmqETI2aCXXrER0z1Vjd/P3gGy4oWidfMActLxsrCvIxjkLbrU6Xo1/TgmhS3l0JQ==");
        // Initialize Text Recognition Settings
        initializeSettings();

        // select target language
        spinner = findViewById(R.id.spinner);
        // Create list for spinner: language selection
        List<String> spinnerList = new ArrayList<String>();
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.languages_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        // extract text from image
        imageButton = findViewById(R.id.img_btn);
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textFromImage();
            }
        });

        // translate text
        translateButton = findViewById(R.id.text_translate_btn);
        translateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                translateText(input.getText().toString());
            }
        });

        // copy text to clipboard
        copyButton = findViewById(R.id.copy_btn);
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                copyToClipBoard(output.getText().toString());
            }
        });

        // keep updating language and translate
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                handler.removeCallbacks(inputChecker);
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (editable.length() > 0){
                    lastTextEdit = System.currentTimeMillis();
                    handler.postDelayed(inputChecker, delay);
                }
            }
        });
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        // update target language on select
        targetLanguage = languages[i];
        // automatically translate
        translateText(input.getText().toString());
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {}


    private Runnable inputChecker = new Runnable() {
        @Override
        public void run() {
            // automatically detect language while app running
            languageDetect();
            if (System.currentTimeMillis() > (lastTextEdit + delay - 500)){
                translateText(input.getText().toString());
            }
        }
    };

    // detect input language
    private void languageDetect(){
        MLRemoteLangDetector detector = MLLangDetectorFactory.getInstance().getRemoteLangDetector();
        Task<String> task = detector.firstBestDetect(input.getText().toString());
        task.addOnSuccessListener(new OnSuccessListener<String>() {
            @Override
            public void onSuccess(String s) {
                // language detection success
                // Toast.makeText(getBaseContext(), s, Toast.LENGTH_SHORT).show();
                // set source language
                sourceLanguage = s;
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                // language detection failed
            }
        });

        if (detector != null){
            detector.stop();
        }
    }

    private void textFromImage(){
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_PICK);
        startActivityForResult(Intent.createChooser(intent, "Select Image"), 20);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 20 && resultCode == Activity.RESULT_OK && null != data){
            imageUri = data.getData();
            frame = null;
            try {
                frame = MLFrame.fromFilePath(this, imageUri);
                // analyze and extract text from image
                startAnalysis();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    // settings for text recognition
    private void initializeSettings(){
        List<String> languageList = new ArrayList();
        languageList.add("en");     // english // iso 639-1 code
        languageList.add("zh");     // chinese
        languageList.add("ms");     // bm
        languageList.add("de");     // german
        languageList.add("ja");     // japanese

        // Create ML Text Settings
        MLRemoteTextSetting mlRemoteTextSetting = new MLRemoteTextSetting.Factory()
                .setTextDensityScene(MLRemoteTextSetting.OCR_LOOSE_SCENE)
                .setLanguageList(languageList)
                .setBorderType(MLRemoteTextSetting.ARC)
                .create();

        textAnalyzer = MLAnalyzerFactory.getInstance().getRemoteTextAnalyzer(mlRemoteTextSetting);
    }

    // start text recognition
    private void startAnalysis(){
        // ProgressDialog while text recognition
        progressDialog = ProgressDialog.show(this, "Text Extraction", "Recognition in progress...");

        Task<MLText> textTask = textAnalyzer.asyncAnalyseFrame(frame);
        textTask.addOnSuccessListener(new OnSuccessListener<MLText>() {
            @Override
            public void onSuccess(MLText mlText) {
                // Text Recognition Success
                Toast.makeText(getBaseContext(), "Recognition success!", Toast.LENGTH_SHORT).show();
                recognizedText = mlText.getStringValue();
                progressDialog.dismiss();
                stopAnalyzer();
                // place recognized text in input field
                input.setText(recognizedText);
                // translate recognized text
                translateText(recognizedText);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                // Text recognition failed
                try {
                    MLException mlException = (MLException) e;
                    int errorCode = mlException.getErrCode();
                    String errorMessage = mlException.getMessage();
                    Log.i("Recognition", errorCode + errorMessage);
                    Toast.makeText(getBaseContext(), "Recognition failed!", Toast.LENGTH_SHORT).show();
                    progressDialog.dismiss();
                    stopAnalyzer();
                } catch (Exception error){
                }
            }
        });
    }

    private void stopAnalyzer(){
        // stop the analyzer after recognition done
        if (textAnalyzer != null){
            try {
                textAnalyzer.stop();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private void translateText(final String raw){
        MLRemoteTranslateSetting setting = new MLRemoteTranslateSetting.Factory()
                .setSourceLangCode(sourceLanguage)
                .setTargetLangCode(targetLanguage)
                .create();
        final MLRemoteTranslator translator = MLTranslatorFactory.getInstance().getRemoteTranslator(setting);

        MLTranslateLanguage.getCloudAllLanguages().addOnSuccessListener(new OnSuccessListener<Set<String>>() {
            @Override
            public void onSuccess(Set<String> strings) {
                Log.i("GetCloudLanguages", "Success");
            }
        });

        final Task<String> task = translator.asyncTranslate(raw);
        task.addOnSuccessListener(new OnSuccessListener<String>() {
            @Override
            public void onSuccess(String result) {
                // translation success
                output.setText(result);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                // translation failed
                try {
                    MLException mlException = (MLException) e;
                    int errorCode = mlException.getErrCode();
                    String errorMessage = mlException.getMessage();
                } catch (Exception error){
                    Toast.makeText(getBaseContext(), "Translation failed!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void copyToClipBoard(String text){
        try {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipData clipData = ClipData.newPlainText("Raw text", text);
            clipboardManager.setPrimaryClip(clipData);
            Toast.makeText(getBaseContext(), "Copied", Toast.LENGTH_SHORT).show();
        } catch (Exception e){
            Toast.makeText(getBaseContext(), "Couldn't copy! Please try again.", Toast.LENGTH_SHORT).show();
        }
    }
}
