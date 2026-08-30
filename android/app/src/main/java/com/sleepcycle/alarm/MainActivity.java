package com.sleepcycle.alarm;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(AlarmSoundPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
