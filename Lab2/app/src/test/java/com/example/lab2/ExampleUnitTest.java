package com.example.lab2;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Local Unit Test - Test chay tren Java VM (development machine)
 * 
 * Khong can Android framework, chay rat nhanh
 * Dung cho logic kinh doanh don gian, unit tests nho
 * 
 * Vi du: testing math, string processing, algorithm, v.v.
 * 
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    /**
     * addition_isCorrect - Test kiem tra phep cong don gian
     * Kiem tra: 2 + 2 = 4
     */
    @Test
    public void addition_isCorrect() {
        // Kiem tra rang 4 = 2 + 2
        assertEquals(4, 2 + 2);
    }
}