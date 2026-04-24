package com.example.lab2;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented Test - Test chay tren device/emulator Android
 * 
 * Cho phep test code ma can Android framework (Context, SharedPreferences, v.v.)
 * Chay cham hon local unit test nhung gan hon thuc te hon
 * 
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    /**
     * useAppContext - Test kiem tra package name ung dung
     * Lay Application Context tu InstrumentationRegistry
     * Kiem tra rang package name la "com.example.lab2"
     */
    @Test
    public void useAppContext() {
        // Lay Context cua ung dung dang test (su dung instrumentation)
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Kiem tra package name
        assertEquals("com.example.lab2", appContext.getPackageName());
    }
}