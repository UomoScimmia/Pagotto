package com.pag.socialz.Activities;

import android.os.Bundle;

import com.pag.socialz.R;

public class ChatChooseActivity extends BaseActivity {

    private static final String TAG = ChatChooseActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_choose);
        if(actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);

    }
}
