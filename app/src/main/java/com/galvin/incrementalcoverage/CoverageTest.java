package com.galvin.incrementalcoverage;

public class CoverageTest {
    /**
     * make some if else, switch, catch blocks
     */
    private void testBranches(int num) {
        if (num > 0) {
            System.out.println("num is positive");
        } else {
            System.out.println("num is negative");
        }

        switch (num) {
            case 1:
                System.out.println("num is 1");
                break;
            case 2:
                System.out.println("num is 2");
                break;
            case 3:
                System.out.println("num is 3");
                break;
            default:
                System.out.println("num is default");
                break;
        }

        try {
            Thread.sleep(100 * num);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void testInnerClass() {
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("I'am running");
            }
        }.start();
    }

    public void test() {
        testBranches(2);
        testInnerClass();
    }
}
