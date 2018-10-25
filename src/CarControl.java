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
    Alley alley;
    Boolean alleyFlag;
    
    
    public Conductor(int no, CarDisplayI cd, Gate g, Semaphore[][] semTiles, Boolean[][] alleyTiles, Alley alley, Barrier bar) {

    	
    	this.alley = alley;
    	this.alleyTiles = alleyTiles;
    	this.semTiles = semTiles;
        this.no = no;
        this.cd = cd;
        this.bar = bar;
        mygate = g;
        startpos = cd.getStartPos(no);
        barpos   = cd.getBarrierPos(no);  // For later use
        alleyFlag = false;
        
        col = chooseColor();

        // special settings for car no. 0
        if (no==0) {
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
                newTile = semTiles[newpos.row][newpos.col];
                
              //Check if we are entering the alley
                if(alleyTiles[newpos.row][newpos.col] && !alleyFlag){
                	alley.enter(no);
                	alleyFlag = true;
                } else if(!alleyTiles[newpos.row][newpos.col] && alleyFlag){
                	alley.leave(no);
                	alleyFlag = false;
                }
                
                if(newTile != curTile){	//Waits for new position to free up
                	semTiles[newpos.row][newpos.col].P();
                }
                
                
                
                
                car.driveTo(newpos);
                //Frees up old position
                if(newTile != curTile){
                	semTiles[curpos.row][curpos.col].V();
                }

                curpos = newpos;
                curTile = newTile;
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

    
    Semaphore tempSem;
    
    public CarControl(CarDisplayI cd) {
        this.cd = cd;
        conductor = new Conductor[9];
        gate = new Gate[9];
        semTiles = new Semaphore[ROWS][COLS];
        alleyTiles = new Boolean[ROWS][COLS];
        bar = new Barrier();
        alley = new Alley();
        
        //Setup tiles
        for(int i = 0; i < ROWS; i++){
        	for (int j = 0; j<COLS; j++){
        		semTiles[i][j] = new Semaphore(1);
        		alleyTiles[i][j] = false;
        	}
        }
        
        //Setup Alley
        for (int i = 1; i < ROWS-1; i++) { alleyTiles[i][0] = true; }
        //Adds the area around the shed to the alley
        alleyTiles[ROWS-2][1] = true;
        alleyTiles[ROWS-2][2] = true;
        
        for (int no = 0; no < 9; no++) {
            gate[no] = new Gate();
            conductor[no] = new Conductor(no,cd,gate[no], semTiles, alleyTiles, alley, bar);
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
				bar.threshold(k);
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

	final int MAX_NO_CARS_ALLEY = 4;
	int carCounter = 0;
	int waitCars = 0;
	Boolean curDir = false; //False = up, True = down
	Semaphore[] sems = new Semaphore[MAX_NO_CARS_ALLEY];
	Boolean[] waiting = new Boolean[MAX_NO_CARS_ALLEY];  
	
	public Alley(){
		for (int i = 0; i< MAX_NO_CARS_ALLEY; i++){
			sems[i] = new Semaphore(0);
			waiting[i] = false;
			
		}
	}
	
	public void enter(int no) throws InterruptedException{
		//If the car is going in the wrong direction, and it there are cars in the alley, wait
		if(carCounter != 0 && no>4 != curDir){
			waiting[no%4] = true;
			sems[no%4].P();
			
		}
		carCounter++;
		curDir = no>4;
	}
	
	public void leave(int no){
		//Checks if the current car matches the direction
		if(no>4 == curDir){
			if (carCounter > 1){
				//In case of more cars we go down
				carCounter--;
			} else {
				//In case that this is the last car, release the waiting cars
				carCounter = 0;
				for (int i = 0; i< MAX_NO_CARS_ALLEY; i++){
					if(waiting[i]){
						sems[i].V();
						waiting[i] = false;
					}
				}
			}
		}
	}
	
	
}




class Barrier {
	
	final int MAX_NO_CARS = 9;
	
	Boolean flag = false;
	Semaphore[] sems = new Semaphore[MAX_NO_CARS]; //Semaphore for each car
	Boolean[] waiting = new Boolean[MAX_NO_CARS];  //Flag if the car is waiting
	int counter = 0;
	int threshold = MAX_NO_CARS;
	
	public Barrier(){
		for(int i = 0; i<MAX_NO_CARS;i++){
			sems[i] = new Semaphore(0);
			waiting[i] = false;
		}
	}
	
	public void sync(int no) throws InterruptedException {
		if(flag) {
			counter++;
			if (counter >= threshold) {
				//Frees the other cars
				freeCars();
			} else {
				waiting[no] = true;
				sems[no].P();		
			}
		}
	}
	
	
	public void on(){
		flag = true;
		
	}
	
	public void off(){
		flag = false;
		//Frees the trapped cars
		freeCars();
		
	}
	
	//Setting the amount of cars that the barrier keeps back
	public void threshold(int k) throws IndexOutOfBoundsException{
		if(k <= MAX_NO_CARS){
			//Sets the new threshold
			threshold = k;
			if (counter >= threshold){
				freeCars();
			}
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
				sems[i].V();
				waiting[i] = false;
			}
			
		}
		
	}
	
	
}





