package com.example.alexa_avs_android;

import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {
    private final String TAG = this.getClass().getSimpleName() + " JW";

    private String mAuthCode = "";

    public HttpServer(int port) {
        super(port);
    }

    public HttpServer(String hostname, int port) {
        super(hostname, port);
    }

    @Override
    public Response serve(IHTTPSession session) {

        try {
            session.parseBody(new HashMap<String, String>());
            //Log.d(TAG, session.getMethod() + "\t" + session.getParameters());
            // Decode String
            List<String> list = session.getParameters().get("code");
            if(list!=null && !list.isEmpty()) {
                mAuthCode = list.get(0);
                Log.d(TAG, "  CODE = " + mAuthCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ResponseException e) {
            e.printStackTrace();
        }


        String url = session.getUri();
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE html><html><body>");
        builder.append("<H1>\r\n");
        builder.append("The Url = " + url + "<br>\r\n");
        builder.append("Parameters = " + session.getMethod() + "\t" + session.getParameters() + "<br>\r\n");
        builder.append("</body></html>\r\n");
        return newFixedLengthResponse(builder.toString());

    }

    public String getAuthCode(){
        return mAuthCode;
    }
}
