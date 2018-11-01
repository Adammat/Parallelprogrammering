//Prototype implementation of Car Control
//Mandatory assignment
//Course 02158 Concurrent Programming, DTU, Fall 2018

//Hans Henrik Lovengreen      Oct 8, 2018


import java.awt.Color;

class Gate {

    Semaphore g = new Semaphore(0);
    Semaphore e = new Semaphore(1);
    boolean isopen = false;

    public void pass() throws InterruptedException {
        g.P(); 
        g.V();
    }

    public void open() {
        try { e.P(); } catch (InterruptedException e) {}
        if (!isopen) { g.V();  isopen = true; }
        e.V();
    }

    public void close() {
        try { e.P(); } catch (InterruptedException e) {}
        if (isopen) { 
            try { g.P(); } catch (InterruptedException e) {}
            isopen = false;
        }
        e.V();
    }

}

class Conductor extends Thread {

    final static int steps = 10;
    static final int upperBarRow = 4;
    static final int lowerBarRow = 5;

    double basespeed = 6.0;          // Tiles per second
    double variation =  50;          // Percentage of base speed

    CarDisplayI cd;                  // GUI part
    Semaphore[][] semTiles;			 // anti crash semaphores
    Boolean[][] alleyTiles;			 //Grid for the alley

    int no;                          // Car number
    Pos startpos;                    // Start position (provided by GUI)
    Pos barpos;                      // Barrier position (provided by GUI)
    Color col;                       // Car  color
    Gate mygate;                     // Gate at start position

    Pos curpos;                      // Current position 
    Pos newpos;                      // New position to go to

    Semaphore curTile;				//Current tile semaphore
    Semaphore newTile;				//New tile semaphore
    
    Barrier bar;
    Alley alley = new Alley();
    Pos alleyEnter;
    Pos alleyLeave;
    
    
    public Conductor(int no, CarDisplayI cd, Gate g, Semaphore[][] semTiles, Alley alley, Barrier bar, Pos alleyEnter, Pos alleyLeave) {

    	
    	this.alleyEnter = alleyEnter;
        this.alleyLeave = alleyLeave;
        this.alley = alley;
        this.semTiles = semTiles;
        this.no = no;
        this.cd = cd;
        this.bar = bar;
        mygate = g;
        startpos = cd.getStartPos(no);
        barpos   = cd.getBarrierPos(no);  // For later use
        
        col = chooseColor();

        // special settings for car no. 0
        if (true) {
            basespeed = -1.0;  
            variation = 0; 
        }
    }

    public synchronized void setSpeed(double speed) { 
        basespeed = speed;
    }

    public synchronized void setVariation(int var) { 
        if (no != 0 && 0 <= var && var <= 100) {
            variation = var;
        }
        else
            cd.println("Illegal variation settings");
    }

    synchronized double chooseSpeed() { 
        double factor = (1.0D+(Math.random()-0.5D)*2*variation/100);
        return factor*basespeed;
    }

    Color chooseColor() { 
        return Color.blue; // You can get any color, as longs as it's blue 
    }

    Pos nextPos(Pos pos) {
        // Get my track from display
        return cd.nextPos(no,pos);
    }

    boolean atGate(Pos pos) {
        return pos.equals(startpos);
    }

	private boolean atBarrier(Pos pos) {
		//Checks if current car is at the barrier
		return pos.equals(cd.getBarrierPos(no));
	}
    
    public void run() {
        try {
            CarI car = cd.newCar(no, col, startpos);
            curpos = startpos;
            curTile = semTiles[curpos.row][curpos.col];
            cd.register(car);

            
            while (true) { 
            	
                if (atGate(curpos)) { 
                    mygate.pass(); 
                    car.setSpeed(chooseSpeed());
                }
                
                if (atBarrier(curpos)) {
                	bar.sync(no);
                }	
                
                newpos = nextPos(curpos);
                
              //Check if we are entering the alley
                if(newpos.equals(alleyEnter)){
                	alley.enter(no);
                } 
               
               
               
                semTiles[newpos.row][newpos.col].P();
                
                car.driveTo(newpos);
                //Frees up old position
                semTiles[curpos.row][curpos.col].V();
                
                
                if(newpos.equals(alleyLeave)){
                	alley.leave(no);
                }
                curpos = newpos;
            }

        } catch (Exception e) {
            cd.println("Exception in Car no. " + no);
            System.err.println("Exception in Car no. " + no + ":" + e);
            e.printStackTrace();
        }
    }



}

public class CarControl implements CarControlI{

	Barrier bar;
    CarDisplayI cd;           // Reference to GUI
    Conductor[] conductor;    // Car controllers
    Gate[] gate;              // Gates
    Semaphore[][] semTiles;
    Boolean[][] alleyTiles;
    Alley alley;
    static final int ROWS = 11;
    static final int COLS = 12;
	

    Pos[] alleyEnter;
    Pos[] alleyLeave;
    
    Semaphore tempSem;
    
    public CarControl(CarDisplayI cd) {
        this.cd = cd;
        conductor = new Conductor[9];
        gate = new Gate[9];
        semTiles = new Semaphore[ROWS][COLS];
        bar = new Barrier();
        alley = new Alley();
        alleyEnter = new Pos[9];
        alleyLeave = new Pos[9];
        
        //Setup tiles
        for(int i = 0; i < ROWS; i++){
        	for (int j = 0; j<COLS; j++){
        		semTiles[i][j] = new Semaphore(1);
        	}
        }
        
        Pos tempAlleyEnter;
        Pos tempAlleyLeave;
        for (int no = 0; no < 9; no++) {
        	//Setup alley enter and leave points
        	if(no<3){
        		tempAlleyEnter = new Pos(ROWS-3,0);
        		tempAlleyLeave = new Pos(1,1);
        	} else if (no < 5){
        		tempAlleyEnter = new Pos(ROWS-2,2);
        		tempAlleyLeave = new Pos(1,1);
            } else {
            	tempAlleyEnter = new Pos(1,0);
            	tempAlleyLeave = new Pos(ROWS-1, 2);
            }
        	
            gate[no] = new Gate();
            conductor[no] = new Conductor(no,cd,gate[no], semTiles, alley, bar, tempAlleyEnter, tempAlleyLeave);
            conductor[no].setName("Conductor-" + no);
            conductor[no].start();
        } 
    }

    public void startCar(int no) {
        gate[no].open();
    }

    public void stopCar(int no) {
        gate[no].close();
    }

    public void barrierOn() { 
        bar.on();
    }

    public void barrierOff() {
    	bar.off();
    	}

    public void barrierSet(int k) { 
        
        	try {
				bar.barrierSet(k);
			} catch (IndexOutOfBoundsException e) {
				// Prints the error to the console
				cd.println(e.toString());
			}
        	
        //try { Thread.sleep(3000); } catch (InterruptedException e) { }
    }

    public void removeCar(int no) { 
        cd.println("Remove Car not implemented in this version");
    }

    public void restoreCar(int no) { 
        cd.println("Restore Car not implemented in this version");
    }

    /* Speed settings for testing purposes */

    public void setSpeed(int no, double speed) { 
        conductor[no].setSpeed(speed);
    }

    public void setVariation(int no, int var) { 
        conductor[no].setVariation(var);
    }

}

class Alley{

	final int MAX_NO_CARS = 8;
	int carCounter = 0;
	int waitCars = 0;
	Boolean curDir = false; //False = up, True = down
	Semaphore[] sems = new Semaphore[MAX_NO_CARS];
	Boolean[] waiting = new Boolean[MAX_NO_CARS];  
	Semaphore lock = new Semaphore(1);
	
	
	public Alley(){
		for (int i = 0; i< MAX_NO_CARS; i++){
			sems[i] = new Semaphore(0);
			waiting[i] = false;
			
		}
	}
	
	public void enter(int no) throws InterruptedException{
		
		lock.P();
		//If there are cars in the alley and the car is going in the wrong direction, wait
		while(carCounter != 0 && no>4 != curDir){
			waiting[no-1] = true;
			lock.V();
			sems[no-1].P();
			lock.P();
		}
		curDir = no>4;
		carCounter++;
		
		lock.V();//*/
		
	}
	
	public void leave(int no){
		try {lock.P();} catch (InterruptedException e) {}
		//Checks if the current car matches the direction
		if(no>4 == curDir){
			carCounter--;
			if (carCounter <= 0){
				//In case that this is the last car, release the waiting cars
				carCounter = 0;
				int offset;
				if (no>4){
					offset = 0;
				} else {
					offset = 4;
				}
				for (int i = offset; i< 4+offset; i++){
					if(waiting[i]){
						waiting[i] = false;
						sems[i].V();
					}
				}
				
			}
		}//*/
		lock.V();
	}
	
	public String toString(){
		return "Car Counter: "+carCounter;
	}
	
}




class Barrier {
	
	final int MAX_NO_CARS = 9;
	
	Boolean flag = false;
	Semaphore[] sems = new Semaphore[MAX_NO_CARS]; //Semaphore for each car
	Boolean[] waiting = new Boolean[MAX_NO_CARS];  //Flag if the car is waiting
	int counter = 0;
	int threshold = MAX_NO_CARS;
	//int newThreshold = threshold;
	Semaphore lock = new Semaphore(1);
	
	
	public Barrier(){
		for(int i = 0; i<MAX_NO_CARS;i++){
			sems[i] = new Semaphore(0);
			waiting[i] = false;
		}
	}
	
	public void sync(int no) throws InterruptedException {
		if(flag) {
			lock.P();
			counter++;
			if (counter >= threshold) {
				//Frees the other cars
				freeCars();
			} else {
				waiting[no] = true;
				lock.V();
				sems[no].P();
				lock.P();
			}
			lock.V();
		}
	}
	
	
	public void on(){
		try {lock.P();} catch (InterruptedException e) {}
		flag = true;
		lock.V();
	}
	
	public void off(){
		try {lock.P();} catch (InterruptedException e) {}
		flag = false;
		//Frees the trapped cars
		freeCars();
		
		lock.V();
	}
	
	//Setting the amount of cars that the barrier keeps back
	public void barrierSet(int k) throws IndexOutOfBoundsException{
		if(k <= MAX_NO_CARS){
			try {lock.P();} catch (InterruptedException e) {}
			//Sets the new threshold
			threshold = k;
			if (counter >= threshold){
				freeCars();
			}
			lock.V();
		}else{
			throw new IndexOutOfBoundsException("Threshold greater than Max number of cars"); 
		}
	}
	
	private void freeCars(){
		counter = 0;
		//Itterates over the cars
		for (int i = 0; i <MAX_NO_CARS; i++){
			//If the car is waiting, free it
			if (waiting[i]){
				waiting[i] = false;
				sems[i].V();
				
			}
			
		}
		//threshold = newThreshold;
		
	}
	
	public String toString(){//For debugging
		return "Counter: " + counter;
	}
	
	
}





