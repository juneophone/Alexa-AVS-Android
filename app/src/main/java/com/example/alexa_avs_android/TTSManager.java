package com.example.alexa_avs_android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Locale;

public class TTSManager {
    private final String TAG = this.getClass().getSimpleName() + " JW";

    private TextToSpeech mTTS = null;
    private boolean isLoaded = false;
    private Context mContext;

    private String mErrMsg = "Need to install Google TTS and set the preferred engine.";

    public void initTTS(Context context){
        //Log.i(TAG, "initTTS()");
        mContext = context;
        mTTS = new TextToSpeech(context, onInitListener);
    }

    private TextToSpeech.OnInitListener onInitListener = new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(int status) {

            if (status == TextToSpeech.SUCCESS) {
                int result = mTTS.setLanguage(Locale.US);
                isLoaded = true;
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "This Language is not supported");
                }
            } else {
                Log.e(TAG, mErrMsg);
                /*
                When i initiated TextToSpeech in public void onInit(int status)
                I was getting status -1 "Denotes a generic operation failure."
                Problem: In my phone was not install no one TTS.
                Solution:
                    1. Install Google TTS
                    2. Go to a phone setting tehen system Settings->Language & input.->Text-to-speech output.->preferred engine.
                    3. Pick google TTS.

                Download url :
	                https://apkpure.com/speech-services-by-google/com.google.android.tts
	                https://play.google.com/store/apps/details?id=com.google.android.tts&hl=zh_TW&gl=US
                 */
                showAlertDialog();
            }
        }
    };

    public void TTSStop() {
        mTTS.stop();
    }

    public void TTSDestroy() {
        mTTS.shutdown();
    }

    public void addQueue(String text) {
        if (isLoaded)
            mTTS.speak(text, TextToSpeech.QUEUE_ADD, null);
        else
            Log.e(TAG, "TTS Not Initialized");
    }

    public void initQueue(String text) {
        if (isLoaded)
            mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        else
            Log.e(TAG, "TTS Not Initialized");
    }

    public void saveToFile(String str, String path) {
        if (isLoaded) {
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, str);
            mTTS.synthesizeToFile(str, params, path);
        }else {
            Log.e(TAG, "TTS Not Initialized");
        }
    }

    private void startTTSSetting(){
        Intent intent = new Intent();
        intent.setAction("com.android.settings.TTS_SETTINGS");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void showAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle("")
                .setMessage(mErrMsg)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // TODO Auto-generated method stub
                        startTTSSetting();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
        alert.getWindow().getAttributes();
        TextView mMessage = (TextView) alert.findViewById(android.R.id.message);
        mMessage.setTextSize(40);
        alert.getButton(DialogInterface.BUTTON_POSITIVE).setTextSize(50);
    }
}
