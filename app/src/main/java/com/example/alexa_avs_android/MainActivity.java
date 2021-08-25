package com.example.alexa_avs_android;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;


public class MainActivity extends AppCompatActivity {
    private final String TAG = this.getClass().getSimpleName() + " JW";

    private EditText mInput_Code, mInput_Text;
    private HttpServer mHttpServer;
    private MediaPlayer mMediaPlayer;
    private TTSManager mTTSManager;

    private final String mSaveDirPath = Environment.getExternalStorageDirectory().getPath() + "/alexa/";
    private final String mMp3FileName = "response.mp3";
    private final String mTTSFileName = "TTSFile.wav";

    // Alexa Parameter
    private String mAccess_Token;
    private String mRefresh_Token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mInput_Code = (EditText) findViewById(R.id.input_code);
        mInput_Text = (EditText) findViewById(R.id.input_text);

        mMediaPlayer = new MediaPlayer();
        mTTSManager = new TTSManager();
        mTTSManager.initTTS(this);

        try {
            FileUtils.forceMkdir(new File(mSaveDirPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        startHttpServer();
    }

    private void startHttpServer(){
        mHttpServer = new HttpServer(9745);
        try {
            mHttpServer.start();
        }catch (Exception e) {
            //當斷線時會跳到catch,可以在這裡寫上斷開連線後的處理
            e.printStackTrace();
            Log.e(TAG, "HttpServer Exception = " + e.toString());
        }
    }

    private String GetAmazonUrl() {
        String CLIENT_ID = "amzn1.application-oa2-client.479a904b71c84e14802467e80277c0ee";
        String DEVICE_TYPE_ID = "test_device";
        String DEVICE_SERIAL_NUMBER = "12a847876062b5132bbb1a9c1f70a0f9a99c661f292952021ec7a4a4af90b5ab";
        String REDIRECT_URI = "http://localhost:9745/authresponse";
        String RESPONSE_TYPE = "code";
        String SCOPE = "alexa:all";
        String SCOPE_DATA = "{\"alexa:all\": {\"productID\": \"" + DEVICE_TYPE_ID + "\", \"productInstanceAttributes\": {\"deviceSerialNumber\": \"" + DEVICE_SERIAL_NUMBER + "\"}}}";
        String AUTH_URL = "https://www.amazon.com/ap/oa?client_id=" + CLIENT_ID + "&scope=" + SCOPE + "&scope_data=" + SCOPE_DATA + "&response_type=" + RESPONSE_TYPE + "&redirect_uri=" + REDIRECT_URI + "";
        Log.d(TAG, "AUTH_URL = " + AUTH_URL);
        showMsg(AUTH_URL);
        return AUTH_URL;
    }

    public void Get_AuthCode(View v) {
        trustAllHosts();
        // Go to the Browser
        String url = GetAmazonUrl();
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);

    }

    public void Get_Token(View v){
        trustAllHosts();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    String CLIENT_ID = "amzn1.application-oa2-client.479a904b71c84e14802467e80277c0ee";
                    String CLIENT_SECRET = "12a847876062b5132bbb1a9c1f70a0f9a99c661f292952021ec7a4a4af90b5ab";
                    String CODE = mHttpServer.getAuthCode();
                    String GRANT_TYPE = "authorization_code";
                    String REDIRECT_URL = "http://localhost:9745/authresponse";
                    Log.d(TAG, "CODE = " + CODE);

                    URL url = new URL("https://api.amazon.com/auth/o2/token");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setUseCaches(false);
                    connection.connect();

                    // add http parameter
                    String body =   "grant_type=" + GRANT_TYPE +
                                    "&code=" + CODE +
                                    "&client_id=" + CLIENT_ID +
                                    "&client_secret=" + CLIENT_SECRET +
                                    "&redirect_uri=" + REDIRECT_URL + "";
//                    Log.d(TAG, body);
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
                    writer.write(body);
                    writer.close();

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Token responseCode = " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream inputStream = connection.getInputStream();
                        String result = is2String(inputStream);//将流转换为字符串。
//                        Log.d(TAG, "Result: " + result);
                        // decode result
                        JSONObject jsonObject = new JSONObject(result);
                        mAccess_Token = jsonObject.getString("access_token");
                        mRefresh_Token = jsonObject.getString("refresh_token");
                        showMsg(mAccess_Token);
                        Log.d(TAG, "access_token  = " + mAccess_Token);
                        Log.d(TAG, "refresh_token = " + mRefresh_Token);
                    } else {
                        String errStr = "Error: Get Token responseCode = " + responseCode + " returned is incorrect.";
                        showMsg(errStr);
                        Log.d(TAG, errStr);
                    }

                } catch (Exception e) {
                    //當斷線時會跳到catch,可以在這裡寫上斷開連線後的處理
                    e.printStackTrace();
                    Log.e(TAG, "Exception HttpURLConnection" + e.toString());
                }
            }
        }).start();
    }

    public void Refresh_Token(View v) {
        trustAllHosts();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    String CLIENT_ID = "amzn1.application-oa2-client.479a904b71c84e14802467e80277c0ee";
                    String CLIENT_SECRET = "12a847876062b5132bbb1a9c1f70a0f9a99c661f292952021ec7a4a4af90b5ab";
                    String GRANT_TYPE = "refresh_token";
                    String REDIRECT_URL = "http://localhost:9745/authresponse";

                    //URL url = new URL(Url);
                    URL url = new URL("https://api.amazon.com/auth/o2/token");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setUseCaches(false);
                    connection.connect();

                    // add http parameter
                    String body =   "grant_type=" + GRANT_TYPE +
                            "&refresh_token=" + mRefresh_Token +
                            "&client_id=" + CLIENT_ID +
                            "&client_secret=" + CLIENT_SECRET +
                            "&redirect_uri=" + REDIRECT_URL + "";
//                    Log.d(TAG, body);
                    BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"));
                    writer.write(body);
                    writer.close();

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Refresh Token responseCode = " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream inputStream = connection.getInputStream();
                        String result = is2String(inputStream);//将流转换为字符串。
                        Log.d(TAG, "Result: " + result);
                        // decode result
//                        JSONObject jsonObject = new JSONObject(result);
//                        mAccess_Token = jsonObject.getString("access_token");
//                        mRefresh_Token = jsonObject.getString("refresh_token");
//                        showText(mAccess_Token);
//                        Log.d(TAG, "access_token  = " + mAccess_Token);
//                        Log.d(TAG, "refresh_token = " + mRefresh_Token);
                    } else {
                        String errStr = "Error: Refresh Token responseCode = " + responseCode + " returned is incorrect.";
                        showMsg(errStr);
                        Log.d(TAG, errStr);
                    }

                } catch (Exception e) {
                    //當斷線時會跳到catch,可以在這裡寫上斷開連線後的處理
                    e.printStackTrace();
                    Log.e(TAG, "Exception HttpURLConnection" + e.toString());
                }
            }
        }).start();
    }

    public void Get_Alexa(View v) {
        trustAllHosts();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://access-alexa-na.amazon.com/v1/avs/speechrecognizer/recognize");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setUseCaches(false);
                    connection.addRequestProperty("Authorization", "Bearer " + mAccess_Token);
                    connection.addRequestProperty("Content-Type", "multipart/form-data; boundary=BOUNDARY1234");
                    connection.setRequestProperty("Connection", "Keep-Alive");
                    connection.connect();

                    // read Assets/multipart_body.txt
                    InputStream is = getAssets().open("multipart_body.txt");
                    int lenght = is.available();
                    byte[]  buffer = new byte[lenght];
                    is.read(buffer);
                    //String body = new String(buffer, "utf8");
                    //Log.d(TAG, "body = " + body);

                    OutputStream out = connection.getOutputStream();
                    out.write(buffer);
                    out.close();

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Alexa responseCode = " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream inputStream = connection.getInputStream();
                        copyInputStreamToFile(inputStream, new File(mSaveDirPath + mMp3FileName));
                        showMsg(mSaveDirPath + mMp3FileName);
                        Log.d(TAG, "response MP3 save to : " + mSaveDirPath + mMp3FileName);
                        // playing MP3 file
                        playAudioFile();
                    } else {
                        Log.d(TAG, "Error : The responseCode = " + responseCode + " returned is incorrect.");
                    }

                } catch (Exception e) {
                    //當斷線時會跳到catch,可以在這裡寫上斷開連線後的處理
                    e.printStackTrace();
                    Log.e(TAG, "Exception HttpURLConnection" + e.toString());
                }

            }
        }).start();
    }

    public void Call_Alexa_Assistant(View view) {
        Send_WavToAlexa();
    }

    public void Send_WavToAlexa() {
        getTexttoSpeak();
        showMsg("Connecting with alexa cloud...");
        trustAllHosts();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://access-alexa-na.amazon.com/v1/avs/speechrecognizer/recognize");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setUseCaches(false);
                    connection.addRequestProperty("Authorization", "Bearer " + mAccess_Token);
                    connection.addRequestProperty("Content-Type", "multipart/form-data; boundary=BOUNDARY1234");
                    connection.setRequestProperty("Connection", "Keep-Alive");
                    connection.connect();
                    //------------------------------------------------------------------------------
                    String NEWLINE = "\r\n";
                    String BOUNDARY = "--BOUNDARY1234" + NEWLINE;
                    String METADATA_CONTENT = "Content-Disposition: form-data; name=\"metadata\"" + NEWLINE
                            + "Content-Type: application/json; charset=UTF-8" + NEWLINE + NEWLINE;
                    String METADATA_DATA = "{\"messageHeader\": {},\"messageBody\": {\"profile\": \"alexa-close-talk\",\"locale\": \"en-us\",\"format\": \"audio/L16; rate=16000; channels=1\"}}" + NEWLINE + NEWLINE;
                    String AUDIO_CONTENT = "Content-Disposition: form-data; name=\"audio\"" + NEWLINE +
                            "Content-Type: audio/L16; rate=16000; channels=1" + NEWLINE + NEWLINE;
                    String dataStr = BOUNDARY + METADATA_CONTENT + METADATA_DATA + BOUNDARY + AUDIO_CONTENT;
                    String dataEnd = NEWLINE + NEWLINE + BOUNDARY + NEWLINE;

                    File fp = new File(mSaveDirPath + mTTSFileName);
                    DataOutputStream out = new DataOutputStream(connection.getOutputStream());
                    out.writeBytes(dataStr);
                    out.write(FileUtils.readFileToByteArray(fp));
                    out.writeBytes(dataEnd);
                    out.flush();
                    //------------------------------------------------------------------------------
                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Alexa responseCode = " + responseCode);
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream inputStream = connection.getInputStream();
                        copyInputStreamToFile(inputStream, new File(mSaveDirPath + mMp3FileName));
                        showMsg(mSaveDirPath + mMp3FileName);
                        Log.d(TAG, "response MP3 save to : " + mSaveDirPath + mMp3FileName);
                        // playing MP3 file
                        playAudioFile();
                    } else {
                        String errStr = "Error: The Alexa responseCode = " + responseCode + " returned is incorrect.";
                        showMsg(errStr);
                        Log.d(TAG, errStr);
                    }

                } catch (Exception e) {
                    //當斷線時會跳到catch,可以在這裡寫上斷開連線後的處理
                    e.printStackTrace();
                    Log.e(TAG, "Exception HttpURLConnection" + e.toString());
                }

            }
        }).start();
    }

    private void playAudioFile() {
        try {
            Thread.sleep(500);
            if(mMediaPlayer.isPlaying()){   //檢查是否撥放中
                mMediaPlayer.stop();
            }
            mMediaPlayer.reset();

            mMediaPlayer.setDataSource(mSaveDirPath + mMp3FileName);
            mMediaPlayer.prepare(); //準備播放
            mMediaPlayer.start();   //播放聲音
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void stopAudioPlay(){
        if(mMediaPlayer.isPlaying()){
            mMediaPlayer.stop();
            mMediaPlayer.reset();
        }
    }

    public static String is2String(InputStream in) throws IOException {
        StringBuffer out = new StringBuffer();
        InputStreamReader inread = new InputStreamReader(in, "UTF-8");
        char[] b = new char[4096];
        for (int n; (n = inread.read(b)) != -1;) {
            out.append(new String(b, 0, n));
        }
        return out.toString();
    }

    private static void trustAllHosts() {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[]{};
            }

            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                //Log.i(TAG, "checkClientTrusted");
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                //Log.i(TAG, "checkServerTrusted");
            }
        }};
        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void copyInputStreamToFile(InputStream inputStream, File file)
            throws IOException {

        // append = false
        try (FileOutputStream outputStream = new FileOutputStream(file, false)) {
            int read;
            byte[] bytes = new byte[8192];
            while ((read = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, read);
            }
        }

    }

    private void showMsg(String Str){
        MainActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                mInput_Code.setText(Str);
            }
        });
    }

    private void getTexttoSpeak() {
        showMsg("Convert text to speech. Processing.....");
        String str = mInput_Text.getText().toString().trim();
        if(str.isEmpty() || (str.length() == 0)){
            str = "how are you doing today";
        }
        Log.d(TAG, "TTS input : " + str);
        mTTSManager.initQueue(str);
        mTTSManager.saveToFile(str, mSaveDirPath + mTTSFileName);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void clearInputText(View view) {
        mInput_Text.setText("");
    }

//    @Override
//    public boolean dispatchKeyEvent(KeyEvent event) {
//        Log.d(TAG, "Key Event = " + event.getKeyCode());
//        if(event.getKeyCode() == KeyEvent.KEYCODE_ENTER){
//            Send_WavToAlexa();
//            return true;
//        }
//        return super.dispatchKeyEvent(event);
//    }

    @Override
    protected void onDestroy() {
        mMediaPlayer.release();
        mMediaPlayer = null;
        mTTSManager.TTSStop();
        mTTSManager.TTSDestroy();

        super.onDestroy();
    }

    public void btn_Name1(View view) {
        mInput_Text.setText("what's the Steve Jobs");
    }

    public void btn_Name2(View view) {
        mInput_Text.setText("what's the Jeff Bezos");
    }

    public void btn_Weather1(View view) {
        mInput_Text.setText("what's the weather in Taoyuan");
    }

    public void btn_Weather2(View view) {
        mInput_Text.setText("what's the weather in Taipei");
    }

    public void btn_Math1(View view) {
        mInput_Text.setText("4 plus 6");
    }

    public void btn_Math2(View view) {
        mInput_Text.setText("25 Subtraction 5");
    }

    public void btn_Math3(View view) {
        mInput_Text.setText("6 Multiplication 5");
    }

    public void btn_Math4(View view) {
        mInput_Text.setText("8 factorial");
    }
}