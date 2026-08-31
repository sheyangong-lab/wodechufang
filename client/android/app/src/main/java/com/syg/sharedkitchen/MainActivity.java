package com.syg.sharedkitchen;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SyncBridgePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
