//Prototype implementation of Car Test class
//Mandatory assignment
//Course 02158 Concurrent Programming, DTU, Fall 2018

//Hans Henrik Lovengreen      Oct 8, 2018

public class CarTest extends Thread {

    CarTestingI cars;
    int testno;

    public CarTest(CarTestingI ct, int no) {
        cars = ct;
        testno = no;
    }

    public void run() {
        try {
            switch (testno) { 
            case 0:
                // Demonstration of startAll/stopAll.
                // Should let the cars go one round (unless very fast)
                cars.startAll();
                sleep(3000);
                cars.stopAll();
                break;

            case 19:
                // Demonstration of speed setting.
                cars.println("Setting high speeds");
                for (int i = 1; i < 9; i++) {
                    cars.setSpeed(i,15.0);
                    cars.setVariation(i,20);
                };
                break;

            default:
                cars.println("Test " + testno + " not available");
            }

            cars.println("Test ended");

        } catch (Exception e) {
            System.err.println("Exception in test: "+e);
        }
    }

}



